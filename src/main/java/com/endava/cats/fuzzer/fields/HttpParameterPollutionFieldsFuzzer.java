package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Duplicates query parameters in the request URL with conflicting second values
 * (e.g. {@code ?status=ACTIVE&status=catsPolluted}) to detect HTTP Parameter Pollution (HPP)
 * and array-coercion crashes.
 */
@Singleton
@FieldFuzzer
public class HttpParameterPollutionFieldsFuzzer implements Fuzzer {
    static final String POLLUTED_STRING = "catsPolluted";
    static final String POLLUTED_NUMBER = "-999999";
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;

    public HttpParameterPollutionFieldsFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Set<String> queryParams = data.getQueryParams();
        if (queryParams == null || queryParams.isEmpty()) {
            logger.skip("No query parameters declared for {} {}", data.getMethod(), data.getPath());
            return;
        }
        queryParams.stream()
                .sorted()
                .filter(param -> JsonUtils.isFieldInJson(data.getPayload(), param))
                .forEach(param -> fuzzQueryParam(data, param));
    }

    private void fuzzQueryParam(FuzzingData data, String targetParam) {
        String pollutedValue = selectPollutedValue(data, targetParam);
        String pollutedPath = buildPollutedPath(data, targetParam, pollutedValue);

        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .fuzzingData(data)
                        .logger(logger)
                        .path(pollutedPath)
                        .payload(data.getPayload())
                        .replaceUrlParams(false)
                        .mutationTarget(RequestTarget.query(targetParam))
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX_TWOXX)
                        .scenario("Duplicate query parameter [%s] with conflicting value [%s] to test HTTP Parameter Pollution"
                                .formatted(targetParam, pollutedValue))
                        .build());
    }

    static String selectPollutedValue(FuzzingData data, String param) {
        Schema<?> schema = data.getRequestPropertyTypes() == null ? null : data.getRequestPropertyTypes().get(param);
        if (CatsModelUtils.isIntegerSchema(schema) || CatsModelUtils.isNumberSchema(schema)) {
            return POLLUTED_NUMBER;
        }
        if (CatsModelUtils.isBooleanSchema(schema)) {
            Object current = JsonUtils.getVariableFromJson(data.getPayload(), param);
            return "true".equalsIgnoreCase(String.valueOf(current)) ? "false" : "true";
        }
        return POLLUTED_STRING;
    }

    static String buildPollutedPath(FuzzingData data, String targetParam, String pollutedValue) {
        StringJoiner queryJoiner = new StringJoiner("&");
        for (String param : data.getQueryParams().stream().sorted().toList()) {
            Object rawValue = JsonUtils.getVariableFromJson(data.getPayload(), param);
            if (!JsonUtils.isNotSet(String.valueOf(rawValue))) {
                queryJoiner.add(encodePair(param, String.valueOf(rawValue)));
            }
            if (param.equals(targetParam)) {
                queryJoiner.add(encodePair(param, pollutedValue));
            }
        }
        String separator = data.getPath().contains("?") ? "&" : "?";
        return data.getPath() + separator + queryJoiner;
    }

    private static String encodePair(String name, String value) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "duplicates query parameters with conflicting values in the URL to detect HTTP Parameter Pollution";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
