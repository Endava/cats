package com.endava.cats.report;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.Problem;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonArray;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
class SummaryTableDataTest {
    @Test
    void shouldIncludeEveryTestAndKeepSharedProblemLabelsSeparateFromRows() {
        var tests = ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun());
        ClusterCompute.assignErrorTemplates(tests, ProblemReportFixtures.RISK_CONTRACT);
        var problems = com.endava.cats.report.problem.ProblemCatalog.build(tests,
                new com.endava.cats.report.problem.ResponseSchemaNames(ProblemReportFixtures.RISK_CONTRACT));
        ProblemPage page = ProblemPage.of(problems, tests, true);

        JsonArray data = JsonUtils.parseAsJsonElement(SummaryTableData.of(page)).getAsJsonArray();

        Assertions.assertThat(data.get(0).getAsJsonArray()).hasSize(tests.size());
        Assertions.assertThat(data.get(1).getAsJsonObject().keySet()).containsExactlyInAnyOrderElementsOf(problems.stream().map(Problem::id).toList());
        for (int i = 0; i < tests.size(); i++) {
            JsonArray row = data.get(0).getAsJsonArray().get(i).getAsJsonArray();
            Assertions.assertThat(row).hasSize(9);
            Assertions.assertThat(row.get(0).getAsString()).isEqualTo(tests.get(i).getId());
            Assertions.assertThat(row.get(5).getAsString()).isEqualTo(tests.get(i).getResult());
        }
    }

    @Test
    void shouldKeepScriptAndHtmlPayloadsInertWithoutChangingTheirText() {
        String attack = "</script><script>alert('test')</script>&\u2028";
        CatsTestCase test = new CatsTestCase();
        test.setTestId("Test 1");
        test.setScenario(attack);
        test.setResult("success");
        test.setRequest(CatsRequest.builder().httpMethod("GET").build());
        test.setResponse(CatsResponse.empty());
        ProblemPage page = ProblemPage.of(List.of(), List.of(CatsTestCaseSummary.fromCatsTestCase(test)), true);

        String json = SummaryTableData.of(page);

        Assertions.assertThat(json).doesNotContain("</script>", "<script>", "\u2028");
        Assertions.assertThat(JsonUtils.parseAsJsonElement(json).getAsJsonArray().get(0).getAsJsonArray().get(0).getAsJsonArray().get(4).getAsString())
                .isEqualTo(attack);
    }
}
