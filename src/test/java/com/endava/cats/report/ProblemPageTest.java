package com.endava.cats.report;

import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.report.problem.ProblemCatalog;
import com.endava.cats.report.problem.ResponseSchemaNames;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

@QuarkusTest
class ProblemPageTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "/risk-rules/{ruleId}|/<wbr>risk-rules/<wbr><span class=\"param-tok\">{ruleId}</span>",
            "/a<b>&'c\"|/<wbr>a&lt;b&gt;&amp;&#39;c&quot;",
            "NULL|''"
    })
    void shouldRenderTablePathsOnTheServer(String path, String expected) {
        Assertions.assertThat(ProblemPage.TestRow.pathHtml(path)).isEqualTo(expected);
    }

    @Test
    void shouldLeadWithTheOutcomeAndKeepTheExampleInProblemOptions() {
        ProblemCard message = card("Request rejected (HTTP 422): Field is required", "Field is required");
        ProblemCard accepted = card("XSS payload reflected in response (HTTP 2xx)", null);

        Assertions.assertThat(message.heading()).isEqualTo("Expected HTTP 405; received HTTP 422.");
        Assertions.assertThat(message.filterLabel()).contains("Field is required");
        Assertions.assertThat(message.filterTitle()).contains("Expected HTTP 405; received HTTP 422.", "Field is required");
        Assertions.assertThat(accepted.heading()).isEqualTo("XSS payload reflected in response (HTTP 2xx)");
    }

    @Test
    void shouldLinkTheUnmodifiedRequestOnceWhenSeveralTestsReferToIt() {
        var cards = cards(ProblemReportFixtures.riskApiRun());
        ProblemCard annotated = cards.stream().filter(ProblemCard::hasBaselineNote).findFirst().orElseThrow();

        Assertions.assertThat(annotated.baselineNote()).startsWith("2 of 8");
        Assertions.assertThat(annotated.baselineTests()).containsExactly(new ProblemCard.TestLink("Test2", "Test 2"));
        Assertions.assertThat(annotated.baselineTestsLabel()).isEqualTo("Unmodified request");
    }

    @Test
    void shouldLinkDistinctUnmodifiedRequestsAndSkipReferencesMissingFromTheReport() {
        var tests = ProblemReportFixtures.riskApiRun();
        tests.get(2).setFuzzer("OnlyRequiredFields");
        tests.get(5).setSameErrorAsBaselineTest("Test 3");
        ProblemCard annotated = cards(tests).stream().filter(ProblemCard::hasBaselineNote).findFirst().orElseThrow();
        Assertions.assertThat(annotated.baselineTests()).extracting(ProblemCard.TestLink::key).containsExactly("Test2", "Test3");
        Assertions.assertThat(annotated.baselineTestsLabel()).isEqualTo("Unmodified requests");

        tests.get(3).setSameErrorAsBaselineTest("Test 9999");
        tests.get(5).setSameErrorAsBaselineTest("Test 9999");
        ProblemCard unavailable = cards(tests).stream().filter(ProblemCard::hasBaselineNote).findFirst().orElseThrow();
        Assertions.assertThat(unavailable.hasBaselineTests()).isFalse();
        Assertions.assertThat(unavailable.baselineNote()).startsWith("2 of 8");
    }

    @Test
    void shouldKeepTheProblemFilterLabelShortWithAStableRankAndCount() {
        ProblemCard longTitle = card("Request rejected: " + "a very long server validation message ".repeat(5), null);
        Assertions.assertThat(longTitle.filterLabel()).startsWith("1. Request rejected:").endsWith("... (1)");
        Assertions.assertThat(longTitle.filterLabel()).hasSizeLessThanOrEqualTo(55);
    }

    private static List<ProblemCard> cards(List<CatsTestCase> tests) {
        List<CatsTestCaseSummary> summaries = ProblemReportFixtures.summaries(tests);
        ClusterCompute.assignErrorTemplates(summaries, ProblemReportFixtures.RISK_CONTRACT);
        var problems = ProblemCatalog.build(summaries, new ResponseSchemaNames(ProblemReportFixtures.RISK_CONTRACT));
        return ProblemPage.of(problems, summaries, true).cards();
    }

    private static ProblemCard card(String title, String serverMessage) {
        return new ProblemCard(1, "1a2b3c4d", "problem-1a2b3c4d", "ERROR_MESSAGE", "Rejected", "error", title, "", serverMessage,
                serverMessage == null ? null : new ProblemMessage("Expected HTTP 405; received HTTP 422.", List.of(),
                        new ProblemCard.TestLink("Test7", "Test 7"), serverMessage),
                null, null, List.of(), "", List.of(), null, List.of(), List.of(), null, 1);
    }
}
