package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.OpenApiUtils;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

/**
 * Checks that paths consistently follow the same naming conventions.
 */
@LinterFuzzer
@Singleton
public class PathCaseLinterFuzzer extends BaseLinterFuzzer {

    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final NamingArguments namingArguments;

    /**
     * Creates a new PathCaseLinterFuzzer instance.
     *
     * @param tcl      the test case listener
     * @param nameArgs naming arguments used to get expected naming case
     */
    public PathCaseLinterFuzzer(TestCaseListener tcl, NamingArguments nameArgs) {
        super(tcl);
        this.namingArguments = nameArgs;
    }

    @Override
    public void process(FuzzingData data) {
        String expectedResult = "Path should follow naming conventions: path elements %s, path variables %s"
                .formatted(namingArguments.getPathNaming().getDescription(), namingArguments.getPathVariablesNaming().getDescription());
        testCaseListener.addScenario(log, "Check if the path elements follow naming conventions: path elements %s, path variables %s"
                .formatted(namingArguments.getPathNaming().getDescription(), namingArguments.getPathVariablesNaming().getDescription()));
        testCaseListener.addExpectedResult(log, expectedResult);

        StringBuilder errorString = new StringBuilder();
        String[] pathElements = OpenApiUtils.getPathElements(data.getPath());

        errorString.append(" path elements %s: ".formatted(namingArguments.getPathNaming().getDescription()));
        String pathElementsErrors = this.checkPathElements(pathElements);
        boolean hasErrors = this.hasErrors(pathElementsErrors);
        errorString.append(pathElementsErrors);

        errorString.append("; path variables %s: ".formatted(namingArguments.getPathVariablesNaming().getDescription()));
        String pathVariablesErrors = this.checkPathVariables(pathElements);
        hasErrors = hasErrors || this.hasErrors(pathVariablesErrors);
        errorString.append(pathVariablesErrors);

        if (hasErrors) {
            testCaseListener.reportResultError(log, data, "Path elements not following recommended naming",
                    "Path elements do not follow naming conventions: {}", StringUtils.stripEnd(errorString.toString().trim(), ","));
        } else {
            testCaseListener.reportResultInfo(log, data, "Path elements follow naming conventions.");
        }
    }

    private String checkPathVariables(String[] pathElements) {
        return this.check(pathElements, pathElement -> OpenApiUtils.isAPathVariable(pathElement)
                && !namingArguments.getPathVariablesNaming().getPattern().matcher(pathElement.replace("{", "").replace("}", "")).matches());
    }

    private String checkPathElements(String[] pathElements) {
        return this.check(pathElements, pathElement -> OpenApiUtils.isNotAPathVariable(pathElement)
                && !namingArguments.getPathNaming().getPattern().matcher(pathElement).matches());
    }


    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that path elements follow naming conventions";
    }
}
