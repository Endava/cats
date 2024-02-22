package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.generator.simple.NumberGenerator;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.OpenApiUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Iterates through path variables and sens random resource identifiers.
 */
@HttpFuzzer
@Singleton
public class RandomResourcesFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;
    private final FilesArguments filesArguments;
    private static final int ITERATIONS = 10;

    /**
     * Creates a new instance.
     *
     * @param simpleExecutor executor used to run the fuzz logic
     * @param filesArguments files argument
     */
    public RandomResourcesFuzzer(SimpleExecutor simpleExecutor, FilesArguments filesArguments) {
        this.simpleExecutor = simpleExecutor;
        this.filesArguments = filesArguments;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Set<String> pathVariables = Arrays.stream(OpenApiUtils.getPathElements(data.getPath()))
                .filter(OpenApiUtils::isAPathVariable)
                .map(element -> element.substring(1, element.length() - 1))
                .collect(Collectors.toSet());

        if (pathVariables.isEmpty()) {
            return;
        }

        Set<String> payloads = new HashSet<>();

        for (int i = 0; i < ITERATIONS; i++) {
            String updatePayload = data.getPayload();
            for (String pathVar : pathVariables) {
                String pathVarValueFromUrlParamsList = filesArguments.getUrlParam(pathVar);
                boolean isPathVarPassedAsUrlParam = StringUtils.isNotBlank(pathVarValueFromUrlParamsList);

                if (isPathVarPassedAsUrlParam) {
                    // when path variable is passed as URL param it won't be fuzzed -> won't be part of the payload, so we add it
                    updatePayload = JsonUtils.addNewElement(data.getPayload(), "$", pathVar, pathVarValueFromUrlParamsList);
                }
                Object existingValue = JsonUtils.getVariableFromJson(updatePayload, pathVar);
                if (JsonUtils.isNotSet(String.valueOf(existingValue))) {
                    throw new IllegalStateException("OpenAPI spec is missing definition for " + pathVar);
                }
                Object newValue = generateNewValue(existingValue);

                updatePayload = CatsUtil.justReplaceField(updatePayload, pathVar, newValue).json();

            }
            payloads.add(updatePayload);
        }

        this.executeTests(data, payloads);
    }

    private void executeTests(FuzzingData data, Set<String> payloads) {
        for (String payload : payloads) {
            simpleExecutor.execute(
                    SimpleExecutorContext.builder()
                            .expectedResponseCode(ResponseCodeFamily.FOURXX_NF)
                            .fuzzingData(data)
                            .logger(logger)
                            .replaceRefData(false)
                            .scenario("Send random values in path variables")
                            .fuzzer(this)
                            .payload(payload)
                            .replaceUrlParams(false)
                            .build()
            );
        }
    }

    private static Object generateNewValue(Object value) {
        String valueAsString = String.valueOf(value);

        if (isUuid(valueAsString)) {
            return UUID.randomUUID().toString();
        }
        if (isLong(valueAsString)) {
            long longValue = Long.parseLong(valueAsString);
            return NumberGenerator.generateRandomLong(longValue - 10000L, longValue + 10000L);
        }
        return StringGenerator.generate("[A-Za-z0-9]+", valueAsString.length() - 1, valueAsString.length());
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String description() {
        return "iterate through each path variable and send random resource identifiers";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.HEAD, HttpMethod.TRACE);
    }
}
