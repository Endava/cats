package com.endava.cats.fuzzer.special;

import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.dsl.DynamicValueResolver;
import com.endava.cats.dsl.api.Parser;
import com.endava.cats.exception.CatsException;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.fields.RequiredOnlyPayloadGenerator;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.BasePayloadMode;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FieldCorrelationRule;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowExpectation;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowStep;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FuzzMode;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.PollConfig;
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
import com.google.gson.JsonNull;
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
    private static final String RESPONSE_HEADER_PREFIX = "header#";
    private static final Object OMIT_OPTIONAL_FIELD = new Object();
    private static final Pattern STEP_TOKEN_PATTERN = Pattern.compile("\\$\\{((?:steps|prev)\\.[^}]+)}|\\$((?:steps|prev)\\.[A-Za-z0-9_.#\\[\\]-]+)");
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(FunctionalFlowFuzzer.class);
    private final FilesArguments filesArguments;
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final List<FuzzingData> registeredOperations = new ArrayList<>();
    @Getter
    private final Map<String, String> variables = new LinkedHashMap<>();
    private final Map<String, Object> typedVariables = new LinkedHashMap<>();
    private final Set<String> savedOutputVariableNames = new LinkedHashSet<>();
    private final Map<String, Map<String, Object>> recordedRefDataByPath = new LinkedHashMap<>();
    private FunctionalFlowDocument initializedExternalDocument;

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

    public void validateFlowFile(File flowFile) throws IOException {
        if (flowFile == null || !flowFile.exists()) {
            return;
        }
        FunctionalFlowDocument document = FunctionalFlowParser.parse(flowFile);
        List<FunctionalFlowStep> selectedSteps = filterStepsByTags(document);
        if (!selectedSteps.isEmpty() || document.steps().isEmpty()) {
            Map<String, String> envVars = serviceCaller == null ? Map.of() : serviceCaller.getEnvironmentVariables();
            FunctionalFlowValidator.validate(document, selectedSteps, envVars);
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
        initializedExternalDocument = null;
        variables.clear();
        typedVariables.clear();
        savedOutputVariableNames.clear();
        recordedRefDataByPath.clear();
        List<String> includeTags = resolveEffectiveStepTags(document);
        List<String> skipTags = resolveEffectiveSkipStepTags(document);
        List<FunctionalFlowStep> selectedSteps = filterStepsByTags(document.steps(), includeTags, skipTags);
        warnExcludedUntaggedSteps(document.steps(), includeTags);
        if (selectedSteps.isEmpty() && !document.steps().isEmpty()) {
            logger.warning("No flow steps matched step tag filters (stepTags={}, skipStepTags={}) in {}",
                    includeTags, skipTags, sourceName);
            return;
        }
        FunctionalFlowValidator.validate(document, selectedSteps, serviceCaller.getEnvironmentVariables());
        initGlobalVariables(document.variables());
        logger.info("Executing {} flow step(s) from {}", selectedSteps.size(), sourceName);
        int stepIndex = 1;
        for (FunctionalFlowStep step : selectedSteps) {
            executeStepAcrossMatchingOperations(document, step, stepIndex++, activeFuzzer);
        }
    }

    List<String> resolveEffectiveStepTags(FunctionalFlowDocument document) {
        List<String> cliTags = filesArguments == null ? List.of() : filesArguments.getStepTags();
        if (!cliTags.isEmpty()) {
            return cliTags;
        }
        return document == null ? List.of() : document.stepTags();
    }

    List<String> resolveEffectiveSkipStepTags(FunctionalFlowDocument document) {
        List<String> cliSkipTags = filesArguments == null ? List.of() : filesArguments.getSkipStepTags();
        if (!cliSkipTags.isEmpty()) {
            return cliSkipTags;
        }
        return document == null ? List.of() : document.skipStepTags();
    }

    private List<FunctionalFlowStep> filterStepsByTags(FunctionalFlowDocument document) {
        return filterStepsByTags(document.steps(), resolveEffectiveStepTags(document), resolveEffectiveSkipStepTags(document));
    }

    private static List<FunctionalFlowStep> filterStepsByTags(List<FunctionalFlowStep> steps,
                                                              List<String> includeTags, List<String> skipTags) {
        if (includeTags.isEmpty() && skipTags.isEmpty()) {
            return steps;
        }
        return steps.stream()
                .filter(step -> step.matchesTags(includeTags, skipTags))
                .toList();
    }

    private void warnExcludedUntaggedSteps(List<FunctionalFlowStep> allSteps, List<String> includeTags) {
        if (includeTags.isEmpty()) {
            return;
        }
        List<String> excludedUntagged = allSteps.stream()
                .filter(step -> step.tags().isEmpty())
                .map(FunctionalFlowStep::name)
                .toList();
        if (!excludedUntagged.isEmpty()) {
            logger.warning("Excluded {} untagged step(s) because stepTags={} is active: {}",
                    excludedUntagged.size(), includeTags, excludedUntagged);
        }
    }

    void initVariablesForExternalExecution(FunctionalFlowDocument document) {
        if (document == null || document.equals(initializedExternalDocument)) {
            return;
        }
        variables.clear();
        typedVariables.clear();
        savedOutputVariableNames.clear();
        initGlobalVariables(document.variables());
        initializedExternalDocument = document;
    }

    private void initGlobalVariables(Map<String, Object> rawVars) {
        if (serviceCaller.getEnvironmentVariables() != null) {
            variables.putAll(serviceCaller.getEnvironmentVariables());
        }
        if (rawVars == null) {
            return;
        }
        for (String key : FunctionalFlowValidator.orderGlobalVariableKeys(rawVars)) {
            storeVariableEntry(key, rawVars.get(key), true);
        }
    }

    private void storeVariableEntry(String key, Object rawValue, boolean markSavedOutput) {
        Object resolved = resolveVariableValue(key, rawValue);
        if (resolved == null || resolved == OMIT_OPTIONAL_FIELD) {
            variables.put(key, "");
            typedVariables.put(key, null);
        } else if (resolved instanceof String strResolved) {
            variables.put(key, strResolved);
            typedVariables.remove(key);
        } else {
            variables.put(key, String.valueOf(resolved));
            typedVariables.put(key, resolved);
        }
        if (markSavedOutput) {
            savedOutputVariableNames.add(key);
        }
    }

    private Object resolveVariableValue(String key, Object rawValue) {
        return switch (rawValue) {
            case null -> null;
            case String strVal -> resolveStringVariableValue(key, strVal);
            case Map<?, ?> mapVal -> resolveMapVariableValue(key, mapVal);
            case List<?> listVal -> listVal.stream()
                    .map(item -> resolveVariableValue(key, item))
                    .filter(item -> item != OMIT_OPTIONAL_FIELD)
                    .toList();
            default -> rawValue;
        };
    }

    private Object resolveStringVariableValue(String key, String strVal) {
        Optional<String> optionalVar = extractStandaloneOptionalVariableName(strVal);
        if (optionalVar.isPresent()) {
            return resolveOptionalVariableValue(optionalVar.get());
        }
        Optional<String> standaloneVar = extractStandaloneVariableName(strVal);
        if (standaloneVar.isPresent() && typedVariables.containsKey(standaloneVar.get())) {
            return typedVariables.get(standaloneVar.get());
        }
        String expanded = replaceEmbeddedOptionalTokens(strVal);
        Object resolved = DynamicValueResolver.resolveAsTypedValue(expanded, variables);
        String resolvedStr = resolved == null ? "" : String.valueOf(resolved);
        serviceCaller.registerSensitiveFieldIfPresent(key, strVal, resolvedStr);
        return resolved;
    }

    private Object resolveOptionalVariableValue(String varName) {
        if (typedVariables.containsKey(varName)) {
            Object val = typedVariables.get(varName);
            return val == null ? OMIT_OPTIONAL_FIELD : val;
        }
        if (variables.containsKey(varName)) {
            String val = variables.get(varName);
            return val == null ? OMIT_OPTIONAL_FIELD : val;
        }
        return OMIT_OPTIONAL_FIELD;
    }

    private Map<String, Object> resolveMapVariableValue(String key, Map<?, ?> mapVal) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        mapVal.forEach((k, v) -> {
            Object val = resolveVariableValue(key + "." + k, v);
            if (val != OMIT_OPTIONAL_FIELD) {
                resolved.put(String.valueOf(k), val);
            }
        });
        return resolved;
    }

    private static Optional<String> extractStandaloneVariableName(String rawExpr) {
        return FunctionalFlowValidator.extractStandaloneIdentifier(rawExpr);
    }

    private static Optional<String> extractStandaloneOptionalVariableName(String rawExpr) {
        return FunctionalFlowValidator.extractStandaloneOptionalIdentifier(rawExpr);
    }

    private String replaceEmbeddedOptionalTokens(String expr) {
        if (expr == null || !expr.contains("${") || !expr.contains("?}")) {
            return expr;
        }
        Matcher matcher = FunctionalFlowValidator.OPTIONAL_VAR_IDENTIFIER_PATTERN.matcher(expr);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            Object val = resolveOptionalVariableValue(matcher.group(1));
            String replacement = val == OMIT_OPTIONAL_FIELD ? "" : String.valueOf(val);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private void executeStepAcrossMatchingOperations(FunctionalFlowDocument document, FunctionalFlowStep step,
                                                     int stepIndex, Fuzzer activeFuzzer) {
        if (step.forEach() != null && !step.forEach().isEmpty()) {
            executeForEachRows(document, step, stepIndex, activeFuzzer);
            return;
        }
        executeSingleStepConfiguration(document, step, stepIndex, step.scenarioText(), activeFuzzer);
    }

    private void executeForEachRows(FunctionalFlowDocument document, FunctionalFlowStep step,
                                    int stepIndex, Fuzzer activeFuzzer) {
        int rowIndex = 1;
        for (Map<String, Object> row : step.forEach()) {
            Map<String, String> previousVars = new LinkedHashMap<>(variables);
            Map<String, Object> previousTyped = new LinkedHashMap<>(typedVariables);
            applyForEachRowVariables(row);
            try {
                String rowScenario = step.scenarioText() + " [row " + rowIndex++ + ": " + row + "]";
                executeSingleStepConfiguration(document, step, stepIndex, rowScenario, activeFuzzer);
            } finally {
                restoreRowVariables(previousVars, previousTyped, row.keySet());
            }
        }
    }

    private void applyForEachRowVariables(Map<String, Object> row) {
        row.forEach((k, v) -> storeVariableEntry(k, v, false));
    }

    private void restoreRowVariables(Map<String, String> previousVars, Map<String, Object> previousTyped, Set<String> rowKeys) {
        for (String key : rowKeys) {
            if (previousVars.containsKey(key)) {
                variables.put(key, previousVars.get(key));
            } else {
                variables.remove(key);
            }
            if (previousTyped.containsKey(key)) {
                typedVariables.put(key, previousTyped.get(key));
            } else {
                typedVariables.remove(key);
            }
        }
    }

    private void executeSingleStepConfiguration(FunctionalFlowDocument document, FunctionalFlowStep step,
                                                int stepIndex, String scenarioText, Fuzzer activeFuzzer) {
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
                scenarioText, activeFuzzer);
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

    void runPayloadsAgainstTargetFields(FunctionalFlowDocument document, FunctionalFlowStep step,
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
                step.variantMatch(), step.correlate(), step.save(), step.expect(), step.fuzz(),
                step.delayMs(), step.repeat(), step.poll(), step.forEach(), step.tags(), step.headersRef());
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

        sleepQuietly(step.delayMs());
        ExchangeExecutionResult execution = executeServiceCallWithRepeatAndPolling(document, step, target, data, expectedCodeFamily);

        recordStepHistory(step, stepIndex, execution.bodyPayload(), execution.requestHeaders(), execution.response());
        if (!execution.response().isBodyTruncated()) {
            captureSavedVariables(step.save(), execution.response(), execution.bodyPayload(), execution.pathParamsPayload());
        }
        evaluateAndReportStepResult(step, data, execution.bodyPayload(), execution.response(), expectedCodeFamily);
    }

    private record ExchangeExecutionResult(String bodyPayload, String pathParamsPayload,
                                           Set<CatsHeader> requestHeaders, CatsResponse response) {
    }

    private ExchangeExecutionResult executeServiceCallWithRepeatAndPolling(FunctionalFlowDocument document,
                                                                           FunctionalFlowStep step,
                                                                           ResolvedStepTarget target,
                                                                           FuzzingData data,
                                                                           ResponseCodeFamily expectedCodeFamily) {
        int repeats = Math.max(1, step.repeat());
        for (int i = 1; i < repeats; i++) {
            invokeStepServiceCall(document, step, target, data);
        }
        PollConfig poll = step.poll();
        if (poll == null || poll.maxAttempts() <= 1) {
            return invokeStepServiceCall(document, step, target, data);
        }
        ExchangeExecutionResult lastResult = invokeStepServiceCall(document, step, target, data);
        for (int attempt = 2; attempt <= poll.maxAttempts(); attempt++) {
            if (isPollingExpectationSatisfied(step, lastResult.bodyPayload(), lastResult.response(), expectedCodeFamily)) {
                return lastResult;
            }
            sleepQuietly(poll.intervalMs());
            lastResult = invokeStepServiceCall(document, step, target, data);
        }
        return lastResult;
    }

    private ExchangeExecutionResult invokeStepServiceCall(FunctionalFlowDocument document,
                                                          FunctionalFlowStep step,
                                                          ResolvedStepTarget target,
                                                          FuzzingData data) {
        PreparedStepPayloads payloads = preparePayloads(document, step, target, data);
        Set<String> protectedHeaders = new LinkedHashSet<>();
        Set<CatsHeader> headers = prepareHeaders(document, step, data, payloads.bodyPayload(), protectedHeaders);
        List<RequestTarget> mutationTargets = computeMutationTargets(data, payloads.bodyPayload(),
                payloads.pathParamsPayload(), headers, payloads.changedFields());
        boolean expectsRateLimit = step.expect() != null
                && step.expect().status() != null
                && step.expect().status().contains(String.valueOf(ResponseCodeFamily.STATUS_TOO_MANY_REQUESTS));
        CatsResponse response = serviceCaller.call(ServiceData.from(data)
                .replaceRefData(false)
                .protectedHeaders(Set.copyOf(protectedHeaders))
                .headers(headers)
                .payload(payloads.bodyPayload())
                .pathParamsPayload(payloads.pathParamsPayload())
                .dynamicVariables(Map.copyOf(variables))
                .mutationTargets(mutationTargets)
                .expectsRateLimit(expectsRateLimit)
                .build());
        return new ExchangeExecutionResult(payloads.bodyPayload(), payloads.pathParamsPayload(), headers, response);
    }

    private boolean isPollingExpectationSatisfied(FunctionalFlowStep step, String requestPayload,
                                                  CatsResponse response, ResponseCodeFamily expectedCodeFamily) {
        if (!expectedCodeFamily.matchesAllowedResponseCodes(response.responseCodeAsString())) {
            return false;
        }
        FunctionalFlowExpectation expect = step.expect();
        List<String> missingFields = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        verifyExpectedFields(expect.fields(), requestPayload, response, missingFields, failures);
        verifyExpectedHeaders(expect.headers(), requestPayload, response, failures);
        verifyBodySubstrings(expect.bodyContains(), expect.bodyNotContains(), response, failures);
        verifyMaxResponseTime(expect.maxResponseTimeMs(), response, failures);
        FieldCorrelationEvaluator.evaluateThatExpectations(expect.that(), requestPayload, response, variables)
                .ifPresent(failures::add);
        return missingFields.isEmpty() && failures.isEmpty();
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private void recordStepHistory(FunctionalFlowStep step, int stepIndex, String requestPayload,
                                   Set<CatsHeader> requestHeaders, CatsResponse response) {
        String respBody = Optional.ofNullable(response.getBody()).orElse("");
        String statusStr = String.valueOf(response.getResponseCode());
        variables.put("prev.request", requestPayload);
        variables.put("prev.response", respBody);
        variables.put("prev.status", statusStr);
        recordHeaderHistory("prev", requestHeaders, response);

        for (String key : stepKeys(step.name(), stepIndex)) {
            variables.put("steps." + key + ".request", requestPayload);
            variables.put("steps." + key + ".response", respBody);
            variables.put("steps." + key + ".status", statusStr);
            recordHeaderHistory("steps." + key, requestHeaders, response);
        }
    }

    private void recordHeaderHistory(String prefix, Set<CatsHeader> requestHeaders, CatsResponse response) {
        for (CatsHeader header : Optional.ofNullable(requestHeaders).orElse(Set.of())) {
            if (header != null && header.getName() != null && header.getValue() != null) {
                variables.put(prefix + ".request.headers." + header.getName(), header.getValue());
                variables.put(prefix + ".request.headers." + header.getName().toLowerCase(Locale.ROOT), header.getValue());
            }
        }
        for (KeyValuePair<String, String> header : Optional.ofNullable(response.getHeaders()).orElse(List.of())) {
            if (header != null && header.getKey() != null && header.getValue() != null) {
                String lower = header.getKey().toLowerCase(Locale.ROOT);
                variables.put(prefix + ".headers." + header.getKey(), header.getValue());
                variables.put(prefix + ".headers." + lower, header.getValue());
                variables.put(prefix + ".response.headers." + header.getKey(), header.getValue());
                variables.put(prefix + ".response.headers." + lower, header.getValue());
            }
        }
    }

    static Set<String> stepKeys(String stepName, int stepIndex) {
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
        return findCandidateByVariantName(candidates, step.variant())
                .or(() -> findCandidateMatchingAllVariantFields(candidates, step.variantMatch()))
                .or(() -> findCandidateMatchingAnyBodyField(candidates, step.body()))
                .or(() -> Optional.of(candidates.getFirst()));
    }

    private static Optional<FuzzingData> findCandidateByVariantName(List<FuzzingData> candidates, String variant) {
        if (StringUtils.isBlank(variant)) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(c -> c.getReqSchemaName() != null && c.getReqSchemaName().endsWith(variant))
                .findFirst();
    }

    private static Optional<FuzzingData> findCandidateMatchingAllVariantFields(List<FuzzingData> candidates,
                                                                               Map<String, String> variantFields) {
        if (variantFields.isEmpty()) {
            return Optional.empty();
        }
        return findCandidateByPayload(candidates,
                payload -> variantFields.entrySet().stream().allMatch(field -> hasFieldValue(payload, field)));
    }

    private static Optional<FuzzingData> findCandidateMatchingAnyBodyField(List<FuzzingData> candidates, Object body) {
        Map<String, String> bodyFields = scalarTopLevelFields(body);
        return findCandidateByPayload(candidates,
                payload -> bodyFields.entrySet().stream().anyMatch(field -> hasFieldValue(payload, field)));
    }

    private static Optional<FuzzingData> findCandidateByPayload(List<FuzzingData> candidates, Predicate<String> payloadMatcher) {
        return candidates.stream()
                .filter(candidate -> JsonUtils.isValidJson(candidate.getPayload()))
                .filter(candidate -> payloadMatcher.test(candidate.getPayload()))
                .findFirst();
    }

    private static Map<String, String> scalarTopLevelFields(Object body) {
        if (!(body instanceof Map<?, ?> bodyMap)) {
            return Map.of();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        bodyMap.forEach((key, value) -> {
            if (key != null && value != null && !(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
                fields.put(String.valueOf(key), String.valueOf(value));
            }
        });
        return fields;
    }

    private static boolean hasFieldValue(String payload, Map.Entry<String, String> field) {
        Object value = JsonUtils.getVariableFromJson(payload, FieldCorrelationEvaluator.normalizeFieldPath(field.getKey()));
        return Objects.equals(String.valueOf(value), field.getValue());
    }

    private PreparedStepPayloads preparePayloads(FunctionalFlowDocument document, FunctionalFlowStep step,
                                                 ResolvedStepTarget target, FuzzingData data) {
        boolean bodyMethod = HttpMethod.requiresBody(data.getMethod());
        String basePayload = resolveBasePayload(step.base(), data, bodyMethod);
        String pathParamsPayload = StringUtils.defaultIfBlank(data.getPathParamsPayload(), "{}");
        Set<String> changedFields = new LinkedHashSet<>();

        RefDataPayloads refDataApplied = applyConfiguredRefData(data, basePayload, pathParamsPayload, bodyMethod);
        basePayload = refDataApplied.bodyPayload();
        pathParamsPayload = refDataApplied.pathParamsPayload();

        String bodyPayload = applyBodyOverrides(basePayload, step.body(), data.getContractPath(), changedFields);
        for (String omitField : resolveOmitEntries(step.omit(), bodyPayload)) {
            String normalized = FieldCorrelationEvaluator.normalizeFieldPath(omitField);
            bodyPayload = JsonUtils.deleteNode(bodyPayload, normalized);
            changedFields.add(normalized);
        }

        Map<String, Object> combinedUrlParams = new LinkedHashMap<>(target.extractedPathParams());
        combinedUrlParams.putAll(target.extractedQueryParams());
        for (Map.Entry<String, Object> paramEntry : combinedUrlParams.entrySet()) {
            Object resolved = resolveValueAndRecordRefData(data.getContractPath(), paramEntry.getKey(), paramEntry.getValue(), bodyPayload);
            changedFields.add(paramEntry.getKey());
            if (isRemoveFieldSentinel(resolved)) {
                if (bodyMethod) {
                    pathParamsPayload = JsonUtils.deleteNode(pathParamsPayload, paramEntry.getKey());
                } else {
                    bodyPayload = JsonUtils.deleteNode(bodyPayload, paramEntry.getKey());
                }
                continue;
            }
            if (bodyMethod) {
                pathParamsPayload = FieldCorrelationEvaluator.setOrInsertField(pathParamsPayload, paramEntry.getKey(), resolved, true);
            } else {
                bodyPayload = FieldCorrelationEvaluator.setOrInsertField(bodyPayload, paramEntry.getKey(), resolved, true);
            }
        }

        List<FieldCorrelationRule> allRules = loadExternalAndFlowCorrelationRules(document, step);
        bodyPayload = FieldCorrelationEvaluator.applyRequestCorrelations(
                data.getContractPath(), data.getMethod(), bodyPayload, allRules, variables, changedFields);

        return new PreparedStepPayloads(bodyPayload, pathParamsPayload, changedFields);
    }

    private record RefDataPayloads(String bodyPayload, String pathParamsPayload) {
    }

    private RefDataPayloads applyConfiguredRefData(FuzzingData data, String basePayload,
                                                   String pathParamsPayload, boolean bodyMethod) {
        Map<String, Object> refData = FilesArguments.resolveRefData(filesArguments, data.getContractPath(), data.getMethod());
        RefDataPayloads current = new RefDataPayloads(basePayload, pathParamsPayload);
        for (Map.Entry<String, Object> entry : refData.entrySet()) {
            current = applySingleRefDataEntry(data.getContractPath(), current, entry, bodyMethod);
        }
        return current;
    }

    private RefDataPayloads applySingleRefDataEntry(String contractPath, RefDataPayloads current,
                                                    Map.Entry<String, Object> entry, boolean bodyMethod) {
        String key = entry.getKey();
        if (key == null || key.matches(CatsDSLWords.ADDITIONAL_PROPERTIES)) {
            return current;
        }
        if (ServiceCaller.CATS_REMOVE_FIELD.equalsIgnoreCase(String.valueOf(entry.getValue()))) {
            String updatedBody = JsonUtils.isFieldInJson(current.bodyPayload(), key)
                    ? JsonUtils.deleteNode(current.bodyPayload(), key)
                    : current.bodyPayload();
            return new RefDataPayloads(updatedBody, current.pathParamsPayload());
        }
        Object resolved = resolveValueAndRecordRefData(contractPath, key, entry.getValue(), current.bodyPayload());
        if (resolved instanceof String str && str.startsWith("${") && str.endsWith("}")) {
            return current;
        }
        if (bodyMethod && JsonUtils.isFieldInJson(current.pathParamsPayload(), key)) {
            String updatedPath = FieldCorrelationEvaluator.setOrInsertField(current.pathParamsPayload(), key, resolved, false);
            return new RefDataPayloads(current.bodyPayload(), updatedPath);
        }
        if (JsonUtils.isFieldInJson(current.bodyPayload(), key)) {
            return new RefDataPayloads(setOrMergeField(current.bodyPayload(), key, resolved), current.pathParamsPayload());
        }
        return current;
    }

    private List<FieldCorrelationRule> loadExternalAndFlowCorrelationRules(FunctionalFlowDocument document,
                                                                           FunctionalFlowStep step) {
        List<FieldCorrelationRule> allRules = new ArrayList<>();
        if (filesArguments != null && filesArguments.getCorrelationsFile() != null) {
            try {
                allRules.addAll(FunctionalFlowParser.parseCorrelationFile(filesArguments.getCorrelationsFile()));
            } catch (IOException e) {
                logger.debug("Unable to parse correlations file in FunctionalFlowFuzzer", e);
            }
        }
        allRules.addAll(document.correlate());
        allRules.addAll(step.correlate());
        return allRules;
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
                return applyStringOrVariableBody(basePayload, rawText, contractPath, changedFields);
            }
            case List<?> listSpec -> {
                Object resolvedList = resolveValueRecursive(contractPath, "$", listSpec, basePayload, changedFields, new ArrayList<>());
                changedFields.add("$");
                return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(resolvedList);
            }
            case Map<?, ?> mapSpec -> {
                return applyMapBodyOverrides(basePayload, mapSpec, contractPath, changedFields);
            }
            default -> {
                //ignore other types, treat as no overrides
            }
        }
        return basePayload;
    }

    private String applyStringOrVariableBody(String basePayload, String rawText, String contractPath, Set<String> changedFields) {
        Optional<String> optionalVar = extractStandaloneOptionalVariableName(rawText);
        if (optionalVar.isPresent()) {
            Object optionalVal = resolveOptionalVariableValue(optionalVar.get());
            if (optionalVal == OMIT_OPTIONAL_FIELD) {
                return basePayload;
            }
            return applyResolvedWholeBody(basePayload, optionalVal, contractPath, changedFields);
        }
        Optional<String> standaloneVar = extractStandaloneVariableName(rawText);
        if (standaloneVar.isPresent() && typedVariables.containsKey(standaloneVar.get())) {
            return applyResolvedWholeBody(basePayload, typedVariables.get(standaloneVar.get()), contractPath, changedFields);
        }
        String resolved = resolveStringExpression(rawText, basePayload);
        changedFields.add("$");
        return JsonUtils.isValidJson(resolved) ? resolved : JsonUtils.serialize(resolved);
    }

    private String applyResolvedWholeBody(String basePayload, Object resolvedVal, String contractPath, Set<String> changedFields) {
        if (resolvedVal instanceof Map<?, ?> mapVal) {
            return applyMapBodyOverrides(basePayload, mapVal, contractPath, changedFields);
        }
        if (resolvedVal instanceof String strVal) {
            changedFields.add("$");
            return JsonUtils.isValidJson(strVal) ? strVal : JsonUtils.serialize(strVal);
        }
        changedFields.add("$");
        return JsonUtils.GSON_NO_PRETTY_PRINTING.toJson(resolvedVal);
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
            List<String> removedNestedPaths = new ArrayList<>();
            Object resolvedVal = resolveValueRecursive(contractPath, normalizedKey, entry.getValue(), current, changedFields, removedNestedPaths);
            if (isRemoveFieldSentinel(resolvedVal)) {
                current = JsonUtils.deleteNode(current, normalizedKey);
                changedFields.add(normalizedKey);
                continue;
            }
            current = setOrMergeField(current, normalizedKey, resolvedVal);
            for (String removedPath : removedNestedPaths) {
                current = JsonUtils.deleteNode(current, removedPath);
                changedFields.add(removedPath);
            }
        }
        return current;
    }

    private static boolean isRemoveFieldSentinel(Object value) {
        return value == OMIT_OPTIONAL_FIELD
                || (value instanceof String str && ServiceCaller.CATS_REMOVE_FIELD.equalsIgnoreCase(str));
    }

    private Object resolveValueRecursive(String contractPath, String fieldPath, Object value,
                                         String currentPayload, Set<String> changedFields, List<String> removedPaths) {
        if (value instanceof Map<?, ?> nestedMap) {
            Map<String, Object> resolvedMap = new LinkedHashMap<>();
            nestedMap.forEach((k, v) -> {
                String childKey = String.valueOf(k);
                String childPath = "$".equals(fieldPath) ? childKey : fieldPath + "#" + childKey;
                Object resolvedChild = resolveValueRecursive(contractPath, childPath, v, currentPayload, changedFields, removedPaths);
                if (isRemoveFieldSentinel(resolvedChild)) {
                    removedPaths.add(childPath);
                } else {
                    resolvedMap.put(childKey, resolvedChild);
                }
            });
            return resolvedMap;
        }
        if (!"$".equals(fieldPath)) {
            changedFields.add(fieldPath);
        }
        if (value instanceof List<?> nestedList) {
            List<Object> resolvedList = new ArrayList<>();
            for (Object item : nestedList) {
                Object resolvedItem = resolveValueRecursive(contractPath, fieldPath, item, currentPayload, changedFields, removedPaths);
                if (!isRemoveFieldSentinel(resolvedItem)) {
                    resolvedList.add(resolvedItem);
                }
            }
            return resolvedList;
        }
        return resolveValueAndRecordRefData(contractPath, fieldPath, value, currentPayload);
    }

    private List<String> resolveOmitEntries(List<String> rawOmit, String currentPayload) {
        if (rawOmit == null || rawOmit.isEmpty()) {
            return List.of();
        }
        List<String> resolvedEntries = new ArrayList<>();
        for (String entry : rawOmit) {
            appendResolvedOmitEntry(entry, currentPayload, resolvedEntries);
        }
        return resolvedEntries;
    }

    private void appendResolvedOmitEntry(String entry, String currentPayload, List<String> target) {
        if (StringUtils.isBlank(entry)) {
            return;
        }
        Optional<String> optionalVar = extractStandaloneOptionalVariableName(entry);
        if (optionalVar.isPresent()) {
            Object val = resolveOptionalVariableValue(optionalVar.get());
            if (val != OMIT_OPTIONAL_FIELD) {
                appendTypedOmitValue(val, target);
            }
            return;
        }
        Optional<String> standaloneVar = extractStandaloneVariableName(entry);
        if (standaloneVar.isPresent() && typedVariables.containsKey(standaloneVar.get())) {
            appendTypedOmitValue(typedVariables.get(standaloneVar.get()), target);
            return;
        }
        String resolved = resolveStringExpression(entry, currentPayload);
        if (StringUtils.isNotBlank(resolved)) {
            target.add(resolved);
        }
    }

    private static void appendTypedOmitValue(Object typedVal, List<String> target) {
        if (typedVal instanceof List<?> listVal) {
            listVal.stream().filter(Objects::nonNull).map(String::valueOf)
                    .filter(StringUtils::isNotBlank).forEach(target::add);
        } else if (typedVal != null && StringUtils.isNotBlank(String.valueOf(typedVal))) {
            target.add(String.valueOf(typedVal));
        }
    }

    private Object resolveValueAndRecordRefData(String contractPath, String fieldPath, Object rawValue, String currentPayload) {
        if (!(rawValue instanceof String strVal)) {
            return rawValue;
        }
        Optional<String> optionalVar = extractStandaloneOptionalVariableName(strVal);
        if (optionalVar.isPresent()) {
            Object optionalVal = resolveOptionalVariableValue(optionalVar.get());
            if (optionalVal != OMIT_OPTIONAL_FIELD) {
                recordVariableConsumptionForRefData(contractPath, fieldPath, "${" + optionalVar.get() + "}", optionalVal);
            }
            return optionalVal;
        }
        Optional<String> standaloneVar = extractStandaloneVariableName(strVal);
        if (standaloneVar.isPresent() && typedVariables.containsKey(standaloneVar.get())) {
            Object typedVal = typedVariables.get(standaloneVar.get());
            recordVariableConsumptionForRefData(contractPath, fieldPath, strVal, typedVal);
            return typedVal;
        }
        String resolved = resolveStringExpression(strVal, currentPayload);
        serviceCaller.registerSensitiveFieldIfPresent(fieldPath, strVal, resolved);
        recordVariableConsumptionForRefData(contractPath, fieldPath, strVal, resolved);
        return DynamicValueResolver.coerceScalarIfOriginalWasTyped(strVal, resolved);
    }

    private void recordVariableConsumptionForRefData(String contractPath, String fieldPath, String rawExpr, Object resolved) {
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
        String withOptional = replaceEmbeddedOptionalTokens(rawExpr);
        String withStepRefs = replaceStepTokens(withOptional);
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
            String token = matcher.group(matcher.group(1) != null ? 1 : 2);
            String replacement = FieldCorrelationEvaluator.resolveStepReference(token, variables)
                    .map(String::valueOf)
                    .orElse(matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
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
        mapVal.forEach((k, v) -> {
            String key = String.valueOf(k);
            if (v == null) {
                child.add(key, JsonNull.INSTANCE);
            } else {
                child.add(key, JsonUtils.GSON_NO_PRETTY_PRINTING.toJsonTree(v));
            }
        });
        root.add(topKey, child);
        return JsonUtils.GSON.toJson(root);
    }

    private Set<CatsHeader> prepareHeaders(FunctionalFlowDocument document, FunctionalFlowStep step,
                                           FuzzingData data, String bodyPayload, Set<String> protectedHeaders) {
        Map<String, CatsHeader> byName = new LinkedHashMap<>();
        for (CatsHeader header : Optional.ofNullable(data.getHeaders()).orElse(Set.of())) {
            byName.put(header.getName().toLowerCase(Locale.ROOT), header.copy());
        }
        if (document != null) {
            applyHeadersRef(byName, document.headersRef(), "Document", data.getContractPath(), bodyPayload, protectedHeaders);
            applyHeaderOverrides(byName, document.headers(), data.getContractPath(), bodyPayload, protectedHeaders);
        }
        applyHeadersRef(byName, step.headersRef(), "Step '" + step.name() + "'", data.getContractPath(), bodyPayload, protectedHeaders);
        applyHeaderOverrides(byName, step.headers(), data.getContractPath(), bodyPayload, protectedHeaders);
        applyHeaderOmissions(byName, resolveOmitEntries(step.omit(), bodyPayload), protectedHeaders);
        return new LinkedHashSet<>(byName.values());
    }

    private void applyHeadersRef(Map<String, CatsHeader> byName, String headersRef, String owner,
                                 String contractPath, String bodyPayload, Set<String> protectedHeaders) {
        if (StringUtils.isBlank(headersRef)) {
            return;
        }
        Optional<String> optionalVar = extractStandaloneOptionalVariableName(headersRef);
        Optional<String> requiredVar = optionalVar.isPresent() ? Optional.empty() : extractStandaloneVariableName(headersRef);
        if (optionalVar.isEmpty() && requiredVar.isEmpty()) {
            throw new CatsException("%s 'headers' must be a map or a standalone '${variable}' reference, got '%s'"
                    .formatted(owner, headersRef));
        }
        String name = optionalVar.orElseGet(requiredVar::orElseThrow);
        Object resolved = optionalVar.isPresent()
                ? resolveOptionalVariableValue(name)
                : (typedVariables.containsKey(name) ? typedVariables.get(name) : OMIT_OPTIONAL_FIELD);
        if (resolved == OMIT_OPTIONAL_FIELD) {
            if (optionalVar.isEmpty()) {
                throw new CatsException("%s 'headers' variable '${%s}' did not resolve to a map/object"
                        .formatted(owner, name));
            }
            return;
        }
        if (resolved == null) {
            return;
        }
        if (!(resolved instanceof Map<?, ?> mapVal)) {
            throw new CatsException("%s 'headers' variable '${%s}' must be a map/object, got %s"
                    .formatted(owner, name, resolved.getClass().getSimpleName()));
        }
        Map<String, Object> headerMap = new LinkedHashMap<>();
        mapVal.forEach((k, v) -> headerMap.put(String.valueOf(k), v));
        applyHeaderOverrides(byName, headerMap, contractPath, bodyPayload, protectedHeaders);
    }

    private void applyHeaderOverrides(Map<String, CatsHeader> byName, Map<String, Object> overrides,
                                      String contractPath, String bodyPayload, Set<String> protectedHeaders) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }
        overrides.forEach((name, rawValue) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            protectedHeaders.add(lower);
            if (rawValue == null) {
                byName.remove(lower);
                return;
            }
            String rawStr = String.valueOf(rawValue);
            Optional<String> optionalVar = extractStandaloneOptionalVariableName(rawStr);
            if (optionalVar.isPresent()) {
                Object optionalVal = resolveOptionalVariableValue(optionalVar.get());
                if (optionalVal == OMIT_OPTIONAL_FIELD) {
                    byName.remove(lower);
                    return;
                }
                String resolvedVal = String.valueOf(optionalVal);
                recordVariableConsumptionForRefData(contractPath, name, "${" + optionalVar.get() + "}", resolvedVal);
                byName.put(lower, CatsHeader.builder().name(name).value(resolvedVal).build());
                return;
            }
            String resolved = resolveStringExpression(rawStr, bodyPayload);
            serviceCaller.registerSensitiveFieldIfPresent(name, rawStr, resolved);
            recordVariableConsumptionForRefData(contractPath, name, rawStr, resolved);
            byName.put(lower, CatsHeader.builder().name(name).value(resolved).build());
        });
    }

    private static void applyHeaderOmissions(Map<String, CatsHeader> byName, List<String> omitList, Set<String> protectedHeaders) {
        for (String omitEntry : Optional.ofNullable(omitList).orElse(List.of())) {
            if (StringUtils.isBlank(omitEntry)) {
                continue;
            }
            String trimmed = omitEntry.trim();
            String headerName = (trimmed.toLowerCase(Locale.ROOT).startsWith("headers.") || trimmed.toLowerCase(Locale.ROOT).startsWith("header."))
                    ? trimmed.substring(trimmed.indexOf('.') + 1).trim().toLowerCase(Locale.ROOT)
                    : trimmed.toLowerCase(Locale.ROOT);
            byName.remove(headerName);
            protectedHeaders.add(headerName);
        }
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
                typedVariables.remove(varName);
                savedOutputVariableNames.add(varName);
                logger.debug("Saved variable [{}] = [{}]", varName, extracted);
            }
        });
    }

    private String extractValueBySelector(String selector, CatsResponse response,
                                          String requestPayload, String pathParamsPayload) {
        if (StringUtils.isBlank(selector)) {
            return JsonUtils.NOT_SET;
        }
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
        return extractResponseValue(response, selector);
    }

    public static String extractResponseValue(CatsResponse response, String selector) {
        if (selector.startsWith(RESPONSE_HEADER_PREFIX) || selector.toLowerCase(Locale.ROOT).startsWith("header.")) {
            String headerName = selector.substring(selector.contains(".")
                    ? selector.indexOf('.') + 1
                    : RESPONSE_HEADER_PREFIX.length());
            KeyValuePair<String, String> header = response.getHeader(headerName);
            return header == null ? JsonUtils.NOT_SET : header.getValue();
        }
        if (("$".equals(selector) || "$body".equalsIgnoreCase(selector) || "$response.body".equalsIgnoreCase(selector))
                && StringUtils.isNotBlank(response.getBody())) {
            return response.getBody().trim().replace("\"", "");
        }
        String cleanSelector = selector.startsWith("$response.") ? selector.substring("$response.".length()) : selector;
        return String.valueOf(FieldCorrelationEvaluator.lookupJsonPath(response.getBody(), cleanSelector));
    }

    private void evaluateAndReportStepResult(FunctionalFlowStep step, FuzzingData data, String requestPayload,
                                             CatsResponse response, ResponseCodeFamily expectedCodeFamily) {
        FunctionalFlowExpectation expect = step.expect();
        if (response.isRateLimited() || response.isBodyTruncated()) {
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
            reportCustomAssertionsOutcome(data, response, expectedCodeFamily, expect.matchesSchema());
            return;
        }
        if (expect.matchesSchema()) {
            testCaseListener.reportResult(logger, data, response, expectedCodeFamily);
        } else {
            testCaseListener.reportResult(logger, data, response, expectedCodeFamily, false, true);
        }
    }

    private static boolean hasCustomAssertions(FunctionalFlowExpectation expect) {
        return !expect.fields().isEmpty() || !expect.headers().isEmpty()
                || !expect.bodyContains().isEmpty() || !expect.bodyNotContains().isEmpty()
                || !expect.that().isEmpty() || expect.maxResponseTimeMs() != null;
    }

    private void reportCustomAssertionsOutcome(FuzzingData data, CatsResponse response,
                                               ResponseCodeFamily expectedCodeFamily, boolean matchesSchema) {
        if (expectedCodeFamily.matchesAllowedResponseCodes(response.responseCodeAsString())) {
            if (matchesSchema && response.getJsonBody() != null && data.getResponses() != null && !data.getResponses().isEmpty()) {
                testCaseListener.reportResult(logger, data, response, expectedCodeFamily);
            } else {
                testCaseListener.reportResultInfo(logger, data, "Response matches all 'verify' parameters");
            }
        } else if (isSameStatusFamily(expectedCodeFamily, response)) {
            testCaseListener.reportResultWarn(logger, data, "Returned response code not matching expected response code",
                    "Response matches all 'verify' parameters, but response code doesn't match expected response code: expected [{}], actual [{}]",
                    expectedCodeFamily, response.responseCodeAsString());
        } else {
            testCaseListener.reportResultError(logger, data, "Unexpected response code: %s".formatted(response.getResponseCode()),
                    "Response matches all 'verify' parameters, but response code doesn't match expected response code: expected [{}], actual [{}]",
                    expectedCodeFamily.allowedResponseCodes(), response.responseCodeAsString());
        }
    }

    private static boolean isSameStatusFamily(ResponseCodeFamily expectedCodeFamily, CatsResponse response) {
        String actualCode = response.responseCodeAsString();
        if (StringUtils.isBlank(actualCode)) {
            return false;
        }
        char familyPrefix = actualCode.charAt(0);
        return expectedCodeFamily.allowedResponseCodes().stream()
                .anyMatch(allowed -> StringUtils.isNotBlank(allowed) && allowed.charAt(0) == familyPrefix);
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
        if (filesArguments.getExportRefDataFile() != null) {
            CatsUtil.writeToYaml(filesArguments.getExportRefDataFile().getAbsolutePath(), recordedRefDataByPath);
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
