package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Fuzzer that sends examples from components.examples; from schema.example(s), MediaType.example(s) Example can be a ref.
 */
@FieldFuzzer
@Singleton
public class ExamplesFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(ExamplesFieldsFuzzer.class);
    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new ExamplesFieldsFuzzer instance.
     *
     * @param simpleExecutor the executor
     */
    @Inject
    public ExamplesFieldsFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        Set<String> payloads = new HashSet<>();
        payloads.add(Optional.ofNullable(data.getReqSchema().getExample())
                .map(JsonUtils::serialize)
                .orElse(""));

        payloads.addAll(Optional.ofNullable(data.getReqSchema().getExamples())
                .orElse(Collections.emptyList())
                .stream()
                .map(JsonUtils::serialize)
                .toList());

        payloads.addAll(data.getExamples()
                .stream()
                .map(JsonUtils::serialize)
                .toList());
        buildPropertyExamplesPayload(data).ifPresent(payloads::add);
        payloads.remove("");

        logger.debug("Fuzzing the following examples: {}", payloads);

        for (String payload : payloads) {
            simpleExecutor.execute(SimpleExecutorContext
                    .builder()
                    .payload(payload)
                    .logger(logger)
                    .fuzzer(this)
                    .fuzzingData(data)
                    .scenario("Send a request for every unique example")
                    .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                    .expectedSpecificResponseCode("2XX")
                    .build()
            );
        }
    }

    private Optional<String> buildPropertyExamplesPayload(FuzzingData data) {
        if (data.getMethod() == HttpMethod.DELETE || data.getRequestPropertyTypes() == null ||
                JsonUtils.isEmptyPayload(data.getPayload()) || !JsonUtils.isValidJson(data.getPayload())) {
            return Optional.empty();
        }
        String candidate = data.getPayload();
        boolean replaced = false;
        for (Map.Entry<String, Schema> entry : data.getRequestPropertyTypes().entrySet()) {
            Schema<?> schema = entry.getValue();
            if (schema == null) {
                continue;
            }
            Object example = schema.getExample();
            if (example == null && schema.getExamples() != null && !schema.getExamples().isEmpty()) {
                example = schema.getExamples().getFirst();
            }
            if (example != null && JsonUtils.isFieldInJson(candidate, entry.getKey())) {
                candidate = CatsUtil.justReplaceField(candidate, entry.getKey(), example).json();
                replaced = true;
            }
        }
        return replaced && !candidate.equals(data.getPayload()) ? Optional.of(candidate) : Optional.empty();
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.HEAD, HttpMethod.TRACE, HttpMethod.DELETE);
    }

    @Override
    public String description() {
        return "send a request for every unique example";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
