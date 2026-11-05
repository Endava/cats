package com.endava.cats.command.triage;

import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.model.CatsTestCase;
import io.swagger.v3.oas.models.OpenAPI;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds a {@link TriageReport} from a {@code cats-report} folder in two passes: a report-wide observation pass
 * (successful producers, deletes, refData mining, contract drift) followed by analysis, rule synthesis and
 * clustering of every failing test, so results do not depend on the order in which failures are visited.
 */
public final class TriageEngine {
    private static final Pattern TEST_FILE = Pattern.compile("Test(\\d+)\\.json$");
    private static final Comparator<RootCauseCluster> CLUSTER_ORDER = Comparator
            .comparingInt(RootCauseCluster::severityRank)
            .thenComparing(Comparator.comparingInt(RootCauseCluster::occurrences).reversed())
            .thenComparing(RootCauseCluster::category)
            .thenComparing(RootCauseCluster::operation)
            .thenComparingInt(RootCauseCluster::statusCode)
            .thenComparing(RootCauseCluster::diagnosis);

    private final TriageOptions options;

    public TriageEngine(TriageOptions options) {
        this.options = options;
    }

    public TriageReport triage(Path reportFolder, OpenAPI openAPI) throws IOException {
        List<TriageTestCase> tests = loadTestCases(reportFolder);
        ReportScan scan = ReportScan.of(tests, openAPI);
        RequestSchemaLookup schemaLookup = new RequestSchemaLookup(openAPI);
        List<ClusterClassifier.Candidate> candidates = analyze(tests.stream().filter(this::isTriaged).toList(), schemaLookup);
        SynthesizedRules rules = SynthesizedRules.of(candidates, new CorrelationRuleSynthesizer(schemaLookup));
        ClusterClassifier classifier = new ClusterClassifier(scan.context(rules, openAPI));
        List<RootCauseCluster> clusters = verifyAndSort(accumulate(candidates, classifier));
        int triaged = scan.errorCount() + (options.includeWarnings() ? scan.warningCount() : 0);
        return new TriageReport(reportFolder.toString(), tests.size(), scan.errorCount(), scan.warningCount(), clusters.size(),
                noiseReduction(triaged, clusters.size()), clusters, rules.distinct(),
                CorrelationsYamlRenderer.render(rules.distinct()), scan.refDataMiner().renderYaml(), scan.drift().renderPatch());
    }

    /**
     * Returns {@code true} when {@code --verify} found a {@code SERVER_CRASH_5XX} cluster that is not {@code FIXED}.
     */
    public static boolean hasUnfixedServerCrash(TriageReport report) {
        return report.clusters().stream()
                .anyMatch(cluster -> "SERVER_CRASH_5XX".equals(cluster.category()) && !LiveClusterVerifier.FIXED.equals(cluster.verificationStatus()));
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

    private boolean isTriaged(TriageTestCase testCase) {
        return testCase.isError() || (options.includeWarnings() && testCase.isWarning());
    }

    private static List<ClusterClassifier.Candidate> analyze(List<TriageTestCase> failing, RequestSchemaLookup schemaLookup) {
        FailureAnalyzer analyzer = new FailureAnalyzer(schemaLookup);
        return failing.stream()
                .map(test -> new ClusterClassifier.Candidate(test, ErrorMessage.of(test), analyzer.analyze(test)))
                .toList();
    }

    private static List<ClusterAccumulator> accumulate(List<ClusterClassifier.Candidate> candidates, ClusterClassifier classifier) {
        List<ClusterAccumulator> accumulators = new ArrayList<>();
        candidates.forEach(candidate -> assign(accumulators, candidate, classifier.classify(candidate)));
        return accumulators;
    }

    private static void assign(List<ClusterAccumulator> accumulators, ClusterClassifier.Candidate candidate, ClusterClassification classification) {
        accumulators.stream()
                .filter(accumulator -> accumulator.accepts(classification))
                .findFirst()
                .orElseGet(() -> register(accumulators, new ClusterAccumulator(classification, candidate.testCase())))
                .add(candidate.testCase());
    }

    private static ClusterAccumulator register(List<ClusterAccumulator> accumulators, ClusterAccumulator accumulator) {
        accumulators.add(accumulator);
        return accumulator;
    }

    private List<RootCauseCluster> verifyAndSort(List<ClusterAccumulator> accumulators) {
        Optional<LiveClusterVerifier> verifier = options.verify()
                ? Optional.of(new LiveClusterVerifier(options.serverUrl(), options.headers(), options.environment()))
                : Optional.empty();
        return accumulators.stream()
                .map(accumulator -> accumulator.toCluster(verifier
                        .map(live -> live.verify(accumulator.classification(), accumulator.representative()))
                        .orElseGet(LiveClusterVerifier.Verification::disabled)))
                .sorted(CLUSTER_ORDER)
                .toList();
    }

    private static double noiseReduction(int triagedCount, int clusterCount) {
        if (triagedCount == 0 || clusterCount == 0) {
            return 0.0;
        }
        double percent = Math.max(0.0, 100.0 * (triagedCount - clusterCount) / triagedCount);
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
