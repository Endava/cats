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
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FuzzMode;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.StepFuzzConfig;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.http.ResponseCodeFamilyDynamic;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsField;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.RequestTargetResolver;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsDSLWords;
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
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standalone fuzzer that executes sequential functional and security test flows (`steps:`).
 */
@Singleton
@SpecialFuzzer
public class FunctionalFlowFuzzer implements Fuzzer {
    private static final String REF_DATA_CUSTOM_FILE = "refData_custom.yml";
    private static final Pattern STEP_TOKEN_PATTERN = Pattern.compile("\\$\\{((?:steps|prev)\\.[^}]+)}|\\$((?:steps|prev)\\.[A-Za-z0-9_.#\\[\\]]+)");
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(FunctionalFlowFuzzer.class);
    private final FilesArguments filesArguments;
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final List<FuzzingData> registeredOperations = new ArrayList<>();
    @Getter
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
        executeDocument(FunctionalFlowParser.parse(flowFile), flowFile.getName(), this);
        writeRefDataOutputIfRequested();
        registeredOperations.clear();
    }

    public void executeDocument(FunctionalFlowDocument document, String sourceName, Fuzzer activeFuzzer) {
        variables.clear();
        savedOutputVariableNames.clear();
        recordedRefDataByPath.clear();
        initGlobalVariables(document.variables());
        logger.info("Executing {} flow step(s) from {}", document.steps().size(), sourceName);
        int stepIndex = 1;
        for (FunctionalFlowStep step : document.steps()) {
            executeStepAcrossMatchingOperations(document, step, stepIndex++, activeFuzzer);
        }
    }

    private void initGlobalVariables(Map<String, String> rawVars) {
        if (serviceCaller.getEnvironmentVariables() != null) {
            variables.putAll(serviceCaller.getEnvironmentVariables());
        }
        if (rawVars == null) {
            return;
        }
        rawVars.forEach((key, value) -> {
            String resolved = DynamicValueResolver.resolve(value, variables);
            variables.put(key, resolved);
            savedOutputVariableNames.add(key);
        });
    }

    private void executeStepAcrossMatchingOperations(FunctionalFlowDocument document, FunctionalFlowStep step,
                                                     int stepIndex, Fuzzer activeFuzzer) {
        if (step.fuzz() != null && step.fuzz().isConfigured()) {
            executeFuzzedStep(document, step, stepIndex, activeFuzzer);
            return;
        }
        ResolvedStepTarget target = resolveStepTarget(step, step.rawTarget());
        Optional<FuzzingData> matchingData = selectFuzzingData(step, target.contractPath(), step.method());
        if (matchingData.isEmpty()) {
            logger.error("No OpenAPI operation found matching [{} {}] for step [{}]",
                    step.method(), step.rawTarget(), step.name());
            return;
        }
        executeSingleStepOnOperation(document, step, stepIndex, target, matchingData.get(),
                step.scenarioText(), activeFuzzer);
    }

    private void executeSingleStepOnOperation(FunctionalFlowDocument document, FunctionalFlowStep step,
                                              int stepIndex, ResolvedStepTarget target, FuzzingData data,
                                              String scenarioText, Fuzzer activeFuzzer) {
        testCaseListener.notifySummaryObservers(data.getContractPath());
        testCaseListener.beforeFuzz(activeFuzzer.getClass(), data.getContractPath(), data.getMethod().name());
        try {
            testCaseListener.createAndExecuteTest(logger, activeFuzzer,
                    () -> runStepExchange(document, step, stepIndex, target, data, scenarioText), data);
        } finally {
            testCaseListener.afterFuzz(data.getContractPath());
        }
    }

    private void executeFuzzedStep(FunctionalFlowDocument document, FunctionalFlowStep step,
                                   int stepIndex, Fuzzer activeFuzzer) {
        StepFuzzConfig fuzzConfig = step.fuzz();
        List<FuzzingData> targetOps = resolveMatchingOperationsForFuzzStep(step);
        if (targetOps.isEmpty()) {
            logger.skip("Skipping step [{}] as no matching contract path/method was registered", step.name());
            return;
        }
        if (!validateFuzzStepConfig(fuzzConfig)) {
            return;
        }
        List<String> payloads = loadPayloadsForFuzzStep(fuzzConfig);
        if (payloads.isEmpty()) {
            return;
        }
        for (FuzzingData data : targetOps) {
            List<String> targetFields = resolveTargetFieldsForOperation(fuzzConfig, data);
            runPayloadsAgainstTargetFields(document, step, stepIndex, data, targetFields, payloads, activeFuzzer);
        }
    }

    private boolean validateFuzzStepConfig(StepFuzzConfig fuzzConfig) {
        List<String> missing = new ArrayList<>();
        if (!fuzzConfig.hasPayloadSource()) {
            missing.add("payloads or payloadsFile");
        }
        if (!fuzzConfig.hasTargets()) {
            missing.add("fields or fieldTypes");
        }
        if (!missing.isEmpty()) {
            String message = "is missing the following mandatory entries: %s".formatted(missing);
            logger.error(message);
            testCaseListener.recordError(message);
            return false;
        }
        return true;
    }

    static List<String> loadPayloadsForFuzzStep(StepFuzzConfig fuzzConfig, PrettyLogger log) {
        List<String> result = new ArrayList<>(fuzzConfig.payloads());
        for (String filePath : fuzzConfig.payloadsFiles()) {
            try {
                List<String> lines = Files.readAllLines(Paths.get(filePath)).stream()
                        .filter(Predicate.not(String::isBlank))
                        .filter(Predicate.not(line -> line.startsWith("# ")))
                        .toList();
                result.addAll(lines);
            } catch (IOException e) {
                log.error("There was a problem reading the payloadsFile: {}. Error: {}", filePath, e.getMessage());
                return List.of();
            }
        }
        return result;
    }

    private List<String> loadPayloadsForFuzzStep(StepFuzzConfig fuzzConfig) {
        return loadPayloadsForFuzzStep(fuzzConfig, logger);
    }

    static List<String> resolveTargetFieldsForOperation(StepFuzzConfig fuzzConfig, FuzzingData data) {
        Set<String> resolved = new LinkedHashSet<>();
        List<String> lowerTypes = fuzzConfig.fieldTypes().stream()
                .map(StringUtils::toRootLowerCase)
                .toList();
        if (!lowerTypes.isEmpty() && data.getAllFieldsAsCatsFields() != null) {
            for (CatsField field : data.getAllFieldsAsCatsFields()) {
                if (matchesFieldTypeOrFormat(field, lowerTypes)
                        && !JsonUtils.isNotSet(String.valueOf(JsonUtils.getVariableFromJson(data.getPayload(), field.getName())))) {
                    resolved.add(field.getName());
                }
            }
        }
        for (String type : lowerTypes) {
            if ("headers".equalsIgnoreCase(type) || CatsDSLWords.CATS_HEADERS.equalsIgnoreCase(type)) {
                resolved.add(CatsDSLWords.CATS_HEADERS);
            } else if ("body".equalsIgnoreCase(type) || CatsDSLWords.CATS_BODY_FUZZ.equalsIgnoreCase(type)) {
                resolved.add(CatsDSLWords.CATS_BODY_FUZZ);
            }
        }
        resolved.addAll(fuzzConfig.fields());
        resolved.removeIf(StringUtils::isBlank);
        return List.copyOf(resolved);
    }

    private static boolean matchesFieldTypeOrFormat(CatsField field, List<String> lowerTypes) {
        if (field.getSchema() == null) {
            return false;
        }
        String type = StringUtils.toRootLowerCase(field.getSchema().getType());
        String format = StringUtils.toRootLowerCase(field.getSchema().getFormat());
        return lowerTypes.contains(type) || lowerTypes.contains(format);
    }

    private void runPayloadsAgainstTargetFields(FunctionalFlowDocument document, FunctionalFlowStep step,
                                                int stepIndex, FuzzingData data, List<String> targetFields,
                                                List<String> payloads, Fuzzer activeFuzzer) {
        if (targetFields.isEmpty()) {
            return;
        }
        StepFuzzConfig fuzzConfig = step.fuzz();
        if (fuzzConfig.mode() == FuzzMode.CROSS && targetFields.size() > 1) {
            runCrossFieldPayloads(document, step, stepIndex, data, targetFields, payloads, activeFuzzer);
            return;
        }
        for (String targetField : targetFields) {
            runSingleFieldPayloads(document, step, stepIndex, data, targetField, payloads, activeFuzzer);
        }
    }

    private void runSingleFieldPayloads(FunctionalFlowDocument document, FunctionalFlowStep step,
                                        int stepIndex, FuzzingData data, String targetField,
                                        List<String> payloads, Fuzzer activeFuzzer) {
        if (CatsDSLWords.CATS_HEADERS.equalsIgnoreCase(targetField)) {
            List<CatsHeader> existingHeaders = new ArrayList<>(Optional.ofNullable(data.getHeaders()).orElse(Set.of()));
            for (String payloadVal : payloads) {
                for (CatsHeader header : existingHeaders) {
                    Map<String, Object> headerOverride = new LinkedHashMap<>(step.headers());
                    headerOverride.put(header.getName(), payloadVal);
                    FunctionalFlowStep mutatedStep = withOverrides(step, step.body(), headerOverride);
                    String scenario = step.scenarioText() + ", header [" + header.getName() + "]";
                    executeMutatedAndFollowUp(document, mutatedStep, stepIndex, data, scenario, activeFuzzer);
                }
            }
            return;
        }
        for (String payloadVal : payloads) {
            Object mutatedBody = buildMutatedBodyForField(step.body(), targetField, payloadVal);
            FunctionalFlowStep mutatedStep = withOverrides(step, mutatedBody, step.headers());
            String scenario = step.scenarioText() + ", field [" + targetField + "]";
            executeMutatedAndFollowUp(document, mutatedStep, stepIndex, data, scenario, activeFuzzer);
        }
    }

    private void runCrossFieldPayloads(FunctionalFlowDocument document, FunctionalFlowStep step,
                                       int stepIndex, FuzzingData data, List<String> targetFields,
                                       List<String> payloads, Fuzzer activeFuzzer) {
        String joinedFields = String.join(", ", targetFields);
        for (String payloadVal : payloads) {
            Object mutatedBody = step.body();
            for (String field : targetFields) {
                if (!CatsDSLWords.CATS_HEADERS.equalsIgnoreCase(field)) {
                    mutatedBody = buildMutatedBodyForField(mutatedBody, field, payloadVal);
                }
            }
            FunctionalFlowStep mutatedStep = withOverrides(step, mutatedBody, step.headers());
            String scenario = step.scenarioText() + ", cross-fields [" + joinedFields + "]";
            executeMutatedAndFollowUp(document, mutatedStep, stepIndex, data, scenario, activeFuzzer);
        }
    }

    private void executeMutatedAndFollowUp(FunctionalFlowDocument document, FunctionalFlowStep mutatedStep,
                                           int stepIndex, FuzzingData data, String scenario, Fuzzer activeFuzzer) {
        ResolvedStepTarget target = new ResolvedStepTarget(data.getContractPath(), new LinkedHashMap<>(mutatedStep.path()), mutatedStep.query());
        executeSingleStepOnOperation(document, mutatedStep, stepIndex, target, data, scenario, activeFuzzer);
    }

    private static Object buildMutatedBodyForField(Object baseBody, String targetField, String payloadVal) {
        if (CatsDSLWords.CATS_BODY_FUZZ.equalsIgnoreCase(targetField)) {
            return payloadVal;
        }
        Map<String, Object> bodyMap = new LinkedHashMap<>();
        if (baseBody instanceof Map<?, ?> existing) {
            existing.forEach((k, v) -> bodyMap.put(String.valueOf(k), v));
        }
        bodyMap.put(targetField, payloadVal);
        return bodyMap;
    }

    private static FunctionalFlowStep withOverrides(FunctionalFlowStep step, Object newBody, Map<String, Object> newHeaders) {
        return new FunctionalFlowStep(
                step.name(), step.description(), step.method(), step.allMethods(),
                step.rawTarget(), step.targetPaths(), step.path(), step.query(),
                newHeaders, newBody, step.base(), step.omit(), step.variant(),
                step.variantMatch(), step.correlate(), step.save(), step.expect(), step.fuzz());
    }

    private List<FuzzingData> resolveMatchingOperationsForFuzzStep(FunctionalFlowStep step) {
        List<String> targetPaths = step.targetPaths().isEmpty() ? List.of(step.rawTarget()) : step.targetPaths();
        boolean matchAllPaths = targetPaths.stream().anyMatch(CatsDSLWords.ALL::equalsIgnoreCase);
        return registeredOperations.stream()
                .filter(d -> matchAllPaths || targetPaths.stream().anyMatch(p -> p.equalsIgnoreCase(d.getContractPath())))
                .filter(d -> step.allMethods() || step.method() == d.getMethod())
                .toList();
    }

    private void runStepExchange(FunctionalFlowDocument document, FunctionalFlowStep step, int stepIndex,
                                 ResolvedStepTarget target, FuzzingData data, String scenarioText) {
        ResponseCodeFamily expectedCodeFamily = new ResponseCodeFamilyDynamic(step.expect().status());
        testCaseListener.addScenario(logger, "Scenario: {}", scenarioText);
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

        recordStepHistory(step, stepIndex, payloads.bodyPayload(), response);
        if (!response.isBodyTruncated()) {
            captureSavedVariables(step.save(), response, payloads.bodyPayload(), payloads.pathParamsPayload());
        }
        evaluateAndReportStepResult(step, data, payloads.bodyPayload(), response, expectedCodeFamily);
    }

    private void recordStepHistory(FunctionalFlowStep step, int stepIndex, String requestPayload, CatsResponse response) {
        String respBody = Optional.ofNullable(response.getBody()).orElse("");
        String statusStr = String.valueOf(response.getResponseCode());
        variables.put("prev.request", requestPayload);
        variables.put("prev.response", respBody);
        variables.put("prev.status", statusStr);

        for (String key : stepKeys(step.name(), stepIndex)) {
            variables.put("steps." + key + ".request", requestPayload);
            variables.put("steps." + key + ".response", respBody);
            variables.put("steps." + key + ".status", statusStr);
        }
    }

    private static Set<String> stepKeys(String stepName, int stepIndex) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("step" + stepIndex);
        if (StringUtils.isNotBlank(stepName)) {
            keys.add(stepName.trim());
            keys.add(stepName.trim().replaceAll("\\s+", "_"));
        }
        return keys;
    }

    private record ResolvedStepTarget(String contractPath, Map<String, Object> extractedPathParams,
                                      Map<String, Object> extractedQueryParams) {
    }

    private record PreparedStepPayloads(String bodyPayload, String pathParamsPayload, Set<String> changedFields) {
    }

    private ResolvedStepTarget resolveStepTarget(FunctionalFlowStep step, String rawTarget) {
        String raw = StringUtils.defaultString(rawTarget);
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
                .filter(d -> method == null || d.getMethod() == method)
                .map(FuzzingData::getContractPath)
                .distinct()
                .toList();
        for (String candidate : candidatePaths) {
            if (candidate.equalsIgnoreCase(rawPath)) {
                return candidate;
            }
        }
        String resolvedRawPath = resolveStringExpression(rawPath, "{}");
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

    private Optional<FuzzingData> selectFuzzingData(FunctionalFlowStep step, String contractPath, HttpMethod method) {
        List<FuzzingData> candidates = registeredOperations.stream()
                .filter(d -> d.getContractPath().equalsIgnoreCase(contractPath)
                        && (step.allMethods() || method == null || d.getMethod() == method))
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
        boolean bodyMethod = HttpMethod.requiresBody(data.getMethod());
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
        bodyPayload = FieldCorrelationEvaluator.applyRequestCorrelations(
                data.getContractPath(), bodyPayload, allRules, variables, changedFields);

        return new PreparedStepPayloads(bodyPayload, pathParamsPayload, changedFields);
    }

    private static String resolveBasePayload(BasePayloadMode mode, FuzzingData data, boolean bodyMethod) {
        return switch (mode) {
            case EMPTY -> "{}";
            case ONLY_REQUIRED -> RequiredOnlyPayloadGenerator.generate(
                    data.getPayload(), data.getAllRequiredFields(), data.getPath(), bodyMethod);
            case MERGE -> bodyMethod
                    ? StringUtils.defaultIfBlank(data.getPayload(), "{}")
                    : RequiredOnlyPayloadGenerator.generate(
                    data.getPayload(), data.getAllRequiredFields(), data.getPath(), false);
        };
    }

    private String applyBodyOverrides(String basePayload, Object bodySpec, String contractPath, Set<String> changedFields) {
        switch (bodySpec) {
            case null -> {
                return basePayload;
            }
            case String rawText -> {
                String resolved = resolveStringExpression(rawText, basePayload);
                changedFields.add("$");
                return JsonUtils.isValidJson(resolved) ? resolved : JsonUtils.serialize(resolved);
            }
            case List<?> listSpec -> {
                Object resolvedList = resolveValueRecursive(contractPath, "$", listSpec, basePayload, changedFields);
                changedFields.add("$");
                return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(resolvedList);
            }
            case Map<?, ?> mapSpec -> {
                return applyMapBodyOverrides(basePayload, mapSpec, contractPath, changedFields);
            }
            default -> {
            }
        }
        return basePayload;
    }

    private String applyMapBodyOverrides(String basePayload, Map<?, ?> mapSpec, String contractPath, Set<String> changedFields) {
        String current = StringUtils.defaultIfBlank(basePayload, "{}");
        for (Map.Entry<?, ?> entry : mapSpec.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String normalizedKey = FieldCorrelationEvaluator.normalizeFieldPath(key);
            if (!JsonUtils.isFieldInJson(current, normalizedKey)) {
                changedFields.add("$");
            }
            if (!(entry.getValue() instanceof Map<?, ?>)) {
                changedFields.add(normalizedKey);
            }
            Object resolvedVal = resolveValueRecursive(contractPath, normalizedKey, entry.getValue(), current, changedFields);
            current = setOrMergeField(current, normalizedKey, resolvedVal);
        }
        return current;
    }

    private Object resolveValueRecursive(String contractPath, String fieldPath, Object value,
                                         String currentPayload, Set<String> changedFields) {
        if (value instanceof Map<?, ?> nestedMap) {
            Map<String, Object> resolvedMap = new LinkedHashMap<>();
            nestedMap.forEach((k, v) -> {
                String childKey = String.valueOf(k);
                String childPath = "$".equals(fieldPath) ? childKey : fieldPath + "#" + childKey;
                resolvedMap.put(childKey, resolveValueRecursive(contractPath, childPath, v, currentPayload, changedFields));
            });
            return resolvedMap;
        }
        if (!"$".equals(fieldPath)) {
            changedFields.add(fieldPath);
        }
        if (value instanceof List<?> nestedList) {
            List<Object> resolvedList = new ArrayList<>();
            for (Object item : nestedList) {
                resolvedList.add(resolveValueRecursive(contractPath, fieldPath, item, currentPayload, changedFields));
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
        return resolveStringExpression(rawExpr, currentPayload, null);
    }

    private String resolveStringExpression(String rawExpr, String currentPayload, String responseBody) {
        if (rawExpr == null) {
            return null;
        }
        String withStepRefs = replaceStepTokens(rawExpr);
        Map<String, String> context = new LinkedHashMap<>(variables);
        context.put(Parser.REQUEST, StringUtils.defaultIfBlank(currentPayload, "{}"));
        if (responseBody != null) {
            context.put(Parser.RESPONSE, responseBody);
        }
        return DynamicValueResolver.resolve(withStepRefs, context);
    }

    private String replaceStepTokens(String expr) {
        if (expr == null || (!expr.contains("steps.") && !expr.contains("prev."))) {
            return expr;
        }
        Matcher matcher = STEP_TOKEN_PATTERN.matcher(expr);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            String replacement = FieldCorrelationEvaluator.resolveStepReference(token, variables)
                    .map(String::valueOf)
                    .orElse(matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
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
        Optional<Object> stepRef = FieldCorrelationEvaluator.resolveStepReference(selector, variables);
        if (stepRef.isPresent()) {
            return String.valueOf(stepRef.get());
        }
        if (selector.startsWith("$request.") || selector.startsWith("request.")) {
            String dslExpr = selector.startsWith("$") ? selector : "$" + selector;
            return CatsDSLParser.parseAndGetResult(dslExpr, Map.of(Parser.REQUEST, requestPayload));
        }
        if (selector.startsWith("$path.") || selector.startsWith("path.")) {
            String dslExpr = selector.startsWith("$") ? selector : "$" + selector;
            return CatsDSLParser.parseAndGetResult(dslExpr, Map.of(Parser.PATH, StringUtils.defaultIfBlank(pathParamsPayload, requestPayload)));
        }
        if (selector.startsWith("headers.") || selector.startsWith("$headers.")) {
            String headerName = selector.substring(selector.indexOf('.') + 1);
            KeyValuePair<String, String> header = response.getHeader(headerName);
            return header == null ? JsonUtils.NOT_SET : header.getValue();
        }
        return CustomFuzzerUtil.extractResponseValue(response, selector);
    }

    private void evaluateAndReportStepResult(FunctionalFlowStep step, FuzzingData data, String requestPayload,
                                             CatsResponse response, ResponseCodeFamily expectedCodeFamily) {
        FunctionalFlowExpectation expect = step.expect();
        if (response.isBodyTruncated()) {
            testCaseListener.reportResult(logger, data, response, expectedCodeFamily, expect.matchesSchema(), true);
            return;
        }
        List<String> missingFields = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        verifyExpectedFields(expect.fields(), requestPayload, response, missingFields, failures);
        if (!missingFields.isEmpty()) {
            testCaseListener.reportResultError(logger, data, "Verify parameters not present in response",
                    "The following Verify parameters were not present in the response: {}", missingFields);
            return;
        }
        verifyExpectedHeaders(expect.headers(), requestPayload, response, failures);
        verifyBodySubstrings(expect.bodyContains(), expect.bodyNotContains(), response, failures);
        verifyMaxResponseTime(expect.maxResponseTimeMs(), response, failures);
        FieldCorrelationEvaluator.evaluateThatExpectations(expect.that(), requestPayload, response, variables)
                .ifPresent(failures::add);

        if (!failures.isEmpty()) {
            testCaseListener.reportResultError(logger, data, "Verify parameters not matching response",
                    String.join("; ", failures));
            return;
        }
        if (hasCustomAssertions(expect)) {
            reportCustomAssertionsOutcome(data, response, expectedCodeFamily);
            return;
        }
        testCaseListener.reportResult(logger, data, response, expectedCodeFamily, expect.matchesSchema(), true);
    }

    private static boolean hasCustomAssertions(FunctionalFlowExpectation expect) {
        return !expect.fields().isEmpty() || !expect.headers().isEmpty()
                || !expect.bodyContains().isEmpty() || !expect.bodyNotContains().isEmpty()
                || !expect.that().isEmpty() || expect.maxResponseTimeMs() != null;
    }

    private void reportCustomAssertionsOutcome(FuzzingData data, CatsResponse response,
                                               ResponseCodeFamily expectedCodeFamily) {
        if (expectedCodeFamily.matchesAllowedResponseCodes(response.responseCodeAsString())) {
            testCaseListener.reportResultInfo(logger, data, "Response matches all 'verify' parameters");
        } else {
            testCaseListener.reportResultWarn(logger, data, "Returned response code not matching expected response code",
                    "Response matches all 'verify' parameters, but response code doesn't match expected response code: expected [{}], actual [{}]",
                    expectedCodeFamily, response.responseCodeAsString());
        }
    }

    private void verifyExpectedFields(Map<String, Object> expectedFields, String requestPayload,
                                      CatsResponse response, List<String> missingFields, List<String> failures) {
        for (Map.Entry<String, Object> entry : expectedFields.entrySet()) {
            String actual = extractValueBySelector(entry.getKey(), response, requestPayload, requestPayload);
            if (JsonUtils.isNotSet(actual)) {
                missingFields.add(entry.getKey());
                continue;
            }
            String expected = resolveStringExpression(String.valueOf(entry.getValue()), requestPayload, response.getBody());
            if (!matchesExpectedValue(actual, expected)) {
                failures.add("Parameter [%s] with value [%s] not matching [%s]. ".formatted(entry.getKey(), actual, expected));
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
            String expected = resolveStringExpression(entry.getValue(), requestPayload, response.getBody());
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

    private static void verifyMaxResponseTime(Long maxResponseTimeMs, CatsResponse response, List<String> failures) {
        if (maxResponseTimeMs != null && response.getResponseTimeInMs() > maxResponseTimeMs) {
            failures.add("Response time [%d ms] exceeded maxResponseTimeMs [%d ms]"
                    .formatted(response.getResponseTimeInMs(), maxResponseTimeMs));
        }
    }

    private static boolean matchesExpectedValue(String actual, String expected) {
        if (Objects.equals(actual, expected)) {
            return true;
        }
        if (actual != null && actual.equalsIgnoreCase(expected)
                && ("true".equalsIgnoreCase(expected) || "false".equalsIgnoreCase(expected))) {
            return true;
        }
        try {
            return expected != null && Pattern.compile(expected).matcher(Objects.requireNonNull(actual)).matches();
        } catch (RuntimeException _) {
            return false;
        }
    }

    public void writeRefDataOutputIfRequested() throws IOException {
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
