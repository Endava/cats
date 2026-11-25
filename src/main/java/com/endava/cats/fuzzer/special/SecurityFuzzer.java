package com.endava.cats.fuzzer.special;

import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FunctionalFlowStep;
import com.endava.cats.fuzzer.special.FunctionalFlowDocument.StepFuzzConfig;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Fuzzer that performs security fuzzing based on user-supplied payloads and dictionaries
 * using the sequential `steps:` + `fuzz:` flow syntax.
 */
@Singleton
@SpecialFuzzer
public class SecurityFuzzer implements Fuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final FilesArguments filesArguments;
    private final FunctionalFlowFuzzer functionalFlowFuzzer;
    private final TestCaseListener testCaseListener;

    /**
     * Constructs a new SecurityFuzzer instance.
     *
     * @param cp                   The FilesArguments object containing the files to be fuzzed.
     * @param functionalFlowFuzzer The FunctionalFlowFuzzer used to execute security flow steps.
     * @param testCaseListener     The TestCaseListener used to record errors.
     */
    public SecurityFuzzer(FilesArguments cp, FunctionalFlowFuzzer functionalFlowFuzzer, TestCaseListener testCaseListener) {
        this.filesArguments = cp;
        this.functionalFlowFuzzer = functionalFlowFuzzer;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        FunctionalFlowDocument doc = filesArguments.getSecurityFlowDocument();
        if (doc == null || data == null || data.getContractPath() == null) {
            return;
        }
        functionalFlowFuzzer.initVariablesForExternalExecution(doc);
        List<String> includeTags = functionalFlowFuzzer.resolveEffectiveStepTags(doc);
        List<String> skipTags = functionalFlowFuzzer.resolveEffectiveSkipStepTags(doc);
        int stepIdx = 1;
        for (FunctionalFlowStep step : doc.steps()) {
            int currentIdx = stepIdx++;
            if (step.matchesTags(includeTags, skipTags)
                    && matchesStepPath(step, data.getContractPath())) {
                executeSecurityStep(doc, step, currentIdx, data);
            }
        }
    }

    private static boolean matchesStepPath(FunctionalFlowStep step, String contractPath) {
        List<String> paths = step.targetPaths().isEmpty() ? List.of(step.rawTarget()) : step.targetPaths();
        return paths.stream().anyMatch(p -> CatsDSLWords.ALL.equalsIgnoreCase(p) || p.equalsIgnoreCase(contractPath));
    }

    private void executeSecurityStep(FunctionalFlowDocument doc, FunctionalFlowStep step, int stepIndex, FuzzingData data) {
        List<String> missing = getMissingStepKeywords(step);
        if (!missing.isEmpty()) {
            String message = "is missing the following mandatory entries: %s".formatted(missing);
            log.error(message);
            testCaseListener.recordError(message);
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
        functionalFlowFuzzer.runPayloadsAgainstTargetFields(doc, step, stepIndex, data, targetFields, payloads, this);
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

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "use custom dictionaries of 'nasty' strings to target specific fields or data types";
    }
}
