package com.endava.cats.fuzzer.headers;

import com.endava.cats.fuzzer.Fuzzer;
import com.endava.cats.fuzzer.HeaderFuzzer;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Check that responses include Content-Type, Content-Type-Options, X-Frame-Options: deny
 */

@Component
@Slf4j
@HeaderFuzzer
public class CheckSecurityHeadersFuzzer implements Fuzzer {

    private static final List<CatsHeader> SECURITY_HEADERS = Arrays.asList(CatsHeader.builder().name("Cache-Control").value("no-store").build(),
            CatsHeader.builder().name("X-Content-Type-Options").value("nosniff").build(),
            CatsHeader.builder().name("X-Frame-Options").value("DENY").build(),
            CatsHeader.builder().name("X-XSS-Protection").value("1; mode=block").build());

    protected static final String SECURITY_HEADERS_AS_STRING = SECURITY_HEADERS.stream().map(CatsHeader::nameAndValue).collect(Collectors.toSet()).toString();

    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    @Autowired
    public CheckSecurityHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    @Override
    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(log, this, () -> process(data));
    }

    private void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Scenario: send a 'happy' flow request and check the following Security Headers: {}", SECURITY_HEADERS_AS_STRING);
        testCaseListener.addExpectedResult(log, "Expected result: should get a 2XX response code and all security headers in");
        CatsResponse response = serviceCaller.call(data.getMethod(), ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(data.getPayload()).queryParams(data.getQueryParams()).build());

        List<CatsHeader> missingSecurityHeaders = getMissingSecurityHeaders(response);
        if (!missingSecurityHeaders.isEmpty()) {
            testCaseListener.reportError(log, "Missing recommended Security Headers: {}", missingSecurityHeaders.stream().map(CatsHeader::nameAndValue).collect(Collectors.toSet()));
        } else {
            testCaseListener.reportResult(log, data, response, ResponseCodeFamily.TWOXX);
        }
    }

    private List<CatsHeader> getMissingSecurityHeaders(CatsResponse catsResponse) {
        List<CatsHeader> notMatching = new ArrayList<>();
        for (CatsHeader securityHeader : SECURITY_HEADERS) {
            boolean noneMatch = catsResponse.getHeaders().stream().noneMatch(catsHeader -> catsHeader.getName().equalsIgnoreCase(securityHeader.getName())
                    && catsHeader.getValue().toLowerCase().contains(securityHeader.getValue().toLowerCase()));
            if (noneMatch) {
                notMatching.add(securityHeader);
            }
        }

        return notMatching;
    }

    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "check all responses for good practices around Security related headers like: " + SECURITY_HEADERS_AS_STRING;
    }
}
