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
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

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
            List<String> pathCombinations = this.replacePathVariables(data.getPath());
            for (String pathCombination : pathCombinations) {
                simpleExecutor.execute(
                        SimpleExecutorContext.builder()
                                .fuzzer(this)
                                .fuzzingData(data)
                                .logger(logger)
                                .path(pathCombination)
                                .scenario("Fuzz path parameters for HTTP methods with bodies. Current path: %s".formatted(pathCombination))
                                .expectedSpecificResponseCode("[2XX, 4XX]")
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

    private List<String> replacePathVariables(String path) {
        List<String> variables = new ArrayList<>();
        Matcher matcher = VARIABLES_PATTERN.matcher(path);
        while (matcher.find()) {
            variables.add(matcher.group());
        }

        Map<String, String> variablesValues = new HashMap<>();
        for (String variable : variables) {
            String variableName = variable.substring(1, variable.length() - 1);
            String value = Optional.ofNullable(filesArguments.getRefData(path).get(variableName))
                    .map(WordUtils::nullOrValueOf)
                    .orElse(filesArguments.getUrlParamsList()
                            .stream()
                            .filter(param -> param.startsWith(variableName + ":"))
                            .map(item -> item.split(":", -1)[1])
                            .findFirst().orElse(variable));
            variablesValues.put(variable, value);
        }

        return createPathCombinations(path, variables, variablesValues);
    }

    private static List<String> createPathCombinations(String path, List<String> variables, Map<String, String> variablesValues) {
        List<String> result = new ArrayList<>();
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
                result.add(fuzzedPath);
            }
        }

        return result;
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
