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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies RFC 5789 partial-update semantics on {@code PATCH} endpoints by reading an owned resource,
 * sending a {@code PATCH} payload that omits a previously populated optional field, and reading the
 * resource back to ensure the omitted field was not wiped to {@code null} or removed.
 */
@HttpFuzzer
@SecondPhaseFuzzer
@Singleton
public class PatchOmittedFieldsClobberFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(PatchOmittedFieldsClobberFuzzer.class);
    private final RuntimeResourcePool pool;
    private final ApiArguments arguments;
    private final FilterArguments filterArguments;
    private final SimpleExecutor executor;
    private final TestCaseListener listener;

    record PartialPatchPlan(String path, String partialPayload, String omittedField, String baselineValue) {
    }

    /**
     * Creates a partial-PATCH persistence check using resources created in the current run.
     */
    public PatchOmittedFieldsClobberFuzzer(RuntimeResourcePool pool, ApiArguments arguments,
                                           FilterArguments filterArguments, SimpleExecutor executor,
                                           TestCaseListener listener) {
        this.pool = pool;
        this.arguments = arguments;
        this.filterArguments = filterArguments;
        this.executor = executor;
        this.listener = listener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!hasInScopeItemReadAndPatchSchema(data)) {
            logger.skip("Patch omitted-fields check needs an in-scope item GET and request schema on {} {}",
                    data.getMethod(), data.getPath());
            return;
        }
        Optional<ObservedHttpTarget.OwnedWrite> patchWrite =
                ObservedHttpTarget.latestOwnedWrite(pool, arguments, data.getPath(), HttpMethod.PATCH);
        if (patchWrite.isEmpty()) {
            logger.skip("No successful PATCH to an owned resource was observed for {}", data.getPath());
            return;
        }
        ObservedHttpTarget.OwnedWrite observed = patchWrite.get();
        Optional<JsonObject> baseline = readResourceSnapshot(data, observed.path(),
                "Read owned resource before sending partial PATCH with an omitted optional field");
        if (baseline.isEmpty()) {
            return;
        }
        Optional<PartialPatchPlan> plan = buildPartialPatchPlan(data, observed, baseline.get());
        if (plan.isEmpty()) {
            logger.skip("No omittable populated optional field found for partial PATCH check on {}", data.getPath());
            return;
        }
        if (sendPartialPatch(data, plan.get())) {
            verifyOmittedFieldPreserved(data, plan.get());
        }
    }

    private boolean hasInScopeItemReadAndPatchSchema(FuzzingData data) {
        return data.getMethod() == HttpMethod.PATCH
                && data.getPathItem() != null
                && data.getPathItem().getGet() != null
                && data.getReqSchema() != null
                && filterArguments.isHttpMethodSupplied(HttpMethod.GET);
    }

    private Optional<JsonObject> readResourceSnapshot(FuzzingData data, String path, String scenario) {
        AtomicReference<JsonObject> snapshot = new AtomicReference<>();
        executor.execute(SimpleExecutorContext.builder()
                .fuzzer(this).logger(logger).fuzzingData(data)
                .httpMethod(HttpMethod.GET).path(path).payload("{}")
                .replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario(scenario)
                .responseProcessor((response, context) -> captureGetObject(response, context, snapshot))
                .build());
        return Optional.ofNullable(snapshot.get());
    }

    private void captureGetObject(CatsResponse response, FuzzingData data, AtomicReference<JsonObject> snapshot) {
        if (ResponseCodeFamily.is5xxCode(response.getResponseCode())) {
            listener.reportResultError(logger, data, Reason.SERVER_ERROR.value(),
                    "GET during PATCH omitted-field check returned {}", response.responseCodeAsString());
            return;
        }
        if (!ResponseCodeFamily.is2xxCode(response.getResponseCode())
                || response.isBodyTruncated()
                || !JsonUtils.isValidJson(response.getBody())) {
            listener.reportResultInfo(logger, data, "Cannot inspect resource state (GET returned {})",
                    response.responseCodeAsString());
            return;
        }
        JsonElement parsed = JsonUtils.parseAsJsonElement(response.getBody());
        if (!parsed.isJsonObject()) {
            listener.reportResultInfo(logger, data, "PATCH omitted-field check requires a JSON object GET response");
            return;
        }
        snapshot.set(parsed.getAsJsonObject());
        listener.reportResultInfo(logger, data, "Captured baseline resource state before partial PATCH");
    }

    static Optional<PartialPatchPlan> buildPartialPatchPlan(FuzzingData data,
                                                            ObservedHttpTarget.OwnedWrite observed,
                                                            JsonObject baselineGet) {
        JsonElement sentElement = JsonUtils.parseAsJsonElement(observed.body());
        if (!sentElement.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject sentObj = sentElement.getAsJsonObject();
        if (sentObj.size() < 2) {
            return Optional.empty();
        }
        Set<String> required = extractRequiredFields(data);
        for (String field : sentObj.keySet().stream().sorted().toList()) {
            if (isOmittablePopulatedField(data, field, required, sentObj, baselineGet)) {
                JsonObject partial = sentObj.deepCopy();
                partial.remove(field);
                String baselineVal = baselineGet.get(field).getAsJsonPrimitive().getAsString();
                return Optional.of(new PartialPatchPlan(observed.path(), partial.toString(), field, baselineVal));
            }
        }
        return Optional.empty();
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

    private static boolean isOmittablePopulatedField(FuzzingData data, String field, Set<String> required,
                                                     JsonObject sentObj, JsonObject baselineGet) {
        if (required.contains(field) || PutIdempotencyFuzzer.isVolatileField(field)) {
            return false;
        }
        Schema<?> propSchema = data.getRequestPropertyTypes() == null ? null : data.getRequestPropertyTypes().get(field);
        if (propSchema != null && (Boolean.TRUE.equals(propSchema.getReadOnly()) || Boolean.TRUE.equals(propSchema.getWriteOnly()))) {
            return false;
        }
        return sentObj.has(field) && sentObj.get(field).isJsonPrimitive()
                && baselineGet.has(field) && baselineGet.get(field).isJsonPrimitive()
                && !baselineGet.get(field).getAsString().isBlank();
    }

    private boolean sendPartialPatch(FuzzingData data, PartialPatchPlan plan) {
        AtomicBoolean succeeded = new AtomicBoolean(false);
        executor.execute(SimpleExecutorContext.builder()
                .fuzzer(this).logger(logger).fuzzingData(data)
                .httpMethod(HttpMethod.PATCH).path(plan.path()).payload(plan.partialPayload())
                .replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario("Send partial PATCH omitting optional field [%s] to verify omitted fields are preserved"
                        .formatted(plan.omittedField()))
                .responseProcessor((response, context) -> {
                    boolean ok = ResponseCodeFamily.is2xxCode(response.getResponseCode());
                    succeeded.set(ok);
                    if (ResponseCodeFamily.is5xxCode(response.getResponseCode())) {
                        listener.reportResultError(logger, context, Reason.SERVER_ERROR.value(),
                                "Partial PATCH omitting [{}] returned {}", plan.omittedField(), response.responseCodeAsString());
                    } else {
                        listener.reportResultInfo(logger, context, "Partial PATCH omitting [{}] returned {}",
                                plan.omittedField(), response.responseCodeAsString());
                    }
                })
                .build());
        return succeeded.get();
    }

    private void verifyOmittedFieldPreserved(FuzzingData data, PartialPatchPlan plan) {
        executor.execute(SimpleExecutorContext.builder()
                .fuzzer(this).logger(logger).fuzzingData(data)
                .httpMethod(HttpMethod.GET).path(plan.path()).payload("{}")
                .replaceRefData(false).replaceUrlParams(false)
                .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                .scenario("Read back resource after partial PATCH to verify omitted field [%s] was not wiped"
                        .formatted(plan.omittedField()))
                .responseProcessor((response, context) -> checkAfterGet(response, context, plan))
                .build());
    }

    private void checkAfterGet(CatsResponse response, FuzzingData data, PartialPatchPlan plan) {
        if (ResponseCodeFamily.is5xxCode(response.getResponseCode())) {
            listener.reportResultError(logger, data, Reason.SERVER_ERROR.value(),
                    "GET after partial PATCH returned {}", response.responseCodeAsString());
            return;
        }
        if (!ResponseCodeFamily.is2xxCode(response.getResponseCode())
                || response.isBodyTruncated()
                || !JsonUtils.isValidJson(response.getBody())) {
            listener.reportResultInfo(logger, data, "Post-PATCH GET inconclusive (status {})", response.responseCodeAsString());
            return;
        }
        JsonElement afterElement = JsonUtils.parseAsJsonElement(response.getBody());
        if (!afterElement.isJsonObject()) {
            listener.reportResultInfo(logger, data, "Post-PATCH GET did not return a JSON object");
            return;
        }
        JsonObject afterObj = afterElement.getAsJsonObject();
        JsonElement fieldAfter = afterObj.get(plan.omittedField());
        if (fieldAfter == null || fieldAfter.isJsonNull()
                || (fieldAfter.isJsonPrimitive() && fieldAfter.getAsString().isBlank())) {
            listener.reportResultWarn(logger, data, Reason.PATCH_CLOBBERED_OMITTED_FIELDS.value(),
                    "Optional field [{}] had value [{}] before PATCH, but was wiped or set to null after a partial PATCH that omitted it",
                    plan.omittedField(), plan.baselineValue());
        } else {
            listener.reportResultInfo(logger, data,
                    "Omitted optional field [{}] was preserved after partial PATCH", plan.omittedField());
        }
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "send a partial PATCH omitting a populated optional field on an owned resource and warn if the omitted field is wiped on a subsequent GET";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }
}
