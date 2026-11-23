package com.endava.cats.report.wfc;

import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ErrorSignature;
import com.endava.cats.report.error.mask.ErrorSignatureMasker;
import com.endava.cats.report.error.template.ErrorTemplate;

/**
 * The context of a WFC HTTP 500 fault. WFC counts a fault once per operation, category and context, so the context
 * tells two crashes of one operation apart without the server's code location, which a black-box tool cannot see.
 * <p>
 * The context renders the masked error signature as {@code type: message @ frame}. WFC retains its historical
 * request-vocabulary guard independently of problem-normalization fixes, so existing fault identities and counts
 * remain compatible. Generic masks normalize ids and timestamps; application stack frames distinguish crash sites.
 */
final class WfcFaultContext {

    private WfcFaultContext() {
    }

    /**
     * Builds the context of a response. It uses the signature computed from the full response body at test time, and
     * masks the summarized body when the summary was read back from a report.
     *
     * @param summary the test case summary of an HTTP 500 response
     * @return the fault context
     */
    static String of(CatsTestCaseSummary summary) {
        ErrorSignature signature = summary.getErrorSignature().orElseGet(() -> ErrorSignatureMasker.signatureOf(
                summary.getResponseBody(), summary.getResultDetails(), summary.getRequestVocabulary()));
        return ErrorTemplate.textOf(signature);
    }
}
