package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.ContractInfoFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
    private static final String PLURAL_END = "s";

    @Autowired
    public NamingsContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Scenario: Check if the current path REST naming good practices");
        testCaseListener.addExpectedResult(log, "Path should match the REST naming good practices. Must use: nouns, plurals, lowercase hyphen-case/snake_case for endpoints, camelCase/snake_case for JSON properties");
        testCaseListener.addPath(data.getPath());
        testCaseListener.addFullRequestPath("NA");

        StringBuilder errorString = new StringBuilder();
        String[] pathElements = data.getPath().substring(1).split("/");

        errorString.append(this.checkPlurals(pathElements));
        errorString.append(this.checkFormat(pathElements));
        errorString.append(this.checkVariables(pathElements));

        if (!errorString.toString().isEmpty()) {
            testCaseListener.reportError(log, "Path does not follow REST naming good practices: {}.", errorString.toString());
        } else {
            testCaseListener.reportInfo(log, "Path follows the REST naming good practices.");
        }
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
            return String.format(errorMessage + newLine(2), StringUtils.stripStart(result.toString().trim(), ", "));
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
