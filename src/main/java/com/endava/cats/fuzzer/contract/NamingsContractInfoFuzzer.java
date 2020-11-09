package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.ContractInfoFuzzer;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@ContractInfoFuzzer
@Component
@Slf4j
public class NamingsContractInfoFuzzer extends BaseContractInfoFuzzer {
    private static final Pattern HYPHEN_CASE = Pattern.compile("(^[a-z]+((-)?[a-z])+$)+");
    private static final Pattern SNAKE_CASE = Pattern.compile("(^[a-z]+((_)?[a-z])+$)+");
    private static final Pattern CAMEL_CASE = Pattern.compile("(^[a-z]+[A-Za-z]+$)+");
    private static final Pattern CAMEL_CASE_CAPITAL_START = Pattern.compile("(^[A-Z][A-Za-z]+$)+");
    private static final Pattern GENERATED_BODY_OBJECTS = Pattern.compile("body_[0-9]*");
    private static final String PLURAL_END = "s";

    @Autowired
    public NamingsContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Scenario: Check if the current path REST naming good practices");
        testCaseListener.addExpectedResult(log, "Path should match the REST naming good practices. Must use: nouns, plurals, lowercase hyphen-case/snake_case for endpoints, camelCase/snake_case for JSON properties");

        StringBuilder errorString = new StringBuilder();
        String[] pathElements = data.getPath().substring(1).split("/");

        errorString.append(this.checkPlurals(pathElements));
        errorString.append(this.checkFormat(pathElements));
        errorString.append(this.checkVariables(pathElements));
        errorString.append(this.checkJsonObjects(data));

        if (!errorString.toString().isEmpty()) {
            testCaseListener.reportError(log, "Path does not follow REST naming good practices: {}", errorString.toString());
        } else {
            testCaseListener.reportInfo(log, "Path follows the REST naming good practices.");
        }
    }

    private String checkJsonObjects(FuzzingData data) {
        List<String> stringToCheck = new ArrayList<>();
        stringToCheck.add(data.getReqSchemaName());
        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());
        for (ApiResponse apiResponse : operation.getResponses().values()) {
            String ref = apiResponse.get$ref();
            if (ref == null && apiResponse.getContent() != null) {
                ref = apiResponse.getContent().get(MimeTypeUtils.APPLICATION_JSON_VALUE).getSchema().get$ref();
            }

            if (ref != null) {
                stringToCheck.add(ref.substring(ref.lastIndexOf("/") + 1));
            }

        }
        return this.check(stringToCheck.toArray(new String[0]), jsonObject -> !CAMEL_CASE_CAPITAL_START.matcher(jsonObject).matches()
                        && !HYPHEN_CASE.matcher(jsonObject).matches() && !SNAKE_CASE.matcher(jsonObject).matches() && !GENERATED_BODY_OBJECTS.matcher(jsonObject).matches(),
                "The following request/response objects are not matching CamelCase, snake_case or hyphen-case: %s");
    }

    private String checkVariables(String[] pathElements) {
        return this.check(pathElements, pathElement -> this.isAPathVariable(pathElement)
                        && !CAMEL_CASE.matcher(pathElement.replace("{", "").replace("}", "")).matches()
                        && !SNAKE_CASE.matcher(pathElement.replace("{", "").replace("}", "")).matches(),
                "The following path variables are not matching camelCase or snake_case: %s");
    }

    private String checkFormat(String[] pathElements) {
        return this.check(pathElements, pathElement -> this.isNotAPathVariable(pathElement)
                        && !SNAKE_CASE.matcher(pathElement).matches()
                        && !HYPHEN_CASE.matcher(pathElement).matches(),
                "The following path elements are not matching snake_case or hyphen-case: %s");
    }

    private String checkPlurals(String[] pathElements) {
        return this.check(pathElements, pathElement -> this.isNotAPathVariable(pathElement) && !pathElement.endsWith(PLURAL_END),
                "The following path elements are not using plural: %s");
    }

    private String check(String[] pathElements, Predicate<String> checkFunction, String errorMessage) {
        StringBuilder result = new StringBuilder();

        for (String pathElement : pathElements) {
            if (checkFunction.test(pathElement)) {
                result.append(COMMA).append(bold(pathElement));
            }
        }

        if (!result.toString().isEmpty()) {
            return String.format(this.trailNewLines(errorMessage, 2), StringUtils.stripStart(result.toString().trim(), ", "));
        }

        return EMPTY;
    }

    private boolean isNotAPathVariable(String pathElement) {
        return !this.isAPathVariable(pathElement);
    }

    private boolean isAPathVariable(String pathElement) {
        return pathElement.startsWith("{");
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath();
    }

    @Override
    public String description() {
        return "verifies that all OpenAPI contract elements follow REST API naming good practices";
    }
}
