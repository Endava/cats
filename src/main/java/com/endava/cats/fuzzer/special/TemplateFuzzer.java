package com.endava.cats.fuzzer.special;

import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.args.UserArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.fuzzer.special.mutators.api.Mutator;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.jayway.jsonpath.JsonPathException;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import lombok.Setter;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.endava.cats.util.JsonUtils.NOT_SET;

/**
 * Fuzzer that will do fuzzing based on a supplied template, rather than an OpenAPI Spec.
 */
@Singleton
@SpecialFuzzer
public class TemplateFuzzer implements Fuzzer {
    public static final String EMPTY = "";
    private static final String FAKE_FUZZ = "FAKE_FUZZ";
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(TemplateFuzzer.class);
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final UserArguments userArguments;
    private final MatchArguments matchArguments;
    private final StopArguments stopArguments;
    private final Instance<BodyMutator> mutators;
    private final ExecutionStatisticsListener executionStatisticsListener;

    @Setter
    private boolean random;

    /**
     * Constructs a new TemplateFuzzer instance.
     *
     * @param sc The service caller used to make requests to the service under test.
     * @param lr The test case listener used to report test case results and progress.
     * @param ua The UserArguments object containing the user-specified arguments for the fuzzer.
     * @param ma The MatchArguments object containing the match criteria for identifying valid responses.
     */
    public TemplateFuzzer(ServiceCaller sc, TestCaseListener lr, UserArguments ua,
                          MatchArguments ma, StopArguments sa, Instance<BodyMutator> mutators,
                          ExecutionStatisticsListener el) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.userArguments = ua;
        this.matchArguments = ma;
        this.stopArguments = sa;
        this.mutators = mutators;
        this.executionStatisticsListener = el;
    }

    @Override
    public void fuzz(FuzzingData data) {
        testCaseListener.updateUnknownProgress(data);
        if (random) {
            runRandomFuzzing(data);
        } else {
            runNormalFuzzing(data);
        }
    }

    private void runRandomFuzzing(FuzzingData data) {
        long startTime = System.currentTimeMillis();

        boolean shouldStop = false;
        Set<String> allCatsFields = data.getTargetFields();
        String fakePayloadToMutate = """
                 {"FAKE_FUZZ": "someValue"}
                """; // this is a fake payload that will be mutated

        while (!shouldStop) {
            String targetField = CatsUtil.selectRandom(allCatsFields);
            logger.debug("Selected field to be mutated: [{}]", targetField);

            Mutator selectedRandomMutator = CatsUtil.selectRandom(mutators);
            logger.debug("Selected mutator [{}]", selectedRandomMutator.getClass().getSimpleName());

            String fakeMutatedPayload = selectedRandomMutator.mutate(fakePayloadToMutate, FAKE_FUZZ);
            Object mutatedValue = JsonUtils.getVariableFromJson(fakeMutatedPayload, FAKE_FUZZ);
            if (JsonUtils.isNotSet(String.valueOf(mutatedValue))) {
                mutatedValue = fakeMutatedPayload.substring(1, fakeMutatedPayload.length() - 1);
            }

            String mutatedPayload = data.getPayload();
            if (data.getPayload().contains(targetField)) {
                if (userArguments.isNameReplace()) {
                    mutatedPayload = data.getPayload().replace(targetField, String.valueOf(mutatedValue));
                } else {
                    mutatedPayload = selectedRandomMutator.mutate(data.getPayload(), targetField);
                }
            }

            createRequestAndExecuteTest(data, targetField, String.valueOf(mutatedValue), mutatedPayload, selectedRandomMutator.description());

            shouldStop = stopArguments.shouldStop(executionStatisticsListener.getErrors(), testCaseListener.getCurrentTestCaseNumber(), startTime);
        }
    }

    private void runNormalFuzzing(FuzzingData data) {
        for (String targetField : Optional.ofNullable(data.getTargetFields()).orElse(Collections.emptySet())) {
            int payloadSize = this.getPayloadSize(data, targetField);

            if (payloadSize == 0) {
                logger.skip("Field {} was NOT found in request payload, HTTP headers or path/query parameters!", targetField);
            } else {
                List<String> payloads = this.getAllPayloads(payloadSize);
                logger.info("Running {} payloads for field [{}]", payloads.size(), targetField);

                for (String payload : payloads) {
                    String replacedPayload = this.replacePayload(data, payload, targetField);

                    createRequestAndExecuteTest(data, targetField, payload, replacedPayload, FuzzingStrategy.replace().withData(payload).truncatedValue());
                }
            }
        }
    }

    private void createRequestAndExecuteTest(FuzzingData data, String targetField, Object payload, String replacedPayload, String fuzzDescription) {
        List<KeyValuePair<String, Object>> replacedHeaders = this.replaceHeaders(data, payload, targetField);
        String replacedPath = this.replacePath(data, String.valueOf(payload), targetField);

        CatsRequest catsRequest = CatsRequest.builder()
                .payload(replacedPayload)
                .headers(replacedHeaders)
                .httpMethod(data.getMethod().name())
                .url(replacedPath)
                .build();

        testCaseListener.createAndExecuteTest(logger, this, () -> process(data, catsRequest, targetField, payload, fuzzDescription), data);
        testCaseListener.updateUnknownProgress(data);
    }

    String replacePath(FuzzingData data, String withData, String targetField) {
        if (userArguments.isNameReplace()) {
            return data.getPath().replace(targetField, Optional.ofNullable(withData).orElse(EMPTY));
        }

        String finalPath = data.getPath();
        try {
            //handle http://localhost:8080/
            URL url = URI.create(data.getPath()).toURL();
            String replacedPath = Arrays.stream(url.getPath().split("/"))
                    .map(pathElement -> pathElement.equalsIgnoreCase(targetField) ? withData : pathElement)
                    .collect(Collectors.joining("/"));

            if (!"/".equals(url.getPath())) {
                finalPath = finalPath.replace(url.getPath(), replacedPath);
            }

            if (url.getQuery() != null) {
                String replacedQuery = Arrays.stream(url.getQuery().split("&"))
                        .map(queryParam -> replaceQueryParam(targetField, queryParam, withData))
                        .collect(Collectors.joining("&"));

                finalPath = finalPath.replace(url.getQuery(), replacedQuery);
            }
        } catch (Exception e) {
            logger.debug("There was a problem parsing given path!", e);
            logger.warn("There was an issue parsing {}: {}", data.getPath(), e.getMessage());
        }

        return finalPath;
    }

    static String replaceQueryParam(String targetField, String queryPair, String withValue) {
        String[] queryPairArr = queryPair.split("=", -1);
        if (queryPairArr[0].equalsIgnoreCase(targetField) && queryPairArr.length == 2) {
            return queryPairArr[0] + "=" + withValue;
        } else if (queryPair.equalsIgnoreCase(targetField)) {
            return withValue;
        }
        return queryPair;
    }

    private List<String> getAllPayloads(int payloadSize) {
        try {
            if (userArguments.getWords() == null) {
                List<String> payloads = UnicodeGenerator.getAllPayloadsOfSize(payloadSize);
                payloads.add(UnicodeGenerator.getBadPayload());
                payloads.add(UnicodeGenerator.getZalgoText());
                payloads.add(StringGenerator.generateLargeString(20000));
                payloads.add(EMPTY);
                return payloads;
            } else {
                return Files.readAllLines(Path.of(userArguments.getWords().getAbsolutePath()), StandardCharsets.UTF_8)
                        .stream()
                        .filter(Predicate.not(String::isBlank))
                        .filter(Predicate.not(line -> line.startsWith("# ")))
                        .toList();
            }
        } catch (IOException e) {
            logger.debug("Something went wrong while fuzzing!", e);
            logger.error("Something went wrong while reading user supplied dictionary: {}. The file might not exist or is not reachable. Error message: {}",
                    userArguments.getWords().getAbsolutePath(), e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<KeyValuePair<String, Object>> replaceHeaders(FuzzingData data, Object withData, String targetField) {
        return data.getHeaders().stream()
                .map(catsHeader -> new KeyValuePair<>(catsHeader.getName(),
                        catsHeader.getName().equalsIgnoreCase(targetField) ? withData : catsHeader.getValue()))
                .toList();
    }

    private String replacePayload(FuzzingData data, String withData, String targetField) {
        if (userArguments.isNameReplace()) {
            return data.getPayload().replace(targetField, withData);
        }
        try {
            return FuzzingStrategy.replaceField(data.getPayload(), targetField, FuzzingStrategy.replace().withData(withData)).json();
        } catch (JsonPathException e) {
            return data.getPayload();
        }
    }

    private int getPayloadSize(FuzzingData data, String field) {
        String oldValue = data.getPath().contains(field) ? "CATS_FUZZ" : EMPTY;

        if (oldValue.isEmpty()) {
            oldValue = String.valueOf(JsonUtils.getVariableFromJson(data.getPayload(), field));
        }
        if (oldValue.equalsIgnoreCase(NOT_SET)) {
            oldValue = data.getHeaders().stream()
                    .filter(header -> header.getName().equalsIgnoreCase(field))
                    .map(CatsHeader::getValue)
                    .findFirst()
                    .orElse(EMPTY);
        }
        if (oldValue.isEmpty()) {
            oldValue = data.getPayload().contains(field) ? "CATS_FUZZ" : EMPTY;
        }

        return oldValue.length();
    }

    private void process(FuzzingData data, CatsRequest catsRequest, String targetField, Object fuzzedValue, String fuzzDescription) {
        testCaseListener.addScenario(logger, "Replace request field, header or path/query param [{}], with [{}]", targetField, fuzzDescription);
        testCaseListener.addExpectedResult(logger, "Should get a response that doesn't match given arguments");
        testCaseListener.addRequest(catsRequest);
        testCaseListener.addPath(catsRequest.getUrl());
        testCaseListener.addContractPath(data.getContractPath());
        testCaseListener.addFullRequestPath(catsRequest.getUrl());
        long startTime = System.currentTimeMillis();

        try {
            CatsResponse catsResponse = serviceCaller.callService(catsRequest, Set.of(targetField));
            checkResponse(catsResponse, data, fuzzedValue);
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            CatsResponse.ExceptionalResponse exceptionalResponse = CatsResponse.getResponseByException(e);

            CatsResponse catsResponse = CatsResponse.builder()
                    .body(exceptionalResponse.responseBody()).httpMethod(catsRequest.getHttpMethod())
                    .responseTimeInMs(duration).responseCode(exceptionalResponse.responseCode())
                    .jsonBody(JsonUtils.parseAsJsonElement(exceptionalResponse.responseBody()))
                    .fuzzedField(targetField)
                    .build();

            checkResponse(catsResponse, data, fuzzedValue);
        } catch (Exception e) {
            logger.debug("Something unexpected happened: ", e);
            testCaseListener.reportResultError(logger, data, "Check response details", "Something went wrong {}", e.getMessage());
        }
    }

    private void checkResponse(CatsResponse catsResponse, FuzzingData data, Object fuzzedValue) {
        if (matchArguments.isMatchResponse(catsResponse) || matchArguments.isInputReflected(catsResponse, fuzzedValue) || !matchArguments.isAnyMatchArgumentSupplied()) {
            testCaseListener.addResponse(catsResponse);
            testCaseListener.reportResultError(logger, data, "Response matches arguments", "Response matches" + matchArguments.getMatchString());
        } else {
            testCaseListener.skipTest(logger, "Skipping test as response does not match given matchers!");
        }
    }


    @Override
    public String description() {
        return "fuzz user supplied request templates with a set of pre-defined special unicode characters or user supplied dictionaries";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
