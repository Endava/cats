package com.endava.cats.tui.model;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.util.KeyValuePair;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, presentation-safe snapshot of a completed test case.
 */
public record TestResultSnapshot(String id, String traceId, String scenario, String expectedResult, String result,
                                 String resultReason, String resultDetails, String resultIgnoreDetails, String fuzzer,
                                 String path, String contractPath, String fullRequestPath, String server,
                                 boolean validJson, RequestSnapshot request, ResponseSnapshot response,
                                 String replayCommand) {
    /**
     * Creates a snapshot and applies the same configured header masking convention used by reports.
     *
     * @param testCase source test case
     * @param maskedHeaders header names whose values must not be exposed
     * @return immutable test result snapshot
     */
    public static TestResultSnapshot from(CatsTestCase testCase, Set<String> maskedHeaders) {
        Set<String> headersToMask = Optional.ofNullable(maskedHeaders).orElse(Set.of());
        return new TestResultSnapshot(testCase.getTestId(), testCase.getTraceId(), testCase.getScenario(),
                testCase.getExpectedResult(), testCase.getResult(), testCase.getResultReason(),
                testCase.getResultDetails(), testCase.getResultIgnoreDetails(), testCase.getFuzzer(),
                testCase.getPath(), testCase.getContractPath(), testCase.getFullRequestPath(), testCase.getServer(),
                testCase.isValidJson(), RequestSnapshot.from(testCase.getRequest(), headersToMask),
                ResponseSnapshot.from(testCase.getResponse(), headersToMask), testCase.getCatsReplay());
    }

    /**
     * Immutable request details.
     */
    public record RequestSnapshot(String httpMethod, String url, String timestamp, String payload,
                                  List<HeaderSnapshot> headers) {
        private static RequestSnapshot from(CatsRequest request, Set<String> maskedHeaders) {
            CatsRequest source = Optional.ofNullable(request).orElseGet(CatsRequest::empty);
            return new RequestSnapshot(source.getHttpMethod(), source.getUrl(), source.getTimestamp(), source.getPayload(),
                    snapshotHeaders(source.getHeaders(), maskedHeaders));
        }
    }

    /**
     * Immutable response details.
     */
    public record ResponseSnapshot(int responseCode, String httpMethod, long responseTimeInMs,
                                   long contentLengthInBytes, long numberOfWords, long numberOfLines,
                                   String contentType, String body, List<HeaderSnapshot> headers) {
        private static ResponseSnapshot from(CatsResponse response, Set<String> maskedHeaders) {
            CatsResponse source = Optional.ofNullable(response).orElseGet(CatsResponse::empty);
            return new ResponseSnapshot(source.getResponseCode(), source.getHttpMethod(), source.getResponseTimeInMs(),
                    source.getContentLengthInBytes(), source.getNumberOfWordsInResponse(),
                    source.getNumberOfLinesInResponse(), source.getResponseContentType(), source.getBody(),
                    snapshotHeaders(source.getHeaders(), maskedHeaders));
        }
    }

    /**
     * Immutable header entry which preserves duplicate headers and their order.
     */
    public record HeaderSnapshot(String name, String value) {
    }

    private static List<HeaderSnapshot> snapshotHeaders(List<? extends KeyValuePair<String, ?>> headers,
                                                        Set<String> maskedHeaders) {
        return Optional.ofNullable(headers).orElse(List.of()).stream()
                .map(header -> new HeaderSnapshot(header.getKey(), maskedValue(header, maskedHeaders)))
                .toList();
    }

    private static String maskedValue(KeyValuePair<String, ?> header, Set<String> maskedHeaders) {
        if (maskedHeaders.contains(header.getKey())) {
            return "$$" + header.getKey().replaceAll("[_-]*", "");
        }
        return String.valueOf(header.getValue());
    }
}
