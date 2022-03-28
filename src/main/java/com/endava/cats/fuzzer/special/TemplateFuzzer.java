package com.endava.cats.fuzzer.special;

import com.endava.cats.Fuzzer;
import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.args.UserArguments;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.model.util.JsonUtils;
import com.endava.cats.model.util.PayloadUtils;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.jayway.jsonpath.JsonPathException;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@SpecialFuzzer
public class TemplateFuzzer implements Fuzzer {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(TemplateFuzzer.class);
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final CatsUtil catsUtil;
    private final UserArguments userArguments;

    public TemplateFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu, UserArguments ua) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.catsUtil = cu;
        this.userArguments = ua;
    }

    @Override
    public void fuzz(FuzzingData data) {
        for (String targetField : data.getTargetFields()) {
            int payloadSize = this.getPayloadSize(data, targetField);

            if (payloadSize == 0) {
                LOGGER.skip("Field {} was NOT found in request payload, HTTP headers or path/query parameters!", targetField);
            } else {
                List<String> payloads = this.getAllPayloads(payloadSize);
                LOGGER.info("Running {} payloads for field [{}]", payloads.size(), targetField);

                for (String payload : payloads) {
                    List<CatsRequest.Header> replacedHeaders = this.replaceHeaders(data, payload, targetField);
                    String replacedPayload = this.replacePayload(data, payload, targetField);
                    String replacedPath = this.replacePath(data, payload, targetField);
                    CatsRequest catsRequest = CatsRequest.builder()
                            .payload(replacedPayload)
                            .headers(replacedHeaders)
                            .httpMethod(data.getMethod().name())
                            .url(replacedPath)
                            .build();

                    testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(catsRequest, targetField, payload));
                }
            }
        }
    }

    String replacePath(FuzzingData data, String withData, String targetField) {
        String finalPath = data.getPath();
        try {
            URL url = new URL(data.getPath());
            String replacedPath = Arrays.stream(url.getPath().split("/"))
                    .map(pathElement -> pathElement.equalsIgnoreCase(targetField) ? withData : pathElement)
                    .collect(Collectors.joining("/"));

            finalPath = finalPath.replace(url.getPath(), replacedPath);
            if (url.getQuery() != null) {
                String replacedQuery = Arrays.stream(url.getQuery().split("&"))
                        .map(queryParam -> replaceQueryParam(targetField, queryParam, withData))
                        .collect(Collectors.joining("&"));

                finalPath = finalPath.replace(url.getQuery(), replacedQuery);
            }
        } catch (Exception e) {
            LOGGER.warn("There was an issue parsing {}: {}", data.getPath(), e.getMessage());
        }

        return finalPath;
    }

    String replaceQueryParam(String targetField, String queryPair, String withValue) {
        String[] queryPairArr = queryPair.split("=");
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
                List<String> payloads = PayloadUtils.getAllPayloadsOfSize(payloadSize);
                payloads.add(PayloadUtils.getBadPayload());
                payloads.add(PayloadUtils.getZalgoText());
                payloads.add(StringGenerator.generateLargeString(20000));
                payloads.add(null);
                payloads.add("");
                return payloads;
            } else {
                return Files.readAllLines(Path.of(userArguments.getWords().getAbsolutePath()), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            LOGGER.error("Something went wrong while reading user supplied dictionary: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private List<CatsRequest.Header> replaceHeaders(FuzzingData data, String withData, String targetField) {
        return data.getHeaders().stream()
                .map(catsHeader -> new CatsRequest.Header(catsHeader.getName(),
                        catsHeader.getName().equalsIgnoreCase(targetField) ? withData : catsHeader.getValue()))
                .collect(Collectors.toList());
    }

    private String replacePayload(FuzzingData data, String withData, String targetField) {
        try {
            return catsUtil.replaceField(data.getPayload(), targetField, FuzzingStrategy.replace().withData(withData)).getJson();
        } catch (JsonPathException e) {
            return data.getPayload();
        }
    }

    private int getPayloadSize(FuzzingData data, String field) {
        String oldValue = data.getPath().contains(field) ? "CATS_FUZZ" : "";

        if (oldValue.isEmpty()) {
            oldValue = String.valueOf(JsonUtils.getVariableFromJson(data.getPayload(), field));
        }
        if (oldValue.equalsIgnoreCase(JsonUtils.NOT_SET)) {
            oldValue = data.getHeaders().stream()
                    .filter(header -> header.getName().equalsIgnoreCase(field))
                    .map(CatsHeader::getValue)
                    .findFirst()
                    .orElse("");
        }

        return oldValue.length();
    }

    private void process(CatsRequest catsRequest, String targetField, String fuzzValued) {
        testCaseListener.addScenario(LOGGER, "Replace request field, header or path/query param [{}], with [{}]", targetField, FuzzingStrategy.replace().withData(fuzzValued).truncatedValue());
        testCaseListener.addExpectedResult(LOGGER, "Should get a valid response from the service");
        testCaseListener.addRequest(catsRequest);
        testCaseListener.addPath(catsRequest.getUrl());
        testCaseListener.addFullRequestPath(catsRequest.getUrl());
        try {
            CatsResponse catsResponse = serviceCaller.callService(catsRequest, Set.of(targetField));
            testCaseListener.addResponse(catsResponse);
            testCaseListener.reportError(LOGGER, "Service call completed. Please check response details.");
        } catch (Exception e) {
            LOGGER.error("An error happened: {}", e.getMessage());
            testCaseListener.reportError(LOGGER, "Something went wrong {}", e.getMessage());
        }
    }

    @Override
    public String description() {
        return "fuzz user supplied request templates with a set of pre-defined special unicode characters or user supplied dictionaries";
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName().replace("_Subclass", "");
    }

}
