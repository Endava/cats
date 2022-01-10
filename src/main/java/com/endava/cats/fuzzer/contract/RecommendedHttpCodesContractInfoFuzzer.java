package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.ContractInfoFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ContractInfoFuzzer
@Singleton
public class RecommendedHttpCodesContractInfoFuzzer extends BaseContractInfoFuzzer {
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    public RecommendedHttpCodesContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the current path contains all recommended HTTP response codes for HTTP method {}", data.getMethod());
        testCaseListener.addExpectedResult(log, "The following response codes should be present for HTTP operation {}: {}", data.getMethod(), data.getMethod().getRecommendedCodes());

        List<String> missingCodesWithOrLists = data.getMethod().getRecommendedCodes()
                .stream()
                .filter(code -> !data.getResponseCodes().contains(code))
                .collect(Collectors.toList());

        List<String> missingCodes = missingCodesWithOrLists.stream()
                .filter(code -> Arrays.stream(code.split(Pattern.quote("|"))).noneMatch(splitCode -> data.getResponseCodes().contains(splitCode)))
                .collect(Collectors.toList());

        if (missingCodes.isEmpty()) {
            testCaseListener.reportInfo(log, "All recommended HTTP codes are defined!");
        } else {
            testCaseListener.reportError(log, "The following recommended HTTP response codes are missing: {}", missingCodes);
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that the current path contains all recommended HTTP response codes for all operations";
    }

}

