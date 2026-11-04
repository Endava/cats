package com.endava.cats.fuzzer.fields;

import com.endava.cats.annotations.FieldFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsField;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Re-injects properties marked {@code readOnly: true} in the OpenAPI schema into write requests
 * ({@code POST}, {@code PUT}, {@code PATCH}) to detect OWASP API3:2023 Broken Object Property Level
 * Authorization (Mass Assignment of server-managed properties).
 */
@Singleton
@FieldFuzzer
public class ReadOnlyFieldsMassAssignmentFuzzer implements Fuzzer {
    static final String STRING_CANARY = "cats-readonly-tamper";
    static final int NUMERIC_CANARY = 987654;
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;

    public ReadOnlyFieldsMassAssignmentFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (JsonUtils.isEmptyPayload(data.getPayload())) {
            logger.skip("Skip fuzzer as payload is empty");
            return;
        }
        List<CatsField> readOnlyFields = findReadOnlyFields(data);
        if (readOnlyFields.isEmpty()) {
            logger.skip("No readOnly fields declared in the request schema");
            return;
        }
        readOnlyFields.forEach(field -> fuzzReadOnlyField(data, field));
    }

    private static List<CatsField> findReadOnlyFields(FuzzingData data) {
        Set<CatsField> allFields = Optional.ofNullable(data.getAllFieldsAsCatsFields()).orElse(Set.of());
        return allFields.stream()
                .filter(CatsField::isReadOnly)
                .sorted(Comparator.comparing(CatsField::getName))
                .toList();
    }

    private void fuzzReadOnlyField(FuzzingData data, CatsField field) {
        Object canaryValue = selectCanaryValue(field.getSchema());
        Optional<String> mutatedPayload = injectReadOnlyField(data.getPayload(), field.getName(), canaryValue);
        if (mutatedPayload.isEmpty()) {
            logger.debug("Parent object for readOnly field {} is not present in payload", field.getName());
            return;
        }
        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                        .fuzzingData(data)
                        .logger(logger)
                        .scenario("Send schema-declared readOnly field [%s] with value [%s] in write request to test readOnly mass assignment"
                                .formatted(field.getName(), canaryValue))
                        .fuzzer(this)
                        .payload(mutatedPayload.get())
                        .mutationTarget(RequestTarget.body(field.getName()))
                        .responseProcessor((response, fuzzData) -> processResponse(response, fuzzData, field.getName(), canaryValue))
                        .build());
    }

    static Object selectCanaryValue(Schema<?> schema) {
        if (CatsModelUtils.isBooleanSchema(schema)) {
            return true;
        }
        if (CatsModelUtils.isIntegerSchema(schema) || CatsModelUtils.isNumberSchema(schema)) {
            return NUMERIC_CANARY;
        }
        return STRING_CANARY;
    }

    static Optional<String> injectReadOnlyField(String payload, String fieldPath, Object value) {
        try {
            JsonElement root = JsonParser.parseString(payload);
            List<String> segments = List.of(fieldPath.split("#"));
            JsonObject targetParent = resolveParentObject(root, segments);
            if (targetParent == null) {
                return Optional.empty();
            }
            addProperty(targetParent, segments.getLast(), value);
            return Optional.of(root.toString());
        } catch (Exception _) {
            return Optional.empty();
        }
    }

    private static JsonObject resolveParentObject(JsonElement root, List<String> segments) {
        JsonElement current = root;
        for (int i = 0; i < segments.size() - 1; i++) {
            if (!(current instanceof JsonObject obj) || !obj.has(segments.get(i))) {
                return null;
            }
            current = obj.get(segments.get(i));
        }
        return current instanceof JsonObject obj ? obj : null;
    }

    private static void addProperty(JsonObject target, String leafName, Object value) {
        switch (value) {
            case Number number -> target.addProperty(leafName, number);
            case Boolean bool -> target.addProperty(leafName, bool);
            default -> target.addProperty(leafName, String.valueOf(value));
        }
    }

    private void processResponse(CatsResponse response, FuzzingData data, String fieldName, Object canaryValue) {
        int code = response.getResponseCode();
        if (ResponseCodeFamily.is4xxCode(code)) {
            testCaseListener.reportResultInfo(logger, data, "ReadOnly field rejected",
                    "The API properly rejected the write request containing readOnly field [%s] (status %d)"
                            .formatted(fieldName, code));
            return;
        }
        if (ResponseCodeFamily.is2xxCode(code) && isCanaryReflected(response.getBody(), fieldName, canaryValue)) {
            testCaseListener.reportResultError(logger, data, CatsResultFactory.Reason.MASS_ASSIGNMENT_DETECTED.value(),
                    "ReadOnly field [%s] was accepted and reflected with value [%s] in the response (status %d)"
                            .formatted(fieldName, canaryValue, code));
            return;
        }
        if (ResponseCodeFamily.is5xxCode(code)) {
            testCaseListener.reportResultError(logger, data, CatsResultFactory.Reason.SERVER_ERROR.value(),
                    "Server returned %d error when processing readOnly field [%s]".formatted(code, fieldName));
            return;
        }
        testCaseListener.reportResultInfo(logger, data, "ReadOnly field ignored by server",
                "Write request succeeded (status %d) without reflecting the readOnly value for [%s]"
                        .formatted(code, fieldName));
    }

    private static boolean isCanaryReflected(String responseBody, String fieldName, Object canaryValue) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        String leaf = fieldName.substring(fieldName.lastIndexOf('#') + 1).toLowerCase(Locale.ROOT);
        String bodyLower = responseBody.toLowerCase(Locale.ROOT);
        String valLower = String.valueOf(canaryValue).toLowerCase(Locale.ROOT);
        return bodyLower.contains("\"" + leaf + "\"") && bodyLower.contains(valLower);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "sends schema-declared readOnly fields in write requests to detect readOnly property mass assignment";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
