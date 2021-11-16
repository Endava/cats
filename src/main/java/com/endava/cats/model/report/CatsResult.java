package com.endava.cats.model.report;

import lombok.Getter;

public enum CatsResult {
    OK("Response matches expected result. Response code [{}] is documented and response body matches the corresponding schema.", "All Good!"),
    NOT_MATCHING_RESPONSE_SCHEMA("Response does NOT match expected result. Response code [{}] is documented, but response body does NOT matches the corresponding schema.", "Not Marching Response Schema"),
    UNDOCUMENTED_RESPONSE_CODE("Response does NOT match expected result. Response code is from a list of expected codes for this FUZZER, but it is undocumented: expected {}, actual [{}], documented response codes: {}", "Undocumented Response Code"),
    UNEXPECTED_RESPONSE_CODE("Response does NOT match expected result. Response code is NOT from a list of expected codes for this FUZZER: expected {}, actual [{}]", "Unexpected Response Code"),
    NOT_IMPLEMENTED("Response HTTP code 501: you forgot to implement this functionality!", "Not Implemented"),
    UNEXPECTED_BEHAVIOUR("Unexpected behaviour: expected {}, actual [{}]", "Unexpected behaviour"),
    EXCEPTION("Fuzzer [{}] failed due to [{}]", "Unexpected Exception");

    @Getter
    private final String message;
    @Getter
    private final String reason;


    CatsResult(String message, String reason) {
        this.message = message;
        this.reason = reason;
    }
}
