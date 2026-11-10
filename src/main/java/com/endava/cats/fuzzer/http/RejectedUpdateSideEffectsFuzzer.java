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
import com.endava.cats.model.RequestTarget;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsModelUtils;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.WordUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Verifies that a {@code PUT} or {@code PATCH} rejected with {@code 4XX} leaves no side effects on a resource created
 * in this run. After a baseline read proves the resource exists, the probe combines a valid change (a deterministic
 * canary in a free-text string field) with a type violation in a later field, then reads the resource back. A side
 * effect is only flagged when the field changed to the exact value sent. RFC 5789 requires PATCH to be atomic, so a persisted
 * canary is reported as an error for PATCH and as a warning for PUT.
 */
@HttpFuzzer
@SecondPhaseFuzzer
@Singleton
public class RejectedUpdateSideEffectsFuzzer implements Fuzzer {
    private static final String CANARY_PREFIX = "cats-atomic-";
    private static final String NOT_A_NUMBER = "cats-not-a-number";
    private static final String NOT_A_BOOLEAN = "cats-not-a-boolean";
    private static final String NOT_A_STRING = "cats-not-a-string";
    private static final Set<String> IDENTIFIER_WORDS = Set.of("id", "uuid", "guid");

    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(RejectedUpdateSideEffectsFuzzer.class);
    private final RuntimeResourcePool pool;
    private final ApiArguments arguments;
    private final FilterArguments filterArguments;
    private final SimpleExecutor executor;
    private final TestCaseListener listener;

    record ProbeFields(String canaryField, String canaryValue, String invalidField, JsonElement invalidValue) {
        String applyTo(JsonObject body) {
            JsonObject probe = body.deepCopy();
            probe.addProperty(canaryField, canaryValue);
            probe.remove(invalidField);
            probe.add(invalidField, invalidValue.deepCopy());
            return probe.toString();
        }

        boolean canaryPersisted(JsonObject state) {
            JsonElement value = state.get(canaryField);
            return value != null && value.isJsonPrimitive() && canaryValue.equalsIgnoreCase(value.getAsString().trim());
        }
    }

    record AtomicityProbe(String path, String payload, ProbeFields fields) {
    }

    /**
     * Creates a rejected-update side-effect check using only resources created in the current run.
     */
    public RejectedUpdateSideEffectsFuzzer(RuntimeResourcePool pool, ApiArguments arguments, FilterArguments filterArguments,
                                           SimpleExecutor executor, TestCaseListener listener) {
        this.pool = pool;
        this.arguments = arguments;
        this.filterArguments = filterArguments;
        this.executor = executor;
        this.listener = listener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!hasInScopeWriteAndRead(data)) {
            logger.skip("Rejected update side-effect check needs an in-scope item GET for {} {}", data.getMethod(), data.getPath());
            return;
        }
        ObservedHttpTarget.latestOwnedWrite(pool, arguments, data.getPath(), data.getMethod())
                .flatMap(owned -> buildProbe(data, owned))
                .ifPresentOrElse(probe -> runProbe(data, probe), () -> logger.skip(
                        "No owned {} with a free-text string field and a typed field was observed for {}", data.getMethod(), data.getPath()));
    }

    private void runProbe(FuzzingData data, AtomicityProbe probe) {
        if (hasCleanBaseline(data, probe) && sendInvalidUpdate(data, probe)) {
            verifyNoSideEffects(data, probe);
        }
    }

    private boolean hasInScopeWriteAndRead(FuzzingData data) {
        return (data.getMethod() == HttpMethod.PUT || data.getMethod() == HttpMethod.PATCH)
                && data.getPathItem() != null && data.getPathItem().getGet() != null
                && filterArguments.isHttpMethodSupplied(HttpMethod.GET);
    }

    static Optional<AtomicityProbe> buildProbe(FuzzingData data, ObservedHttpTarget.OwnedWrite owned) {
        JsonElement parsed = JsonUtils.parseAsJsonElement(owned.body());
        if (!parsed.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject body = parsed.getAsJsonObject();
        return probeFields(data, body).map(fields -> new AtomicityProbe(owned.path(), fields.applyTo(body), fields));
    }

    private static Optional<ProbeFields> probeFields(FuzzingData data, JsonObject body) {
        String canary = CANARY_PREFIX + data.getMethod().name().toLowerCase(Locale.ROOT);
        Optional<String> canaryField = firstField(body, field -> isCanaryCandidate(data, field, body.get(field), canary));
        return canaryField.flatMap(canaryName -> invalidField(data, body, canaryName).map(invalidName ->
                new ProbeFields(canaryName, canary, invalidName, invalidValue(propertySchema(data, invalidName)))));
    }

    /**
     * Prefers numeric and boolean fields, whose type violations are rejected most reliably, then other string fields.
     */
    private static Optional<String> invalidField(FuzzingData data, JsonObject body, String canaryField) {
        return firstField(body, field -> isTypedField(data, field))
                .or(() -> firstField(body, field -> !field.equals(canaryField) && isViolatableString(data, field)));
    }

    private static Optional<String> firstField(JsonObject body, Predicate<String> filter) {
        return body.keySet().stream().sorted().filter(filter).findFirst();
    }

    private static boolean isCanaryCandidate(FuzzingData data, String field, JsonElement value, String canary) {
        Schema<?> schema = propertySchema(data, field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && isWritable(schema) && isFreeTextString(schema) && fitsLength(schema, canary)
                && !PutIdempotencyFuzzer.isVolatileField(field);
    }

    private static boolean isTypedField(FuzzingData data, String field) {
        Schema<?> schema = propertySchema(data, field);
        return isWritable(schema) && !isIdentifier(field) && !CatsModelUtils.isEnumSchema(schema)
                && (CatsModelUtils.isIntegerSchema(schema) || CatsModelUtils.isNumberSchema(schema) || CatsModelUtils.isBooleanSchema(schema));
    }

    private static boolean isViolatableString(FuzzingData data, String field) {
        Schema<?> schema = propertySchema(data, field);
        return isWritable(schema) && !isIdentifier(field) && CatsModelUtils.isStringSchema(schema);
    }

    private static boolean isIdentifier(String field) {
        List<String> words = WordUtils.splitWords(field);
        return !words.isEmpty() && IDENTIFIER_WORDS.contains(words.getLast().toLowerCase(Locale.ROOT));
    }

    private static boolean isWritable(Schema<?> schema) {
        return schema != null && !Boolean.TRUE.equals(schema.getReadOnly()) && !Boolean.TRUE.equals(schema.getWriteOnly());
    }

    private static boolean isFreeTextString(Schema<?> schema) {
        return CatsModelUtils.isStringSchema(schema) && !CatsModelUtils.isEnumSchema(schema)
                && schema.getPattern() == null && schema.getFormat() == null;
    }

    private static boolean fitsLength(Schema<?> schema, String value) {
        return (schema.getMinLength() == null || schema.getMinLength() <= value.length())
                && (schema.getMaxLength() == null || schema.getMaxLength() >= value.length());
    }

    private static JsonElement invalidValue(Schema<?> schema) {
        if (CatsModelUtils.isStringSchema(schema)) {
            JsonObject notAString = new JsonObject();
            notAString.addProperty("cats", NOT_A_STRING);
            return notAString;
        }
        return new JsonPrimitive(CatsModelUtils.isBooleanSchema(schema) ? NOT_A_BOOLEAN : NOT_A_NUMBER);
    }

    private static Schema<?> propertySchema(FuzzingData data, String field) {
        return data.getRequestPropertyTypes() == null ? null : data.getRequestPropertyTypes().get(field);
    }

    private boolean sendInvalidUpdate(FuzzingData data, AtomicityProbe probe) {
        AtomicBoolean rejected = new AtomicBoolean();
        ProbeFields fields = probe.fields();
        executor.execute(SimpleExecutorContext.builder().fuzzer(this).logger(logger).fuzzingData(data)
                .path(probe.path()).payload(probe.payload()).replaceRefData(false).replaceUrlParams(false)
                .mutationTarget(RequestTarget.body(fields.canaryField()))
                .mutationTarget(RequestTarget.body(fields.invalidField()))
                .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                .scenario("Send %s with a valid change to [%s] and an invalid type in [%s], expecting a rejection that applies nothing"
                        .formatted(data.getMethod(), fields.canaryField(), fields.invalidField()))
                .responseProcessor((response, context) -> rejected.set(classifyRejection(response, context, fields)))
                .build());
        return rejected.get();
    }

    private boolean classifyRejection(CatsResponse response, FuzzingData data, ProbeFields fields) {
        if (ResponseCodeFamily.is5xxCode(response.getResponseCode())) {
            listener.reportResultError(logger, data, Reason.SERVER_ERROR.value(),
                    "Update carrying an invalid [{}] returned {}", fields.invalidField(), response.responseCodeAsString());
            return false;
        }
        if (!isEvaluableRejection(response.getResponseCode())) {
            listener.reportResultInfo(logger, data, "Update carrying an invalid [{}] returned {}; side-effect check not applicable",
                    fields.invalidField(), response.responseCodeAsString());
            return false;
        }
        listener.reportResultInfo(logger, data, "Update carrying an invalid [{}] was rejected with {}",
                fields.invalidField(), response.responseCodeAsString());
        return true;
    }

    static boolean isEvaluableRejection(int code) {
        return ResponseCodeFamily.is4xxCode(code) && !ResponseCodeFamily.isNotFoundOrGone(code);
    }

    /** Proves the resource exists and does not already hold the canary, so a later canary read is meaningful. */
    private boolean hasCleanBaseline(FuzzingData data, AtomicityProbe probe) {
        AtomicBoolean clean = new AtomicBoolean();
        readResource(data, probe.path(), "Read the owned resource before sending the invalid %s".formatted(data.getMethod()),
                (response, context) -> clean.set(classifyBaseline(response, context, probe.fields())));
        return clean.get();
    }

    private boolean classifyBaseline(CatsResponse response, FuzzingData data, ProbeFields fields) {
        if (reportedServerError(response, data)) {
            return false;
        }
        Optional<JsonObject> baseline = inspectableObject(response).filter(Predicate.not(fields::canaryPersisted));
        listener.reportResultInfo(logger, data, baseline.isPresent()
                ? "Captured baseline resource state (GET returned {})"
                : "Rejected update side-effect check skipped: baseline not inspectable or already holds the canary (GET returned {})",
                response.responseCodeAsString());
        return baseline.isPresent();
    }

    private void verifyNoSideEffects(FuzzingData data, AtomicityProbe probe) {
        readResource(data, probe.path(), "Read the resource back and verify the rejected %s did not persist [%s]"
                        .formatted(data.getMethod(), probe.fields().canaryField()),
                (response, context) -> judgePersistedState(response, context, probe.fields()));
    }

    private void readResource(FuzzingData data, String path, String scenario, BiConsumer<CatsResponse, FuzzingData> processor) {
        executor.execute(SimpleExecutorContext.builder().fuzzer(this).logger(logger).fuzzingData(data)
                .httpMethod(HttpMethod.GET).path(path).payload("{}").replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario(scenario)
                .responseProcessor(processor)
                .build());
    }

    private boolean reportedServerError(CatsResponse response, FuzzingData data) {
        if (!ResponseCodeFamily.is5xxCode(response.getResponseCode())) {
            return false;
        }
        listener.reportResultError(logger, data, Reason.SERVER_ERROR.value(),
                "GET during the rejected update side-effect check returned {}", response.responseCodeAsString());
        return true;
    }

    private void judgePersistedState(CatsResponse response, FuzzingData data, ProbeFields fields) {
        if (reportedServerError(response, data)) {
            return;
        }
        inspectableObject(response).ifPresentOrElse(state -> reportSideEffect(data, fields, state),
                () -> listener.reportResultInfo(logger, data, "Rejected update side-effect check inconclusive (GET returned {})",
                        response.responseCodeAsString()));
    }

    private void reportSideEffect(FuzzingData data, ProbeFields fields, JsonObject state) {
        if (!fields.canaryPersisted(state)) {
            listener.reportResultInfo(logger, data, "Rejected update did not persist the change to [{}]", fields.canaryField());
            return;
        }
        String message = "{} rejected because of an invalid [{}] still persisted [{}] = [{}]";
        if (data.getMethod() == HttpMethod.PATCH) {
            listener.reportResultError(logger, data, Reason.REJECTED_UPDATE_PARTIALLY_APPLIED.value(), message,
                    data.getMethod(), fields.invalidField(), fields.canaryField(), fields.canaryValue());
            return;
        }
        listener.reportResultWarn(logger, data, Reason.REJECTED_UPDATE_PARTIALLY_APPLIED.value(), message,
                data.getMethod(), fields.invalidField(), fields.canaryField(), fields.canaryValue());
    }

    private static Optional<JsonObject> inspectableObject(CatsResponse response) {
        if (!ResponseCodeFamily.is2xxCode(response.getResponseCode()) || response.isBodyTruncated()
                || !JsonUtils.isValidJson(response.getBody())) {
            return Optional.empty();
        }
        JsonElement parsed = JsonUtils.parseAsJsonElement(response.getBody());
        return parsed.isJsonObject() ? Optional.of(parsed.getAsJsonObject()) : Optional.empty();
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "send a PUT or PATCH on an owned resource that combines a valid change with an invalid field and verify the rejected request persisted nothing";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }
}
