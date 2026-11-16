package com.endava.cats.command.triage;

import com.endava.cats.exception.UnsupportedReportException;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.Problem;
import com.endava.cats.model.ProblemKind;
import com.endava.cats.report.BaselineComparisonEngine;
import com.endava.cats.report.BaselineFailureRegistry;
import io.swagger.v3.oas.models.OpenAPI;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds a {@link TriageReport} from a {@code cats-report} folder: it reads the problems persisted in
 * {@code cats-summary-report.json} and classifies each occurrence (one problem on one operation) from its
 * {@code TestN.json} files, after a report-wide observation pass (successful producers, deletes, refData mining,
 * contract drift) and rule synthesis, so results do not depend on the order in which failures are visited.
 */
public final class TriageEngine {
    static final String SUMMARY_FILE = "cats-summary-report.json";
    private static final Pattern TEST_FILE = Pattern.compile("Test(\\d+)\\.json$");

    private final TriageOptions options;

    public TriageEngine(TriageOptions options) {
        this.options = options;
    }

    public TriageReport triage(Path reportFolder, OpenAPI openAPI) throws IOException {
        CatsTestReport summary = readProblems(reportFolder);
        List<TriageTestCase> tests = loadTestCases(reportFolder);
        annotateBaselineMatches(tests);
        ReportScan scan = ReportScan.of(tests, openAPI);
        RequestSchemaLookup schemaLookup = new RequestSchemaLookup(openAPI);
        List<ClusterClassifier.Candidate> candidates = analyze(tests.stream().filter(this::isTriaged).toList(), schemaLookup);
        SynthesizedRules rules = SynthesizedRules.of(candidates, new CorrelationRuleSynthesizer(schemaLookup));
        OccurrenceTriage occurrenceTriage = new OccurrenceTriage(new ClusterClassifier(scan.context(rules, openAPI)), verifier());
        List<TriageProblem> problems = triageProblems(summary.getProblems(), byTestId(candidates), occurrenceTriage);
        int occurrences = problems.stream().mapToInt(problem -> problem.occurrences().size()).sum();
        return new TriageReport(reportFolder.toString(), summary.getProblemKeyVersion(), tests.size(), scan.errorCount(), scan.warningCount(),
                problems.size(), occurrences, noiseReduction(candidates.size(), problems.size()), problems, rules.distinct(),
                CorrelationsYamlRenderer.render(rules.distinct()), scan.refDataMiner().renderYaml(), scan.drift().renderPatch());
    }

    /**
     * Returns {@code true} when {@code --verify} found an occurrence of a server error that is not {@code FIXED}.
     */
    public static boolean hasUnfixedServerCrash(TriageReport report) {
        return report.problems().stream()
                .filter(problem -> problem.kind() == ProblemKind.SERVER_ERROR)
                .flatMap(problem -> problem.occurrences().stream())
                .anyMatch(occurrence -> !LiveClusterVerifier.FIXED.equals(occurrence.verificationStatus()));
    }

    static CatsTestReport readProblems(Path reportFolder) throws IOException {
        if (!Files.isRegularFile(reportFolder.resolve(SUMMARY_FILE))) {
            throw new UnsupportedReportException("%s has no %s; run CATS first".formatted(reportFolder, SUMMARY_FILE));
        }
        CatsTestReport summary = BaselineComparisonEngine.loadReport(reportFolder);
        return summary;
    }

    static List<TriageTestCase> loadTestCases(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(TriageEngine::readNumbered)
                    .flatMap(Optional::stream)
                    .filter(test -> test.testCase().getRequest() != null && test.testCase().getResponse() != null)
                    .sorted(Comparator.comparingInt(TriageTestCase::number))
                    .toList();
        }
    }

    private static Optional<TriageTestCase> readNumbered(Path file) {
        Matcher matcher = TEST_FILE.matcher(file.getFileName().toString());
        if (!matcher.find()) {
            return Optional.empty();
        }
        int number = Integer.parseInt(matcher.group(1));
        return CatsTestCase.readTestCaseFile(file).map(testCase -> new TriageTestCase(number, testCase));
    }

    private static void annotateBaselineMatches(List<TriageTestCase> tests) {
        BaselineFailureRegistry registry = new BaselineFailureRegistry();
        tests.stream()
                .map(TriageTestCase::testCase)
                .filter(BaselineFailureRegistry::isSeedTestCase)
                .forEach(registry::recordOrAnnotate);
        tests.stream()
                .map(TriageTestCase::testCase)
                .filter(testCase -> !testCase.hasSameErrorAsBaseline())
                .forEach(registry::recordOrAnnotate);
    }

    private boolean isTriaged(TriageTestCase testCase) {
        return testCase.isError() || (!options.errorsOnly() && testCase.isWarning());
    }

    private static List<ClusterClassifier.Candidate> analyze(List<TriageTestCase> failing, RequestSchemaLookup schemaLookup) {
        FailureAnalyzer analyzer = new FailureAnalyzer(schemaLookup);
        return failing.stream()
                .map(test -> new ClusterClassifier.Candidate(test, ErrorMessage.of(test), analyzer.analyze(test)))
                .toList();
    }

    private static Map<String, ClusterClassifier.Candidate> byTestId(List<ClusterClassifier.Candidate> candidates) {
        return candidates.stream().filter(candidate -> candidate.testCase().testCase().getTestId() != null)
                .collect(Collectors.toMap(candidate -> candidate.testCase().testCase().getTestId(), Function.identity(), (left, _) -> left));
    }

    private static List<TriageProblem> triageProblems(List<Problem> problems, Map<String, ClusterClassifier.Candidate> candidates,
                                                      OccurrenceTriage occurrenceTriage) {
        return problems.stream()
                .map(problem -> triageProblem(problem, candidates, occurrenceTriage))
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<TriageProblem> triageProblem(Problem problem, Map<String, ClusterClassifier.Candidate> candidates,
                                                         OccurrenceTriage occurrenceTriage) {
        List<TriageOccurrence> occurrences = problem.occurrences().stream()
                .map(occurrence -> occurrenceTriage.triage(occurrence, occurrence.testIds().stream().map(candidates::get).filter(Objects::nonNull).toList()))
                .flatMap(Optional::stream)
                .toList();
        if (occurrences.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TriageProblem(problem.id(), problem.kind(), problem.title(), problem.level(), problem.reasons(),
                occurrences.stream().mapToInt(TriageOccurrence::testCount).sum(), problem.errorTemplateId(), problem.errorTemplate(), occurrences));
    }

    private Optional<LiveClusterVerifier> verifier() {
        return options.verify()
                ? Optional.of(new LiveClusterVerifier(options.serverUrl(), options.headers(), options.environment()))
                : Optional.empty();
    }

    private static double noiseReduction(int triagedCount, int problemCount) {
        if (triagedCount == 0 || problemCount == 0) {
            return 0.0;
        }
        double percent = Math.max(0.0, 100.0 * (triagedCount - problemCount) / triagedCount);
        return Math.round(percent * 10.0) / 10.0;
    }

    /**
     * Deduplicated synthesized rules plus the rule (if any) associated with each failing test.
     */
    private record SynthesizedRules(List<SynthesizedCorrelationRule> distinct, Map<Integer, SynthesizedCorrelationRule> byTestNumber) {
        static SynthesizedRules of(List<ClusterClassifier.Candidate> candidates, CorrelationRuleSynthesizer synthesizer) {
            Map<String, SynthesizedCorrelationRule> distinctRules = new LinkedHashMap<>();
            Map<Integer, SynthesizedCorrelationRule> byTest = new LinkedHashMap<>();
            candidates.forEach(candidate -> candidate.analysis()
                    .flatMap(analysis -> synthesizer.synthesize(candidate.testCase(), analysis))
                    .map(rule -> distinctRules.computeIfAbsent(ruleKey(rule), _ -> rule))
                    .ifPresent(rule -> byTest.put(candidate.testCase().number(), rule)));
            return new SynthesizedRules(List.copyOf(distinctRules.values()), Map.copyOf(byTest));
        }

        Set<String> paths() {
            return distinct.stream().map(SynthesizedCorrelationRule::path).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        private static String ruleKey(SynthesizedCorrelationRule rule) {
            return String.join("|", rule.path(), Objects.requireNonNullElse(rule.when(), ""), rule.action(), rule.targetField());
        }
    }

    /**
     * Report-wide observations collected over all tests (successes included).
     */
    private record ReportScan(Set<String> pathsWithSuccessfulPost, Map<String, Integer> firstDeleteTestByPath,
                              RefDataMiner refDataMiner, ContractDriftDetector drift, int errorCount, int warningCount) {

        static ReportScan of(List<TriageTestCase> tests, OpenAPI openAPI) {
            Set<String> successfulPosts = new LinkedHashSet<>();
            Map<String, Integer> firstDeletes = new LinkedHashMap<>();
            RefDataMiner miner = new RefDataMiner(knownPaths(tests, openAPI));
            ContractDriftDetector drift = new ContractDriftDetector(openAPI);
            tests.forEach(test -> recordProducerAndDelete(test, successfulPosts, firstDeletes));
            tests.forEach(miner::observe);
            tests.forEach(drift::observe);
            int errors = (int) tests.stream().filter(TriageTestCase::isError).count();
            int warnings = (int) tests.stream().filter(TriageTestCase::isWarning).count();
            return new ReportScan(successfulPosts, firstDeletes, miner, drift, errors, warnings);
        }

        ClassificationContext context(SynthesizedRules rules, OpenAPI openAPI) {
            return new ClassificationContext(pathsWithSuccessfulPost, firstDeleteTestByPath, refDataMiner.mined(),
                    rules.byTestNumber(), rules.paths(), drift, openAPI);
        }

        private static void recordProducerAndDelete(TriageTestCase test, Set<String> successfulPosts, Map<String, Integer> firstDeletes) {
            if (!ResponseCodeFamily.is2xxCode(test.statusCode())) {
                return;
            }
            if ("POST".equals(test.method())) {
                successfulPosts.add(test.path());
            } else if ("DELETE".equals(test.method())) {
                firstDeletes.putIfAbsent(test.path(), test.number());
            }
        }

        private static Set<String> knownPaths(List<TriageTestCase> tests, OpenAPI openAPI) {
            Set<String> paths = tests.stream().map(TriageTestCase::path).collect(Collectors.toCollection(LinkedHashSet::new));
            Optional.ofNullable(openAPI).map(OpenAPI::getPaths).ifPresent(contractPaths -> paths.addAll(contractPaths.keySet()));
            return paths;
        }
    }
}
