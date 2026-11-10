package com.endava.cats.fuzzer.http;

import com.endava.cats.annotations.HttpFuzzer;
import com.endava.cats.annotations.SecondPhaseFuzzer;
import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory.Reason;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.WordUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies RFC 9110 section 9.3.4 full-replacement semantics on {@code PUT} endpoints. On a resource created in this
 * run, reads the baseline representation via {@code GET}, sends a {@code PUT} (omitting a populated optional field
 * declared in both the {@code PUT} and {@code GET} schemas when one exists), and reads the resource back via
 * {@code GET}. Warns when an omitted optional field retains its pre-{@code PUT} value or when a sent field declared in
 * both schemas differs in the subsequent {@code GET}.
 */
@HttpFuzzer
@SecondPhaseFuzzer
@Singleton
public class PartialUpdatePutFuzzer implements Fuzzer {
    private static final Set<String> IDENTIFIER_WORDS = Set.of("id", "uuid", "guid");
    private static final Set<String> SENSITIVE_WORDS = Set.of("password", "passwd", "secret", "token", "apikey", "hash");

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(PartialUpdatePutFuzzer.class);
    private final RuntimeResourcePool pool;
    private final ApiArguments arguments;
    private final FilterArguments filterArguments;
    private final SimpleExecutor executor;
    private final TestCaseListener listener;

    record OmittedField(String name, String baselineValue) {
    }

    @SuppressWarnings("rawtypes")
    record PutReplacementPlan(String path, JsonObject payloadObj, OmittedField omitted, Map<String, Schema> sharedProps) {
        String payload() {
            return payloadObj.toString();
        }
    }

    /**
     * Creates a full-replacement {@code PUT} check using resources created in the current run.
     */
    public PartialUpdatePutFuzzer(RuntimeResourcePool pool, ApiArguments arguments, FilterArguments filterArguments,
                                  SimpleExecutor executor, TestCaseListener listener) {
        this.pool = pool;
        this.arguments = arguments;
        this.filterArguments = filterArguments;
        this.executor = executor;
        this.listener = listener;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void fuzz(FuzzingData data) {
        if (!hasInScopeReadAndPutSchema(data)) {
            logger.skip("PUT full-replacement check needs an in-scope item GET and request schema on {} {}",
                    data.getMethod(), data.getPath());
            return;
        }
        Map<String, Schema> candidateProps = candidateWritableProperties(data);
        if (candidateProps.isEmpty()) {
            logger.skip("No shared writable fields declared in PUT request and GET response schemas on {}", data.getPath());
            return;
        }
        ObservedHttpTarget.latestPutTarget(pool, arguments, data.getPath())
                .ifPresentOrElse(observed -> runReplacementCheck(data, observed, candidateProps),
                        () -> logger.skip("No successful PUT to a resource created in this run was observed for {}", data.getPath()));
    }

    private boolean hasInScopeReadAndPutSchema(FuzzingData data) {
        return data.getMethod() == HttpMethod.PUT
                && data.getPathItem() != null
                && data.getPathItem().getGet() != null
                && data.getReqSchema() != null
                && filterArguments.isHttpMethodSupplied(HttpMethod.GET);
    }

    @SuppressWarnings("rawtypes")
    private void runReplacementCheck(FuzzingData data, ObservedHttpTarget.OwnedWrite observed,
                                     Map<String, Schema> sharedProps) {
        Optional<JsonObject> baseline = readBaseline(data, observed.path());
        if (baseline.isEmpty()) {
            return;
        }
        Optional<PutReplacementPlan> plan = buildPlan(data, observed, baseline.get(), sharedProps);
        if (plan.isEmpty()) {
            logger.skip("No comparable PUT fields found for full-replacement check on {}", data.getPath());
            return;
        }
        if (sendPut(data, plan.get())) {
            verifyAfterGet(data, plan.get());
        }
    }

    private Optional<JsonObject> readBaseline(FuzzingData data, String path) {
        AtomicReference<JsonObject> snapshot = new AtomicReference<>();
        executor.execute(SimpleExecutorContext.builder()
                .fuzzer(this).logger(logger).fuzzingData(data)
                .httpMethod(HttpMethod.GET).path(path).payload("{}")
                .replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario("Read resource before sending PUT to verify full-replacement semantics")
                .responseProcessor((response, context) -> captureBaseline(response, context, snapshot))
                .build());
        return Optional.ofNullable(snapshot.get());
    }

    private void captureBaseline(CatsResponse response, FuzzingData data, AtomicReference<JsonObject> snapshot) {
        if (reportedServerError(response, data, "GET before PUT full-replacement check returned {}")) {
            return;
        }
        inspectableObject(response).ifPresentOrElse(obj -> {
            snapshot.set(obj);
            listener.reportResultInfo(logger, data, "Captured baseline resource state before PUT");
        }, () -> listener.reportResultInfo(logger, data, "Cannot inspect baseline resource state (GET returned {})",
                response.responseCodeAsString()));
    }

    @SuppressWarnings("rawtypes")
    static Optional<PutReplacementPlan> buildPlan(FuzzingData data, ObservedHttpTarget.OwnedWrite observed,
                                                  JsonObject baseline, Map<String, Schema> candidateProps) {
        JsonElement parsed = JsonUtils.parseAsJsonElement(observed.body());
        Map<String, Schema> sharedProps = filterByBaselineWhenGetSchemaAbsent(data, candidateProps, baseline);
        if (!parsed.isJsonObject() || sharedProps.isEmpty()) {
            return Optional.empty();
        }
        JsonObject sentObj = parsed.getAsJsonObject();
        Optional<OmittedField> omitted = findOmittableField(data, sentObj, baseline, sharedProps);
        JsonObject payloadObj = sentObj.deepCopy();
        omitted.map(OmittedField::name).ifPresent(payloadObj::remove);
        if (omitted.isEmpty() && sharedProps.keySet().stream().noneMatch(field -> isPrimitiveField(payloadObj, field))) {
            return Optional.empty();
        }
        return Optional.of(new PutReplacementPlan(observed.path(), payloadObj, omitted.orElse(null), sharedProps));
    }

    @SuppressWarnings("rawtypes")
    private static Map<String, Schema> filterByBaselineWhenGetSchemaAbsent(FuzzingData data, Map<String, Schema> candidateProps,
                                                                           JsonObject baseline) {
        if (!getResponseProperties(data).isEmpty()) {
            return candidateProps;
        }
        Map<String, Schema> filtered = new LinkedHashMap<>();
        candidateProps.forEach((field, schema) -> {
            if (baseline.has(field)) {
                filtered.put(field, schema);
            }
        });
        return Map.copyOf(filtered);
    }

    @SuppressWarnings("rawtypes")
    private static Optional<OmittedField> findOmittableField(FuzzingData data, JsonObject sentObj, JsonObject baseline,
                                                             Map<String, Schema> sharedProps) {
        if (sentObj.size() < 2) {
            return Optional.empty();
        }
        Set<String> required = extractRequiredFields(data);
        return sentObj.keySet().stream().sorted()
                .filter(sharedProps::containsKey)
                .filter(field -> !required.contains(field))
                .filter(field -> sharedProps.get(field).getDefault() == null)
                .filter(field -> isPrimitiveField(sentObj, field) && isPopulatedNonZeroPrimitive(baseline.get(field)))
                .findFirst()
                .map(field -> new OmittedField(field, baseline.getAsJsonPrimitive(field).getAsString()));
    }

    private static Set<String> extractRequiredFields(FuzzingData data) {
        Set<String> required = new HashSet<>();
        if (data.getAllRequiredFields() != null) {
            required.addAll(data.getAllRequiredFields());
        }
        Schema<?> resolved = CatsModelUtils.resolveSchema(data.getReqSchema(), data.getSchemaMap());
        if (resolved != null && resolved.getRequired() != null) {
            required.addAll(resolved.getRequired());
        }
        return required;
    }

    private static boolean isPopulatedNonZeroPrimitive(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return false;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        String text = primitive.getAsString().trim();
        if (text.isEmpty() || "false".equalsIgnoreCase(text)) {
            return false;
        }
        return !isNumericZero(text);
    }

    private static boolean isNumericZero(String text) {
        try {
            return new BigDecimal(text).compareTo(BigDecimal.ZERO) == 0;
        } catch (NumberFormatException _) {
            return false;
        }
    }

    private boolean sendPut(FuzzingData data, PutReplacementPlan plan) {
        AtomicBoolean succeeded = new AtomicBoolean();
        executor.execute(SimpleExecutorContext.builder()
                .fuzzer(this).logger(logger).fuzzingData(data)
                .httpMethod(HttpMethod.PUT).path(plan.path()).payload(plan.payload())
                .replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario(putScenario(plan))
                .responseProcessor((response, context) -> succeeded.set(classifyPutResponse(response, context)))
                .build());
        return succeeded.get();
    }

    private static String putScenario(PutReplacementPlan plan) {
        return plan.omitted() != null
                ? "Send PUT omitting optional field [%s] to verify PUT performs a full replacement".formatted(plan.omitted().name())
                : "Send PUT and verify all shared schema fields are replaced on a subsequent GET";
    }

    private boolean classifyPutResponse(CatsResponse response, FuzzingData data) {
        if (reportedServerError(response, data, "PUT during full-replacement check returned {}")) {
            return false;
        }
        listener.reportResultInfo(logger, data, "PUT during full-replacement check returned {}", response.responseCodeAsString());
        return ResponseCodeFamily.is2xxCode(response.getResponseCode());
    }

    private void verifyAfterGet(FuzzingData data, PutReplacementPlan plan) {
        executor.execute(SimpleExecutorContext.builder()
                .fuzzer(this).logger(logger).fuzzingData(data)
                .httpMethod(HttpMethod.GET).path(plan.path()).payload("{}")
                .replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario("Read back resource after PUT to verify full-replacement semantics")
                .responseProcessor((response, context) -> checkAfterGet(response, context, plan))
                .build());
    }

    private void checkAfterGet(CatsResponse response, FuzzingData data, PutReplacementPlan plan) {
        if (reportedServerError(response, data, "GET after PUT full-replacement check returned {}")) {
            return;
        }
        inspectableObject(response).ifPresentOrElse(afterObj -> reportVerdict(data, plan, afterObj),
                () -> listener.reportResultInfo(logger, data, "Post-PUT GET inconclusive (status {})",
                        response.responseCodeAsString()));
    }

    private void reportVerdict(FuzzingData data, PutReplacementPlan plan, JsonObject afterObj) {
        List<String> violations = collectViolations(plan, afterObj);
        if (violations.isEmpty()) {
            listener.reportResultInfo(logger, data, "PUT full-replacement check passed on {}", plan.path());
            return;
        }
        listener.reportResultWarn(logger, data, Reason.PARTIAL_UPDATE_PUT.value(),
                "PUT did not perform a full resource replacement on {}: {}", plan.path(), violations);
    }

    static List<String> collectViolations(PutReplacementPlan plan, JsonObject afterObj) {
        List<String> violations = new ArrayList<>();
        if (retainedOmittedField(plan, afterObj)) {
            violations.add("omitted optional field [%s] retained value [%s]"
                    .formatted(plan.omitted().name(), plan.omitted().baselineValue()));
        }
        plan.sharedProps().forEach((field, schema) -> {
            if (sentFieldDiffers(plan.payloadObj(), afterObj, field, schema)) {
                violations.add("sent field [%s] differs".formatted(field));
            }
        });
        return List.copyOf(violations);
    }

    private static boolean retainedOmittedField(PutReplacementPlan plan, JsonObject afterObj) {
        OmittedField omitted = plan.omitted();
        if (omitted == null || !isPrimitiveField(afterObj, omitted.name())) {
            return false;
        }
        JsonPrimitive afterVal = afterObj.getAsJsonPrimitive(omitted.name());
        return UpdateReadConsistencyFuzzer.samePrimitiveValue(
                new JsonPrimitive(omitted.baselineValue()), afterVal, plan.sharedProps().get(omitted.name()));
    }

    private static boolean sentFieldDiffers(JsonObject sent, JsonObject afterObj, String field, Schema<?> schema) {
        return isPrimitiveField(sent, field) && isPrimitiveField(afterObj, field)
                && !UpdateReadConsistencyFuzzer.samePrimitiveValue(
                        sent.getAsJsonPrimitive(field), afterObj.getAsJsonPrimitive(field), schema);
    }

    private static boolean isPrimitiveField(JsonObject object, String field) {
        return object.has(field) && object.get(field) != null && object.get(field).isJsonPrimitive();
    }

    private boolean reportedServerError(CatsResponse response, FuzzingData data, String message) {
        if (!ResponseCodeFamily.is5xxCode(response.getResponseCode())) {
            return false;
        }
        listener.reportResultError(logger, data, Reason.SERVER_ERROR.value(), message, response.responseCodeAsString());
        return true;
    }

    private static Optional<JsonObject> inspectableObject(CatsResponse response) {
        if (!ResponseCodeFamily.is2xxCode(response.getResponseCode())
                || response.isBodyTruncated()
                || !JsonUtils.isValidJson(response.getBody())) {
            return Optional.empty();
        }
        JsonElement parsed = JsonUtils.parseAsJsonElement(response.getBody());
        return parsed.isJsonObject() ? Optional.of(parsed.getAsJsonObject()) : Optional.empty();
    }

    /**
     * Returns writable, non-volatile, non-identifier fields declared in the PUT request schema and, when the GET
     * operation declares a response schema, also present in that GET response schema (papers.md A1, WFC 117).
     */
    @SuppressWarnings("rawtypes")
    static Map<String, Schema> candidateWritableProperties(FuzzingData data) {
        Map<String, Schema> putProps = UpdateReadConsistencyFuzzer.requestProperties(data);
        Map<String, Schema> getProps = getResponseProperties(data);
        Map<String, Schema> shared = new LinkedHashMap<>();
        putProps.forEach((name, putSchema) -> {
            Schema<?> getSchema = getProps.isEmpty() ? putSchema : getProps.get(name);
            if (isComparableSharedField(name, putSchema, getSchema)) {
                shared.put(name, putSchema);
            }
        });
        return Map.copyOf(shared);
    }

    @SuppressWarnings("rawtypes")
    static Map<String, Schema> sharedWritableProperties(FuzzingData data) {
        return getResponseProperties(data).isEmpty() ? Map.of() : candidateWritableProperties(data);
    }

    private static boolean isComparableSharedField(String name, Schema<?> putSchema, Schema<?> getSchema) {
        return putSchema != null && getSchema != null
                && isWritableAndReadable(putSchema) && isWritableAndReadable(getSchema)
                && !PutIdempotencyFuzzer.isVolatileField(name)
                && !isIdentifierOrSensitive(name);
    }

    private static boolean isWritableAndReadable(Schema<?> schema) {
        return !Boolean.TRUE.equals(schema.getReadOnly()) && !Boolean.TRUE.equals(schema.getWriteOnly());
    }

    private static boolean isIdentifierOrSensitive(String field) {
        List<String> words = WordUtils.splitWords(field);
        if (words.isEmpty()) {
            return false;
        }
        String last = words.getLast().toLowerCase(Locale.ROOT);
        String normalized = WordUtils.normalizeAlphaNumeric(field);
        return IDENTIFIER_WORDS.contains(last) || SENSITIVE_WORDS.contains(last) || SENSITIVE_WORDS.contains(normalized);
    }

    @SuppressWarnings("rawtypes")
    static Map<String, Schema> getResponseProperties(FuzzingData data) {
        Operation getOp = data.getPathItem() == null ? null : data.getPathItem().getGet();
        if (getOp == null || getOp.getResponses() == null) {
            return Map.of();
        }
        Map<String, Schema> props = collectMatchingResponseProperties(getOp, data, true);
        return props.isEmpty() ? collectMatchingResponseProperties(getOp, data, false) : props;
    }

    @SuppressWarnings("rawtypes")
    private static Map<String, Schema> collectMatchingResponseProperties(Operation getOp, FuzzingData data, boolean twoXxOnly) {
        Map<String, Schema> props = new LinkedHashMap<>();
        Map<String, Schema> schemaMap = resolveSchemaMap(data);
        getOp.getResponses().forEach((code, response) -> {
            if (matchesResponseCode(code, twoXxOnly)) {
                collectApiResponseProperties(response, data, schemaMap, props);
            }
        });
        return props;
    }

    private static boolean matchesResponseCode(String code, boolean twoXxOnly) {
        return code != null && (twoXxOnly ? code.startsWith("2") : "default".equalsIgnoreCase(code));
    }

    @SuppressWarnings("rawtypes")
    private static void collectApiResponseProperties(ApiResponse rawResponse, FuzzingData data,
                                                     Map<String, Schema> schemaMap, Map<String, Schema> props) {
        ApiResponse resolved = data.getOpenApi() != null
                ? OpenApiUtils.resolveApiResponse(rawResponse, data.getOpenApi()) : rawResponse;
        if (resolved == null || resolved.getContent() == null) {
            return;
        }
        Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (MediaType media : resolved.getContent().values()) {
            if (media != null && media.getSchema() != null) {
                UpdateReadConsistencyFuzzer.collectProperties(media.getSchema(), schemaMap, 0, visited, props);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static Map<String, Schema> resolveSchemaMap(FuzzingData data) {
        if (data.getSchemaMap() != null && !data.getSchemaMap().isEmpty()) {
            return data.getSchemaMap();
        }
        if (data.getOpenApi() != null && data.getOpenApi().getComponents() != null
                && data.getOpenApi().getComponents().getSchemas() != null) {
            return data.getOpenApi().getComponents().getSchemas();
        }
        return Map.of();
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "send a PUT on an owned resource and warn if an omitted optional field retains its previous value or a shared writable field differs on a subsequent GET";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }
}
