package com.endava.cats.report;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.FindingEvidence;
import com.endava.cats.report.security.SecurityFindingClassifier;

/** Selects crash and trustworthy security findings without changing their underlying oracle classification. */
public final class BlackboxSeverityPolicy {
    private BlackboxSeverityPolicy() {
    }

    /** Security findings require explicit confirmation; an error verdict alone does not establish confidence. */
    public static boolean isReportable(FindingEvidence evidence) {
        if (ResponseCodeFamily.isUnimplemented(evidence.responseCode())) {
            return false;
        }
        if (ResponseCodeFamily.is5xxCode(evidence.responseCode())) {
            return true;
        }
        return evidence.confidence() == FindingConfidence.CONFIRMED && SecurityFindingClassifier.isHighSeverity(evidence);
    }
}
