package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
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
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Sends a happy flow request and checks that the response body does not expose fields marked as {@code writeOnly}
 * in the contract, nor undeclared fields with sensitive names (like passwords, secrets or API keys) holding unmasked values.
 */
@HttpFuzzer
@Singleton
public class SensitiveDataExposureFuzzer implements Fuzzer {
    private static final int MAX_SCHEMA_DEPTH = 20;
    private static final int MAX_ARRAY_ITEMS = 50;
    private static final List<String> SENSITIVE_NAME_SUFFIXES = List.of(
            "password", "passwd", "passphrase", "passwordhash", "secret", "secretkey", "privatekey",
            "apikey", "accesstoken", "refreshtoken", "ssn", "cvv", "cvc");
    private static final Pattern MASKED_VALUE = Pattern.compile("^[*•xX#-]+$|.*[*•]{3,}.*");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(SensitiveDataExposureFuzzer.class);
    private final SimpleExecutor simpleExecutor;
    private final TestCaseListener testCaseListener;

    /**
     * Creates a new SensitiveDataExposureFuzzer instance.
     *
     * @param simpleExecutor   the executor used to send the request
     * @param testCaseListener the listener used to report results
     */
    public SensitiveDataExposureFuzzer(SimpleExecutor simpleExecutor, TestCaseListener testCaseListener) {
        this.simpleExecutor = simpleExecutor;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        simpleExecutor.execute(SimpleExecutorContext.builder()
                .logger(logger)
                .fuzzingData(data)
                .fuzzer(this)
                .payload(data.getPayload())
                .scenario("Send a happy flow request and check that the response does not expose writeOnly or sensitive fields")
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .expectedResult(" and no writeOnly or sensitive fields in the response body")
                .responseProcessor(this::checkResponse)
                .build());
    }

    void checkResponse(CatsResponse response, FuzzingData data) {
        if (!ResponseCodeFamily.is2xxCode(response.getResponseCode()) || response.isBodyTruncated() ||
                !JsonUtils.isValidJson(response.getBody())) {
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX);
            return;
        }
        SchemaFields schemaFields = responseSchemaFields(response, data);
        Set<String> writeOnlyFields = new HashSet<>(schemaFields.writeOnly());
        writeOnlyFields.addAll(requestWriteOnlyFields(data));

        Exposures exposures = new Exposures(new LinkedHashSet<>(), new LinkedHashSet<>());
        collectExposures(JsonUtils.parseAsJsonElement(response.getBody()), "", writeOnlyFields, schemaFields, exposures);

        if (!exposures.writeOnly().isEmpty()) {
            testCaseListener.reportResultError(logger, data, CatsResultFactory.Reason.WRITE_ONLY_FIELDS_EXPOSED.value(),
                    "Response exposes fields marked as writeOnly in the contract: {}", exposures.writeOnly());
        } else if (!exposures.sensitive().isEmpty()) {
            testCaseListener.reportResultWarn(logger, data, CatsResultFactory.Reason.SENSITIVE_DATA_EXPOSED.value(),
                    "Response exposes undeclared fields with sensitive names and unmasked values: {}", exposures.sensitive());
        } else {
            testCaseListener.reportResult(logger, data, response, ResponseCodeFamilyPredefined.TWOXX);
        }
    }

    private Set<String> requestWriteOnlyFields(FuzzingData data) {
        Set<String> fields = new HashSet<>();
        if (data.getReqSchema() == null || data.getRequestPropertyTypes() == null) {
            return fields;
        }
        Optional.ofNullable(data.getAllFieldsAsCatsFields()).orElse(Set.of()).stream()
                .filter(CatsField::isWriteOnly)
                .map(CatsField::getName)
                .map(name -> name.substring(name.lastIndexOf('#') + 1))
                .forEach(fields::add);
        return fields;
    }

    private SchemaFields responseSchemaFields(CatsResponse response, FuzzingData data) {
        Optional<Schema<?>> responseSchema = data.getResponseSchema(response.responseCodeAsString(),
                response.responseCodeAsResponseRange(), response.getResponseContentType());
        if (responseSchema.isEmpty()) {
            return new SchemaFields(false, false, Set.of(), Set.of());
        }
        SchemaWalk walk = new SchemaWalk(Optional.ofNullable(data.getSchemaMap()).orElse(Map.of()));
        walk.visit(responseSchema.get(), 0);
        return new SchemaFields(true, walk.freeForm, walk.declared, walk.writeOnly);
    }

    private void collectExposures(JsonElement element, String path, Set<String> writeOnlyFields,
                                  SchemaFields schemaFields, Exposures exposures) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().asList().stream().limit(MAX_ARRAY_ITEMS)
                    .forEach(item -> collectExposures(item, path + "[*]", writeOnlyFields, schemaFields, exposures));
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String fieldPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
            JsonElement value = entry.getValue();
            if (writeOnlyFields.contains(entry.getKey()) && hasValue(value)) {
                exposures.writeOnly().add(fieldPath);
            } else if (schemaFields.canDetectUndeclared() && !schemaFields.declared().contains(entry.getKey()) &&
                    isSensitiveName(entry.getKey()) && isUnmaskedPrimitive(value)) {
                exposures.sensitive().add(fieldPath);
            }
            collectExposures(value, fieldPath, writeOnlyFields, schemaFields, exposures);
        }
    }

    private boolean hasValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            return !primitive.isString() || isUnmasked(primitive.getAsString());
        }
        return true;
    }

    private boolean isUnmaskedPrimitive(JsonElement value) {
        return value != null && value.isJsonPrimitive() && !value.getAsJsonPrimitive().isBoolean() &&
                isUnmasked(value.getAsJsonPrimitive().getAsString());
    }

    private boolean isUnmasked(String value) {
        return !value.isBlank() && !MASKED_VALUE.matcher(value).matches();
    }

    private boolean isSensitiveName(String name) {
        String normalized = NON_ALPHANUMERIC.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("");
        return SENSITIVE_NAME_SUFFIXES.stream().anyMatch(normalized::endsWith);
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return Arrays.stream(HttpMethod.values())
                .filter(method -> method != HttpMethod.GET && method != HttpMethod.POST &&
                        method != HttpMethod.PUT && method != HttpMethod.PATCH)
                .toList();
    }

    @Override
    public String description() {
        return "send a happy flow request and check that the response does not expose writeOnly fields or undeclared sensitive fields like passwords, secrets or API keys";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    private record SchemaFields(boolean known, boolean freeForm, Set<String> declared, Set<String> writeOnly) {
        boolean canDetectUndeclared() {
            return known && !freeForm;
        }
    }

    private record Exposures(Set<String> writeOnly, Set<String> sensitive) {
    }

    private static final class SchemaWalk {
        private final Map<String, Schema> schemaMap;
        private final Set<String> visitedRefs = new HashSet<>();
        private final Set<String> declared = new HashSet<>();
        private final Set<String> writeOnly = new HashSet<>();
        private boolean freeForm;

        private SchemaWalk(Map<String, Schema> schemaMap) {
            this.schemaMap = schemaMap;
        }

        private void visit(Schema<?> schema, int depth) {
            if (schema == null || depth > MAX_SCHEMA_DEPTH) {
                return;
            }
            if (schema.get$ref() != null) {
                String name = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
                if (visitedRefs.add(name)) {
                    visit(schemaMap.get(name), depth + 1);
                }
                return;
            }
            Map<String, Schema> properties = Optional.ofNullable(schema.getProperties()).orElse(Map.of());
            properties.forEach((name, property) -> {
                declared.add(name);
                if (Boolean.TRUE.equals(property.getWriteOnly())) {
                    writeOnly.add(name);
                }
                visit(property, depth + 1);
            });
            visit(schema.getItems(), depth + 1);
            Stream.of(schema.getAllOf(), schema.getAnyOf(), schema.getOneOf())
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .forEach(item -> visit(item, depth + 1));
            Object additionalProperties = schema.getAdditionalProperties();
            if (additionalProperties instanceof Schema<?> additionalSchema) {
                freeForm = true;
                visit(additionalSchema, depth + 1);
            } else if (Boolean.TRUE.equals(additionalProperties) || CatsModelUtils.isFreeFormSchema(schema) ||
                    CatsModelUtils.isEmptyObjectSchema(schema)) {
                freeForm = true;
            }
        }
    }
}
