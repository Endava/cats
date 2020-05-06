package com.endava.cats.fuzzer.fields;

import com.endava.cats.CatsMain;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.google.gson.JsonElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class CustomFuzzer implements Fuzzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomFuzzer.class);
    private static final String EXPECTED_RESPONSE_CODE = "expectedResponseCode";

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final CatsUtil catsUtil;
    @Value("${customFuzzerFile:empty}")
    private String customFuzzerFile;

    @Autowired
    public CustomFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.catsUtil = cu;
    }

    public void fuzz(FuzzingData data) {
        if (CatsMain.EMPTY.equalsIgnoreCase(customFuzzerFile)) {
            LOGGER.info("No custom Fuzzer file. CustomFuzzer will be skipped!");
        } else {
            this.processCustomFuzzerFile(data);
        }
    }

    protected void processCustomFuzzerFile(FuzzingData data) {
        try {
            Map<String, Map<String, Object>> customFuzzerDetails = catsUtil.parseYaml(customFuzzerFile);
            Map<String, Object> currentPathValues = customFuzzerDetails.get(data.getPath());
            if (currentPathValues != null) {
                currentPathValues.forEach((key, value) -> this.executeTestCases(data, key, value));
            } else {
                LOGGER.info("Skipping path [{}] as it was not configured in customFuzzerFile", data.getPath());
            }
        } catch (Exception e) {
            LOGGER.error("Error processing customFuzzerFile!", e);
        }
    }

    private void executeTestCases(FuzzingData data, String key, Object value) {
        LOGGER.info("Path [{}] has the following custom data [{}]", data.getPath(), value);

        if (this.entryIsValid((Map<String, Object>) value)) {
            List<Map<String, String>> individualTestCases = this.createIndividualRequest((Map<String, Object>) value);
            for (Map<String, String> individualTestCase : individualTestCases) {
                testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data, key, individualTestCase));
            }
        } else {
            LOGGER.warn("Skipping path [{}] as not valid. It either doesn't contain a valid expectedResponseCode or there is more than one list of values for a specific field", data.getPath());
        }
    }

    private void process(FuzzingData data, String testName, Map<String, String> currentPathValues) {
        testCaseListener.addScenario(LOGGER, "Scenario: send request with custom values supplied. Test key [{}]", testName);
        String expectedResponseCode = String.valueOf(currentPathValues.get(EXPECTED_RESPONSE_CODE));
        testCaseListener.addExpectedResult(LOGGER, "Expected result: should return [{}]", expectedResponseCode);

        String payloadWithCustomValuesReplaced = this.getStringWithCustomValuesFromFile(data, currentPathValues);
        CatsResponse response = serviceCaller.call(data.getMethod(), ServiceData.builder().relativePath(data.getPath()).replaceRefData(false)
                .headers(data.getHeaders()).payload(payloadWithCustomValuesReplaced).build());
        testCaseListener.reportResult(LOGGER, data, response, ResponseCodeFamily.from(expectedResponseCode));
    }

    private String getStringWithCustomValuesFromFile(FuzzingData data, Map<String, String> currentPathValues) {
        JsonElement jsonElement = catsUtil.parseAsJsonElement(data.getPayload());

        if (jsonElement.isJsonObject()) {
            this.replaceFieldsWithCustomValue(currentPathValues, jsonElement);
        } else if (jsonElement.isJsonArray()) {
            for (JsonElement element : jsonElement.getAsJsonArray()) {
                replaceFieldsWithCustomValue(currentPathValues, element);
            }
        }
        LOGGER.info("Final payload after reference data replacement [{}]", jsonElement);

        return jsonElement.toString();
    }

    private void replaceFieldsWithCustomValue(Map<String, String> currentPathValues, JsonElement jsonElement) {
        for (Map.Entry<String, String> entry : currentPathValues.entrySet()) {
            /*If we didn't fuzz a Ref Data field, we replace the value with the ref data*/
            this.replaceElementWithCustomValue(entry, jsonElement);
        }
    }


    private void replaceElementWithCustomValue(Map.Entry<String, String> entry, JsonElement jsonElement) {
        JsonElement element = catsUtil.getJsonElementBasedOnFullyQualifiedName(jsonElement, entry.getKey());
        String[] depth = entry.getKey().split("#");

        if (element != null) {
            String key = depth[depth.length - 1];
            String fuzzedValue = String.valueOf(entry.getValue());

            if (fuzzedValue != null && element.getAsJsonObject().remove(key) != null) {
                element.getAsJsonObject().addProperty(key, fuzzedValue);
                LOGGER.info("Replacing property [{}] with value [{}]", entry.getKey(), fuzzedValue);
            }
        }
    }

    /**
     * Custom tests can contain multiple values for a specific field. We iterate through those values and create a list of individual requests
     *
     * @param testCase
     * @return
     */
    private List<Map<String, String>> createIndividualRequest(Map<String, Object> testCase) {
        Optional<Map.Entry<String, Object>> listOfValuesOptional = testCase.entrySet().stream().filter(entry -> entry.getValue() instanceof List).findFirst();
        List<Map<String, String>> allValues = new ArrayList<>();

        if (listOfValuesOptional.isPresent()) {
            Map.Entry<String, Object> listOfValues = listOfValuesOptional.get();
            for (Object value : (List) listOfValues.getValue()) {
                testCase.put(listOfValues.getKey(), value);
                allValues.add(testCase.entrySet()
                        .stream().collect(Collectors.toMap(Map.Entry::getKey, en -> String.valueOf(en.getValue()))));
            }
            return allValues;
        }

        return Collections.singletonList(testCase.entrySet()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, en -> String.valueOf(en.getValue()))));
    }

    private boolean entryIsValid(Map<String, Object> currentPathValues) {
        boolean responseCodeValid = ResponseCodeFamily.isValidCode(String.valueOf(currentPathValues.get(EXPECTED_RESPONSE_CODE)));
        boolean hasAtMostOneArrayOfData = currentPathValues.entrySet().stream().filter(entry -> entry.getValue() instanceof ArrayList).count() <= 1;

        return responseCodeValid && hasAtMostOneArrayOfData;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "allows to configure user supplied values for specific fields withing payloads; this is useful when testing scenarios where the the user want to test a predefined list of blacklisted strings";
    }
}
