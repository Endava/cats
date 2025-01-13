package com.endava.cats.fuzzer.executor;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.generator.Cloner;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Executor used to execute logic when fuzzing headers.
 */
@Singleton
public class HeadersIteratorExecutor {

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final MatchArguments matchArguments;
    private final FilterArguments filterArguments;

    /**
     * Creates a new HeadersIteratorExecutor instance.
     *
     * @param serviceCaller    the service caller
     * @param testCaseListener the test case listener
     * @param ma               matching arguments
     * @param ia               filter arguments
     */
    public HeadersIteratorExecutor(ServiceCaller serviceCaller, TestCaseListener testCaseListener, MatchArguments ma, FilterArguments ia) {
        this.serviceCaller = serviceCaller;
        this.testCaseListener = testCaseListener;
        this.matchArguments = ma;
        this.filterArguments = ia;
    }

    /**
     * Executes the actual fuzzing logic.
     *
     * @param context the context used for fuzzing
     */
    public void execute(HeadersIteratorExecutorContext context) {
        Set<CatsHeader> headersWithoutAuth = this.getHeadersWithoutAuthHeaders(context);
        if (headersWithoutAuth.isEmpty()) {
            context.getLogger().skip("No headers to fuzz");
        }

        Set<CatsHeader> clonedHeaders = Cloner.cloneMe(headersWithoutAuth);

        for (CatsHeader header : clonedHeaders) {
            if (filterArguments.getSkipHeaders().stream().noneMatch(ignoredHeader -> ignoredHeader.equalsIgnoreCase(header.getName()))) {
                for (FuzzingStrategy fuzzingStrategy : context.getFuzzValueProducer().get()) {
                    context.getLogger().debug("Fuzzing strategy {} for header {}", fuzzingStrategy.name(), header);
                    String previousHeaderValue = header.getValue();
                    header.withValue(String.valueOf(fuzzingStrategy.process(previousHeaderValue)));
                    try {
                        testCaseListener.createAndExecuteTest(context.getLogger(), context.getFuzzer(), () -> {
                            boolean isRequiredHeaderFuzzed = clonedHeaders.stream().filter(CatsHeader::isRequired).toList().contains(header);
                            ResponseCodeFamily expectedResponseCode = this.getExpectedResultCode(isRequiredHeaderFuzzed, context);

                            testCaseListener.addScenario(context.getLogger(), context.getScenario() + "  Current header [{}] [{}]", header.getName(), fuzzingStrategy);
                            testCaseListener.addExpectedResult(context.getLogger(), "Should return [{}]",
                                    expectedResponseCode != null ? expectedResponseCode.asString() : "a response that doesn't match" + matchArguments.getMatchString());

                            ServiceData serviceData = ServiceData.builder()
                                    .relativePath(context.getFuzzingData().getPath())
                                    .contractPath(context.getFuzzingData().getContractPath())
                                    .headers(clonedHeaders)
                                    .payload(context.getFuzzingData().getPayload())
                                    .fuzzedHeader(header.getName())
                                    .queryParams(context.getFuzzingData().getQueryParams())
                                    .httpMethod(context.getFuzzingData().getMethod())
                                    .contentType(context.getFuzzingData().getFirstRequestContentType())
                                    .pathParamsPayload(context.getFuzzingData().getPathParamsPayload())
                                    .build();

                            CatsResponse response = serviceCaller.call(serviceData);
                            this.reportResult(context, expectedResponseCode, response);
                        }, context.getFuzzingData());
                    } finally {
                        /* we reset back the current header */
                        header.withValue(previousHeaderValue);
                    }
                }
            }
        }
    }

    private void reportResult(HeadersIteratorExecutorContext context, ResponseCodeFamily expectedResponseCode, CatsResponse response) {
        if (expectedResponseCode != null) {
            testCaseListener.reportResult(context.getLogger(), context.getFuzzingData(), response, expectedResponseCode, context.isMatchResponseSchema(), context.isShouldMatchContentType());
        } else if (matchArguments.isMatchResponse(response) || !matchArguments.isAnyMatchArgumentSupplied()) {
            testCaseListener.reportResultError(context.getLogger(), context.getFuzzingData(), CatsResultFactory.Reason.RESPONSE_MATCHES_ARGUMENTS.value(), "Response matches" + matchArguments.getMatchString());
        } else {
            testCaseListener.skipTest(context.getLogger(), "Skipping test as response does not match given matchers!");
        }
    }

    ResponseCodeFamily getExpectedResultCode(boolean required, HeadersIteratorExecutorContext context) {
        return required ? context.getExpectedResponseCodeForRequiredHeaders() : context.getExpectedResponseCodeForOptionalHeaders();
    }

    private Set<CatsHeader> getHeadersWithoutAuthHeaders(HeadersIteratorExecutorContext context) {
        if (context.isSkipAuthHeaders()) {
            Set<CatsHeader> headersWithoutAuth = context.getFuzzingData().getHeaders().stream()
                    .filter(catsHeader -> !serviceCaller.isAuthenticationHeader(catsHeader.getName()))
                    .collect(Collectors.toSet());
            context.getLogger().note("All headers excluding auth headers: {}", headersWithoutAuth);
            return headersWithoutAuth;
        }
        return context.getFuzzingData().getHeaders();
    }
}
