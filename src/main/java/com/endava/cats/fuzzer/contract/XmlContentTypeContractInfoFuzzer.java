package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.ContractInfoFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@ContractInfoFuzzer
@Component
@Slf4j
public class XmlContentTypeContractInfoFuzzer extends BaseContractInfoFuzzer {
    private static final String APPLICATION_XML = "application/xml";

    protected XmlContentTypeContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Scenario: Check if the current path accepts [application/xml] Content-Type");
        testCaseListener.addExpectedResult(log, "Paths should avoid accepting [application/xml] and focus only on [application/json] Content-Type");


        if (data.getRequestContentTypes().contains(APPLICATION_XML)) {
            testCaseListener.reportError(log, "Path accepts [application/xml] as Content-Type]");
        } else {
            testCaseListener.reportInfo(log, "Path does not accept [application/xml] as Content-Type");
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath();
    }

    @Override
    public String description() {
        return "verifies that all OpenAPI contract paths responses and requests does not offer `application/xml` as a Content-Type";
    }
}
