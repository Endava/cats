package com.endava.cats.model;

/**
 * A security finding detected in a response, together with how reliably it is attributed to the fuzzed input.
 *
 * @param reason     the result reason (finding title)
 * @param message    the finding details
 * @param confidence the confidence of the finding
 */
public record SecurityFinding(String reason, String message, FindingConfidence confidence) {

    /**
     * Returns the finding details followed by the confidence level and what it means.
     *
     * @return the message enriched with the confidence explanation
     */
    public String messageWithConfidence() {
        return "%s Confidence: %s (%s).".formatted(message, confidence, confidence.description());
    }
}
