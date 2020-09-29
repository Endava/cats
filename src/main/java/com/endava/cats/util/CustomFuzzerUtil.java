package com.endava.cats.util;

import com.endava.cats.fuzzer.fields.CustomFuzzerBase;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.google.gson.JsonElement;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CustomFuzzerUtil {
    public static final String DESCRIPTION = "description";
    public static final String HTTP_METHOD = "httpMethod";
    public static final String EXPECTED_RESPONSE_CODE = "expectedResponseCode";
    public static final String OUTPUT = "output";
    public static final String VERIFY = "verify";
    public static final String STRINGS_FILE = "stringsFile";
    public static final String TARGET_FIELDS = "targetFields";

    private static final List<String> RESERVED_WORDS = Arrays.asList(DESCRIPTION, HTTP_METHOD, EXPECTED_RESPONSE_CODE, OUTPUT, VERIFY, STRINGS_FILE, TARGET_FIELDS);
    private static final String NOT_SET = "NOT_SET";
    private static final String NOT_MATCHING_ERROR = "Parameter [%s] with value [%s] not matching [%s]. ";
    private final Map<String, String> variables = new HashMap<>();
    private final CatsUtil catsUtil;
    private final TestCaseListener testCaseListener;
    private final ServiceCaller serviceCaller;

    @Autowired
    public CustomFuzzerUtil(ServiceCaller sc, CatsUtil cu, TestCaseListener tcl) {
        this.serviceCaller = sc;
        catsUtil = cu;
        testCaseListener = tcl;
    }

    public void process(FuzzingData data, String testName, Map<String, String> currentPathValues) {
        String expectedResponseCode = String.valueOf(currentPathValues.get(EXPECTED_RESPONSE_CODE));
        this.startCustomTest(testName, currentPathValues, expectedResponseCode);

        String payloadWithCustomValuesReplaced = this.getStringWithCustomValuesFromFile(data, currentPathValues);
        String servicePath = this.replacePathVariablesWithCustomValues(data, currentPathValues);
        CatsResponse response = serviceCaller.call(data.getMethod(), ServiceData.builder().relativePath(servicePath).replaceRefData(false)
                .headers(data.getHeaders()).payload(payloadWithCustomValuesReplaced).queryParams(data.getQueryParams()).build());

        this.setOutputVariables(currentPathValues, response);

        String verify = currentPathValues.get(CustomFuzzerUtil.VERIFY);
        if (verify != null) {
            this.checkVerifies(response, verify, expectedResponseCode);
        } else {
            testCaseListener.reportResult(log, data, response, ResponseCodeFamily.from(expectedResponseCode));
        }
    }


    private void setOutputVariables(Map<String, String> currentPathValues, CatsResponse response) {
        String output = currentPathValues.get(CustomFuzzerUtil.OUTPUT);

        if (output != null) {
            Map<String, String> variablesFromYaml = this.parseYmlEntryIntoMap(output);
            this.variables.putAll(variablesFromYaml);
            this.variables.putAll(matchVariablesWithTheResponse(response, variablesFromYaml, Map.Entry::getValue));
            log.info("The following OUTPUT variables were identified {}", variables);
        }
    }

    private void checkVerifies(CatsResponse response, String verify, String expectedResponseCode) {
        Map<String, String> verifies = this.parseYmlEntryIntoMap(verify);
        Map<String, String> responseValues = this.matchVariablesWithTheResponse(response, verifies, Map.Entry::getKey);
        log.info("Parameters to verify: {}", verifies);
        log.info("Parameters matched to response: {}", responseValues);
        if (responseValues.entrySet().stream().anyMatch(entry -> entry.getValue().equalsIgnoreCase(NOT_SET))) {
            log.error("There are Verify parameters which were not present in the response!");

            testCaseListener.reportError(log, "The following Verify parameters were not present in the response: {}",
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
                testCaseListener.reportInfo(log, "Response matches all 'verify' parameters");
            } else if (errorMessages.length() == 0) {
                testCaseListener.reportWarn(log,
                        "Response matches all 'verify' parameters, but response code doesn't match expected response code: expected [{}], actual [{}]", expectedResponseCode, response.responseCodeAsString());
            } else {
                testCaseListener.reportError(log, errorMessages.toString());
            }
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
            log.error("Arrays are not supported for Output variables!");
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
            log.error("Expected variable {} was not found on response. Setting to NOT_SET", value);
            return NOT_SET;
        }
        if (outputVariable.isJsonArray()) {
            log.error("Arrays are not supported. Variable {} will be set to NOT_SET", value);
            return NOT_SET;
        }
        String[] depth = value.split("#");
        return outputVariable.getAsJsonObject().get(depth[depth.length - 1]).getAsString();
    }

    public String getTestScenario(String testName, Map<String, String> currentPathValues) {
        String description = currentPathValues.get(DESCRIPTION);
        if (StringUtils.isNotBlank(description)) {
            return description;
        }

        return "send request with custom values supplied. Test key [" + testName + "]";
    }


    public void startCustomTest(String testName, Map<String, String> currentPathValues, String expectedResponseCode) {
        String testScenario = this.getTestScenario(testName, currentPathValues);
        testCaseListener.addScenario(log, "Scenario: {}", testScenario);
        testCaseListener.addExpectedResult(log, "Expected result: should return [{}]", expectedResponseCode);
    }

    public String getStringWithCustomValuesFromFile(FuzzingData data, Map<String, String> currentPathValues) {
        JsonElement jsonElement = catsUtil.parseAsJsonElement(data.getPayload());

        if (jsonElement.isJsonObject()) {
            this.replaceFieldsWithCustomValue(currentPathValues, jsonElement);
        } else if (jsonElement.isJsonArray()) {
            for (JsonElement element : jsonElement.getAsJsonArray()) {
                replaceFieldsWithCustomValue(currentPathValues, element);
            }
        }
        log.info("Final payload after custom values replaced: [{}]", jsonElement);

        return jsonElement.toString();
    }

    public void executeTestCases(FuzzingData data, String key, Object value, CustomFuzzerBase fuzzer) {
        log.info("Path [{}] has the following custom data [{}]", data.getPath(), value);

        if (this.entryIsValid((Map<String, Object>) value) && isHttpMethodMatchingCustomTest((Map<String, Object>) value, data.getMethod())) {
            List<Map<String, String>> individualTestCases = this.createIndividualRequest((Map<String, Object>) value);
            for (Map<String, String> testCase : individualTestCases) {
                testCaseListener.createAndExecuteTest(log, fuzzer, () -> this.process(data, key, testCase));
            }
        } else if (!isHttpMethodMatchingCustomTest((Map<String, Object>) value, data.getMethod())) {
            log.warn("Skipping path [{}] as HTTP method [{}] does not match custom test", data.getPath(), data.getMethod());
        } else {
            log.warn("Skipping path [{}] as missing [{}] specific fields. List of reserved words: [{}]",
                    data.getPath(), fuzzer.getClass().getSimpleName(), fuzzer.reservedWords());
        }
    }

    private boolean isHttpMethodMatchingCustomTest(Map<String, Object> currentPathValues, HttpMethod httpMethod) {
        Optional<HttpMethod> httpMethodFromYaml = HttpMethod.fromString(String.valueOf(currentPathValues.get(CustomFuzzerUtil.HTTP_METHOD)));

        return !httpMethodFromYaml.isPresent() || httpMethodFromYaml.get().equals(httpMethod);
    }


    private boolean entryIsValid(Map<String, Object> currentPathValues) {
        boolean responseCodeValid = ResponseCodeFamily.isValidCode(String.valueOf(currentPathValues.get(EXPECTED_RESPONSE_CODE)));
        boolean hasAtMostOneArrayOfData = currentPathValues.entrySet().stream().filter(entry -> entry.getValue() instanceof ArrayList).count() <= 1;

        return responseCodeValid && hasAtMostOneArrayOfData;
    }

    /**
     * Custom tests can contain multiple values for a specific field. We iterate through those values and create a list of individual requests
     *
     * @param testCase object from the custom fuzzer file
     * @return individual requests
     */
    public List<Map<String, String>> createIndividualRequest(Map<String, Object> testCase) {
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

    public String replacePathVariablesWithCustomValues(FuzzingData data, Map<String, String> currentPathValues) {
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

    public void replaceFieldsWithCustomValue(Map<String, String> currentPathValues, JsonElement jsonElement) {
        for (Map.Entry<String, String> entry : currentPathValues.entrySet()) {
            if (this.isNotAReservedWord(entry.getKey())) {
                this.replaceElementWithCustomValue(entry, jsonElement);
            }
        }
    }

    private boolean isNotAReservedWord(String key) {
        return !RESERVED_WORDS.contains(key);
    }

    private void replaceElementWithCustomValue(Map.Entry<String, String> entry, JsonElement jsonElement) {
        JsonElement element = catsUtil.getJsonElementBasedOnFullyQualifiedName(jsonElement, entry.getKey());
        String[] depth = entry.getKey().split("#");

        if (element != null) {
            String key = depth[depth.length - 1];
            String propertyValue = this.getPropertyValueToReplaceInBody(entry);

            if (element.getAsJsonObject().remove(key) != null) {
                element.getAsJsonObject().addProperty(key, propertyValue);
                log.info("Replacing property [{}] with value [{}]", entry.getKey(), propertyValue);
            } else {
                log.warn("Property [{}] does not exist", entry.getKey());
            }
        }
    }

    public String getPropertyValueToReplaceInBody(Map.Entry<String, String> entry) {
        String propertyValue = String.valueOf(entry.getValue());

        if (propertyValue.startsWith("${") && propertyValue.endsWith("}")) {
            String variableValue = variables.get(propertyValue.replace("${", "").replace("}", ""));

            if (variableValue == null) {
                log.error("Supplied variable was not found [{}]", propertyValue);
            } else {
                log.info("Variable [{}] found. Will be replaced with [{}]", propertyValue, variableValue);
                propertyValue = variableValue;
            }
        }
        return propertyValue;
    }
}
