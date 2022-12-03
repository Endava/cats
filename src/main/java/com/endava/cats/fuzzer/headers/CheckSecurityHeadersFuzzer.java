package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Check that responses include Content-Type, Content-Type-Options, X-Frame-Options: deny
 */

@Singleton
@HeaderFuzzer
public class CheckSecurityHeadersFuzzer implements Fuzzer {

    protected static final String SECURITY_HEADERS_AS_STRING;
    private static final Map<String, List<CatsHeader>> SECURITY_HEADERS = new HashMap<>();

    static {
        SECURITY_HEADERS.put("Cache-Control", Collections.singletonList(CatsHeader.builder().name("Cache-Control").value("no-store").build()));
        SECURITY_HEADERS.put("X-Content-Type-Options", Collections.singletonList(CatsHeader.builder().name("X-Content-Type-Options").value("nosniff").build()));
        SECURITY_HEADERS.put("X-Frame-Options/Content-Security-Policy", List.of(CatsHeader.builder().name("X-Frame-Options").value("DENY").build(),
                CatsHeader.builder().name("Content-Security-Policy").value("frame-ancestors 'none'").build()));
        SECURITY_HEADERS.put("X-XSS-Protection", List.of(CatsHeader.builder().name("X-XSS-Protection").value("1; mode=block").build(),
                CatsHeader.builder().name("X-XSS-Protection").value("0").build()));

        SECURITY_HEADERS_AS_STRING = new HashSet<>(SECURITY_HEADERS.keySet()).toString();
    }

    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final TestCaseListener testCaseListener;
    private final SimpleExecutor simpleExecutor;

    public CheckSecurityHeadersFuzzer(TestCaseListener lr, SimpleExecutor simpleExecutor) {
        this.testCaseListener = lr;
        this.simpleExecutor = simpleExecutor;
    }

    @Override
    public void fuzz(FuzzingData data) {
        simpleExecutor.execute(
                SimpleExecutorContext.builder()
                        .logger(log)
                        .fuzzingData(data)
                        .fuzzer(this)
                        .scenario("Send a happy flow request and check the following Security Headers: %s".formatted(SECURITY_HEADERS_AS_STRING))
                        .expectedResult(" and all the above security headers within the response")
                        .expectedResponseCode(ResponseCodeFamily.TWOXX)
                        .responseProcessor(this::checkResponse)
                        .build()
        );
    }

    private void checkResponse(CatsResponse response, FuzzingData data) {
        List<CatsHeader> missingSecurityHeaders = this.getMissingSecurityHeaders(response);
        if (!missingSecurityHeaders.isEmpty()) {
            testCaseListener.reportResultError(log, data, "Missing recommended security headers",
                    "Missing recommended Security Headers: {}", missingSecurityHeaders.stream().map(CatsHeader::nameAndValue).collect(Collectors.toSet()));
        } else {
            testCaseListener.reportResult(log, data, response, ResponseCodeFamily.TWOXX);
        }
    }

    private List<CatsHeader> getMissingSecurityHeaders(CatsResponse catsResponse) {
        List<CatsHeader> notMatching = new ArrayList<>();
        for (Map.Entry<String, List<CatsHeader>> securityHeaders : SECURITY_HEADERS.entrySet()) {
            boolean noneMatch = catsResponse.getHeaders().stream()
                    .noneMatch(catsHeader -> securityHeaders.getValue().stream()
                            .anyMatch(securityHeader -> this.containsAsAlphanumeric(catsHeader.getKey(), securityHeader.getName())
                                    && this.containsAsAlphanumeric(catsHeader.getValue(), securityHeader.getValue())));
            if (noneMatch) {
                notMatching.addAll(securityHeaders.getValue());
            }
        }

        return notMatching;
    }

    private boolean containsAsAlphanumeric(String string1, String string2) {
        return string1.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT)
                .contains(string2.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return ConsoleUtils.sanitizeFuzzerName(this.getClass().getSimpleName());
    }

    @Override
    public String description() {
        return "check all responses for good practices around Security related headers like: " + SECURITY_HEADERS_AS_STRING;
    }
}
