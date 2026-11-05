package com.endava.cats.command.triage;

import com.google.gson.JsonObject;

import java.util.Optional;

/**
 * Interpretation of a {@code 400}/{@code 409}/{@code 422} rejection of a request that was expected to succeed.
 *
 * @param message         the extracted error message
 * @param payload         the request body (first element for root arrays)
 * @param intent          what the message demands (presence, absence or uniqueness), if anything
 * @param referencedField the dotted request field the message is about
 * @param protectedField  the fuzzer-mutated field the server rejected; correlations cannot override fields a fuzzer
 *                        is mutating, so only {@code --refData} pinning (or a contract fix) helps
 */
record FailureAnalysis(ErrorMessage message,
                       JsonObject payload,
                       Optional<FailureIntent> intent,
                       Optional<String> referencedField,
                       Optional<String> protectedField) {

    boolean isDomainRejectionOfMutatedField() {
        return protectedField.isPresent();
    }
}
