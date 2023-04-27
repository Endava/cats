package com.endava.cats.fuzzer.executor;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.generator.Cloner;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;

import jakarta.inject.Singleton;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class HeadersIteratorExecutor {

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;
    private final MatchArguments matchArguments;

    private final IgnoreArguments ignoreArguments;

    public HeadersIteratorExecutor(ServiceCaller serviceCaller, TestCaseListener testCaseListener, MatchArguments ma, IgnoreArguments ia) {
        this.serviceCaller = serviceCaller;
        this.testCaseListener = testCaseListener;
        this.matchArguments = ma;
        this.ignoreArguments = ia;
    }

    public void execute(HeadersIteratorExecutorContext context) {
        Set<CatsHeader> headersWithoutAuth = this.getHeadersWithoutAuthHeaders(context);
        if (headersWithoutAuth.isEmpty()) {
            context.getLogger().skip("No headers to fuzz");
        }

        Set<CatsHeader> clonedHeaders = Cloner.cloneMe(headersWithoutAuth);

        for (CatsHeader header : clonedHeaders) {
            if (ignoreArguments.getSkipHeaders().stream().noneMatch(ignoredHeader -> ignoredHeader.equalsIgnoreCase(header.getName()))) {
                for (FuzzingStrategy fuzzingStrategy : context.getFuzzValueProducer().get()) {
                    context.getLogger().debug("Fuzzing strategy {} for header {}", fuzzingStrategy.name(), header);
                    String previousHeaderValue = header.getValue();
                    header.withValue(String.valueOf(fuzzingStrategy.process(previousHeaderValue)));
                    try {
                        testCaseListener.createAndExecuteTest(context.getLogger(), context.getFuzzer(), () -> {
                            boolean isRequiredHeaderFuzzed = clonedHeaders.stream().filter(CatsHeader::isRequired).toList().contains(header);
                            ResponseCodeFamily expectedResponseCode = this.getExpectedResultCode(isRequiredHeaderFuzzed, context);

                            testCaseListener.addScenario(context.getLogger(), context.getScenario() + "  Current header [{}] [{}]", header.getName(), fuzzingStrategy);
                            testCaseListener.addExpectedResult(context.getLogger(), "Should return [{}]", expectedResponseCode != null ? expectedResponseCode.asString() : "a valid response");

                            ServiceData serviceData = ServiceData.builder()
                                    .relativePath(context.getFuzzingData().getPath())
                                    .contractPath(context.getFuzzingData().getContractPath())
                                    .headers(clonedHeaders)
                                    .payload(context.getFuzzingData().getPayload())
                                    .fuzzedHeader(header.getName())
                                    .queryParams(context.getFuzzingData().getQueryParams())
                                    .httpMethod(context.getFuzzingData().getMethod())
                                    .contentType(context.getFuzzingData().getFirstRequestContentType())
                                    .build();

                            CatsResponse response = serviceCaller.call(serviceData);
                            this.reportResult(context, expectedResponseCode, response);
                        });
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
            testCaseListener.reportResult(context.getLogger(), context.getFuzzingData(), response, expectedResponseCode, context.isMatchResponseSchema());
        } else if (matchArguments.isMatchResponse(response) || !matchArguments.isAnyMatchArgumentSupplied()) {
            testCaseListener.reportResultError(context.getLogger(), context.getFuzzingData(), "Check response details", "Service call completed. Please check response details");
        } else {
            testCaseListener.skipTest(context.getLogger(), "Skipping test as response does not match given matchers!");
        }
    }

    ResponseCodeFamily getExpectedResultCode(boolean required, HeadersIteratorExecutorContext context) {
        return required ? context.getExpectedResponseCodeForRequiredHeaders() : context.getExpectedResponseCodeForOptionalHeaders();
    }

    public Set<CatsHeader> getHeadersWithoutAuthHeaders(HeadersIteratorExecutorContext context) {
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
