package com.endava.cats.fuzzer.special;

import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.fields.base.CustomFuzzerBase;
import com.endava.cats.model.CustomFuzzerExecution;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executes functional tests written in Cats DSL.
 */
@Singleton
@SpecialFuzzer
public class FunctionalFuzzer implements CustomFuzzerBase {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(FunctionalFuzzer.class);
    private final FilesArguments filesArguments;
    private final CustomFuzzerUtil customFuzzerUtil;
    private final List<CustomFuzzerExecution> executions = new ArrayList<>();
    private final TestCaseListener testCaseListener;

    /**
     * Constructs a new FunctionalFuzzer instance.
     *
     * @param cp               The FilesArguments object containing the files to be fuzzed.
     * @param cfu              The CustomFuzzerUtil object used to perform custom fuzzing operations.
     * @param testCaseListener The TestCaseListener object used to report test case results and progress.
     */
    public FunctionalFuzzer(FilesArguments cp, CustomFuzzerUtil cfu, TestCaseListener testCaseListener) {
        this.filesArguments = cp;
        this.customFuzzerUtil = cfu;
        this.testCaseListener = testCaseListener;
    }

    @Override
    public void fuzz(FuzzingData data) {
        if (!filesArguments.getCustomFuzzerDetails().isEmpty()) {
            this.processCustomFuzzerFile(data);
        }
    }

    void processCustomFuzzerFile(FuzzingData data) {
        Map<String, Object> currentPathValues = filesArguments.getCustomFuzzerDetails().get(data.getContractPath());
        if (currentPathValues != null) {
            currentPathValues.entrySet().stream()
                    .filter(stringObjectEntry -> customFuzzerUtil.isMatchingHttpMethod(stringObjectEntry.getValue(), data.getMethod()))
                    .forEach(entry -> executions.add(CustomFuzzerExecution.builder()
                            .fuzzingData(data).testId(entry.getKey()).testEntry(entry.getValue()).build()));
        } else {
            logger.skip("Skipping path [{}] for method [{}] as it was not configured in customFuzzerFile", data.getContractPath(), data.getMethod());
        }
    }

    /**
     * This will execute the CustomTests stored in the {@code executions} collection.
     * Before executing we make sure we sort the collection so that it appears in the same order as in the custom fuzzer file.
     * We decouple the execution of the custom fuzzer tests from their creation in order to execute them in the order defined in the customFuzzerFile,
     * rather than the order defined by the OpenAPI contract.
     */
    public void executeCustomFuzzerTests() {
        logger.debug("Executing {} functional tests.", executions.size());
        Collections.sort(executions);

        executions.stream().collect(Collectors.groupingBy(customFuzzerExecution -> customFuzzerExecution.getFuzzingData().getContractPath(), Collectors.counting()))
                .forEach((s, aLong) -> testCaseListener.setTotalRunsPerPath(s, aLong.intValue()));

        for (Map.Entry<String, Map<String, Object>> entry : filesArguments.getCustomFuzzerDetails().entrySet()) {
            executions.stream().filter(customFuzzerExecution -> customFuzzerExecution.getFuzzingData().getContractPath().equalsIgnoreCase(entry.getKey()))
                    .forEach(customFuzzerExecution -> {
                        testCaseListener.beforeFuzz(this.getClass());
                        customFuzzerUtil.executeTestCases(customFuzzerExecution.getFuzzingData(), customFuzzerExecution.getTestId(),
                                customFuzzerExecution.getTestEntry(), this);
                        testCaseListener.afterFuzz(customFuzzerExecution.getFuzzingData().getContractPath(), customFuzzerExecution.getFuzzingData().getMethod().name());
                    });
        }
    }

    /**
     * Replaces variables in the reference data file (refData.yml) with output variables generated
     * by the FunctionalFuzzer. If no reference data file exists, or if `filesArguments.isCreateRefData()`
     * is `true`, a new reference data file will be created.
     *
     * @throws IOException if an I/O error occurs
     */
    public void replaceRefData() throws IOException {
        if (filesArguments.getRefDataFile() != null) {
            logger.debug("Replacing variables in refData file with output variables from FunctionalFuzzer!");
            List<String> refDataLines = Files.readAllLines(filesArguments.getRefDataFile().toPath());
            List<String> updatedLines = refDataLines.stream().map(this::replaceWithVariable).toList();
            String currentFile = filesArguments.getRefDataFile().getAbsolutePath();
            Path file = Paths.get(currentFile.substring(0, currentFile.lastIndexOf(".")) + "_replaced.yml");
            Files.write(file, updatedLines, StandardCharsets.UTF_8);
        } else if (filesArguments.isCreateRefData()) {
            logger.debug("Creating refData file with output variables from FunctionalFuzzer!");
            customFuzzerUtil.writeRefDataFileWithOutputVariables();
        }
    }

    private String replaceWithVariable(String line) {
        String result = line;
        for (Map.Entry<String, String> variable : customFuzzerUtil.getVariables().entrySet()) {
            result = result.replace("${" + variable.getKey() + "}", variable.getValue());
        }

        return result;
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "leverage the self-healing and auto generation powers of CATS in order to write functional tests";
    }

    @Override
    public List<String> requiredKeywords() {
        return Arrays.asList(CatsDSLWords.EXPECTED_RESPONSE_CODE, CatsDSLWords.HTTP_METHOD, CatsDSLWords.DESCRIPTION);
    }
}
