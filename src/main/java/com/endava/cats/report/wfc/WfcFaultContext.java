package com.endava.cats.report.wfc;

import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ErrorSignature;
import com.endava.cats.report.error.mask.ErrorSignatureMasker;
import com.endava.cats.report.error.template.ErrorTemplate;

/**
 * The context of a WFC HTTP 500 fault. WFC counts a fault once per operation, category and context, so the context
 * tells two crashes of one operation apart without the server's code location, which a black-box tool cannot see.
 * <p>
 * It is the masked error signature of the response, the same one error templates are mined from
 * ({@code type: message @ frame}): values echoed from the request, ids and timestamps do not make one crash look like
 * several, and stack frames of every runtime tell crash sites apart.
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
