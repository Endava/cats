package com.endava.cats.fuzzer.fields;

import com.endava.cats.CatsMain;
import com.endava.cats.fuzzer.FieldFuzzer;
import com.endava.cats.fuzzer.SpecialFuzzer;
import com.endava.cats.model.CustomFuzzerExecution;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.CustomFuzzerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

@Component
@SpecialFuzzer
public class CustomFuzzer implements CustomFuzzerBase {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomFuzzer.class);


    private final CatsUtil catsUtil;
    private final CustomFuzzerUtil customFuzzerUtil;
    private final List<CustomFuzzerExecution> executions = new ArrayList<>();
    @Value("${customFuzzerFile:empty}")
    private String customFuzzerFile;
    private Map<String, Map<String, Object>> customFuzzerDetails = new HashMap<>();

    @Autowired
    public CustomFuzzer(CatsUtil cu, CustomFuzzerUtil cfu) {
        this.catsUtil = cu;
        this.customFuzzerUtil = cfu;
    }


    @PostConstruct
    public void loadCustomFuzzerFile() {
        try {
            if (CatsMain.EMPTY.equalsIgnoreCase(customFuzzerFile)) {
                LOGGER.info("No custom Fuzzer file. CustomFuzzer will be skipped!");
            } else {
                customFuzzerDetails = catsUtil.parseYaml(customFuzzerFile);
            }
        } catch (Exception e) {
            LOGGER.error("Error processing customFuzzerFile!", e);
        }
    }


    @Override
    public void fuzz(FuzzingData data) {
        if (!customFuzzerDetails.isEmpty()) {
            this.processCustomFuzzerFile(data);
        }
    }

    protected void processCustomFuzzerFile(FuzzingData data) {
        Map<String, Object> currentPathValues = customFuzzerDetails.get(data.getPath());
        if (currentPathValues != null) {
            currentPathValues.forEach((key, value) -> executions.add(CustomFuzzerExecution.builder()
                    .fuzzingData(data).testId(key).testEntry(value).build()));
        } else {
            LOGGER.info("Skipping path [{}] as it was not configured in customFuzzerFile", data.getPath());
        }
    }

    public void executeCustomFuzzerTests() {
        MDC.put("fuzzer", this.getClass().getSimpleName());
        for (Map.Entry<String, Map<String, Object>> entry : customFuzzerDetails.entrySet()) {
            executions.stream().filter(customFuzzerExecution -> customFuzzerExecution.getFuzzingData().getPath().equalsIgnoreCase(entry.getKey()))
                    .forEach(customFuzzerExecution -> customFuzzerUtil.executeTestCases(customFuzzerExecution.getFuzzingData(), customFuzzerExecution.getTestId(),
                            customFuzzerExecution.getTestEntry(), this));
        }
        MDC.put("fuzzer", "");
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
