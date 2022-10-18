package com.endava.cats.model;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum CatsResult {
    OK("Response matches expected result. Response code [$code] is documented and response body matches the corresponding schema.", "All Good!"),
    NOT_MATCHING_RESPONSE_SCHEMA("Response does NOT match expected result. Response code [$code] is documented, but response body does NOT matches the corresponding schema.", "Not Matching Response Schema"),
    UNDOCUMENTED_RESPONSE_CODE("Response does NOT match expected result. Response code is from a list of expected codes for this FUZZER, but it is undocumented: expected $expected_rc, actual [$code], documented response codes: $documented_rc", "Undocumented Response Code: $code"),
    UNEXPECTED_RESPONSE_CODE("Response does NOT match expected result. Response code is NOT from a list of expected codes for this FUZZER: expected $expected_rc, actual [$code]", "Unexpected Response Code: $code"),
    NOT_IMPLEMENTED("Response HTTP code 501: you forgot to implement this functionality!", "Not Implemented"),
    NOT_FOUND("Response HTTP code 404: you might need to provide business context using --refData or --urlParams", "Not Found"),
    UNEXPECTED_BEHAVIOUR("Unexpected behaviour: expected $expected_rc, actual [$code]", "Unexpected behaviour: $code"),
    EXCEPTION("Fuzzer [$expected_rc] failed due to [$documented_rc]", "Unexpected Exception");

    private final String message;
    private final String reason;

    private String responseCode = "";
    private String expectedResponseCodes = "";
    private String documentedResponseCodes = "";

    public CatsResult withResponseCode(String rc) {
        this.responseCode = rc;
        return this;
    }

    public CatsResult withExpectedResponseCodes(String responseCodes) {
        this.expectedResponseCodes = responseCodes;
        return this;
    }

    public CatsResult withDocumentedResponseCodes(String documentedResponseCodes) {
        this.documentedResponseCodes = documentedResponseCodes;
        return this;
    }

    public String getMessage() {
        return this.message.replace("$code", responseCode)
                .replace("$expected_rc", expectedResponseCodes)
                .replace("$documented_rc", documentedResponseCodes);
    }

    public String getReason() {
        return this.reason.replace("$code", responseCode);
    }
}
