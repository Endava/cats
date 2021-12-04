package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.SpecialFuzzer;
import com.endava.cats.fuzzer.fields.base.CustomFuzzerBase;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CustomFuzzerExecution;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.CustomFuzzerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
@SpecialFuzzer
public class CustomFuzzer implements CustomFuzzerBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomFuzzer.class);

    private final FilesArguments filesArguments;
    private final CustomFuzzerUtil customFuzzerUtil;
    private final List<CustomFuzzerExecution> executions = new ArrayList<>();

    public CustomFuzzer(FilesArguments cp, CustomFuzzerUtil cfu) {
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
            LOGGER.info("Skipping path [{}] for method [{}] as it was not configured in customFuzzerFile", data.getPath(), data.getMethod());
        }
    }

    private boolean isMatchingHttpMethod(Object currentValues, HttpMethod httpMethod) {
        Map<String, Object> currentPathValues = (Map<String, Object>) currentValues;
        Optional<HttpMethod> httpMethodFromYaml = HttpMethod.fromString(String.valueOf(currentPathValues.get(CustomFuzzerUtil.HTTP_METHOD)));

        return httpMethodFromYaml.isEmpty() || httpMethodFromYaml.get().equals(httpMethod);
    }

    /**
     * This will execute the CustomTests stored in the {@code executions} collection.
     * Before executing we make sure we sort the collection so that it appears in the same order as in the custom fuzzer file.
     * We decouple the execution of the custom fuzzer tests from their creation in order to execute them in the order defined in the customFuzzerFile,
     * rather than the order defined by the OpenAPI contract.
     */
    public void executeCustomFuzzerTests() {
        MDC.put("fuzzer", "CF");
        MDC.put("fuzzerKey", "CustomFuzzer");

        Collections.sort(executions);

        for (Map.Entry<String, Map<String, Object>> entry : filesArguments.getCustomFuzzerDetails().entrySet()) {
            executions.stream().filter(customFuzzerExecution -> customFuzzerExecution.getFuzzingData().getPath().equalsIgnoreCase(entry.getKey()))
                    .forEach(customFuzzerExecution -> customFuzzerUtil.executeTestCases(customFuzzerExecution.getFuzzingData(), customFuzzerExecution.getTestId(),
                            customFuzzerExecution.getTestEntry(), this));
        }
        MDC.put("fuzzer", CatsUtil.FUZZER_KEY_DEFAULT);
        MDC.put("fuzzerKey", CatsUtil.FUZZER_KEY_DEFAULT);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "allows to configure user supplied values for specific fields withing payloads; this is useful when testing more complex functional scenarios";
    }

    @Override
    public List<String> reservedWords() {
        return Arrays.asList(CustomFuzzerUtil.EXPECTED_RESPONSE_CODE, CustomFuzzerUtil.DESCRIPTION, CustomFuzzerUtil.OUTPUT, CustomFuzzerUtil.VERIFY, CustomFuzzerUtil.MAP_VALUES,
                CustomFuzzerUtil.ONE_OF_SELECTION, CustomFuzzerUtil.ADDITIONAL_PROPERTIES, CustomFuzzerUtil.ELEMENT, CustomFuzzerUtil.HTTP_METHOD);
    }
}
