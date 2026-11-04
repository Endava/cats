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
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Merges properties from two distinct OpenAPI {@code oneOf} branches into a single JSON object
 * to verify that the API enforces exclusive {@code oneOf} semantics (expecting 4XX) rather than {@code anyOf}.
 */
@Singleton
@FieldFuzzer
public class OneOfMultipleBranchesMergedFieldsFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(getClass());
    private final SimpleExecutor simpleExecutor;

    public OneOfMultipleBranchesMergedFieldsFuzzer(SimpleExecutor simpleExecutor) {
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!JsonUtils.isValidJson(data.getPayload())) {
            logger.debug("Skipping fuzzer because payload is not valid JSON");
            return;
        }
        JsonElement parsedRoot = JsonUtils.parseAsJsonElement(data.getPayload());
        for (JsonPayloadTarget target : collectOneOfTargets(data)) {
            mergeWithSecondBranch(parsedRoot, target, data.getSchemaMap())
                    .ifPresent(mergedPayload -> executeMergedTest(data, target, mergedPayload));
        }
    }

    private void executeMergedTest(FuzzingData data, JsonPayloadTarget target, String mergedPayload) {
        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .fuzzer(this)
                        .fuzzingData(data)
                        .logger(logger)
                        .payload(mergedPayload)
                        .mutationTarget(target.root() ? RequestTarget.requestBody() : RequestTarget.body(target.path()))
                        .expectedResponseCode(ResponseCodeFamilyPredefined.FOURXX)
                        .replaceRefData(false)
                        .scenario("Merge properties from two distinct oneOf branches in [%s] to violate exclusive oneOf constraint"
                                .formatted(target.displayName()))
                        .build());
    }

    static List<JsonPayloadTarget> collectOneOfTargets(FuzzingData data) {
        Schema<?> rootOneOf = resolveRootOneOfSchema(data);
        List<JsonPayloadTarget> candidates = JsonPayloadTarget.candidatesFor(data).stream()
                .map(target -> target.root() && rootOneOf != null
                        ? new JsonPayloadTarget("$", true, rootOneOf)
                        : target)
                .filter(target -> hasAtLeastTwoOneOfBranches(target.schema()))
                .toList();
        return List.copyOf(candidates);
    }

    private static boolean hasAtLeastTwoOneOfBranches(Schema<?> schema) {
        return schema != null && CatsUtil.isNotEmpty(schema.getOneOf()) && schema.getOneOf().size() >= 2;
    }

    private static Schema<?> resolveRootOneOfSchema(FuzzingData data) {
        Schema<?> reqSchema = CatsModelUtils.resolveSchema(data.getReqSchema(), data.getSchemaMap());
        if (hasAtLeastTwoOneOfBranches(reqSchema)) {
            return reqSchema;
        }
        if (data.getPathItem() == null || data.getMethod() == null) {
            return null;
        }
        Operation op = HttpMethod.getOperation(data.getMethod(), data.getPathItem());
        if (op == null || op.getRequestBody() == null || op.getRequestBody().getContent() == null) {
            return null;
        }
        return op.getRequestBody().getContent().values().stream()
                .map(MediaType::getSchema)
                .filter(Objects::nonNull)
                .map(s -> CatsModelUtils.resolveSchema(s, data.getSchemaMap()))
                .filter(OneOfMultipleBranchesMergedFieldsFuzzer::hasAtLeastTwoOneOfBranches)
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    static Optional<String> mergeWithSecondBranch(JsonElement parsedRoot, JsonPayloadTarget target,
                                                  Map<String, Schema> schemaMap) {
        Optional<JsonObject> currentObjOpt = target.objectFrom(parsedRoot);
        if (currentObjOpt.isEmpty() || target.schema() == null) {
            return Optional.empty();
        }
        JsonObject merged = currentObjOpt.get().deepCopy();
        String discriminatorProp = target.schema().getDiscriminator() != null
                ? target.schema().getDiscriminator().getPropertyName()
                : null;
        int initialSize = merged.size();

        for (Schema<?> rawBranch : target.schema().getOneOf()) {
            Schema<?> branch = CatsModelUtils.resolveSchema(rawBranch, schemaMap);
            if (branch != null && branch.getProperties() != null) {
                addMissingBranchProperties(merged, branch.getProperties(), discriminatorProp, schemaMap);
                if (merged.size() > initialSize) {
                    return Optional.of(target.replaceValueIn(parsedRoot.toString(), merged));
                }
            }
        }
        return Optional.empty();
    }

    private static void addMissingBranchProperties(JsonObject targetObj, Map<String, Schema> branchProps,
                                                   String discriminatorProp, Map<String, Schema> schemaMap) {
        for (Map.Entry<String, Schema> entry : branchProps.entrySet()) {
            String propName = entry.getKey();
            if (!targetObj.has(propName) && !propName.equals(discriminatorProp)) {
                Schema<?> resolvedProp = CatsModelUtils.resolveSchema(entry.getValue(), schemaMap);
                addSampleProperty(targetObj, propName, resolvedProp);
            }
        }
    }

    private static void addSampleProperty(JsonObject targetObj, String propName, Schema<?> propSchema) {
        if (propSchema != null && CatsUtil.isNotEmpty(propSchema.getEnum())) {
            targetObj.addProperty(propName, String.valueOf(propSchema.getEnum().getFirst()));
        } else if (CatsModelUtils.isBooleanSchema(propSchema)) {
            targetObj.addProperty(propName, true);
        } else if (CatsModelUtils.isIntegerSchema(propSchema) || CatsModelUtils.isNumberSchema(propSchema)) {
            targetObj.addProperty(propName, 1);
        } else {
            targetObj.addProperty(propName, "catsOneOfMerged");
        }
    }

    @Override
    public List<HttpMethod> skipForHttpMethods() {
        return List.of(HttpMethod.GET, HttpMethod.DELETE, HttpMethod.HEAD, HttpMethod.TRACE);
    }

    @Override
    public String description() {
        return "merges properties from two distinct oneOf schema branches into a single payload to verify exclusive oneOf validation";
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(getClass().getSimpleName());
    }
}
