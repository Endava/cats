package com.endava.cats.fuzzer.special;

import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.dsl.DynamicValueResolver;
import com.endava.cats.dsl.api.Parser;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.fields.RequiredOnlyPayloadGenerator;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.BasePayloadMode;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FieldCorrelationRule;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowExpectation;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowStep;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyDynamic;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.RequestTargetResolver;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Standalone fuzzer that executes sequential functional flow YAML documents (`steps:`).
 */
@Singleton
@SpecialFuzzer
public class FunctionalFlowFuzzer implements Fuzzer {
    private static final String REF_DATA_CUSTOM_FILE = "refData_custom.yml";
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(FunctionalFlowFuzzer.class);
    private final FilesArguments filesArguments;
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final List<FuzzingData> registeredOperations = new ArrayList<>();
    private final Map<String, String> variables = new LinkedHashMap<>();
    private final Set<String> savedOutputVariableNames = new LinkedHashSet<>();
    private final Map<String, Map<String, Object>> recordedRefDataByPath = new LinkedHashMap<>();

    public FunctionalFlowFuzzer(FilesArguments filesArguments, ServiceCaller serviceCaller, TestCaseListener testCaseListener) {
        this.filesArguments = filesArguments;
        this.serviceCaller = serviceCaller;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (data != null) {
            registeredOperations.add(data);
        }
    }

    public void executeFlows() throws IOException {
        File flowFile = filesArguments.getFunctionalFlowFile();
        if (flowFile == null || !flowFile.exists()) {
            return;
        }
        variables.clear();
        savedOutputVariableNames.clear();
        recordedRefDataByPath.clear();
        FunctionalFlowDocument document = FunctionalFlowParser.parse(flowFile);
        initGlobalVariables(document.variables());
        logger.info("Executing {} functional flow step(s) from {}", document.steps().size(), flowFile.getName());

        for (FunctionalFlowStep step : document.steps()) {
            executeSingleStep(document, step);
        }
        writeRefDataOutputIfRequested();
        registeredOperations.clear();
    }

    private void initGlobalVariables(Map<String, String> rawVars) {
        if (rawVars == null) {
            return;
        }
        rawVars.forEach((key, value) -> variables.put(key, DynamicValueResolver.resolve(value, variables)));
    }

    private void executeSingleStep(FunctionalFlowDocument document, FunctionalFlowStep step) {
        ResolvedStepTarget target = resolveStepTarget(step);
        Optional<FuzzingData> matchingData = selectFuzzingData(step, target.contractPath());
        if (matchingData.isEmpty()) {
            logger.error("No OpenAPI operation found matching [{} {}] for step [{}]",
                    step.method(), step.rawTarget(), step.name());
            return;
        }
        FuzzingData data = matchingData.get();
        testCaseListener.notifySummaryObservers(data.getContractPath());
        testCaseListener.beforeFuzz(this.getClass(), data.getContractPath(), data.getMethod().name());
        try {
            testCaseListener.createAndExecuteTest(logger, this, () -> runStepExchange(document, step, target, data), data);
        } finally {
            testCaseListener.afterFuzz(data.getContractPath());
        }
    }

    private void runStepExchange(FunctionalFlowDocument document, FunctionalFlowStep step,
                                 ResolvedStepTarget target, FuzzingData data) {
        ResponseCodeFamily expectedCodeFamily = new ResponseCodeFamilyDynamic(step.expect().status());
        testCaseListener.addScenario(logger, "Scenario: {}", step.name());
        testCaseListener.addExpectedResult(logger, "Should return {}", expectedCodeFamily.allowedResponseCodes());
        if (!testCaseListener.shouldContinueExecution(logger, expectedCodeFamily)) {
            testCaseListener.skipTest(logger, "Test skipped due to response code filtering");
            return;
        }

        PreparedStepPayloads payloads = preparePayloads(document, step, target, data);
        Set<CatsHeader> headers = prepareHeaders(step, data, payloads.bodyPayload());
        List<RequestTarget> mutationTargets = computeMutationTargets(data, payloads.bodyPayload(),
                payloads.pathParamsPayload(), headers, payloads.changedFields());

        CatsResponse response = serviceCaller.call(ServiceData.from(data)
                .replaceRefData(false)
                .headers(headers)
                .payload(payloads.bodyPayload())
                .pathParamsPayload(payloads.pathParamsPayload())
                .dynamicVariables(Map.copyOf(variables))
                .mutationTargets(mutationTargets)
                .build());

        if (!response.isBodyTruncated()) {
            captureSavedVariables(step.save(), response, payloads.bodyPayload(), payloads.pathParamsPayload());
        }
        evaluateAndReportStepResult(step, data, payloads.bodyPayload(), response, expectedCodeFamily);
    }

    private record ResolvedStepTarget(String contractPath, Map<String, Object> extractedPathParams,
                                      Map<String, Object> extractedQueryParams) {
    }

    private record PreparedStepPayloads(String bodyPayload, String pathParamsPayload, Set<String> changedFields) {
    }

    private ResolvedStepTarget resolveStepTarget(FunctionalFlowStep step) {
        String raw = step.rawTarget();
        int qIdx = raw.indexOf('?');
        String rawPath = qIdx >= 0 ? raw.substring(0, qIdx) : raw;
        String rawQuery = qIdx >= 0 ? raw.substring(qIdx + 1) : "";

        Map<String, Object> queryParams = new LinkedHashMap<>(parseInlineQueryParams(rawQuery));
        queryParams.putAll(step.query());

        Map<String, Object> pathParams = new LinkedHashMap<>(step.path());
        String matchedContractPath = matchContractPathAndExtractParams(step.method(), rawPath, pathParams);
        return new ResolvedStepTarget(matchedContractPath, pathParams, queryParams);
    }

    private String matchContractPathAndExtractParams(HttpMethod method, String rawPath, Map<String, Object> pathParams) {
        List<String> candidatePaths = registeredOperations.stream()
                .filter(d -> d.getMethod() == method)
                .map(FuzzingData::getContractPath)
                .distinct()
                .toList();
        for (String candidate : candidatePaths) {
            if (candidate.equalsIgnoreCase(rawPath)) {
                return candidate;
            }
        }
        String resolvedRawPath = DynamicValueResolver.resolve(rawPath, variables);
        String[] actualSegs = stripLeadingTrailingSlashes(resolvedRawPath).split("/", -1);
        String[] rawSegs = stripLeadingTrailingSlashes(rawPath).split("/", -1);
        for (String candidate : candidatePaths) {
            String[] templateSegs = stripLeadingTrailingSlashes(candidate).split("/", -1);
            if (templateSegs.length == actualSegs.length && matchesTemplateSegments(templateSegs, actualSegs)) {
                extractTemplateParams(templateSegs, rawSegs, actualSegs, pathParams);
                return candidate;
            }
        }
        return rawPath;
    }

    private static boolean matchesTemplateSegments(String[] templateSegs, String[] actualSegs) {
        for (int i = 0; i < templateSegs.length; i++) {
            String t = templateSegs[i];
            if (!isTemplateVar(t) && !t.equalsIgnoreCase(actualSegs[i])) {
                return false;
            }
        }
        return true;
    }

    private static void extractTemplateParams(String[] templateSegs, String[] rawSegs,
                                              String[] actualSegs, Map<String, Object> pathParams) {
        for (int i = 0; i < templateSegs.length; i++) {
            String t = templateSegs[i];
            if (isTemplateVar(t)) {
                String paramName = t.substring(1, t.length() - 1);
                String rawSeg = i < rawSegs.length ? rawSegs[i] : actualSegs[i];
                if (!isTemplateVar(rawSeg)) {
                    pathParams.putIfAbsent(paramName, rawSeg);
                }
            }
        }
    }

    private static boolean isTemplateVar(String segment) {
        return segment.startsWith("{") && segment.endsWith("}");
    }

    private static String stripLeadingTrailingSlashes(String path) {
        return StringUtils.strip(path, "/");
    }

    private Map<String, Object> parseInlineQueryParams(String rawQuery) {
        if (StringUtils.isBlank(rawQuery)) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (String pair : rawQuery.split("&", -1)) {
            if (StringUtils.isNotBlank(pair)) {
                String[] kv = pair.split("=", 2);
                String key = URLDecoder.decode(kv[0].trim(), StandardCharsets.UTF_8);
                String val = kv.length > 1 ? URLDecoder.decode(kv[1].trim(), StandardCharsets.UTF_8) : "";
                map.put(key, val);
            }
        }
        return map;
    }

    private Optional<FuzzingData> selectFuzzingData(FunctionalFlowStep step, String contractPath) {
        List<FuzzingData> candidates = registeredOperations.stream()
                .filter(d -> d.getContractPath().equalsIgnoreCase(contractPath) && d.getMethod() == step.method())
                .toList();
        if (candidates.size() <= 1) {
            return candidates.stream().findFirst();
        }
        Optional<FuzzingData> byVariantName = findCandidateByVariantName(candidates, step.variant());
        if (byVariantName.isPresent()) {
            return byVariantName;
        }
        Map<String, String> matchMap = buildVariantMatchMap(step);
        for (FuzzingData candidate : candidates) {
            if (matchesDiscriminatorFields(candidate.getPayload(), matchMap)) {
                return Optional.of(candidate);
            }
        }
        return Optional.of(candidates.getFirst());
    }

    private static Optional<FuzzingData> findCandidateByVariantName(List<FuzzingData> candidates, String variant) {
        if (StringUtils.isBlank(variant)) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(c -> c.getReqSchemaName() != null && c.getReqSchemaName().endsWith(variant))
                .findFirst();
    }

    private static Map<String, String> buildVariantMatchMap(FunctionalFlowStep step) {
        Map<String, String> matchMap = new LinkedHashMap<>(step.variantMatch());
        if (step.body() instanceof Map<?, ?> bodyMap) {
            bodyMap.forEach((k, v) -> {
                if (k != null && v != null && !(v instanceof Map<?, ?>) && !(v instanceof List<?>)) {
                    matchMap.putIfAbsent(String.valueOf(k), String.valueOf(v));
                }
            });
        }
        return matchMap;
    }

    private static boolean matchesDiscriminatorFields(String candidatePayload, Map<String, String> matchMap) {
        if (matchMap.isEmpty() || !JsonUtils.isValidJson(candidatePayload)) {
            return false;
        }
        for (Map.Entry<String, String> entry : matchMap.entrySet()) {
            Object val = JsonUtils.getVariableFromJson(candidatePayload, FieldCorrelationEvaluator.normalizeFieldPath(entry.getKey()));
            if (Objects.equals(String.valueOf(val), entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private PreparedStepPayloads preparePayloads(FunctionalFlowDocument document, FunctionalFlowStep step,
                                                 ResolvedStepTarget target, FuzzingData data) {
        boolean bodyMethod = HttpMethod.requiresBody(step.method());
        String basePayload = resolveBasePayload(step.base(), data, bodyMethod);
        Set<String> changedFields = new LinkedHashSet<>();

        String bodyPayload = applyBodyOverrides(basePayload, step.body(), data.getContractPath(), changedFields);
        for (String omitField : step.omit()) {
            String normalized = FieldCorrelationEvaluator.normalizeFieldPath(omitField);
            bodyPayload = JsonUtils.deleteNode(bodyPayload, normalized);
            changedFields.add(normalized);
        }

        String pathParamsPayload = StringUtils.defaultIfBlank(data.getPathParamsPayload(), "{}");
        Map<String, Object> combinedUrlParams = new LinkedHashMap<>(target.extractedPathParams());
        combinedUrlParams.putAll(target.extractedQueryParams());
        for (Map.Entry<String, Object> paramEntry : combinedUrlParams.entrySet()) {
            Object resolved = resolveValueAndRecordRefData(data.getContractPath(), paramEntry.getKey(), paramEntry.getValue(), bodyPayload);
            changedFields.add(paramEntry.getKey());
            if (bodyMethod) {
                pathParamsPayload = FieldCorrelationEvaluator.setOrInsertField(pathParamsPayload, paramEntry.getKey(), resolved, true);
            } else {
                bodyPayload = FieldCorrelationEvaluator.setOrInsertField(bodyPayload, paramEntry.getKey(), resolved, true);
            }
        }

        List<FieldCorrelationRule> allRules = new ArrayList<>(document.correlate());
        allRules.addAll(step.correlate());
        bodyPayload = FieldCorrelationEvaluator.applyRequestCorrelations(data.getContractPath(), bodyPayload, allRules, variables);

        return new PreparedStepPayloads(bodyPayload, pathParamsPayload, changedFields);
    }

    private static String resolveBasePayload(BasePayloadMode mode, FuzzingData data, boolean bodyMethod) {
        return switch (mode) {
            case EMPTY -> "{}";
            case ONLY_REQUIRED -> RequiredOnlyPayloadGenerator.generate(
                    data.getPayload(), data.getAllRequiredFields(), data.getPath(), bodyMethod);
            case MERGE -> StringUtils.defaultIfBlank(data.getPayload(), "{}");
        };
    }

    private String applyBodyOverrides(String basePayload, Object bodySpec, String contractPath, Set<String> changedFields) {
        if (bodySpec == null) {
            return basePayload;
        }
        if (bodySpec instanceof String rawText) {
            String resolved = resolveStringExpression(rawText, basePayload);
            changedFields.add("$");
            return JsonUtils.isValidJson(resolved) ? resolved : JsonUtils.serialize(resolved);
        }
        if (bodySpec instanceof List<?> listSpec) {
            Object resolvedList = resolveValueRecursive(contractPath, "$", listSpec, basePayload);
            changedFields.add("$");
            return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(resolvedList);
        }
        if (bodySpec instanceof Map<?, ?> mapSpec) {
            String current = StringUtils.defaultIfBlank(basePayload, "{}");
            for (Map.Entry<?, ?> entry : mapSpec.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String normalizedKey = FieldCorrelationEvaluator.normalizeFieldPath(key);
                Object resolvedVal = resolveValueRecursive(contractPath, normalizedKey, entry.getValue(), current);
                changedFields.add(normalizedKey);
                current = setOrMergeField(current, normalizedKey, resolvedVal);
            }
            return current;
        }
        return basePayload;
    }

    private Object resolveValueRecursive(String contractPath, String fieldPath, Object value, String currentPayload) {
        if (value instanceof Map<?, ?> nestedMap) {
            Map<String, Object> resolvedMap = new LinkedHashMap<>();
            nestedMap.forEach((k, v) -> {
                String childKey = String.valueOf(k);
                String childPath = "$".equals(fieldPath) ? childKey : fieldPath + "#" + childKey;
                resolvedMap.put(childKey, resolveValueRecursive(contractPath, childPath, v, currentPayload));
            });
            return resolvedMap;
        }
        if (value instanceof List<?> nestedList) {
            List<Object> resolvedList = new ArrayList<>();
            for (Object item : nestedList) {
                resolvedList.add(resolveValueRecursive(contractPath, fieldPath, item, currentPayload));
            }
            return resolvedList;
        }
        return resolveValueAndRecordRefData(contractPath, fieldPath, value, currentPayload);
    }

    private Object resolveValueAndRecordRefData(String contractPath, String fieldPath, Object rawValue, String currentPayload) {
        if (!(rawValue instanceof String strVal)) {
            return rawValue;
        }
        String resolved = resolveStringExpression(strVal, currentPayload);
        recordVariableConsumptionForRefData(contractPath, fieldPath, strVal, resolved);
        return coerceScalarIfOriginalWasTyped(strVal, resolved);
    }

    private void recordVariableConsumptionForRefData(String contractPath, String fieldPath, String rawExpr, String resolved) {
        if (!rawExpr.contains("${")) {
            return;
        }
        for (String savedVar : savedOutputVariableNames) {
            if (rawExpr.contains("${" + savedVar + "}")) {
                recordedRefDataByPath.computeIfAbsent(contractPath, _ -> new LinkedHashMap<>())
                        .put(fieldPath.replace('#', '.'), resolved);
                return;
            }
        }
    }

    private String resolveStringExpression(String rawExpr, String currentPayload) {
        Map<String, String> context = new LinkedHashMap<>(variables);
        context.put(Parser.REQUEST, StringUtils.defaultIfBlank(currentPayload, "{}"));
        return DynamicValueResolver.resolve(rawExpr, context);
    }

    private static Object coerceScalarIfOriginalWasTyped(String rawExpr, String resolved) {
        if (!rawExpr.startsWith("#(") && !rawExpr.startsWith("T(")) {
            return resolved;
        }
        if ("true".equalsIgnoreCase(resolved) || "false".equalsIgnoreCase(resolved)) {
            return Boolean.valueOf(resolved);
        }
        if (resolved.matches("-?\\d+")) {
            try {
                return Long.parseLong(resolved);
            } catch (NumberFormatException _) {
                return resolved;
            }
        }
        return resolved;
    }

    private static String setOrMergeField(String jsonPayload, String fieldPath, Object value) {
        if (JsonUtils.isFieldInJson(jsonPayload, fieldPath)) {
            if (value instanceof Map<?, ?> mapVal) {
                Object existing = JsonUtils.getVariableFromJson(jsonPayload, fieldPath);
                if (existing != null && !JsonUtils.isNotSet(String.valueOf(existing)) && !fieldPath.contains("#")) {
                    return mergeNestedObject(jsonPayload, fieldPath, mapVal);
                }
            }
            return CatsUtil.justReplaceField(jsonPayload, fieldPath, value).json();
        }
        return FieldCorrelationEvaluator.setOrInsertField(jsonPayload, fieldPath, value, true);
    }

    private static String mergeNestedObject(String jsonPayload, String topKey, Map<?, ?> mapVal) {
        JsonObject root = JsonParser.parseString(StringUtils.defaultIfBlank(jsonPayload, "{}")).getAsJsonObject();
        JsonObject child = root.has(topKey) && root.get(topKey).isJsonObject()
                ? root.getAsJsonObject(topKey)
                : new JsonObject();
        JsonElement additions = JsonUtils.GSON_NO_PRETTY_PRINTING.toJsonTree(mapVal);
        if (additions.isJsonObject()) {
            additions.getAsJsonObject().entrySet().forEach(e -> child.add(e.getKey(), e.getValue()));
        }
        root.add(topKey, child);
        return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(root);
    }

    private Set<CatsHeader> prepareHeaders(FunctionalFlowStep step, FuzzingData data, String bodyPayload) {
        Map<String, CatsHeader> byName = new LinkedHashMap<>();
        for (CatsHeader header : Optional.ofNullable(data.getHeaders()).orElse(Set.of())) {
            byName.put(header.getName().toLowerCase(Locale.ROOT), header.copy());
        }
        step.headers().forEach((name, rawValue) -> {
            String resolved = resolveStringExpression(String.valueOf(rawValue), bodyPayload);
            byName.put(name.toLowerCase(Locale.ROOT), CatsHeader.builder().name(name).value(resolved).build());
        });
        return new LinkedHashSet<>(byName.values());
    }

    private List<RequestTarget> computeMutationTargets(FuzzingData data, String mutatedPayload,
                                                       String mutatedPathPayload, Set<CatsHeader> headers,
                                                       Set<String> changedFields) {
        Set<RequestTarget> targets = new LinkedHashSet<>(RequestTargetResolver.resolveCustomMutations(
                data, mutatedPayload, mutatedPathPayload, headers, changedFields));
        if (changedFields.contains("$")) {
            targets.add(RequestTarget.requestBody());
        }
        return List.copyOf(targets);
    }

    private void captureSavedVariables(Map<String, String> saveSpec, CatsResponse response,
                                       String requestPayload, String pathParamsPayload) {
        if (saveSpec == null || saveSpec.isEmpty()) {
            return;
        }
        saveSpec.forEach((varName, selector) -> {
            String extracted = extractValueBySelector(selector.trim(), response, requestPayload, pathParamsPayload);
            if (!JsonUtils.isNotSet(extracted)) {
                variables.put(varName, extracted);
                savedOutputVariableNames.add(varName);
                logger.debug("Saved variable [{}] = [{}]", varName, extracted);
            }
        });
    }

    private String extractValueBySelector(String selector, CatsResponse response,
                                          String requestPayload, String pathParamsPayload) {
        if (selector.startsWith("$request.")) {
            return CatsDSLParser.parseAndGetResult(selector, Map.of(Parser.REQUEST, requestPayload));
        }
        if (selector.startsWith("$path.")) {
            return CatsDSLParser.parseAndGetResult(selector, Map.of(Parser.PATH, StringUtils.defaultIfBlank(pathParamsPayload, requestPayload)));
        }
        return CustomFuzzerUtil.extractResponseValue(response, selector);
    }

    private void evaluateAndReportStepResult(FunctionalFlowStep step, FuzzingData data, String requestPayload,
                                             CatsResponse response, ResponseCodeFamily expectedCodeFamily) {
        FunctionalFlowExpectation expect = step.expect();
        boolean statusMatches = expectedCodeFamily.matchesAllowedResponseCodes(response.responseCodeAsString());

        List<String> failures = new ArrayList<>();
        verifyExpectedFields(expect.fields(), requestPayload, response, failures);
        verifyExpectedHeaders(expect.headers(), requestPayload, response, failures);
        verifyBodySubstrings(expect.bodyContains(), expect.bodyNotContains(), response, failures);
        FieldCorrelationEvaluator.evaluateWhereExpectations(expect.where(), requestPayload, response, variables)
                .ifPresent(failures::add);

        if (!failures.isEmpty()) {
            testCaseListener.reportResultError(logger, data, "Verify parameters not matching response",
                    String.join("; ", failures));
            return;
        }
        boolean hasCustomAssertions = !expect.fields().isEmpty() || !expect.headers().isEmpty()
                || !expect.bodyContains().isEmpty() || !expect.bodyNotContains().isEmpty() || !expect.where().isEmpty();
        if (hasCustomAssertions) {
            if (statusMatches) {
                testCaseListener.reportResultInfo(logger, data, "Response matches all 'verify' parameters");
            } else {
                testCaseListener.reportResultWarn(logger, data, "Returned response code not matching expected response code",
                        "Response matches all 'verify' parameters, but response code doesn't match expected response code: expected [{}], actual [{}]",
                        expectedCodeFamily.allowedResponseCodes(), response.responseCodeAsString());
            }
            return;
        }
        testCaseListener.reportResult(logger, data, response, expectedCodeFamily, expect.matchesSchema(), true);
    }

    private void verifyExpectedFields(Map<String, Object> expectedFields, String requestPayload,
                                      CatsResponse response, List<String> failures) {
        for (Map.Entry<String, Object> entry : expectedFields.entrySet()) {
            String actual = extractValueBySelector(entry.getKey(), response, requestPayload, requestPayload);
            if (JsonUtils.isNotSet(actual)) {
                failures.add("Field [" + entry.getKey() + "] was not present in the response");
                continue;
            }
            String expected = resolveStringExpression(String.valueOf(entry.getValue()), requestPayload);
            if (!matchesExpectedValue(actual, expected)) {
                failures.add("Parameter [%s] with value [%s] not matching [%s]".formatted(entry.getKey(), actual, expected));
            }
        }
    }

    private void verifyExpectedHeaders(Map<String, String> expectedHeaders, String requestPayload,
                                       CatsResponse response, List<String> failures) {
        for (Map.Entry<String, String> entry : expectedHeaders.entrySet()) {
            KeyValuePair<String, String> header = response.getHeader(entry.getKey());
            if (header == null || header.getValue() == null) {
                failures.add("Header [" + entry.getKey() + "] was not present in the response");
                continue;
            }
            String expected = resolveStringExpression(entry.getValue(), requestPayload);
            if (!matchesExpectedValue(header.getValue(), expected)) {
                failures.add("Header [%s] with value [%s] not matching [%s]".formatted(entry.getKey(), header.getValue(), expected));
            }
        }
    }

    private static void verifyBodySubstrings(List<String> mustContain, List<String> mustNotContain,
                                             CatsResponse response, List<String> failures) {
        String body = Optional.ofNullable(response.getBody()).orElse("");
        for (String substring : mustContain) {
            if (!body.contains(substring)) {
                failures.add("Response body does not contain expected text [" + substring + "]");
            }
        }
        for (String forbidden : mustNotContain) {
            if (body.contains(forbidden)) {
                failures.add("Response body contains forbidden text [" + forbidden + "]");
            }
        }
    }

    private static boolean matchesExpectedValue(String actual, String expected) {
        if (Objects.equals(actual, expected)) {
            return true;
        }
        try {
            return Pattern.compile(expected).matcher(actual).matches();
        } catch (RuntimeException _) {
            return false;
        }
    }

    private void writeRefDataOutputIfRequested() throws IOException {
        if (filesArguments.getRefDataFile() != null) {
            CustomFuzzerUtil.writeReplacedRefDataFile(filesArguments.getRefDataFile(), variables);
        } else if (filesArguments.isCreateRefData()) {
            CatsUtil.writeToYaml(REF_DATA_CUSTOM_FILE, recordedRefDataByPath);
        }
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "execute sequential functional test flows with cross-field correlations and response assertions";
    }
}
