package com.endava.cats.command.triage;

import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.Problem;
import com.endava.cats.report.ClusterCompute;
import com.endava.cats.report.problem.ProblemCatalog;
import com.endava.cats.report.problem.ResponseSchemaNames;
import com.endava.cats.util.JsonUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes the {@code cats-summary-report.json} of a folder of {@code TestN.json} fixtures the way the report writer
 * does: templates for 4xx/5xx responses, then problems.
 */
public final class TriageReportFixtures {
    private TriageReportFixtures() {
    }

    public static void writeSummary(Path folder) throws IOException {
        List<CatsTestCaseSummary> summaries = TriageEngine.loadTestCases(folder).stream().map(TriageReportFixtures::summaryOf).toList();
        ClusterCompute.assignErrorTemplates(summaries);
        List<Problem> problems = ProblemCatalog.build(summaries, new ResponseSchemaNames(null));
        CatsTestReport report = CatsTestReport.builder().testCases(summaries).problems(problems).problemKeyVersion(Problem.KEY_VERSION)
                .processingErrors(List.of()).build();
        Files.writeString(folder.resolve(TriageEngine.SUMMARY_FILE), JsonUtils.GSON.toJson(report));
    }

    /**
     * Compact fixtures often omit the result reason, which real reports always carry.
     */
    private static CatsTestCaseSummary summaryOf(TriageTestCase test) {
        CatsTestCase testCase = test.testCase();
        CatsResponse response = testCase.getResponse();
        testCase.setResponse(CatsResponse.builder().responseCode(response.getResponseCode()).body(test.rawResponseBody()).build());
        if (StringUtils.isBlank(testCase.getResultReason())) {
            testCase.setResultReason("Unexpected response code: " + response.getResponseCode());
        }
        return CatsTestCaseSummary.fromCatsTestCase(testCase);
    }
}
