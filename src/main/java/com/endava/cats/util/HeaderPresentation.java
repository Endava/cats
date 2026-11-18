package com.endava.cats.util;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;

import java.util.List;
import java.util.Optional;

/** Shared request/response header presentation, without changing the recorded request. */
public final class HeaderPresentation {
    private final ReportingArguments arguments;

    public HeaderPresentation(ReportingArguments arguments) {
        this.arguments = arguments;
    }

    public boolean shouldMaskRequest(CatsTestCase testCase, KeyValuePair<String, ?> header) {
        return shouldMask(header, SyntheticAuthHeaderPolicy.allows(testCase, header));
    }

    public Object requestValue(CatsTestCase testCase, KeyValuePair<String, ?> header) {
        return value(header, SyntheticAuthHeaderPolicy.allows(testCase, header));
    }

    public Object responseValue(KeyValuePair<String, ?> header) {
        return value(header, false);
    }

    Object value(KeyValuePair<String, ?> header, boolean publicProbe) {
        if (SensitiveDataPolicy.REDACTED.equals(header.getValue())) {
            return SensitiveDataPolicy.REDACTED;
        }
        return shouldMask(header, publicProbe)
                ? SensitiveDataPolicy.placeholder(SensitiveDataPolicy.headerEnvironmentVariable(header.getKey())) : header.getValue();
    }

    private boolean shouldMask(KeyValuePair<String, ?> header, boolean publicProbe) {
        return SensitiveDataPolicy.isSensitiveHeader(header.getKey(), arguments.getMaskHeaders(), true)
                || (arguments.shouldMaskHeader(header.getKey()) && !publicProbe);
    }

    public List<KeyValuePair<String, Object>> requestHeaders(CatsTestCase testCase) {
        return Optional.ofNullable(request(testCase).getHeaders()).orElse(List.of()).stream()
                .map(header -> new KeyValuePair<>(header.getKey(), requestValue(testCase, header))).toList();
    }

    public List<KeyValuePair<String, Object>> responseHeaders(CatsResponse response) {
        return Optional.ofNullable(response).map(CatsResponse::getHeaders).orElse(List.of()).stream()
                .map(header -> new KeyValuePair<>(header.getKey(), responseValue(header))).toList();
    }

    /** Uses the same shell quoting as replay and triage, with the report's effective URL. */
    public String curl(CatsTestCase testCase) {
        CatsRequest source = request(testCase);
        return source.toBuilder().headers(requestHeaders(testCase))
                .url(Optional.ofNullable(testCase.getFullRequestPath()).orElse(source.getUrl())).build().toCurlCommand();
    }

    private static CatsRequest request(CatsTestCase testCase) {
        return Optional.ofNullable(testCase.getRequest()).orElseGet(CatsRequest::empty);
    }
}
