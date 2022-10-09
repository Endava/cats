package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.ContractInfoFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ContractInfoFuzzer
@Singleton
public class VersionsContractInfoFuzzer extends BaseContractInfoFuzzer {
    private static final List<String> VERSIONS = Arrays.asList("version\\d*\\.?", "v\\d+\\.?");
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    public VersionsContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the current path contains versioning information");
        testCaseListener.addExpectedResult(log, "Paths should not contain versioning information. This should be handled in the [servers] definition");

        boolean found = false;
        for (String version : VERSIONS) {
            Pattern p = Pattern.compile(version);
            Matcher m = p.matcher(data.getPath());
            if (m.find()) {
                found = true;
            }
        }

        if (found) {
            testCaseListener.reportResultError(log, data, "Path contains versioning info", "Path contains versioning information");
        } else {
            testCaseListener.reportResultInfo(log, data, "Path does not contain versioning information");
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
