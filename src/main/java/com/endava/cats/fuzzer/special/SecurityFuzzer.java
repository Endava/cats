package com.endava.cats.fuzzer.special;

import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.CustomFuzzerBase;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowStep;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FuzzMode;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.StepFuzzConfig;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import net.minidev.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fuzzer that performs security fuzzing based on user-supplied payloads and dictionaries
 * using the sequential `steps:` + `fuzz:` flow syntax.
 */
@Singleton
@SpecialFuzzer
public class SecurityFuzzer implements CustomFuzzerBase {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final FilesArguments filesArguments;
    private final CustomFuzzerUtil customFuzzerUtil;

    /**
     * Constructs a new SecurityFuzzer instance.
     *
     * @param cp  The FilesArguments object containing the files to be fuzzed.
     * @param cfu The CustomFuzzerUtil object used to perform custom fuzzing operations.
     */
    public SecurityFuzzer(FilesArguments cp, CustomFuzzerUtil cfu) {
        this.filesArguments = cp;
        this.customFuzzerUtil = cfu;
    }

    @Override
    public void fuzz(FuzzingData data) {
        FunctionalFlowDocument doc = filesArguments.getSecurityFlowDocument();
        if (doc == null || data == null || data.getContractPath() == null) {
            return;
        }
        int stepIdx = 1;
        for (FunctionalFlowStep step : doc.steps()) {
            String stepKey = "step_" + stepIdx++;
            if (matchesStepPath(step, data.getContractPath())) {
                executeSecurityStep(step, stepKey, data);
            }
        }
    }

    private static boolean matchesStepPath(FunctionalFlowStep step, String contractPath) {
        List<String> paths = step.targetPaths().isEmpty() ? List.of(step.rawTarget()) : step.targetPaths();
        return paths.stream().anyMatch(p -> CatsDSLWords.ALL.equalsIgnoreCase(p) || p.equalsIgnoreCase(contractPath));
    }

    private void executeSecurityStep(FunctionalFlowStep step, String stepKey, FuzzingData data) {
        List<String> missing = getMissingStepKeywords(step);
        if (!missing.isEmpty()) {
            String message = "is missing the following mandatory entries: %s".formatted(missing);
            log.error(message);
            customFuzzerUtil.recordError(message);
            return;
        }
        if (!step.allMethods() && step.method() != data.getMethod()) {
            return;
        }
        StepFuzzConfig fuzzConfig = step.fuzz();
        List<String> payloads = FunctionalFlowFuzzer.loadPayloadsForFuzzStep(fuzzConfig, log);
        if (payloads.isEmpty()) {
            return;
        }
        List<String> targetFields = FunctionalFlowFuzzer.resolveTargetFieldsForOperation(fuzzConfig, data);
        if (fuzzConfig.mode() == FuzzMode.CROSS && targetFields.size() > 1) {
            executeCrossFields(data, stepKey, step, payloads, targetFields);
            return;
        }
        Map<String, Object> baseConfig = buildExecutionConfig(step, data);
        this.fuzzFields(data, stepKey, baseConfig, payloads, targetFields);
    }

    private void executeCrossFields(FuzzingData data, String stepKey, FunctionalFlowStep step,
                                    List<String> payloads, List<String> targetFields) {
        String joined = String.join(", ", targetFields);
        for (String payloadVal : payloads) {
            Map<String, Object> config = buildExecutionConfig(step, data);
            for (String field : targetFields) {
                config.put(field, payloadVal);
            }
            config.put(CatsDSLWords.DESCRIPTION, step.scenarioText() + ", cross-fields [" + joined + "]");
            customFuzzerUtil.executeTestCases(data, stepKey, config, this);
        }
    }

    private static Map<String, Object> buildExecutionConfig(FunctionalFlowStep step, FuzzingData data) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(CatsDSLWords.HTTP_METHOD, step.allMethods() ? data.getMethod().name() : step.method().name());
        config.put(CatsDSLWords.EXPECTED_RESPONSE_CODE, step.expect().status().size() == 1
                ? step.expect().status().getFirst()
                : step.expect().status());
        config.put(CatsDSLWords.DESCRIPTION, step.scenarioText());
        if (step.body() instanceof Map<?, ?> bodyMap) {
            bodyMap.forEach((k, v) -> config.put(String.valueOf(k), mapBodyEntry(String.valueOf(k), v, data)));
        }
        step.omit().forEach(omitField -> config.put(omitField, "cats_remove_field"));
        if (!step.save().isEmpty()) {
            config.put(CatsDSLWords.OUTPUT, step.save());
        }
        if (!step.expect().fields().isEmpty()) {
            config.put(CatsDSLWords.VERIFY, step.expect().fields());
        }
        return config;
    }

    private static Object mapBodyEntry(String key, Object value, FuzzingData data) {
        if (value instanceof List<?> list && !(JsonUtils.getVariableFromJson(data.getPayload(), key) instanceof JSONArray)) {
            return list.isEmpty() ? "" : list.getFirst();
        }
        return value;
    }

    private static List<String> getMissingStepKeywords(FunctionalFlowStep step) {
        List<String> missing = new ArrayList<>();
        if (step.expect().status().isEmpty()) {
            missing.add("status");
        }
        if (!step.allMethods() && step.method() == null) {
            missing.add("method");
        }
        StepFuzzConfig fuzz = step.fuzz();
        if (fuzz == null || !fuzz.hasPayloadSource()) {
            missing.add("payloads or payloadsFile");
        }
        if (fuzz == null || !fuzz.hasTargets()) {
            missing.add("fields or fieldTypes");
        }
        return missing;
    }

    private void fuzzFields(FuzzingData data, String key, Map<String, Object> baseConfig, List<String> nastyStrings, List<String> targetFields) {
        log.debug("Target fields {}", targetFields);

        for (String targetField : targetFields) {
            log.info("Fuzzing field [{}]", targetField);
            Map<String, Object> configClone = baseConfig.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            configClone.put(targetField, nastyStrings);
            configClone.put(CatsDSLWords.DESCRIPTION, customFuzzerUtil.getScenarioOrDescription(baseConfig) + ", field [" + targetField + "]");
            customFuzzerUtil.executeTestCases(data, key, configClone, this);
        }
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "use custom dictionaries of 'nasty' strings to target specific fields or data types";
    }

    @Override
    public List<String> requiredKeywords() {
        return List.of(CatsDSLWords.EXPECTED_RESPONSE_CODE, CatsDSLWords.HTTP_METHOD);
    }
}
