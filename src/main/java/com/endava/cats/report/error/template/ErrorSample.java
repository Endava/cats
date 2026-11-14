package com.endava.cats.report.error.template;

import com.endava.cats.model.ErrorSignature;

import java.util.Objects;

/**
 * One error response to mine a template for. Samples are only compared with samples of the same partition:
 * same scope, status code, error type and stack frame.
 *
 * @param scope      an extra partition dimension chosen by the caller (e.g. the result reason), or empty
 * @param statusCode the HTTP status code
 * @param signature  the masked error signature
 */
public record ErrorSample(String scope, int statusCode, ErrorSignature signature) {

    String partitionKey() {
        return String.join("|", Objects.requireNonNullElse(scope, ""), String.valueOf(statusCode),
                signature.errorType(), signature.stackFrame());
    }
}
