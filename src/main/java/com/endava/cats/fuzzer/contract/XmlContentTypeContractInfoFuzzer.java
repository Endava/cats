package com.endava.cats.fuzzer.contract;

import com.endava.cats.fuzzer.ContractInfoFuzzer;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.springframework.stereotype.Component;

@ContractInfoFuzzer
@Component
public class XmlContentTypeContractInfoFuzzer extends BaseContractInfoFuzzer {
    private static final String APPLICATION_XML = "application/xml";
    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());

    protected XmlContentTypeContractInfoFuzzer(TestCaseListener tcl) {
        super(tcl);
    }

    @Override
    public void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Check if the current path accepts [application/xml] Content-Type for HTTP method {}", data.getMethod());
        testCaseListener.addExpectedResult(log, "Paths should avoid accepting [application/xml] and focus only on [application/json] Content-Type");


        if (data.getRequestContentTypes().contains(APPLICATION_XML)) {
            testCaseListener.reportError(log, "Path accepts [application/xml] as Content-Type]");
        } else {
            testCaseListener.reportInfo(log, "Path does not accept [application/xml] as Content-Type");
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
