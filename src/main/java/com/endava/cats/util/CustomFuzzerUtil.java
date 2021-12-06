package com.endava.cats.util;

import com.endava.cats.fuzzer.fields.base.CustomFuzzerBase;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.apache.commons.lang3.StringUtils;

import javax.enterprise.context.ApplicationScoped;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class CustomFuzzerUtil {
    public static final String DESCRIPTION = "description";
    public static final String HTTP_METHOD = "httpMethod";
    public static final String EXPECTED_RESPONSE_CODE = "expectedResponseCode";
    public static final String OUTPUT = "output";
    public static final String VERIFY = "verify";
    public static final String STRINGS_FILE = "stringsFile";
    public static final String TARGET_FIELDS = "targetFields";
    public static final String ONE_OF_SELECTION = "oneOfSelection";
    public static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    public static final String ELEMENT = "topElement";
    public static final String MAP_VALUES = "mapValues";
    private static final List<String> RESERVED_WORDS = Arrays.asList(DESCRIPTION, HTTP_METHOD, EXPECTED_RESPONSE_CODE, OUTPUT, VERIFY, STRINGS_FILE, TARGET_FIELDS, ONE_OF_SELECTION,
            ADDITIONAL_PROPERTIES, ELEMENT, MAP_VALUES);
    private static final String NOT_SET = "NOT_SET";
    private static final String NOT_MATCHING_ERROR = "Parameter [%s] with value [%s] not matching [%s]. ";
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(CustomFuzzerUtil.class);
    private final Map<String, String> variables = new HashMap<>();
    private final CatsUtil catsUtil;
    private final TestCaseListener testCaseListener;
    private final ServiceCaller serviceCaller;
    private final CatsDSLParser catsDSLParser;

    public CustomFuzzerUtil(ServiceCaller sc, CatsUtil cu, TestCaseListener tcl, CatsDSLParser cdsl) {
        this.serviceCaller = sc;
        catsUtil = cu;
        testCaseListener = tcl;
        this.catsDSLParser = cdsl;
    }


    public void process(FuzzingData data, String testName, Map<String, String> currentPathValues) {
        String expectedResponseCode = currentPathValues.get(EXPECTED_RESPONSE_CODE);
        this.startCustomTest(testName, currentPathValues, expectedResponseCode);

        String payloadWithCustomValuesReplaced = this.getJsonWithCustomValuesFromFile(data, currentPathValues);
        catsUtil.setAdditionalPropertiesToPayload(currentPathValues, payloadWithCustomValuesReplaced);

        String servicePath = this.replacePathVariablesWithCustomValues(data, currentPathValues);
        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(servicePath).replaceRefData(false).httpMethod(data.getMethod())
                .headers(data.getHeaders()).payload(payloadWithCustomValuesReplaced).queryParams(data.getQueryParams()).build());

        this.setOutputVariables(currentPathValues, response);

        String verify = currentPathValues.get(CustomFuzzerUtil.VERIFY);
        if (verify != null) {
            this.checkVerifiesAndReport(payloadWithCustomValuesReplaced, response, verify, expectedResponseCode);
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

    private void checkVerifiesAndReport(String request, CatsResponse response, String verify, String expectedResponseCode) {
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
                value = catsDSLParser.parseAndGetResult(value, response.getBody());

                /*this is a variable*/
                if (value.startsWith("$request")) {
                    value = String.valueOf(this.getVariableFromJson(request, value.replace("request", "").substring(2)));
                } else if (value.startsWith("$")) {
                    value = variables.get(value.substring(1));
                }

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
                            variableArray -> variableArray[1])));
        }

        return result;
    }

    private Map<String, String> matchVariablesWithTheResponse(CatsResponse response, Map<String, String> variablesMap, Function<Map.Entry<String, String>, String> mappingFunction) {
        Map<String, String> result = new HashMap<>();

        result.putAll(variablesMap.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.valueOf(this.getVariableFromJson(response.getBody(), mappingFunction.apply(entry))))
        ));

        return result;
    }

    private Object getVariableFromJson(String body, String value) {
        DocumentContext jsonDoc = JsonPath.parse(body);
        try {
            return jsonDoc.read(catsUtil.sanitizeToJsonPath(value));
        } catch (PathNotFoundException e) {
            log.error("Expected variable {} was not found on response. Setting to NOT_SET", value);
            return NOT_SET;
        }
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
        testCaseListener.addExpectedResult(log, "Should return [{}]", expectedResponseCode);
    }

    public String getJsonWithCustomValuesFromFile(FuzzingData data, Map<String, String> currentPathValues) {
        String payload = data.getPayload();

        for (Map.Entry<String, String> entry : currentPathValues.entrySet()) {
            if (this.isNotAReservedWord(entry.getKey())) {
                payload = this.replaceElementWithCustomValue(entry, payload);
            }
        }

        log.info("Final payload after custom values replaced: [{}]", payload);

        return payload;
    }

    public void executeTestCases(FuzzingData data, String key, Object value, CustomFuzzerBase fuzzer) {
        log.info("Path [{}] for method [{}] has the following custom data [{}]", data.getPath(), data.getMethod(), value);
        boolean isValidOneOf = this.isValidOneOf(data, (Map<String, Object>) value);

        if (this.entryIsValid((Map<String, Object>) value) && isValidOneOf) {
            List<Map<String, String>> individualTestCases = this.createIndividualRequest((Map<String, Object>) value);
            for (Map<String, String> testCase : individualTestCases) {
                testCaseListener.createAndExecuteTest(log, fuzzer, () -> this.process(data, key, testCase));
            }
        } else if (!isValidOneOf) {
            log.skip("Skipping path [{}] with payload [{}] as it does not match oneOfSelection", data.getPath(), data.getPayload());
        } else {
            log.warning("Skipping path [{}] as missing [{}] specific fields. List of reserved words: [{}]",
                    data.getPath(), fuzzer.getClass().getSimpleName(), fuzzer.reservedWords());
        }
    }

    private boolean isValidOneOf(FuzzingData data, Map<String, Object> currentPathValues) {
        String oneOfSelection = String.valueOf(currentPathValues.get(ONE_OF_SELECTION));

        if (!"null".equalsIgnoreCase(oneOfSelection)) {
            return wasOneOfSelectionReplaced(oneOfSelection, data);
        }
        return true;
    }

    public boolean wasOneOfSelectionReplaced(String oneOfSelection, FuzzingData data) {
        String[] oneOfArray = oneOfSelection.replace("{", "").replace("}", "").split("=");

        String updatedJson = this.replaceElementWithCustomValue(new AbstractMap.SimpleEntry<>(oneOfArray[0], oneOfArray[1]), data.getPayload());
        return catsUtil.equalAsJson(data.getPayload(), updatedJson);
    }

    private boolean entryIsValid(Map<String, Object> currentPathValues) {
        boolean responseCodeValid = ResponseCodeFamily.isValidCode(String.valueOf(currentPathValues.get(EXPECTED_RESPONSE_CODE)));
        boolean hasAtMostOneArrayOfData = currentPathValues.entrySet().stream().filter(entry -> entry.getValue() instanceof ArrayList).count() <= 1;
        boolean hasHttpMethod = currentPathValues.get(HTTP_METHOD) != null;

        return responseCodeValid && hasAtMostOneArrayOfData && hasHttpMethod;
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
            for (Object value : (List<?>) listOfValues.getValue()) {
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

    private boolean isNotAReservedWord(String key) {
        return !RESERVED_WORDS.contains(key);
    }

    private String replaceElementWithCustomValue(Map.Entry<String, String> keyValue, String payload) {
        String toReplace = catsDSLParser.parseAndGetResult(this.getPropertyValueToReplaceInBody(keyValue), payload);
        try {
            FuzzingStrategy fuzzingStrategy = FuzzingStrategy.replace().withData(toReplace);
            return catsUtil.replaceField(payload, keyValue.getKey(), fuzzingStrategy).getJson();
        } catch (Exception e) {
            log.warning("Property [{}] does not exist", keyValue.getKey());
            return payload;
        }
    }

    public String getPropertyValueToReplaceInBody(Map.Entry<String, String> keyValue) {
        String propertyValue = keyValue.getValue();

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
