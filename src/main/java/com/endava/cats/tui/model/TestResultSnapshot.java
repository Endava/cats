package com.endava.cats.tui.model;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.HeaderPresentation;

import java.util.List;
import java.util.Optional;

/**
 * Immutable, presentation-safe snapshot of a completed test case.
 */
public record TestResultSnapshot(String id, String traceId, String scenario, String expectedResult, String result,
                                 String resultReason, String resultDetails, String resultIgnoreDetails, String fuzzer,
                                 String path, String contractPath, String fullRequestPath, String server,
                                 boolean validJson, RequestSnapshot request, ResponseSnapshot response,
                                 String replayCommand, String curlCommand,
                                 List<String> mutationTargets, List<String> resourceCorrelations) {

    public TestResultSnapshot(String id, String traceId, String scenario, String expectedResult, String result,
                              String resultReason, String resultDetails, String resultIgnoreDetails, String fuzzer,
                              String path, String contractPath, String fullRequestPath, String server,
                              boolean validJson, RequestSnapshot request, ResponseSnapshot response,
                              String replayCommand) {
        this(id, traceId, scenario, expectedResult, result, resultReason, resultDetails, resultIgnoreDetails,
                fuzzer, path, contractPath, fullRequestPath, server, validJson, request, response,
                replayCommand, null, List.of(), List.of());
    }

    /**
     * Creates a snapshot and applies the same configured header masking convention used by reports.
     *
     * @param testCase source test case
     * @param arguments the same masking arguments used by report writers
     * @return immutable test result snapshot
     */
    public static TestResultSnapshot from(CatsTestCase testCase, ReportingArguments arguments) {
        HeaderPresentation headers = new HeaderPresentation(arguments);
        List<String> targets = Optional.ofNullable(testCase.getMutationTargets()).orElse(List.of()).stream()
                .map(target -> target.location().getDisplayName() + ": " + target.name())
                .toList();
        List<String> correlations = Optional.ofNullable(testCase.getRuntimeCorrelations()).orElse(List.of()).stream()
                .map(corr -> "%s:%s <- %s %s (%s)".formatted(
                        corr.getTargetLocation(), corr.getTargetField(),
                        corr.getSourceMethod(), corr.getSourcePath(), corr.getSourceLocation()))
                .toList();
        RequestSnapshot reqSnapshot = RequestSnapshot.from(testCase, headers);
        return new TestResultSnapshot(testCase.getTestId(), testCase.getTraceId(), testCase.getScenario(),
                testCase.getExpectedResult(), testCase.getResult(), testCase.getResultReason(),
                testCase.getResultDetails(), testCase.getResultIgnoreDetails(), testCase.getFuzzer(),
                testCase.getPath(), testCase.getContractPath(), testCase.getFullRequestPath(), testCase.getServer(),
                testCase.isValidJson(), reqSnapshot,
                ResponseSnapshot.from(testCase.getResponse(), headers), testCase.getCatsReplay(),
                headers.curl(testCase), targets, correlations);
    }

    /**
     * Immutable request details.
     */
    public record RequestSnapshot(String httpMethod, String url, String timestamp, String payload,
                                  List<HeaderSnapshot> headers) {
        private static RequestSnapshot from(CatsTestCase testCase, HeaderPresentation headers) {
            CatsRequest source = Optional.ofNullable(testCase.getRequest()).orElseGet(CatsRequest::empty);
            return new RequestSnapshot(source.getHttpMethod(), source.getUrl(), source.getTimestamp(), source.getPayload(),
                    snapshotHeaders(headers.requestHeaders(testCase)));
        }
    }

    /**
     * Immutable response details.
     */
    public record ResponseSnapshot(int responseCode, String httpMethod, long responseTimeInMs,
                                   long contentLengthInBytes, long declaredContentLength, boolean bodyTruncated,
                                   long numberOfWords, long numberOfLines, String contentType, String body,
                                   List<HeaderSnapshot> headers) {
        private static ResponseSnapshot from(CatsResponse response, HeaderPresentation headers) {
            CatsResponse source = Optional.ofNullable(response).orElseGet(CatsResponse::empty);
            return new ResponseSnapshot(source.getResponseCode(), source.getHttpMethod(), source.getResponseTimeInMs(),
                    source.getContentLengthInBytes(), source.getDeclaredContentLength(), source.isBodyTruncated(),
                    source.getNumberOfWordsInResponse(), source.getNumberOfLinesInResponse(), source.getResponseContentType(), source.getBody(),
                    snapshotHeaders(headers.responseHeaders(source)));
        }
    }

    /**
     * Immutable header entry which preserves duplicate headers and their order.
     */
    public record HeaderSnapshot(String name, String value) {
    }

    private static List<HeaderSnapshot> snapshotHeaders(List<? extends KeyValuePair<String, ?>> headers) {
        return headers.stream().map(header -> new HeaderSnapshot(header.getKey(), String.valueOf(header.getValue()))).toList();
    }
}
