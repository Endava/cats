package com.endava.cats.fuzzer.special;

import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.dsl.api.Parser;
import com.endava.cats.fuzzer.fields.base.CustomFuzzerBase;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.json.JsonUtils;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsDSLWords;
import com.endava.cats.util.CatsUtil;
import com.endava.cats.util.WordUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.endava.cats.json.JsonUtils.NOT_SET;
import static com.endava.cats.util.CatsDSLWords.*;

/**
 * Common methods used by the FunctionalFuzzer and SecurityFuzzer.
 */
@ApplicationScoped
public class CustomFuzzerUtil {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(CustomFuzzerUtil.class);
    @Getter
    private final Map<String, String> variables = new HashMap<>();
    private final Map<String, Map<String, Object>> pathsWithInputVariables = new HashMap<>();
    private final TestCaseListener testCaseListener;
    private final ServiceCaller serviceCaller;

    /**
     * Constructs a new instance of CustomFuzzerUtil with the provided dependencies.
     *
     * <p>This utility class is used for custom fuzzing operations and requires instances of ServiceCaller,
     * CatsUtil, and TestCaseListener to perform its functions. The provided instances of these dependencies
     * are stored internally for use within the CustomFuzzerUtil.</p>
     *
     * @param sc  The ServiceCaller instance responsible for making service calls during custom fuzzing.
     * @param tcl The TestCaseListener instance handling test case events and notifications.
     */
    public CustomFuzzerUtil(ServiceCaller sc, TestCaseListener tcl) {
        this.serviceCaller = sc;
        testCaseListener = tcl;
    }


    /**
     * Processes and executes fuzzing tests based on the provided FuzzingData, test name, and current path values.
     *
     * <p>This method determines the number of iterations for test execution based on headers and performs
     * fuzzing tests accordingly. It considers headers fuzzing if CATS_HEADERS are present in the current path values.
     * The expected response code is retrieved from the current path values for each iteration.</p>
     *
     * @param data              The FuzzingData containing information about the path, method, payload, and headers.
     * @param testName          The name of the test being executed.
     * @param currentPathValues The current path values, including information about headers and expected response code.
     */
    public void process(FuzzingData data, String testName, Map<String, Object> currentPathValues) {
        int howManyTests = this.getNumberOfIterationsBasedOnHeaders(data, currentPathValues);
        boolean isHeadersFuzzing = currentPathValues.get(CATS_HEADERS) != null;
        CatsHeader[] arrayOfHeaders = data.getHeaders().toArray(new CatsHeader[0]);

        for (int i = 0; i < howManyTests; i++) {
            String expectedResponseCode = String.valueOf(currentPathValues.get(EXPECTED_RESPONSE_CODE));
            this.startCustomTest(testName, currentPathValues, expectedResponseCode);

            String payloadWithCustomValuesReplaced = this.getJsonWithCustomValuesFromFile(data, currentPathValues);
            CatsUtil.setAdditionalPropertiesToPayload(currentPathValues, payloadWithCustomValuesReplaced);

            Set<CatsHeader> headers = new HashSet<>(Arrays.asList(arrayOfHeaders));
            if (isHeadersFuzzing) {
                headers = getHeadersWithFuzzing(arrayOfHeaders, currentPathValues, i);
            }

            String servicePath = this.replacePathVariablesWithCustomValues(data, currentPathValues);
            CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(servicePath).replaceRefData(false).httpMethod(data.getMethod())
                    .headers(headers).payload(payloadWithCustomValuesReplaced).queryParams(data.getQueryParams()).contractPath(data.getContractPath())
                    .contentType(data.getFirstRequestContentType()).build());

            this.setOutputVariables(currentPathValues, response, payloadWithCustomValuesReplaced);

            String verify = WordUtils.nullOrValueOf(currentPathValues.get(VERIFY));

            if (verify != null) {
                this.checkVerifiesAndReport(data, payloadWithCustomValuesReplaced, response, verify, expectedResponseCode);
            } else {
                testCaseListener.reportResult(log, data, response, ResponseCodeFamily.from(expectedResponseCode));
            }
        }
    }

    private Set<CatsHeader> getHeadersWithFuzzing(CatsHeader[] existingHeaders, Map<String, Object> currentPathValues, int i) {
        Set<CatsHeader> headers = new HashSet<>(Arrays.asList(existingHeaders));
        if (!headers.isEmpty()) {
            CatsHeader headerToReplace = existingHeaders[i];
            Object toReplaceWith = currentPathValues.get(headerToReplace.getName());
            headerToReplace.withValue(WordUtils.nullOrValueOf(toReplaceWith));
            headers.add(headerToReplace);
        }
        return headers;
    }

    private int getNumberOfIterationsBasedOnHeaders(FuzzingData data, Map<String, Object> currentPathValues) {
        boolean isHeadersFuzzing = currentPathValues.get(CATS_HEADERS) != null;
        if (isHeadersFuzzing) {
            log.note("Fuzzing headers! Total number of headers: {}", data.getHeaders().size());
            return data.getHeaders().size();
        }

        return 1;
    }

    private void setOutputVariables(Map<String, Object> currentPathValues, CatsResponse response, String request) {
        Object output = currentPathValues.get(OUTPUT);

        /* add all variables first; resolve any variables requiring request access, resolve response variables and merge all in the end.*/
        /* we merge request variables at the end, because otherwise the resolved values will try to be searched in response and result in NOT_SET*/
        if (output != null) {
            Map<String, String> variablesFromYaml = this.parseYmlEntryIntoMap(String.valueOf(output));
            this.variables.putAll(variablesFromYaml);
            Map<String, String> requestVariables = matchVariablesFromRequest(request);
            this.variables.putAll(matchVariablesWithTheResponse(response, variablesFromYaml, Map.Entry::getValue));
            this.variables.putAll(requestVariables);

            log.note("The following OUTPUT variables were identified {}", variables);
        }
    }

    private Map<String, String> matchVariablesFromRequest(String request) {
        return variables.entrySet().stream()
                .filter(entry -> entry.getValue().startsWith("$request"))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> CatsDSLParser.parseAndGetResult(entry.getValue(), Map.of(Parser.REQUEST, request))));
    }

    private void checkVerifiesAndReport(FuzzingData data, String request, CatsResponse response, String verify, String expectedResponseCode) {
        Map<String, String> verifies = this.parseYmlEntryIntoMap(verify);
        Map<String, String> responseValues = this.matchVariablesWithTheResponse(response, verifies, Map.Entry::getKey);
        log.debug("Parameters to verify: {}", verifies);
        log.debug("Parameters matched to response: {}", responseValues);
        if (responseValues.entrySet().stream().anyMatch(entry -> entry.getValue().equalsIgnoreCase(NOT_SET))) {
            log.error("Test failed! There are Verify parameters which were not present in the response!");

            testCaseListener.reportResultError(log, data, "Verify parameters not present in response", "The following Verify parameters were not present in the response: {}",
                    responseValues.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(NOT_SET))
                            .map(Map.Entry::getKey).toList());
        } else {
            StringBuilder errorMessages = new StringBuilder();

            verifies.forEach((key, value) -> {
                String valueToCheck = responseValues.get(key);
                String parsedVerifyValue = this.getVerifyValue(request, response, value);

                Matcher verifyMatcher = Pattern.compile(parsedVerifyValue).matcher(valueToCheck);
                if (!verifyMatcher.matches()) {
                    errorMessages.append(String.format("Parameter [%s] with value [%s] not matching [%s]. ", key, valueToCheck, parsedVerifyValue));
                }
            });

            if (errorMessages.isEmpty() && ResponseCodeFamily.matchAsCodeOrRange(expectedResponseCode, response.responseCodeAsString())) {
                testCaseListener.reportResultInfo(log, data, "Response matches all 'verify' parameters");
            } else if (errorMessages.isEmpty()) {
                testCaseListener.reportResultWarn(log, data, "Returned response code not matching expected response code",
                        "Response matches all 'verify' parameters, but response code doesn't match expected response code: expected [{}], actual [{}]", expectedResponseCode, response.responseCodeAsString());
            } else {
                testCaseListener.reportResultError(log, data, "Verify parameters not matching response", errorMessages.toString());
            }
        }
    }

    private String getVerifyValue(String request, CatsResponse response, String value) {
        String verifyValue = CatsDSLParser.parseAndGetResult(value, Map.of(Parser.REQUEST, request, Parser.RESPONSE, response.getBody()));

        /* It means that it's 'just' a CATS variable */
        if (verifyValue.startsWith("$")) {
            return variables.get(verifyValue.substring(1));
        }
        return verifyValue;
    }


    /**
     * This will transform both {@code verify} and CATS variables in a format without {}.
     * A variable like {@code ${request.name}} will become {@code $request.name}
     *
     * @param output the current YAML entry
     * @return a key,value Map with all the sub-entries under the current YAML entry
     */
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

        result.putAll(variablesMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> String.valueOf(JsonUtils.getVariableFromJson(response.getBody(), mappingFunction.apply(entry))))
                ));

        //we make sure that "checkBoolean" is not marked as NOT_SET and set to TRUE so that is matched against the computed expression
        return result.entrySet()
                .stream()
                .map(CustomFuzzerUtil::remapCheckBoolean)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map.Entry<String, String> remapCheckBoolean(Map.Entry<String, String> entry) {
        return CHECKS.entrySet()
                .stream()
                .filter(checkEntry -> entry.getKey().startsWith(checkEntry.getKey()))
                .map(checkEntry -> new AbstractMap.SimpleEntry<>(entry.getKey(), checkEntry.getValue()))
                .findFirst()
                .orElseGet(() -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
    }

    private String getTestScenario(String testName, Map<String, Object> currentPathValues) {
        String description = WordUtils.nullOrValueOf(currentPathValues.get(DESCRIPTION));
        if (StringUtils.isNotBlank(description)) {
            return description;
        }

        return "send request with custom values supplied. Test key [" + testName + "]";
    }


    private void startCustomTest(String testName, Map<String, Object> currentPathValues, String expectedResponseCode) {
        String testScenario = this.getTestScenario(testName, currentPathValues);
        testCaseListener.addScenario(log, "Scenario: {}", testScenario);
        testCaseListener.addExpectedResult(log, "Should return [{}]", expectedResponseCode);
    }

    /**
     * Retrieves JSON with custom values from a file based on the provided FuzzingData and current path values.
     *
     * <p>This method checks if custom body fuzzing data is available in the current path values.
     * If present, it returns the JSON string representing the custom body fuzz data. Otherwise, it performs
     * additional processing or returns a default value based on the specific use case.</p>
     *
     * @param data              The FuzzingData containing information about the path, method, and payload.
     * @param currentPathValues The current path values, which may include custom body fuzzing data.
     * @return The JSON string with custom values from the file, or a default value if not found.
     */
    public String getJsonWithCustomValuesFromFile(FuzzingData data, Map<String, Object> currentPathValues) {
        if (currentPathValues.get(CATS_BODY_FUZZ) != null) {
            return String.valueOf(currentPathValues.get(CATS_BODY_FUZZ));
        }

        String payload = data.getPayload();

        for (Map.Entry<String, Object> entry : currentPathValues.entrySet()) {
            if (this.isCatsRemove(entry)) {
                payload = JsonUtils.deleteNode(payload, entry.getKey());
            } else if (this.isNotAReservedWord(entry.getKey())) {
                payload = this.replaceElementWithCustomValue(entry, payload);
            }
        }

        log.debug("Final payload after custom values replaced: [{}]", payload);

        return payload;
    }

    private boolean isCatsRemove(Map.Entry<String, Object> keyValue) {
        return ServiceCaller.CATS_REMOVE_FIELD.equalsIgnoreCase(String.valueOf(keyValue.getValue()));
    }

    /**
     * Executes test cases based on provided FuzzingData, key, value, and a custom fuzzer.
     *
     * <p>This method logs information about the path, method, and custom data before processing.
     * It validates the custom data, ensures it is a valid "oneOf" entry, and then populates the
     * input variables map with the processed data. Subsequently, it generates individual test cases
     * and performs further processing for each case.</p>
     *
     * @param data   The FuzzingData containing information about the path, method, and payload.
     * @param key    The key associated with the custom data in the FuzzingData.
     * @param value  The custom data associated with the provided key.
     * @param fuzzer The custom fuzzer used for generating individual test cases.
     */
    public void executeTestCases(FuzzingData data, String key, Object value, CustomFuzzerBase fuzzer) {
        log.debug("Path [{}] for method [{}] has the following custom data [{}]", data.getPath(), data.getMethod(), value);
        boolean isValidOneOf = this.isValidOneOf(data, (Map<String, Object>) value);

        if (this.entryIsValid((Map<String, Object>) value) && isValidOneOf) {
            this.pathsWithInputVariables.put(data.getPath(), (Map<String, Object>) value);
            List<Map<String, Object>> individualTestCases = this.createIndividualRequest((Map<String, Object>) value, data.getPayload());
            for (Map<String, Object> testCase : individualTestCases) {
                testCaseListener.createAndExecuteTest(log, fuzzer, () -> this.process(data, key, testCase));
            }
        } else if (!isValidOneOf) {
            log.skip("Skipping path [{}] as it does not match oneOfSelection", data.getPath());
            log.debug("Payload: {}", data.getPayload());
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

    private boolean wasOneOfSelectionReplaced(String oneOfSelection, FuzzingData data) {
        String[] oneOfArray = oneOfSelection.replace("{", "").replace("}", "").split("=", -1);

        String updatedJson = this.replaceElementWithCustomValue(new AbstractMap.SimpleEntry<>(oneOfArray[0], oneOfArray[1]), data.getPayload());
        return JsonUtils.equalAsJson(data.getPayload(), updatedJson);
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
    private List<Map<String, Object>> createIndividualRequest(Map<String, Object> testCase, String payload) {
        Optional<Map.Entry<String, Object>> listOfValuesOptional = testCase.entrySet().stream().filter(entry -> entry.getValue() instanceof List).findFirst();
        List<Map<String, Object>> allValues = new ArrayList<>();

        if (listOfValuesOptional.isPresent()) {
            Map.Entry<String, Object> listOfValues = listOfValuesOptional.get();
            if (!(JsonUtils.getVariableFromJson(payload, listOfValues.getKey()) instanceof JSONArray)) {
                for (Object value : (List<?>) listOfValues.getValue()) {
                    testCase.put(listOfValues.getKey(), value);
                    allValues.add(testCase.entrySet()
                            .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                }
                return List.copyOf(allValues);
            }
        }

        return Collections.singletonList(testCase.entrySet()
                .stream().collect(HashMap::new, (m, v) -> m.put(v.getKey(), v.getValue()), HashMap::putAll));
    }

    private String replacePathVariablesWithCustomValues(FuzzingData data, Map<String, Object> currentPathValues) {
        String newPath = data.getPath();
        if (HttpMethod.requiresBody(data.getMethod())) {
            for (Map.Entry<String, Object> entry : currentPathValues.entrySet()) {
                String valueToReplaceWith = String.valueOf(entry.getValue());
                if (this.isVariable(valueToReplaceWith)) {
                    valueToReplaceWith = variables.getOrDefault(this.getVariableName(valueToReplaceWith), NOT_SET);
                }
                newPath = newPath.replace("{" + entry.getKey() + "}", valueToReplaceWith);
            }
        }
        return newPath;
    }

    private boolean isNotAReservedWord(String key) {
        return !RESERVED_WORDS.contains(key);
    }

    private String replaceElementWithCustomValue(Map.Entry<String, Object> keyValue, String payload) {
        Map<String, String> contextForParser = new HashMap<>();
        contextForParser.put(Parser.REQUEST, payload);
        contextForParser.putAll(variables);

        Object toReplace = this.getPropertyValueToReplaceInBody(keyValue);
        if (toReplace instanceof String str) {
            toReplace = CatsDSLParser.parseAndGetResult(str, contextForParser);
        }
        try {
            FuzzingStrategy fuzzingStrategy = FuzzingStrategy.replace().withData(toReplace);
            return CatsUtil.replaceField(payload, keyValue.getKey(), fuzzingStrategy).json();
        } catch (Exception e) {
            log.debug("Something went wrong while parsing!", e);
            log.warning("Property [{}] does not exist", keyValue.getKey());
            return payload;
        }
    }

    private Object getPropertyValueToReplaceInBody(Map.Entry<String, Object> keyValue) {
        Object propertyValue = keyValue.getValue();

        if (this.isVariable(String.valueOf(propertyValue))) {
            String variableValue = variables.get(this.getVariableName(String.valueOf(propertyValue)));

            if (variableValue == null) {
                log.error("Supplied variable was not found [{}]", propertyValue);
            } else {
                log.note("Variable [{}] found. Will be replaced with [{}]", propertyValue, variableValue);
                propertyValue = CatsUtil.getAsAppropriateType(variableValue);
            }
        }
        return propertyValue;
    }

    /**
     * Extracts the variable name from a Cats variable string.
     *
     * <p>The Cats variable string is expected to be in the format "${variableName}". This method
     * removes the "${" prefix and "}" suffix to retrieve the actual variable name.</p>
     *
     * @param catsVariable The Cats variable string to extract the variable name from.
     * @return The variable name extracted from the Cats variable string.
     */
    public String getVariableName(String catsVariable) {
        return catsVariable.replace("${", "").replace("}", "");
    }

    /**
     * Checks if the given string is a variable by examining its format.
     *
     * <p>A variable is considered valid if it starts with "${" and ends with "}".</p>
     *
     * @param candidate The string to be checked for being a variable.
     * @return {@code true} if the string is a valid variable, {@code false} otherwise.
     */
    public boolean isVariable(String candidate) {
        return candidate.startsWith("${") && candidate.endsWith("}");
    }

    /**
     * Writes a reference data file with output variables based on the available input variables.
     *
     * <p>This method processes the input variables associated with different paths, filtering out reserved words
     * and removing non-output variables. It then generates a reference data file with the remaining output variables.</p>
     *
     * <p>The generated reference data file includes a mapping of path names to the corresponding output variables,
     * and it is written to an external file.</p>
     *
     * @throws IOException If an I/O error occurs while writing the reference data file.
     */
    public void writeRefDataFileWithOutputVariables() throws IOException {
        Map<String, Map<String, Object>> possibleVariables = this.pathsWithInputVariables.entrySet().stream()
                .filter(entry -> !RESERVED_WORDS.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        possibleVariables.values().forEach(value -> value.values().removeIf(innerValue -> !String.valueOf(innerValue).startsWith("${")));
        possibleVariables.values().removeIf(Map::isEmpty);

        Map<String, Map<String, Object>> finalPathsAndVariables = possibleVariables.entrySet()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().entrySet()
                        .stream().collect(Collectors.toMap(Map.Entry::getKey, variableEntry ->
                                variables.getOrDefault(this.getVariableName(String.valueOf(variableEntry.getValue())), NOT_SET)))));


        CatsUtil.writeToYaml("refData_custom.yml", finalPathsAndVariables);
        log.complete("Finish writing refData_custom.yml");
    }

    /**
     * Checks if the provided HTTP method matches the one specified in a YAML file.
     *
     * @param currentValues An object representing the current values, expected to be a Map of (String, Object)
     *                      containing information about the YAML file, including the HTTP method.
     * @param httpMethod    The HTTP method to compare against the one specified in the YAML file.
     * @return {@code true} if the YAML file does not specify an HTTP method or if the specified
     * HTTP method matches the provided one, {@code false} otherwise.
     */
    public boolean isMatchingHttpMethod(Object currentValues, HttpMethod httpMethod) {
        Map<String, Object> currentPathValues = (Map<String, Object>) currentValues;
        Optional<HttpMethod> httpMethodFromYaml = HttpMethod.fromString(String.valueOf(currentPathValues.get(CatsDSLWords.HTTP_METHOD)));

        return httpMethodFromYaml.isEmpty() || httpMethodFromYaml.get().equals(httpMethod);
    }
}
