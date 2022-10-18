package com.endava.cats.fuzzer.special;

import com.endava.cats.annotations.SpecialFuzzer;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.fuzzer.fields.base.CustomFuzzerBase;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CustomFuzzerExecution;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.slf4j.MDC;

import javax.inject.Singleton;
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
import java.util.Optional;

@Singleton
@SpecialFuzzer
public class FunctionalFuzzer implements CustomFuzzerBase {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(FunctionalFuzzer.class);
    private final FilesArguments filesArguments;
    private final CustomFuzzerUtil customFuzzerUtil;
    private final List<CustomFuzzerExecution> executions = new ArrayList<>();

    public FunctionalFuzzer(FilesArguments cp, CustomFuzzerUtil cfu) {
        this.filesArguments = cp;
        this.customFuzzerUtil = cfu;
    }


    @Override
    public void fuzz(FuzzingData data) {
        if (!filesArguments.getCustomFuzzerDetails().isEmpty()) {
            this.processCustomFuzzerFile(data);
        }
    }

    protected void processCustomFuzzerFile(FuzzingData data) {
        Map<String, Object> currentPathValues = filesArguments.getCustomFuzzerDetails().get(data.getPath());
        if (currentPathValues != null) {
            currentPathValues.entrySet().stream().filter(stringObjectEntry -> isMatchingHttpMethod(stringObjectEntry.getValue(), data.getMethod()))
                    .forEach(entry -> executions.add(CustomFuzzerExecution.builder()
                            .fuzzingData(data).testId(entry.getKey()).testEntry(entry.getValue()).build()));
        } else {
            logger.info("Skipping path [{}] for method [{}] as it was not configured in customFuzzerFile", data.getPath(), data.getMethod());
        }
    }

    private boolean isMatchingHttpMethod(Object currentValues, HttpMethod httpMethod) {
        Map<String, Object> currentPathValues = (Map<String, Object>) currentValues;
        Optional<HttpMethod> httpMethodFromYaml = HttpMethod.fromString(String.valueOf(currentPathValues.get(CatsDSLWords.HTTP_METHOD)));

        return httpMethodFromYaml.isEmpty() || httpMethodFromYaml.get().equals(httpMethod);
    }

    /**
     * This will execute the CustomTests stored in the {@code executions} collection.
     * Before executing we make sure we sort the collection so that it appears in the same order as in the custom fuzzer file.
     * We decouple the execution of the custom fuzzer tests from their creation in order to execute them in the order defined in the customFuzzerFile,
     * rather than the order defined by the OpenAPI contract.
     */
    public void executeCustomFuzzerTests() {
        logger.debug("Executing {} functional tests.", executions.size());
        MDC.put("fuzzer", "FF");
        MDC.put("fuzzerKey", "FunctionalFuzzer");

        Collections.sort(executions);

        for (Map.Entry<String, Map<String, Object>> entry : filesArguments.getCustomFuzzerDetails().entrySet()) {
            executions.stream().filter(customFuzzerExecution -> customFuzzerExecution.getFuzzingData().getPath().equalsIgnoreCase(entry.getKey()))
                    .forEach(customFuzzerExecution -> customFuzzerUtil.executeTestCases(customFuzzerExecution.getFuzzingData(), customFuzzerExecution.getTestId(),
                            customFuzzerExecution.getTestEntry(), this));
        }
        MDC.put("fuzzer", CatsUtil.FUZZER_KEY_DEFAULT);
        MDC.put("fuzzerKey", CatsUtil.FUZZER_KEY_DEFAULT);
    }

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
    public List<String> reservedWords() {
        return Arrays.asList(CatsDSLWords.EXPECTED_RESPONSE_CODE, CatsDSLWords.DESCRIPTION, CatsDSLWords.OUTPUT, CatsDSLWords.VERIFY, CatsDSLWords.MAP_VALUES,
                CatsDSLWords.ONE_OF_SELECTION, CatsDSLWords.ADDITIONAL_PROPERTIES, CatsDSLWords.ELEMENT, CatsDSLWords.HTTP_METHOD);
    }
}
