package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.generator.Cloner;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.stream.Collectors;

public abstract class BaseHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(this.getClass());

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    @Autowired
    protected BaseHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    public void fuzz(FuzzingData fuzzingData) {
        if (fuzzingData.getHeaders().isEmpty()) {
            logger.skip("No headers to fuzz");
        }

        Set<CatsHeader> clonedHeaders = Cloner.cloneMe(fuzzingData.getHeaders());

        for (CatsHeader header : clonedHeaders) {
            testCaseListener.createAndExecuteTest(logger, this, () -> process(fuzzingData, clonedHeaders, header));
        }
    }

    private void process(FuzzingData data, Set<CatsHeader> clonedHeaders, CatsHeader header) {
        String previousHeaderValue = header.getValue();
        header.withValue(this.fuzzStrategy().process(previousHeaderValue));
        try {
            boolean isRequiredHeaderFuzzed = clonedHeaders.stream().filter(CatsHeader::isRequired).collect(Collectors.toList()).contains(header);

            testCaseListener.addScenario(logger, "Scenario: Send [{}] in headers: header [{}] with value [{}]", this.typeOfDataSentToTheService(), header.getName(), header.getTruncatedValue());
            testCaseListener.addExpectedResult(logger, "Expected result: should get a [{}] response code", this.getExpectedResultCode(isRequiredHeaderFuzzed).asString());

            ServiceData serviceData = ServiceData.builder().relativePath(data.getPath()).headers(clonedHeaders)
                    .payload(data.getPayload()).fuzzedHeader(header.getName()).queryParams(data.getQueryParams()).build();

            CatsResponse response = serviceCaller.call(data.getMethod(), serviceData);

            testCaseListener.reportResult(logger, data, response, this.getExpectedResultCode(isRequiredHeaderFuzzed));
        } finally {
            /* we reset back the current header */
            header.withValue(previousHeaderValue);
        }
    }

    protected abstract String typeOfDataSentToTheService();

    ResponseCodeFamily getExpectedResultCode(boolean required) {
        return required ? this.getExpectedHttpCodeForRequiredHeadersFuzzed() : this.getExpectedHttpForOptionalHeadersFuzzed();
    }

    /**
     * What is the expected HTTP Code when required headers are fuzzed with invalid values
     *
     * @return expected HTTP code
     */
    protected abstract ResponseCodeFamily getExpectedHttpCodeForRequiredHeadersFuzzed();

    /**
     * What is the expected HTTP code when optional headers are fuzzed with invalid values
     *
     * @return expected HTTP code
     */
    protected abstract ResponseCodeFamily getExpectedHttpForOptionalHeadersFuzzed();

    /**
     * What is the Fuzzing strategy the current Fuzzer will apply
     *
     * @return expected FuzzingStrategy
     */
    protected abstract FuzzingStrategy fuzzStrategy();
}
