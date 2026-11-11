package com.endava.cats.model;

/**
 * How reliably a security finding is attributed to the injected payload, modelled after the
 * certain/firm/tentative scale used by Burp Scanner and the confidence levels used by ZAP alerts.
 */
public enum FindingConfidence {
    /**
     * Strong evidence that is absent from a baseline response of the same operation and reproduced on replay.
     */
    CONFIRMED("strong evidence, absent from the baseline response and reproduced on replay"),
    /**
     * Evidence that reproduced on replay, but is either strong without a baseline to compare against,
     * or weak while being absent from the baseline response.
     */
    FIRM("evidence reproduced on replay, but either no baseline response was available or the evidence is heuristic"),
    /**
     * Heuristic evidence that could not be attributed to the payload, or did not reproduce on replay.
     */
    TENTATIVE("heuristic evidence that could not be verified against a baseline response or did not reproduce on replay");

    private final String description;

    FindingConfidence(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /**
     * Whether the finding is reliable enough to be reported as an error.
     *
     * @return true for {@link #CONFIRMED} and {@link #FIRM}
     */
    public boolean isReliable() {
        return this != TENTATIVE;
    }
}
