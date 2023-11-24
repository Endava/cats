package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.LinterFuzzer;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Optional;

@LinterFuzzer
@Singleton
public class QueryParamsCaseLinterFuzzer extends BaseLinterFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final NamingArguments namingArguments;

    public QueryParamsCaseLinterFuzzer(TestCaseListener tcl, NamingArguments nameArgs) {
        super(tcl);
        this.namingArguments = nameArgs;
    }

    @Override
    public void process(FuzzingData data) {
        String expectedResult = "Query params must follow %s naming".formatted(namingArguments.getQueryParamsNaming().getDescription());
        testCaseListener.addScenario(log, "Check if query params follow % naming", namingArguments.getQueryParamsNaming().getDescription());
        testCaseListener.addExpectedResult(log, expectedResult);

        String errors = this.checkQueryParams(data);

        if (this.hasErrors(errors)) {
            testCaseListener.reportResultError(log, data, "Query params not following recommended naming",
                    "Query params not following {} naming: {}", namingArguments.getHeadersNaming().getDescription(), StringUtils.stripEnd(errors.trim(), ","));
        } else {
            testCaseListener.reportResultInfo(log, data, "Query params follow naming conventions.");
        }
    }

    private String checkQueryParams(FuzzingData data) {
        return this.check(Optional.ofNullable(data.getQueryParams()).orElse(Collections.emptySet()).toArray(new String[0]), queryParam ->
                !namingArguments.getQueryParamsNaming().getPattern().matcher(queryParam).matches());
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that query params follow naming conventions";
    }
}