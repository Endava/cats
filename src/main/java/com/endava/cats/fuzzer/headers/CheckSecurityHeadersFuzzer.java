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
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Check that responses include Content-Type, Content-Type-Options, X-Frame-Options: deny
 */

@ApplicationScoped
@HeaderFuzzer
public class CheckSecurityHeadersFuzzer implements Fuzzer {

    protected static final String SECURITY_HEADERS_AS_STRING;
    private static final Map<String, List<CatsHeader>> SECURITY_HEADERS = new HashMap<>();

    static {
        SECURITY_HEADERS.put("Cache-Control", Collections.singletonList(CatsHeader.builder().name("Cache-Control").value("no-store").build()));
        SECURITY_HEADERS.put("X-Content-Type-Options", Collections.singletonList(CatsHeader.builder().name("X-Content-Type-Options").value("nosniff").build()));
        SECURITY_HEADERS.put("X-Frame-Options", Collections.singletonList(CatsHeader.builder().name("X-Frame-Options").value("DENY").build()));
        SECURITY_HEADERS.put("X-XSS-Protection", Arrays.asList(CatsHeader.builder().name("X-XSS-Protection").value("1; mode=block").build(),
                CatsHeader.builder().name("X-XSS-Protection").value("0").build()));

        SECURITY_HEADERS_AS_STRING = SECURITY_HEADERS.entrySet().stream().map(Object::toString).collect(Collectors.toSet()).toString();
    }

    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final ServiceCaller serviceCaller;
    private final TestCaseListener testCaseListener;

    public CheckSecurityHeadersFuzzer(ServiceCaller sc, TestCaseListener lr) {
        this.serviceCaller = sc;
        this.testCaseListener = lr;
    }

    @Override
    public void fuzz(FuzzingData data) {
        testCaseListener.createAndExecuteTest(log, this, () -> process(data));
    }

    private void process(FuzzingData data) {
        testCaseListener.addScenario(log, "Send a happy flow request and check the following Security Headers: {}", SECURITY_HEADERS_AS_STRING);
        testCaseListener.addExpectedResult(log, "Should get a 2XX response code and all the above security headers within the response");
        CatsResponse response = serviceCaller.call(ServiceData.builder().relativePath(data.getPath()).headers(data.getHeaders())
                .payload(data.getPayload()).queryParams(data.getQueryParams()).httpMethod(data.getMethod()).build());

        List<CatsHeader> missingSecurityHeaders = getMissingSecurityHeaders(response);
        if (!missingSecurityHeaders.isEmpty()) {
            testCaseListener.reportError(log, "Missing recommended Security Headers: {}", missingSecurityHeaders.stream().map(CatsHeader::nameAndValue).collect(Collectors.toSet()));
        } else {
            testCaseListener.reportResult(log, data, response, ResponseCodeFamily.TWOXX);
        }
    }

    private List<CatsHeader> getMissingSecurityHeaders(CatsResponse catsResponse) {
        List<CatsHeader> notMatching = new ArrayList<>();
        for (Map.Entry<String, List<CatsHeader>> securityHeaders : SECURITY_HEADERS.entrySet()) {
            boolean noneMatch = catsResponse.getHeaders().stream()
                    .noneMatch(catsHeader -> securityHeaders.getValue().stream().anyMatch(securityHeader -> catsHeader.getName().equalsIgnoreCase(securityHeader.getName())
                            && catsHeader.getValue().toLowerCase().contains(securityHeader.getValue().toLowerCase())));
            if (noneMatch) {
                notMatching.addAll(securityHeaders.getValue());
            }
        }

        return notMatching;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String description() {
        return "check all responses for good practices around Security related headers like: " + SECURITY_HEADERS_AS_STRING;
    }
}
