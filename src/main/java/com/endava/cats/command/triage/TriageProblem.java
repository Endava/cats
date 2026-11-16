package com.endava.cats.command.triage;

import com.endava.cats.model.ProblemKind;

import java.util.List;

/**
 * A problem of the report (as persisted in {@code cats-summary-report.json}) with the triage of each of its
 * occurrences. {@code errorTemplateId}/{@code errorTemplate} are the server message, only for problems identified by it.
 *
 * @param problemId       the problem id, the same as in the HTML report and the summary
 * @param kind            what the oracle judged
 * @param title           the problem title
 * @param level           {@code error} or {@code warn}
 * @param reasons         the result reasons of the triaged tests
 * @param testCount       the number of triaged tests
 * @param errorTemplateId the server message id, or {@code null}
 * @param errorTemplate   the server message, or {@code null}
 * @param occurrences     the problem per operation, triaged
 */
public record TriageProblem(String problemId, ProblemKind kind, String title, String level, List<String> reasons,
                            int testCount, String errorTemplateId, String errorTemplate, List<TriageOccurrence> occurrences) {
}
