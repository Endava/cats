package com.endava.cats.fuzzer.fields;

import com.endava.cats.CatsMain;
import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CustomFuzzerExecution;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.google.gson.JsonElement;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class CustomFuzzer implements Fuzzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomFuzzer.class);
    private static final String HTTP_METHOD = "httpMethod";
    private static final String EXPECTED_RESPONSE_CODE = "expectedResponseCode";
    private static final String OUTPUT = "output";
    private static final String VERIFY = "verify";
    private static final String DESCRIPTION = "description";
    private static final String NOT_SET = "NOT_SET";
    private static final String NOT_MATCHING_ERROR = "Parameter [%s] with value [%s] not matching [%s]. ";

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final CatsUtil catsUtil;
    private final Map<String, String> variables = new HashMap<>();

    @Value("${customFuzzerFile:empty}")
    private String customFuzzerFile;

    private Map<String, Map<String, Object>> customFuzzerDetails = new HashMap<>();
    private List<CustomFuzzerExecution> executions = new ArrayList<>();

    @Autowired
    public CustomFuzzer(ServiceCaller sc, TestCaseListener lr, CatsUtil cu) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
        this.catsUtil = cu;
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

    private Map<String, String> parseYmlEntryIntoMap(String output) {
        Map<String, String> result = new HashMap<>();
        if (StringUtils.isNotBlank(output)) {
            output = output.replace("{", "").replace("}", "");
            result.putAll(Arrays.stream(output.split(","))
                    .map(variable -> variable.trim().split("=")).collect(Collectors.toMap(
                            variableArray -> variableArray[0],
                            variableArray -> variableArray[1]
                    )));
        }

        return result;
    }

    private Map<String, String> matchVariablesWithTheResponse(CatsResponse response, Map<String, String> variablesMap, Function<Map.Entry<String, String>, String> mappingFunction) {
        Map<String, String> result = new HashMap<>();

        JsonElement body = response.getJsonBody();
        if (body.isJsonArray()) {
            LOGGER.error("Arrays are not supported for Output variables!");
        } else {
            result.putAll(variablesMap.entrySet().stream().collect(
                    Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> this.getOutputVariable(body, mappingFunction.apply(entry)))
            ));
        }

        return result;
    }

    private String getOutputVariable(JsonElement body, String value) {
        JsonElement outputVariable = catsUtil.getJsonElementBasedOnFullyQualifiedName(body, value);

        if (outputVariable == null || outputVariable.isJsonNull()) {
            LOGGER.error("Expected variable {} was not found on response. Setting to NOT_SET", value);
            return NOT_SET;
        }
        if (outputVariable.isJsonArray()) {
            LOGGER.error("Arrays are not supported. Variable {} will be set to NOT_SET", value);
            return NOT_SET;
        }
        String[] depth = value.split("#");
        return outputVariable.getAsJsonObject().get(depth[depth.length - 1]).getAsString();
    }


    public void fuzz(FuzzingData data) {
        if (!customFuzzerDetails.isEmpty()) {
            this.processCustomFuzzerFile(data);
        }
    }

    protected void processCustomFuzzerFile(FuzzingData data) {
        try {
            Map<String, Object> currentPathValues = customFuzzerDetails.get(data.getPath());
            if (currentPathValues != null) {
                currentPathValues.forEach((key, value) -> executions.add(CustomFuzzerExecution.builder()
                        .fuzzingData(data).testId(key).testEntry(value).build()));
            } else {
                LOGGER.info("Skipping path [{}] as it was not configured in customFuzzerFile", data.getPath());
            }
        } catch (Exception e) {
            LOGGER.error("Error processing customFuzzerFile!", e);
        }
    }

    public void executeCustomFuzzerTests() {
        MDC.put("fuzzer", this.getClass().getSimpleName());
        for (Map.Entry<String, Map<String, Object>> entry : customFuzzerDetails.entrySet()) {
            executions.stream().filter(customFuzzerExecution -> customFuzzerExecution.getFuzzingData().getPath().equalsIgnoreCase(entry.getKey()))
                    .forEach(customFuzzerExecution -> this.executeTestCases(customFuzzerExecution.getFuzzingData(), customFuzzerExecution.getTestId(),
                            customFuzzerExecution.getTestEntry()));
        }
        MDC.put("fuzzer", "");
    }

    private void executeTestCases(FuzzingData data, String key, Object value) {
        LOGGER.info("Path [{}] has the following custom data [{}]", data.getPath(), value);

        if (this.entryIsValid((Map<String, Object>) value) && isHttpMethodMatchingCustomTest((Map<String, Object>) value, data.getMethod())) {
            List<Map<String, String>> individualTestCases = this.createIndividualRequest((Map<String, Object>) value);
            for (Map<String, String> individualTestCase : individualTestCases) {
                testCaseListener.createAndExecuteTest(LOGGER, this, () -> process(data, key, individualTestCase));
            }
        } else if (!isHttpMethodMatchingCustomTest((Map<String, Object>) value, data.getMethod())) {
            LOGGER.warn("Skipping path [{}] as HTTP method [{}] does not match custom test", data.getPath(), data.getMethod());
        } else {
            LOGGER.warn("Skipping path [{}] as not valid. It either doesn't contain a valid expectedResponseCode or there is more than one list of values for a specific field", data.getPath());
        }
    }

    private boolean isHttpMethodMatchingCustomTest(Map<String, Object> currentPathValues, HttpMethod httpMethod) {
        Optional<HttpMethod> httpMethodFromYaml = HttpMethod.fromString(String.valueOf(currentPathValues.get(HTTP_METHOD)));

        return !httpMethodFromYaml.isPresent() || httpMethodFromYaml.get().equals(httpMethod);
    }

    private void process(FuzzingData data, String testName, Map<String, String> currentPathValues) {
        String testScenario = this.getTestScenario(testName, currentPathValues);
        testCaseListener.addScenario(LOGGER, "Scenario: {}", testScenario);
        String expectedResponseCode = String.valueOf(currentPathValues.get(EXPECTED_RESPONSE_CODE));
        testCaseListener.addExpectedResult(LOGGER, "Expected result: should return [{}]", expectedResponseCode);

        String payloadWithCustomValuesReplaced = this.getStringWithCustomValuesFromFile(data, currentPathValues);
        String servicePath = this.replacePathVariablesWithCustomValues(data, currentPathValues);
        CatsResponse response = serviceCaller.call(data.getMethod(), ServiceData.builder().relativePath(servicePath).replaceRefData(false)
                .headers(data.getHeaders()).payload(payloadWithCustomValuesReplaced).queryParams(data.getQueryParams()).build());

        this.setOutputVariables(currentPathValues, response);

        String verify = currentPathValues.get(VERIFY);
        if (verify != null) {
            this.checkVerifies(response, verify, expectedResponseCode);
        } else {
            testCaseListener.reportResult(LOGGER, data, response, ResponseCodeFamily.from(expectedResponseCode));
        }
    }

    private String replacePathVariablesWithCustomValues(FuzzingData data, Map<String, String> currentPathValues) {
        String newPath = data.getPath();
        for (Map.Entry<String, String> entry : currentPathValues.entrySet()) {
            String valueToReplaceWith = entry.getValue();
            if (entry.getValue().startsWith("${") && entry.getValue().endsWith("}")) {
                valueToReplaceWith = variables.get(entry.getValue().replace("${", "").replace("}", ""));
            }
            newPath = newPath.replace("{" + entry.getKey() + "}", valueToReplaceWith);
        }
        return newPath;
    }

    private void checkVerifies(CatsResponse response, String verify, String expectedResponseCode) {
        Map<String, String> verifies = this.parseYmlEntryIntoMap(verify);
        Map<String, String> responseValues = this.matchVariablesWithTheResponse(response, verifies, Map.Entry::getKey);
        LOGGER.info("Parameters to verify: {}", verifies);
        LOGGER.info("Parameters matched to response: {}", responseValues);
        if (responseValues.entrySet().stream().anyMatch(entry -> entry.getValue().equalsIgnoreCase(NOT_SET))) {
            LOGGER.error("There are Verify parameters which were not present in the response!");

            testCaseListener.reportError(LOGGER, "The following Verify parameters were not present in the response: {}",
                    responseValues.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(NOT_SET))
                            .map(Map.Entry::getKey).collect(Collectors.toList()));
        } else {
            StringBuilder errorMessages = new StringBuilder();

            verifies.forEach((key, value) -> {
                String valueToCheck = responseValues.get(key);
                Matcher verifyMatcher = Pattern.compile(value).matcher(valueToCheck);
                if (!verifyMatcher.matches()) {
                    errorMessages.append(String.format(NOT_MATCHING_ERROR, key, valueToCheck, value));
                }

            });

            if (errorMessages.length() == 0 && expectedResponseCode.equalsIgnoreCase(response.responseCodeAsString())) {
                testCaseListener.reportInfo(LOGGER, "Response matches all 'verify' parameters");
            } else if (errorMessages.length() == 0) {
                testCaseListener.reportWarn(LOGGER,
                        "Response matches all 'verify' parameters, but response code doesn't match expected response code: expected [{}], actual [{}]", expectedResponseCode, response.responseCodeAsString());
            } else {
                testCaseListener.reportError(LOGGER, errorMessages.toString());
            }
        }
    }

    private String getTestScenario(String testName, Map<String, String> currentPathValues) {
        String description = currentPathValues.get(DESCRIPTION);
        if (StringUtils.isNotBlank(description)) {
            return description;
        }

        return "send request with custom values supplied. Test key [" + testName + "]";
    }

    private void setOutputVariables(Map<String, String> currentPathValues, CatsResponse response) {
        String output = currentPathValues.get(OUTPUT);

        if (output != null) {
            Map<String, String> variablesFromYaml = this.parseYmlEntryIntoMap(output);
            this.variables.putAll(variablesFromYaml);
            this.variables.putAll(matchVariablesWithTheResponse(response, variablesFromYaml, Map.Entry::getValue));
            LOGGER.info("The following OUTPUT variables were identified {}", variables);
        }
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
        LOGGER.info("Final payload after custom values replaced: [{}]", jsonElement);

        return jsonElement.toString();
    }

    private void replaceFieldsWithCustomValue(Map<String, String> currentPathValues, JsonElement jsonElement) {
        for (Map.Entry<String, String> entry : currentPathValues.entrySet()) {
            if (this.isNotAReservedWord(entry.getKey())) {
                this.replaceElementWithCustomValue(entry, jsonElement);
            }
        }
    }

    private boolean isNotAReservedWord(String key) {
        return !key.equalsIgnoreCase(OUTPUT) && !key.equalsIgnoreCase(DESCRIPTION) &&
                !key.equalsIgnoreCase(EXPECTED_RESPONSE_CODE) && !key.equalsIgnoreCase(VERIFY)
                && !key.equalsIgnoreCase(HTTP_METHOD);
    }


    private void replaceElementWithCustomValue(Map.Entry<String, String> entry, JsonElement jsonElement) {
        JsonElement element = catsUtil.getJsonElementBasedOnFullyQualifiedName(jsonElement, entry.getKey());
        String[] depth = entry.getKey().split("#");

        if (element != null) {
            String key = depth[depth.length - 1];
            String propertyValue = this.getPropertyValueToReplaceInBody(entry);

            if (element.getAsJsonObject().remove(key) != null) {
                element.getAsJsonObject().addProperty(key, propertyValue);
                LOGGER.info("Replacing property [{}] with value [{}]", entry.getKey(), propertyValue);
            } else {
                LOGGER.warn("Property [{}] does not exist", entry.getKey());
            }
        }
    }

    private String getPropertyValueToReplaceInBody(Map.Entry<String, String> entry) {
        String propertyValue = String.valueOf(entry.getValue());

        if (propertyValue.startsWith("${") && propertyValue.endsWith("}")) {
            String variableValue = variables.get(propertyValue.replace("${", "").replace("}", ""));

            if (variableValue == null) {
                LOGGER.error("Supplied variable was not found [{}]", propertyValue);
            } else {
                LOGGER.info("Variable [{}] found. Will be replaced with [{}]", propertyValue, variableValue);
                propertyValue = variableValue;
            }
        }
        return propertyValue;
    }

    /**
     * Custom tests can contain multiple values for a specific field. We iterate through those values and create a list of individual requests
     *
     * @param testCase object from the custom fuzzer file
     * @return individual requests
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
