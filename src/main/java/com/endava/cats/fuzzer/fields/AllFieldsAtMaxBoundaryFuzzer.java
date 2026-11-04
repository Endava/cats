package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.generator.simple.StringGenerator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTargetResolver;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Co-mutates all boundary-constrained fields in the request payload to their maximum
 * valid OpenAPI boundary ({@code maxLength}, {@code maximum}) simultaneously in a single request,
 * expecting 2XX.
 */
@Singleton
@FieldFuzzer
public class AllFieldsAtMaxBoundaryFuzzer implements Fuzzer {
    private static final int MAX_SAFE_STRING_BOUNDARY = 10_000;
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;
    private final FilesArguments filesArguments;

    public AllFieldsAtMaxBoundaryFuzzer(SimpleExecutor simpleExecutor, FilesArguments filesArguments) {
        this.simpleExecutor = simpleExecutor;
        this.filesArguments = filesArguments;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.skip("Skip fuzzer as payload is empty");
            return;
        }
        MaxBoundaryPayload maxPayload = buildAllMaxPayload(data);
        if (maxPayload.mutatedFields().isEmpty()) {
            logger.skip("No fields with maxLength or maximum boundaries found in payload");
            return;
        }
        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .fuzzingData(data)
                        .logger(logger)
                        .payload(maxPayload.payload())
                        .mutationTargets(RequestTargetResolver.resolvePayloadFields(data, maxPayload.mutatedFields()))
                        .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                        .scenario("Send all constrained fields simultaneously at their maximum valid OpenAPI boundary %s"
                                .formatted(maxPayload.mutatedFields()))
                        .build());
    }

    record MaxBoundaryPayload(String payload, List<String> mutatedFields) {
    }

    MaxBoundaryPayload buildAllMaxPayload(FuzzingData data) {
        Set<String> allFields = Optional.ofNullable(data.getAllFieldsByHttpMethod()).orElse(Set.of());
        Map<String, Object> refData = filesArguments != null ? filesArguments.getRefData(data.getPath()) : Map.of();
        String currentPayload = data.getPayload();
        List<String> mutatedFields = new ArrayList<>();

        for (String field : allFields.stream().sorted().toList()) {
            if (!refData.containsKey(field) && JsonUtils.isFieldInJson(currentPayload, field)) {
                Schema<?> schema = data.getRequestPropertyTypes().get(field);
                Optional<Object> maxVal = computeMaxValidValue(schema);
                if (maxVal.isPresent()) {
                    currentPayload = CatsUtil.justReplaceField(currentPayload, field, maxVal.get()).json();
                    mutatedFields.add(field);
                }
            }
        }
        return new MaxBoundaryPayload(currentPayload, List.copyOf(mutatedFields));
    }

    static Optional<Object> computeMaxValidValue(Schema<?> schema) {
        if (schema == null || CatsModelUtils.isEnumSchema(schema) || schema.getConst() != null) {
            return Optional.empty();
        }
        if (CatsModelUtils.isIntegerSchema(schema) || CatsModelUtils.isNumberSchema(schema)) {
            return computeMaxNumericValue(schema);
        }
        if (CatsModelUtils.isStringSchema(schema) && StringUtils.isBlank(schema.getFormat())) {
            return computeMaxStringValue(schema);
        }
        return Optional.empty();
    }

    private static Optional<Object> computeMaxNumericValue(Schema<?> schema) {
        boolean isInteger = CatsModelUtils.isIntegerSchema(schema);
        if (schema.getExclusiveMaximumValue() != null) {
            BigDecimal bound = schema.getExclusiveMaximumValue().subtract(BigDecimal.ONE);
            return Optional.of(isInteger ? bound.longValue() : bound);
        }
        if (schema.getMaximum() != null) {
            BigDecimal max = Boolean.TRUE.equals(schema.getExclusiveMaximum())
                    ? schema.getMaximum().subtract(BigDecimal.ONE)
                    : schema.getMaximum();
            return Optional.of(isInteger ? max.longValue() : max);
        }
        return Optional.empty();
    }

    private static Optional<Object> computeMaxStringValue(Schema<?> schema) {
        if (schema.getMaxLength() == null || schema.getMaxLength() <= 0) {
            return Optional.empty();
        }
        int length = Math.min(schema.getMaxLength(), MAX_SAFE_STRING_BOUNDARY);
        if (StringUtils.isNotBlank(schema.getPattern())) {
            try {
                String generated = StringGenerator.generateExactLength(schema, schema.getPattern(), length);
                if (generated != null && generated.length() == length) {
                    return Optional.of(generated);
                }
            } catch (Exception _) {
                return Optional.empty();
            }
            return Optional.empty();
        }
        return Optional.of("a".repeat(length));
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "sets all boundary-constrained fields simultaneously to their maximum valid OpenAPI boundary in a single request";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }
}
