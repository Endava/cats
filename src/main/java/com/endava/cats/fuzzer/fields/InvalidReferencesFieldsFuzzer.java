package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fuzzer that sends invalid references in fields.
 */
@Singleton
@FieldFuzzer
public class InvalidReferencesFieldsFuzzer implements Fuzzer {
    private static final Pattern VARIABLES_PATTERN = Pattern.compile("\\{([^{]*)}");
    private static final String DEFAULT_PATH_VARIABLE_FALLBACK = "1";
    private static final List<String> RAW_QUERY_REFERENCE_PAYLOADS = List.of("%", "%00", "\r\n", "\u0000", "\u001B");
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(InvalidReferencesFieldsFuzzer.class);

    private final FilesArguments filesArguments;
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;

    /**
     * Creates a new InvalidReferencesFieldsFuzzer instance.
     *
     * @param filesArguments   files arguments
     * @param simpleExecutor   the executor
     * @param testCaseListener the test case listener
     */
    public InvalidReferencesFieldsFuzzer(FilesArguments filesArguments, SimpleExecutor simpleExecutor, TestCaseListener testCaseListener) {
        this.filesArguments = filesArguments;
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (this.hasPathVariables(data.getPath())) {
            List<PathMutation> pathCombinations = this.replacePathVariables(data);
            for (PathMutation pathMutation : pathCombinations) {
                simpleExecutor.execute(
                        SimpleExecutorContext.builder()
                                .fuzzer(this)
                                .fuzzingData(data)
                                .logger(logger)
                                .path(pathMutation.path())
                                .mutationTarget(RequestTarget.path(pathMutation.parameter()))
                                .scenario("Fuzz path parameters for HTTP methods with bodies. Current path: %s".formatted(pathMutation.path()))
                                .expectedSpecificResponseCode("[2XX, 4XX]")
                                .replaceUrlParams(false)
                                .responseProcessor(this::processResponse)
                                .build());
            }
        } else if (data.getQueryParams() != null && !data.getQueryParams().isEmpty()) {
            String firstQueryParam = data.getQueryParams().iterator().next();
            for (String rawQuerySuffix : RAW_QUERY_REFERENCE_PAYLOADS) {
                String rawQueryPath = data.getPath() + "?" + firstQueryParam + "=" + rawQuerySuffix;
                simpleExecutor.execute(
                        SimpleExecutorContext.builder()
                                .fuzzer(this)
                                .fuzzingData(data)
                                .logger(logger)
                                .path(rawQueryPath)
                                .mutationTarget(RequestTarget.query(firstQueryParam))
                                .scenario("Fuzz query parameters with raw invalid reference: %s".formatted(rawQueryPath))
                                .expectedSpecificResponseCode("[2XX, 4XX]")
                                .replaceUrlParams(false)
                                .responseProcessor(this::processResponse)
                                .build());
            }
        }
    }

    private void processResponse(CatsResponse catsResponse, FuzzingData fuzzingData) {
        if (ResponseCodeFamily.is4xxCode(catsResponse.getResponseCode()) || ResponseCodeFamily.is2xxCode(catsResponse.getResponseCode())) {
            testCaseListener.reportResultInfo(logger, fuzzingData, "Response code expected: [{}]", catsResponse.getResponseCode());
        } else {
            testCaseListener.reportResultError(logger, fuzzingData,
                    CatsResultFactory.createUnexpectedResponseCode(catsResponse.responseCodeAsString(), "4XX, 2XX").reason(),
                    "Request failed unexpectedly for http method [{}]: expected [{}], actual [{}]",
                    catsResponse.getHttpMethod(), "4XX, 2XX", catsResponse.responseCodeAsString());
        }
    }

    private List<PathMutation> replacePathVariables(FuzzingData data) {
        String path = data.getPath();
        List<String> variables = new ArrayList<>();
        Matcher matcher = VARIABLES_PATTERN.matcher(path);
        while (matcher.find()) {
            variables.add(matcher.group());
        }

        Map<String, String> variablesValues = new HashMap<>();
        for (String variable : variables) {
            String variableName = variable.substring(1, variable.length() - 1);
            String value = Optional.ofNullable(filesArguments.getRefData(path))
                    .map(ref -> ref.get(variableName))
                    .map(WordUtils::nullOrValueOf)
                    .orElseGet(() -> Optional.ofNullable(filesArguments.getUrlParamsList())
                            .orElse(List.of())
                            .stream()
                            .filter(param -> param.startsWith(variableName + ":"))
                            .map(item -> item.split(":", -1)[1])
                            .findFirst()
                            .orElseGet(() -> resolvePathVariableFromPayload(data, variableName)));
            variablesValues.put(variable, value);
        }

        return createPathCombinations(path, variables, variablesValues);
    }

    private static String resolvePathVariableFromPayload(FuzzingData data, String variableName) {
        String safeBodyPayload = data.getReqSchema() != null ? StringUtils.defaultString(data.getPayload()) : "";
        for (String candidatePayload : List.of(
                StringUtils.defaultString(data.getPathParamsPayload()),
                safeBodyPayload)) {
            if (StringUtils.isNotBlank(candidatePayload) && JsonUtils.isValidJson(candidatePayload)) {
                Object val = JsonUtils.getVariableFromJson(candidatePayload, variableName);
                if (val != null && !JsonUtils.isNotSet(String.valueOf(val)) && StringUtils.isNotBlank(String.valueOf(val))) {
                    return String.valueOf(val);
                }
            }
        }
        return DEFAULT_PATH_VARIABLE_FALLBACK;
    }

    private static List<PathMutation> createPathCombinations(String path, List<String> variables, Map<String, String> variablesValues) {
        List<PathMutation> result = new ArrayList<>();
        List<String> payloads = new ArrayList<>(UnicodeGenerator.getAbugidasChars());
        payloads.add(UnicodeGenerator.getZalgoText());
        payloads.addAll(UnicodeGenerator.getInvalidReferences());

        for (String variable : variables) {
            String interimPath = path;
            for (Map.Entry<String, String> entry : variablesValues.entrySet()) {
                if (!entry.getKey().equalsIgnoreCase(variable)) {
                    interimPath = interimPath.replace(entry.getKey(), entry.getValue());
                }
            }
            for (String payload : payloads) {
                String fuzzedPath = interimPath.replace(variable, variablesValues.get(variable) + payload);
                result.add(new PathMutation(fuzzedPath, variable.substring(1, variable.length() - 1)));
            }
        }

        return result;
    }

    private record PathMutation(String path, String parameter) {
    }

    private boolean hasPathVariables(String path) {
        return path.contains("{");
    }

    @Override
    public String description() {
        return "iterate through each path and fuzz the path parameters with invalid references";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
