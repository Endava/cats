package com.endava.cats.fuzzer.headers.base;

import com.endava.cats.Fuzzer;
import com.endava.cats.generator.Cloner;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class BaseHeadersFuzzer implements Fuzzer {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(this.getClass());
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    protected BaseHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    public void fuzz(FuzzingData fuzzingData) {
        Set<CatsHeader> headersWithoutAuth = this.getHeadersWithoutAuthHeaders(fuzzingData.getHeaders());
        if (headersWithoutAuth.isEmpty()) {
            logger.skip("No headers to fuzz");
        }

        Set<CatsHeader> clonedHeaders = Cloner.cloneMe(headersWithoutAuth);

        for (CatsHeader header : clonedHeaders) {
            for (FuzzingStrategy fuzzingStrategy : fuzzStrategy()) {
                logger.debug("Fuzzing strategy {} for header {}", fuzzingStrategy.name(), header);
                testCaseListener.createAndExecuteTest(logger, this, () -> process(fuzzingData, clonedHeaders, header, fuzzingStrategy));
            }
        }
    }

    private void process(FuzzingData data, Set<CatsHeader> clonedHeaders, CatsHeader header, FuzzingStrategy fuzzingStrategy) {
        String previousHeaderValue = header.getValue();
        header.withValue(String.valueOf(fuzzingStrategy.process(previousHeaderValue)));
        try {
            boolean isRequiredHeaderFuzzed = clonedHeaders.stream().filter(CatsHeader::isRequired).toList().contains(header);

            testCaseListener.addScenario(logger, "Send [{}] in headers: header [{}] with value [{}]", this.typeOfDataSentToTheService(), header.getName(), fuzzingStrategy.truncatedValue());
            testCaseListener.addExpectedResult(logger, "Should get a [{}] response code", this.getExpectedResultCode(isRequiredHeaderFuzzed).asString());

            ServiceData serviceData = ServiceData.builder().relativePath(data.getPath()).headers(clonedHeaders)
                    .payload(data.getPayload()).fuzzedHeader(header.getName()).queryParams(data.getQueryParams()).httpMethod(data.getMethod())
                    .contentType(data.getFirstRequestContentType()).build();

            CatsResponse response = serviceCaller.call(serviceData);

            testCaseListener.reportResult(logger, data, response, this.getExpectedResultCode(isRequiredHeaderFuzzed), this.matchResponseSchema());
        } finally {
            /* we reset back the current header */
            header.withValue(previousHeaderValue);
        }
    }

    ResponseCodeFamily getExpectedResultCode(boolean required) {
        return required ? this.getExpectedHttpCodeForRequiredHeadersFuzzed() : this.getExpectedHttpForOptionalHeadersFuzzed();
    }

    public Set<CatsHeader> getHeadersWithoutAuthHeaders(Set<CatsHeader> headers) {
        Set<CatsHeader> headersWithoutAuth = headers.stream()
                .filter(catsHeader -> !serviceCaller.isAuthenticationHeader(catsHeader.getName()))
                .collect(Collectors.toSet());
        logger.note("All headers excluding auth headers: {}", headersWithoutAuth);
        return headersWithoutAuth;
    }

    /**
     * Short description of data that is sent to the service.
     *
     * @return a short description
     */
    protected abstract String typeOfDataSentToTheService();

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
    protected abstract List<FuzzingStrategy> fuzzStrategy();

    /**
     * There is a special case when we send Control Chars in Headers and an error (due to HTTP RFC specs)
     * is returned by the app server itself, not the application. In this case we don't want to check
     * if there is even a response body as the error page/response is served by the server, not the application layer.
     *
     * @return true if it should match response schema and false otherwise
     */
    protected boolean matchResponseSchema() {
        return true;
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }
}
