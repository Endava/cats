package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.ContractInfoFuzzer;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.OpenApiUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@ContractInfoFuzzer
@Singleton
public class NamingsContractInfoFuzzer extends BaseContractInfoFuzzer {
    private static final Pattern HYPHEN_CASE = Pattern.compile("^[a-z]+((-)?[a-z])*+$");
    private static final Pattern SNAKE_CASE = Pattern.compile("^[a-z]+((_)?[a-z])*+$");
    private static final Pattern CAMEL_CASE = Pattern.compile("^[a-z]+[A-Za-z]+$");
    private static final Pattern CAMEL_CASE_CAPITAL_START = Pattern.compile("^[A-Z][A-Za-z]+$");
    private static final Pattern GENERATED_BODY_OBJECTS = Pattern.compile("body_[0-9]*");
    private static final String PLURAL_END = "s";
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final ProcessingArguments processingArguments;

    public NamingsContractInfoFuzzer(TestCaseListener tcl, ProcessingArguments proc) {
        super(tcl);
        this.processingArguments = proc;
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the current path follows RESTful API naming good practices for HTTP method {}", data.getMethod());
        testCaseListener.addExpectedResult(log, "Path should follow the RESTful API naming good practices. Must use: nouns, plurals, lowercase hyphen-case/snake_case for endpoints, camelCase/snake_case for JSON properties");

        StringBuilder errorString = new StringBuilder();
        String[] pathElements = data.getPath().substring(1).split("/");

        errorString.append(this.checkPlurals(pathElements));
        errorString.append(this.checkFormat(pathElements));
        errorString.append(this.checkVariables(pathElements));
        errorString.append(this.checkJsonObjects(data));

        if (!errorString.toString().isEmpty()) {
            testCaseListener.reportError(log, "Path does not follow RESTful API naming good practices: {}", errorString.toString());
        } else {
            testCaseListener.reportInfo(log, "Path follows the RESTful API naming good practices.");
        }
    }

    private String checkJsonObjects(FuzzingData data) {
        List<String> stringToCheck = new ArrayList<>();
        stringToCheck.add(data.getReqSchemaName());
        Operation operation = HttpMethod.getOperation(data.getMethod(), data.getPathItem());
        for (ApiResponse apiResponse : operation.getResponses().values()) {
            String ref = apiResponse.get$ref();
            if (ref == null && apiResponse.getContent() != null) {
                ref = OpenApiUtils.getMediaTypeFromContent(apiResponse.getContent(), processingArguments.getDefaultContentType()).getSchema().get$ref();
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
        String[] strippedPathElements = pathElements.length >= 2 ? Arrays.copyOfRange(pathElements, 1, pathElements.length - 1) : pathElements;

        return this.check(strippedPathElements, pathElement -> this.isNotAPathVariable(pathElement) && !pathElement.endsWith(PLURAL_END),
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
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that all OpenAPI contract elements follow RESTful API naming good practices";
    }
}
