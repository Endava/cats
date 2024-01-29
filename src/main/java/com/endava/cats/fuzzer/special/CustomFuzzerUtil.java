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

@ApplicationScoped
public class CustomFuzzerUtil {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(CustomFuzzerUtil.class);
    @Getter
    private final Map<String, String> variables = new HashMap<>();
    private final Map<String, Map<String, Object>> pathsWithInputVariables = new HashMap<>();
    private final CatsUtil catsUtil;
    private final TestCaseListener testCaseListener;
    private final ServiceCaller serviceCaller;

    public CustomFuzzerUtil(ServiceCaller sc, CatsUtil cu, TestCaseListener tcl) {
        this.serviceCaller = sc;
        catsUtil = cu;
        testCaseListener = tcl;
    }


    public void process(FuzzingData data, String testName, Map<String, Object> currentPathValues) {
        int howManyTests = this.getNumberOfIterationsBasedOnHeaders(data, currentPathValues);
        boolean isHeadersFuzzing = currentPathValues.get(CATS_HEADERS) != null;
        CatsHeader[] arrayOfHeaders = data.getHeaders().toArray(new CatsHeader[0]);

        for (int i = 0; i < howManyTests; i++) {
            String expectedResponseCode = String.valueOf(currentPathValues.get(EXPECTED_RESPONSE_CODE));
            this.startCustomTest(testName, currentPathValues, expectedResponseCode);

            String payloadWithCustomValuesReplaced = this.getJsonWithCustomValuesFromFile(data, currentPathValues);
            catsUtil.setAdditionalPropertiesToPayload(currentPathValues, payloadWithCustomValuesReplaced);
            Set<CatsHeader> headers = this.getHeaders(arrayOfHeaders, currentPathValues, isHeadersFuzzing, i);

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

    private Set<CatsHeader> getHeaders(CatsHeader[] existingHeaders, Map<String, Object> currentPathValues, boolean isHeadersFuzzing, int i) {
        Set<CatsHeader> headers = new java.util.HashSet<>(Set.of(existingHeaders));
        if (!headers.isEmpty() && isHeadersFuzzing) {
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

    public String getTestScenario(String testName, Map<String, Object> currentPathValues) {
        String description = WordUtils.nullOrValueOf(currentPathValues.get(DESCRIPTION));
        if (StringUtils.isNotBlank(description)) {
            return description;
        }

        return "send request with custom values supplied. Test key [" + testName + "]";
    }


    public void startCustomTest(String testName, Map<String, Object> currentPathValues, String expectedResponseCode) {
        String testScenario = this.getTestScenario(testName, currentPathValues);
        testCaseListener.addScenario(log, "Scenario: {}", testScenario);
        testCaseListener.addExpectedResult(log, "Should return [{}]", expectedResponseCode);
    }

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

    public boolean isCatsRemove(Map.Entry<String, Object> keyValue) {
        return ServiceCaller.CATS_REMOVE_FIELD.equalsIgnoreCase(String.valueOf(keyValue.getValue()));
    }

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

    public boolean wasOneOfSelectionReplaced(String oneOfSelection, FuzzingData data) {
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
    public List<Map<String, Object>> createIndividualRequest(Map<String, Object> testCase, String payload) {
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

    public String replacePathVariablesWithCustomValues(FuzzingData data, Map<String, Object> currentPathValues) {
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
            return catsUtil.replaceField(payload, keyValue.getKey(), fuzzingStrategy).json();
        } catch (Exception e) {
            log.debug("Something went wrong while parsing!", e);
            log.warning("Property [{}] does not exist", keyValue.getKey());
            return payload;
        }
    }

    public Object getPropertyValueToReplaceInBody(Map.Entry<String, Object> keyValue) {
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

    public String getVariableName(String catsVariable) {
        return catsVariable.replace("${", "").replace("}", "");
    }

    public boolean isVariable(String candidate) {
        return candidate.startsWith("${") && candidate.endsWith("}");
    }

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


        catsUtil.writeToYaml("refData_custom.yml", finalPathsAndVariables);
        log.complete("Finish writing refData_custom.yml");
    }

    public boolean isMatchingHttpMethod(Object currentValues, HttpMethod httpMethod) {
        Map<String, Object> currentPathValues = (Map<String, Object>) currentValues;
        Optional<HttpMethod> httpMethodFromYaml = HttpMethod.fromString(String.valueOf(currentPathValues.get(CatsDSLWords.HTTP_METHOD)));

        return httpMethodFromYaml.isEmpty() || httpMethodFromYaml.get().equals(httpMethod);
    }
}
