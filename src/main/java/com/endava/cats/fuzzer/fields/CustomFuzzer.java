package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.SpecialFuzzer;
import com.endava.cats.fuzzer.fields.base.CustomFuzzerBase;
import com.endava.cats.model.CustomFuzzerExecution;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CustomFuzzerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@SpecialFuzzer
public class CustomFuzzer implements CustomFuzzerBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomFuzzer.class);


    private final FilesArguments filesArguments;
    private final CustomFuzzerUtil customFuzzerUtil;
    private final List<CustomFuzzerExecution> executions = new ArrayList<>();


    @Autowired
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
            currentPathValues.forEach((key, value) -> executions.add(CustomFuzzerExecution.builder()
                    .fuzzingData(data).testId(key).testEntry(value).build()));
        } else {
            LOGGER.info("Skipping path [{}] as it was not configured in customFuzzerFile", data.getPath());
        }
    }

    public void executeCustomFuzzerTests() {
        MDC.put("fuzzer", "CF");
        MDC.put("fuzzerKey", "CustomFuzzer");

        for (Map.Entry<String, Map<String, Object>> entry : filesArguments.getCustomFuzzerDetails().entrySet()) {
            executions.stream().filter(customFuzzerExecution -> customFuzzerExecution.getFuzzingData().getPath().equalsIgnoreCase(entry.getKey()))
                    .forEach(customFuzzerExecution -> customFuzzerUtil.executeTestCases(customFuzzerExecution.getFuzzingData(), customFuzzerExecution.getTestId(),
                            customFuzzerExecution.getTestEntry(), this));
        }
        MDC.put("fuzzer", null);
        MDC.put("fuzzerKey", null);
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
