package com.endava.cats.report;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.ErrorSignature;
import com.endava.cats.report.error.contract.ErrorResponseContract;
import com.endava.cats.report.error.contract.ErrorResponseContracts;
import com.endava.cats.report.error.mask.ErrorSignatureMasker;
import com.endava.cats.report.error.template.ErrorSample;
import com.endava.cats.report.error.template.ErrorTemplate;
import com.endava.cats.report.error.template.ErrorTemplateMiner;
import io.swagger.v3.oas.models.OpenAPI;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Utility class that mines the error templates of the failing/warning test cases whose response carries an error
 * message (4xx/5xx), partitioned by result reason (see {@link ErrorTemplateMiner}). Responses that carry no error
 * message (2xx/3xx, no response) get no template: their body is the returned resource, not a message.
 */
public class ClusterCompute {

    private ClusterCompute() {
        // Prevent instantiation of utility class
    }

    /**
     * Mines and assigns an error template to every error and warning test case with a 4xx/5xx response, partitioned
     * by result reason. Does nothing when all of them already carry a template, e.g. summaries read back from
     * {@code cats-summary-report.json}.
     *
     * @param testCases the test case summaries of a run
     */
    public static void assignErrorTemplates(List<CatsTestCaseSummary> testCases) {
        assignErrorTemplates(testCases, null);
    }

    /**
     * Mines and assigns an error template to every error and warning test case with a 4xx/5xx response, reading the
     * error bodies by the field roles the contract declares for them. Templates that a report of an older version
     * stored for other responses are dropped. Does nothing more when all 4xx/5xx test cases already carry a template.
     *
     * @param testCases the test case summaries of a run
     * @param openAPI   the contract of the run, or {@code null} to read the error bodies by field name only
     */
    public static void assignErrorTemplates(List<CatsTestCaseSummary> testCases, OpenAPI openAPI) {
        List<CatsTestCaseSummary> reportableCases = filterReportableTestCases(Optional.ofNullable(testCases).orElse(List.of()));
        reportableCases.stream().filter(Predicate.not(ClusterCompute::carriesErrorMessage))
                .forEach(testCase -> testCase.assignErrorTemplate(null, null));
        List<CatsTestCaseSummary> errorMessageCases = reportableCases.stream().filter(ClusterCompute::carriesErrorMessage).toList();
        if (errorMessageCases.stream().map(CatsTestCaseSummary::getErrorTemplateId).allMatch(Objects::nonNull)) {
            return;
        }
        List<ErrorTemplate> templates = ErrorTemplateMiner.mine(errorMessageCases.stream().map(testCase -> errorSample(testCase, openAPI)).toList());
        IntStream.range(0, errorMessageCases.size())
                .forEach(index -> errorMessageCases.get(index).assignErrorTemplate(templates.get(index).id(), templates.get(index).text()));
    }

    /**
     * Whether the response of a test case carries an error message that a template is mined for.
     *
     * @param testCase the test case summary
     * @return {@code true} for 4xx and 5xx responses
     */
    static boolean carriesErrorMessage(CatsTestCaseSummary testCase) {
        int status = testCase.getHttpResponseCode();
        return ResponseCodeFamily.is4xxCode(status) || ResponseCodeFamily.is5xxCode(status);
    }

    private static ErrorSample errorSample(CatsTestCaseSummary testCase, OpenAPI openAPI) {
        return new ErrorSample(testCase.getResultReason(), testCase.getHttpResponseCode(), testCase.getErrorSignature()
                .orElseGet(() -> signatureOf(testCase, openAPI)));
    }

    private static ErrorSignature signatureOf(CatsTestCaseSummary testCase, OpenAPI openAPI) {
        ErrorResponseContract declaredBody = ErrorResponseContracts.lookup(openAPI, testCase.getHttpMethod(), testCase.getPath(),
                testCase.getHttpResponseCode());
        return ErrorSignatureMasker.signatureOf(testCase.getResponseBody(), null, testCase.getRequestVocabulary(), declaredBody);
    }

    private static List<CatsTestCaseSummary> filterReportableTestCases(List<CatsTestCaseSummary> testCases) {
        // Stream.toList() never returns null; keep that contract explicit across template assignment.
        return Objects.requireNonNull(testCases.stream()
                .filter(Objects::nonNull)
                .filter(tc -> StringUtils.isNotBlank(tc.getResultReason())
                        && (tc.getError() || tc.getWarning()))
                .toList());
    }
}
