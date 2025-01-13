package com.endava.cats.fuzzer.headers;

import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.api.Fuzzer;
import com.endava.cats.fuzzer.executor.SimpleExecutor;
import com.endava.cats.fuzzer.executor.SimpleExecutorContext;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsResultFactory;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.ConsoleUtils;
import com.endava.cats.util.KeyValuePair;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.endava.cats.util.WordUtils.matchesAsLowerCase;

/**
 * Check that responses include Content-Type, Content-Type-Options, X-Frame-Options: deny
 */
@Singleton
@HeaderFuzzer
public class CheckSecurityHeadersFuzzer implements Fuzzer {

    static final String SECURITY_HEADERS_AS_STRING;
    static final Map<String, List<KeyValuePair<String, String>>> SECURITY_HEADERS = new HashMap<>();

    static {
        SECURITY_HEADERS.put("Cache-Control", Collections.singletonList(new KeyValuePair<>("Cache-Control", ".*no-store.*")));
        SECURITY_HEADERS.put("X-Content-Type-Options", Collections.singletonList(new KeyValuePair<>("X-Content-Type-Options", "nosniff")));
        SECURITY_HEADERS.put("X-Frame-Options/Content-Security-Policy", List.of(new KeyValuePair<>("X-Frame-Options", "DENY"),
                new KeyValuePair<>("Content-Security-Policy", "frame-ancestors 'none'")));
        SECURITY_HEADERS.put("X-XSS-Protection", List.of(new KeyValuePair<>("X-XSS-Protection", null),
                new KeyValuePair<>("X-XSS-Protection", "0")));

        SECURITY_HEADERS_AS_STRING = new HashSet<>(SECURITY_HEADERS.keySet()).toString();
    }

    private final PrettyLogger log = PrettyLoggerFactory.getLogger(this.getClass());
    private final TestCaseListener testCaseListener;
    private final SimpleExecutor simpleExecutor;

    /**
     * Creates a new instance.
     *
     * @param lr             listener used to report test cases progress
     * @param simpleExecutor executor used to run the fuzz logic
     */
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
                        .expectedResponseCode(ResponseCodeFamilyPredefined.TWOXX)
                        .responseProcessor(this::checkResponse)
                        .build()
        );
    }

    private void checkResponse(CatsResponse response, FuzzingData data) {
        List<KeyValuePair<String, String>> missingSecurityHeaders = this.getMissingSecurityHeaders(response);
        if (!missingSecurityHeaders.isEmpty()) {
            testCaseListener.reportResultError(log, data, CatsResultFactory.Reason.MISSING_SECURITY_HEADERS.value(),
                    "Missing recommended Security Headers: {}", missingSecurityHeaders.stream().map(pair -> pair.getKey() + "=" + pair.getValue()).collect(Collectors.toSet()));
        } else {
            testCaseListener.reportResult(log, data, response, ResponseCodeFamilyPredefined.TWOXX);
        }
    }

    private List<KeyValuePair<String, String>> getMissingSecurityHeaders(CatsResponse catsResponse) {
        return SECURITY_HEADERS.entrySet().stream()
                .filter(entry -> {
                    String headerName = entry.getKey();
                    List<KeyValuePair<String, String>> possibleValues = entry.getValue();

                    if (possibleValues.stream().noneMatch(possibleHeader -> catsResponse.containsHeader(possibleHeader.getKey()))) {
                        return possibleValues.stream().noneMatch(keyPair -> keyPair.getValue() == null);
                    }

                    KeyValuePair<String, String> responseHeader = catsResponse.getHeader(headerName);
                    return responseHeader != null &&
                            possibleValues.stream().noneMatch(possibleHeader -> this.matchesSecurityHeader(possibleHeader, responseHeader));
                }).flatMap(entry -> entry.getValue().stream())
                .toList();
    }

    private boolean matchesSecurityHeader(KeyValuePair<String, String> expected, KeyValuePair<String, String> actual) {
        if (expected.getValue() == null || actual.getValue() == null) {
            return false;
        }
        return matchesAsLowerCase(expected.getKey(), actual.getKey()) &&
                matchesAsLowerCase(expected.getValue(), actual.getValue());
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
