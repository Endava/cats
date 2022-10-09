package com.endava.cats.fuzzer.contract;

import com.endava.cats.annotations.ContractInfoFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Singleton;

@ContractInfoFuzzer
@Singleton
public class XmlContentTypeContractInfoFuzzer extends BaseContractInfoFuzzer {
    private static final String APPLICATION_XML = "application/xml";
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    public XmlContentTypeContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the current path accepts [application/xml] Content-Type for HTTP method {}", data.getMethod());
        testCaseListener.addExpectedResult(log, "Paths should avoid accepting [application/xml] and focus only on [application/json] Content-Type");


        if (data.getRequestContentTypes().contains(APPLICATION_XML)) {
            testCaseListener.reportResultError(log, data, "Path accepts [application/xml]", "Path accepts [application/xml] as Content-Type");
        } else {
            testCaseListener.reportResultInfo(log, data, "Path does not accept [application/xml] as Content-Type");
        }
    }

    @Override
    protected String runKey(FuzzingData data) {
        return data.getPath() + data.getMethod();
    }

    @Override
    public String description() {
        return "verifies that all OpenAPI contract paths responses and requests does not offer `application/xml` as a Content-Type";
    }
}
