package com.endava.cats.report;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.FindingConfidence;
import com.endava.cats.model.ExpectedResponse;
import com.endava.cats.model.Problem;
import com.endava.cats.model.ProblemKind;
import com.endava.cats.report.problem.OccurrenceDelta.MatchedBy;
import com.endava.cats.report.problem.ProblemCatalog;
import com.endava.cats.report.problem.ResponseSchemaNames;
import com.endava.cats.report.wfc.WfcReportBuilder;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Card 17 section 10 invariants on reports written from synthetic runs shaped like the private reports.
 */
@QuarkusTest
class ProblemReportConsistencyTest {
    private static final String PROBLEMS_VARIABLE = "window.CATS_PROBLEMS = ";
    private static final Pattern ID_ATTRIBUTE = Pattern.compile("\\sid=\"([^\"]+)\"");
    private static final Pattern HREF_ANCHOR = Pattern.compile("href=\"#([^\"]+)\"");
    private static final Pattern CARD = Pattern.compile("<article id=\"([^\"]+)\"(.*?)</article>", Pattern.DOTALL);

    @TempDir
    Path reportDirectory;

    @BeforeEach
    void setUp() {
        CatsRandom.initRandom(0);
    }

    @Test
    void shouldPutEveryReportableTestInExactlyOneProblem() throws IOException {
        List<CatsTestCaseSummary> summaries = ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun());
        CatsTestReport report = write(summaries, reportDirectory);

        List<CatsTestCaseSummary> reportable = summaries.stream().filter(ProblemCatalog::isReportable).toList();
        List<String> occurrenceTests = report.getProblems().stream().flatMap(problem -> problem.occurrences().stream())
                .flatMap(occurrence -> occurrence.testIds().stream()).toList();
        Assertions.assertThat(report.getProblemKeyVersion()).isEqualTo(Problem.KEY_VERSION);
        Assertions.assertThat(report.getProblems().stream().mapToInt(Problem::testCount).sum()).isEqualTo(reportable.size());
        Assertions.assertThat(occurrenceTests).doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(reportable.stream().map(CatsTestCaseSummary::getId).toList());
        report.getTestCases().stream().filter(ProblemCatalog::isReportable).forEach(test -> Assertions.assertThat(report.getProblems())
                .filteredOn(problem -> problem.id().equals(test.getProblemId())).singleElement()
                .satisfies(problem -> Assertions.assertThat(problem.occurrences()).anyMatch(occurrence -> occurrence.testIds().contains(test.getId()))));
        Assertions.assertThat(report.getTestCases()).filteredOn(test -> !ProblemCatalog.isReportable(test)).allMatch(test -> test.getProblemId() == null);
    }

    @Test
    void shouldGroupTheRiskRunIntoItsFixes() throws IOException {
        CatsTestReport report = write(ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun()), reportDirectory);

        Assertions.assertThat(report.getProblems()).extracting(Problem::kind).containsExactly(
                ProblemKind.SERVER_ERROR, ProblemKind.ACCEPTED_REQUEST, ProblemKind.ERROR_MESSAGE, ProblemKind.ERROR_MESSAGE,
                ProblemKind.ACCEPTED_REQUEST, ProblemKind.ACCEPTED_REQUEST, ProblemKind.RESPONSE_VIOLATION,
                ProblemKind.RESPONSE_VIOLATION, ProblemKind.STATUS_CODE, ProblemKind.ACCEPTED_REQUEST);
        Problem schema = byKind(report, ProblemKind.RESPONSE_VIOLATION).stream().filter(problem -> problem.testCount() == 8).findFirst().orElseThrow();
        Assertions.assertThat(schema.occurrences()).hasSize(2);
        Assertions.assertThat(schema.sameAsBaseline()).isEqualTo(2);
        Problem status = byKind(report, ProblemKind.STATUS_CODE).getFirst();
        Assertions.assertThat(status.variants()).hasSize(2);
        Assertions.assertThat(status.alsoIn()).isNotEmpty();
        Assertions.assertThat(schema.title()).isEqualTo("Response does not match the schema RuleResponse");
        Assertions.assertThat(report.getProblems().getLast().title()).isEqualTo("Decimal fields right boundary accepted (HTTP 2xx)");
    }

    @Test
    void shouldStoreTemplatesOnlyForResponsesThatCarryAnErrorMessage() throws IOException {
        write(ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun()), reportDirectory);

        JsonObject json = JsonUtils.GSON.fromJson(Files.readString(reportDirectory.resolve("cats-summary-report.json")), JsonObject.class);
        List<JsonObject> tests = StreamSupport.stream(json.getAsJsonArray("testCases").spliterator(), false).map(JsonElement::getAsJsonObject).toList();
        Assertions.assertThat(tests).noneMatch(test -> test.has("errorTemplate")).noneMatch(test -> test.has("mutatedFields"));
        tests.stream().filter(test -> !"success".equals(test.get("result").getAsString())).forEach(test -> {
            int status = test.get("httpResponseCode").getAsInt();
            boolean carriesMessage = status >= 400 && status < 600;
            Assertions.assertThat(test.get("errorTemplateId").isJsonNull()).as(test.get("id").getAsString()).isEqualTo(!carriesMessage);
            Assertions.assertThat(test.get("problemId").getAsString()).matches("[0-9a-f]{8}");
        });
        Assertions.assertThat(json.get("problemKeyVersion").getAsInt()).isEqualTo(1);
    }

    @Test
    void shouldReadBackTheSameProblemIdsAndOccurrences() throws IOException {
        CatsTestReport written = write(ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun()), reportDirectory);
        CatsTestReport readBack = BaselineComparisonEngine.loadReport(reportDirectory);

        List<Problem> recomputed = ProblemCatalog.build(readBack.getTestCases(), new ResponseSchemaNames(ProblemReportFixtures.RISK_CONTRACT));
        Assertions.assertThat(readBack.getProblems()).isEqualTo(written.getProblems());
        Assertions.assertThat(recomputed).extracting(Problem::id).containsExactlyElementsOf(written.getProblems().stream().map(Problem::id).toList());
        Assertions.assertThat(recomputed).extracting(Problem::occurrences).extracting(occurrences -> occurrences.stream().map(Problem.Occurrence::testIds).toList())
                .containsExactlyElementsOf(written.getProblems().stream().map(problem -> problem.occurrences().stream().map(Problem.Occurrence::testIds).toList()).toList());
    }

    @Test
    void shouldWriteTheSameProblemsWhateverTheOrderOfTheTests() throws IOException {
        String expected = problemsJson(write(ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun()), reportDirectory.resolve("ordered")));
        List<CatsTestCaseSummary> shuffled = new ArrayList<>(ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun()));
        Collections.shuffle(shuffled, new Random(7));

        Assertions.assertThat(problemsJson(write(shuffled, reportDirectory.resolve("shuffled")))).isEqualTo(expected);
    }

    @Test
    void shouldKeepInjectionFindingsOnTheRequestSideWithTheirEvidence() throws IOException {
        CatsTestReport report = write(ProblemReportFixtures.summaries(ProblemReportFixtures.injectionRun()), reportDirectory);

        Assertions.assertThat(report.getProblems()).hasSize(4);
        Problem ssti = report.getProblems().stream().filter(problem -> problem.title().startsWith("SSTI")).findFirst().orElseThrow();
        Assertions.assertThat(ssti.kind()).isEqualTo(ProblemKind.ACCEPTED_REQUEST);
        Assertions.assertThat(ssti.title()).isEqualTo("SSTI injection vulnerability detected (HTTP 2xx)");
        Assertions.assertThat(ssti.occurrences()).hasSize(2).allMatch(occurrence -> occurrence.fields().equals(List.of(new Problem.FieldCount("body.name", 1))));
        Assertions.assertThat(ssti.evidence().confidence()).isEqualTo(FindingConfidence.CONFIRMED);
        Assertions.assertThat(ssti.evidence().details()).contains("evaluated to 49 in the response");
        Problem reflected = byTitle(report, "XSS payload reflected in response (HTTP 2xx)");
        Problem accepted = byTitle(report, "XSS payload accepted without validation (HTTP 2xx)");
        Assertions.assertThat(reflected.occurrences()).hasSize(2).allMatch(occurrence -> occurrence.fields().size() == 2);
        Assertions.assertThat(reflected.alsoIn()).containsExactly(accepted.id());
        Assertions.assertThat(accepted.alsoIn()).containsExactly(reflected.id());
        Problem sqli = byKind(report, ProblemKind.SERVER_ERROR).getFirst();
        Assertions.assertThat(sqli.testCount()).isEqualTo(2);
        Assertions.assertThat(sqli.errorTemplateId()).isNotBlank();
        Assertions.assertThat(sqli.occurrences().getFirst().fields()).extracting(Problem.FieldCount::field).containsExactly("body.name", "body.title");
        Assertions.assertThat(report.getProblems()).filteredOn(problem -> !problem.kind().isIdentifiedByServerMessage())
                .allMatch(problem -> problem.errorTemplate() == null && problem.errorTemplateId() == null)
                .noneMatch(problem -> problem.title().contains("<script>") || problem.title().contains("49"));
    }

    @Test
    void shouldKeepAnchorsBannersAndCardsConsistent() throws IOException {
        CatsTestReport report = write(ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun()), reportDirectory);
        String html = Files.readString(reportDirectory.resolve(TestCaseExporter.REPORT_HTML));
        Map<String, TestCaseExporter.ProblemBannerEntry> banners = banners(reportDirectory);
        Map<String, TestCaseExporter.ProblemIndexEntry> index = problemIndex(reportDirectory);
        Map<String, String> cards = cards(html);

        List<String> ids = ID_ATTRIBUTE.matcher(html).results().map(match -> match.group(1)).toList();
        Assertions.assertThat(ids).doesNotHaveDuplicates();
        Assertions.assertThat(cards.keySet()).containsExactlyElementsOf(report.getProblems().stream().map(problem -> "problem-" + problem.id()).toList());
        Assertions.assertThat(HREF_ANCHOR.matcher(html).results().map(match -> match.group(1)).distinct()).allMatch(ids::contains);
        Assertions.assertThat(banners.keySet()).containsExactlyInAnyOrderElementsOf(report.getTestCases().stream()
                .filter(ProblemCatalog::isReportable).map(CatsTestCaseSummary::getKey).toList());
        banners.forEach((testKey, banner) -> {
            Problem problem = report.getProblems().stream().filter(candidate -> candidate.id().equals(banner.problem())).findFirst().orElseThrow();
            TestCaseExporter.ProblemIndexEntry metadata = index.get(banner.problem());
            Assertions.assertThat(ids).contains(metadata.anchor());
            Assertions.assertThat(metadata.count()).isEqualTo(problem.testCount());
            Assertions.assertThat(metadata.title()).isEqualTo(problem.title());
            Assertions.assertThat(cards.get(metadata.anchor())).contains("href=\"" + testKey + ".html\"");
            Assertions.assertThat(JsonUtils.GSON.toJsonTree(banner).getAsJsonObject().keySet()).containsExactly("problem", "fields");
        });
        Assertions.assertThat(index.keySet()).containsExactlyElementsOf(report.getProblems().stream().map(Problem::id).toList());
    }

    @Test
    void shouldNeverShowATemplateOnCardsThatAreNotIdentifiedByTheServerMessage() throws IOException {
        List<CatsTestCaseSummary> summaries = ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun());
        CatsTestReport report = write(summaries, reportDirectory);
        String html = Files.readString(reportDirectory.resolve(TestCaseExporter.REPORT_HTML));
        Map<String, String> cards = cards(html);
        Set<String> templates = report.getProblems().stream().flatMap(problem -> java.util.stream.Stream.concat(
                java.util.stream.Stream.ofNullable(problem.errorTemplate()), problem.variants().stream().map(Problem.Variant::text))).collect(Collectors.toSet());

        Assertions.assertThat(html).doesNotContainPattern("T-[0-9a-f]{8}").doesNotContainIgnoringCase("error template").doesNotContainIgnoringCase("cluster");
        report.getProblems().stream().filter(problem -> !problem.kind().isIdentifiedByServerMessage()).forEach(problem -> {
            String card = cards.get("problem-" + problem.id());
            Assertions.assertThat(card).as(problem.title()).doesNotContain("Server-message pattern:", "Example response");
            Assertions.assertThat(templates).noneMatch(template -> problem.title().contains(template));
        });
        report.getProblems().stream().filter(problem -> problem.kind().isIdentifiedByServerMessage())
                .forEach(problem -> Assertions.assertThat(cards.get("problem-" + problem.id())).contains("Server-message pattern:"));
    }

    @Test
    void shouldDefaultToAllTestsAndKeepProblemsAvailableWhenThereAreFailures() throws IOException {
        write(ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun()), reportDirectory);
        String html = Files.readString(reportDirectory.resolve(TestCaseExporter.REPORT_HTML));

        Assertions.assertThat(html).contains("class=\"report-tab active\" aria-selected=\"true\" data-view=\"tests-view\"",
                "id=\"tests-view\" class=\"report-view active\"", "id=\"problems-view\" class=\"report-view\"",
                "<th style=\"width: 220px\">Problem</th>", "id=\"problemFilter\"", "How these tests were grouped", "View 8 tests");
        Assertions.assertThat(html).contains("id=\"cats-test-data\" type=\"application/json\"", "id=\"fuzzerFilter\"")
                .doesNotContain("<tr data-id=");
        Assertions.assertThat(html.indexOf("id=\"tests-view\"")).isLessThan(html.indexOf("id=\"problems-view\""));
    }

    @Test
    void shouldRenderBaselineLinksBesideTheHintAndReadableProblemOptions() throws IOException {
        write(ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun()), reportDirectory);
        String html = Files.readString(reportDirectory.resolve(TestCaseExporter.REPORT_HTML));
        String note = Pattern.compile("(?s)<div class=\"problem-line problem-baseline\">(.*?)</div>")
                .matcher(html).results().findFirst().orElseThrow().group(1);

        Assertions.assertThat(note).contains("2 of 8 also fail the same way", "Unmodified request:", "href=\"Test2.html\"");
        Assertions.assertThat(html).contains("class=\"tests-filters\"", "class=\"problem-filter-control\"", "<label for=\"problemFilter\">Problem</label>");
    }

    @Test
    void shouldExplainDistinctRejectionsAndExposeActualValidationBounds() throws IOException {
        CatsTestReport report = write(ProblemReportFixtures.summaries(ProblemReportFixtures.riskApiRun()), reportDirectory);
        Map<String, String> cards = cards(Files.readString(reportDirectory.resolve(TestCaseExporter.REPORT_HTML)));
        Problem operator = report.getProblems().stream().filter(problem -> problem.title().contains("XEL-4220")).findFirst().orElseThrow();
        Problem bounds = report.getProblems().stream().filter(problem -> problem.title().contains("XEL-4221")).findFirst().orElseThrow();

        Assertions.assertThat(cards.get("problem-" + operator.id())).contains("Expected HTTP 2xx; received HTTP 422.</h3>",
                        "<div class=\"problem-example-message\">XEL-4220: Operator GT requires a numeric value</div>",
                        "Expected HTTP 2xx; received HTTP 422.", "2 tests share this server-message pattern and HTTP 422 across 2 operations.")
                .doesNotContain("problem-legend", "&lt;*&gt; varies");
        Assertions.assertThat(cards.get("problem-" + bounds.id())).contains("Observed in one test on one operation.",
                "<details class=\"problem-grouping\">", "<details class=\"problem-sample\">", "between 1 and 64",
                "A number hidden for grouping", "this does not mean they differed between responses", "Open Test ");
        report.getProblems().stream().filter(problem -> problem.kind().isIdentifiedByServerMessage())
                .forEach(problem -> assertMessagePresentation(cards.get("problem-" + problem.id())));
    }

    private static void assertMessagePresentation(String card) {
        String heading = Pattern.compile("<h3 class=\"problem-title\">(.*?)</h3>").matcher(card).results().findFirst().orElseThrow().group(1);
        Assertions.assertThat(heading).doesNotContain("NUM", "&lt;*&gt;").contains("HTTP");
        Assertions.assertThat(card.indexOf("problem-example")).isLessThan(card.indexOf("problem-operations-scroll"));
        Assertions.assertThat(card.indexOf("problem-operations-scroll")).isLessThan(card.indexOf("problem-sample"));
        Assertions.assertThat(card.indexOf("problem-sample")).isLessThan(card.indexOf("problem-grouping"));
        Assertions.assertThat(card).contains("Example server message", "Full example response").doesNotContain("problem-expectation");
    }

    @Test
    void shouldEscapeUnmaskedExamplesWhileLinkingTheirSourceTest() throws IOException {
        CatsTestCase test = ProblemReportFixtures.riskApiRun().stream().filter(tc -> tc.getFuzzer().equals("HappyPath"))
                .filter(tc -> tc.getResponse().getResponseCode() == 422).findFirst().orElseThrow();
        test.setResponse(CatsResponse.builder().responseCode(422).body(JsonUtils.GSON.toJson(
                Map.of("message", "</div><script>alert('example')</script>"))).build());
        write(ProblemReportFixtures.summaries(List.of(test)), reportDirectory);
        String html = Files.readString(reportDirectory.resolve(TestCaseExporter.REPORT_HTML));
        String example = Pattern.compile("<div class=\"problem-example-message\">(.*?)</div>").matcher(html)
                .results().findFirst().orElseThrow().group(1);

        Assertions.assertThat(example).contains("&lt;script&gt;", "alert(").doesNotContain("<script>", "</div>");
        Assertions.assertThat(html).contains("href=\"" + test.getTestId().replace(" ", "") + ".html\"");
    }

    @Test
    void shouldComparePersistedRootStringParseErrorsByIdWithoutWarningRegressions() throws IOException {
        Path baseline = reportDirectory.resolve("baseline");
        Path target = reportDirectory.resolve("target");
        write(List.of(rootStringRejection("TOKEN_ALPHA")), baseline);
        write(List.of(rootStringRejection("TOKEN_BETA")), target);

        var comparison = BaselineComparisonEngine.compareReports(baseline, target, true);

        Assertions.assertThat(comparison.regression()).isFalse();
        Assertions.assertThat(comparison.newOccurrences()).isEmpty();
        Assertions.assertThat(comparison.fixedOccurrences()).isEmpty();
        Assertions.assertThat(comparison.notComparable()).isEmpty();
        Assertions.assertThat(comparison.unchangedOccurrences()).singleElement()
                .satisfies(delta -> Assertions.assertThat(delta.matchedBy()).isEqualTo(MatchedBy.ID));
    }

    @Test
    void shouldKeepProblemKeysAndWfcFaultsWhenOnlyExpectationMetadataChanges() throws IOException {
        List<CatsTestCase> run = ProblemReportFixtures.riskApiRun();
        List<CatsTestCaseSummary> before = ProblemReportFixtures.summaries(run);
        run.forEach(test -> test.recordExpectedResponse(new ExpectedResponse(List.of("2XX", "4XX"))));
        List<CatsTestCaseSummary> after = ProblemReportFixtures.summaries(run);
        CatsTestReport baseline = write(before, reportDirectory.resolve("before"));
        CatsTestReport target = write(after, reportDirectory.resolve("after"));

        Assertions.assertThat(target.getProblems()).isEqualTo(baseline.getProblems());
        Assertions.assertThat(BaselineComparisonEngine.compareReports(reportDirectory.resolve("before"), reportDirectory.resolve("after"), true).regression()).isFalse();
        var oldFaults = WfcReportBuilder.build(before, null, ProblemReportFixtures.RISK_CONTRACT, List.of(), null, "test", 0).getFaults();
        var newFaults = WfcReportBuilder.build(after, null, ProblemReportFixtures.RISK_CONTRACT, List.of(), null, "test", 0).getFaults();
        Assertions.assertThat(JsonUtils.GSON.toJson(newFaults)).isEqualTo(JsonUtils.GSON.toJson(oldFaults));
    }

    private static CatsTestCaseSummary rootStringRejection(String value) {
        CatsTestCase test = new CatsTestCase();
        test.setTestId("Test 1");
        test.setResult("warn");
        test.setResultReason("Unexpected response code: 400");
        test.setFuzzer("HttpMethods");
        test.setContractPath("/items");
        test.setRequest(CatsRequest.builder().httpMethod("PUT").url("http://localhost/items").payload(JsonUtils.GSON.toJson(value)).build());
        String body = JsonUtils.GSON.toJson(Map.of("message", "Cannot deserialize from String value ('%s')".formatted(value)));
        test.setResponse(CatsResponse.builder().responseCode(400).body(body).build());
        return CatsTestCaseSummary.fromCatsTestCase(test);
    }

    static Map<String, TestCaseExporter.ProblemBannerEntry> banners(Path directory) throws IOException {
        String script = Files.readString(directory.resolve(TestCaseExporter.PROBLEMS_JS));
        String json = script.substring(script.indexOf(PROBLEMS_VARIABLE) + PROBLEMS_VARIABLE.length(), script.lastIndexOf(';'));
        return JsonUtils.GSON.fromJson(json, new TypeToken<Map<String, TestCaseExporter.ProblemBannerEntry>>() {
        }.getType());
    }

    static Map<String, TestCaseExporter.ProblemIndexEntry> problemIndex(Path directory) throws IOException {
        String script = Files.readString(directory.resolve(TestCaseExporter.PROBLEMS_JS));
        String assignment = "window.CATS_PROBLEMS_INDEX = ";
        int start = script.indexOf(assignment) + assignment.length();
        String json = script.substring(start, script.indexOf(";\n", start));
        return JsonUtils.GSON.fromJson(json, new TypeToken<Map<String, TestCaseExporter.ProblemIndexEntry>>() {
        }.getType());
    }

    static Map<String, String> cards(String html) {
        return CARD.matcher(html).results().collect(Collectors.toMap(match -> match.group(1), match -> match.group(2),
                (left, _) -> left, java.util.LinkedHashMap::new));
    }

    static CatsTestReport write(List<CatsTestCaseSummary> summaries, Path directory) throws IOException {
        CatsGlobalContext context = new CatsGlobalContext();
        context.setOpenAPI(ProblemReportFixtures.RISK_CONTRACT);
        ReportingArguments reportingArguments = Mockito.mock(ReportingArguments.class);
        Mockito.when(reportingArguments.getOutputReportFolder()).thenReturn(directory.toString());
        TestCaseExporterHtmlJs exporter = new TestCaseExporterHtmlJs(reportingArguments, context,
                Mockito.mock(ProcessingArguments.class), Mockito.mock(StopArguments.class));
        exporter.appVersion = "test-version";
        exporter.initPath(directory.toString());
        ProblemReportFixtures.writeSummary(exporter, summaries, new ExecutionStatisticsListener().snapshot(false, "fail on errors"));
        return BaselineComparisonEngine.loadReport(directory);
    }

    static void writeTestCases(List<CatsTestCase> tests, TestCaseExporter exporter) {
        tests.forEach(exporter::writeTestCase);
    }

    private static String problemsJson(CatsTestReport report) {
        return JsonUtils.GSON.toJson(report.getProblems());
    }

    private static List<Problem> byKind(CatsTestReport report, ProblemKind kind) {
        return report.getProblems().stream().filter(problem -> problem.kind() == kind).toList();
    }

    private static Problem byTitle(CatsTestReport report, String title) {
        return report.getProblems().stream().filter(problem -> problem.title().equals(title)).findFirst().orElseThrow();
    }

    static Set<String> ids(List<Problem> problems) {
        return problems.stream().map(Problem::id).collect(Collectors.toSet());
    }
}
