package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.ContractInfoFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ContractInfoFuzzer
@Component
@Slf4j
public class VersionsContractInfoFuzzer extends BaseContractInfoFuzzer {
    private static final List<String> VERSIONS = Arrays.asList("version\\d*\\.?", "v\\d+\\.?");


    public VersionsContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Scenario: Check if the current path contains versioning information");
        testCaseListener.addExpectedResult(log, "Paths should not contain versioning information. This should be handled in the [servers] definition");
        testCaseListener.addPath(data.getPath());
        testCaseListener.addFullRequestPath("NA");

        boolean found = false;
        for (String version : VERSIONS) {
            Pattern p = Pattern.compile(version);
            Matcher m = p.matcher(data.getPath());
            if (m.find()) {
                found = true;
            }
        }

        if (found) {
            testCaseListener.reportError(log, "Path contains versioning information");
        } else {
            testCaseListener.reportInfo(log, "Path does not contain versioning information");
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath();
    }

    @Override
    public String description() {
        return "verifies that a given path doesn't contain versioning information";
    }
}
