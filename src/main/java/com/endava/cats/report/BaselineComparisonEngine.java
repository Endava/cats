package com.endava.cats.report;

import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.util.AnsiUtils;
import com.endava.cats.util.JsonUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Performs an apples-to-apples regression comparison between a baseline CATS report and a target/current run.
 * <p>
 * Only {@code (contractPath, httpMethod, fuzzer)} scopes present in both runs are evaluated for regressions and
 * fixes so differences in profile or path selection never produce false-positive regressions.
 */
public final class BaselineComparisonEngine {
    public static final String SUMMARY_FILE_NAME = "cats-summary-report.json";

    private BaselineComparisonEngine() {
    }

    /**
     * Loads a {@link CatsTestReport} from either a report directory or a direct summary JSON file path.
     *
     * @param reportPath directory containing {@code cats-summary-report.json} or path to the JSON file itself
     * @return parsed {@link CatsTestReport}
     * @throws IOException when the file does not exist or cannot be parsed
     */
    public static CatsTestReport loadReport(Path reportPath) throws IOException {
        Path resolved = resolveSummaryFile(reportPath);
        try (Reader reader = Files.newBufferedReader(resolved, StandardCharsets.UTF_8)) {
            CatsTestReport report = JsonUtils.GSON.fromJson(reader, CatsTestReport.class);
            if (report == null) {
                throw new IOException("Empty or invalid CATS summary report: " + resolved);
            }
            return report;
        }
    }

    static Path resolveSummaryFile(Path path) throws IOException {
        if (path == null) {
            throw new IOException("Report path must not be null");
        }
        Path candidate = Files.isDirectory(path) ? path.resolve(SUMMARY_FILE_NAME) : path;
        if (!Files.isRegularFile(candidate)) {
            throw new IOException("CATS summary report not found at: " + candidate);
        }
        return candidate;
    }

    /**
     * Compares a baseline report file/directory against the live test case summaries of the current run.
     *
     * @param baselinePath       baseline report directory or summary JSON file
     * @param currentTestCases   test case summaries from the current run
     * @param currentExecTimeSec current execution time in seconds
     * @return apples-to-apples comparison result
     * @throws IOException if the baseline report cannot be read
     */
    public static ComparisonResult compareWithCurrent(Path baselinePath, List<CatsTestCaseSummary> currentTestCases,
                                                      long currentExecTimeSec) throws IOException {
        CatsTestReport baselineReport = loadReport(baselinePath);
        List<CatsTestCaseSummary> baselineCases = Optional.ofNullable(baselineReport.getTestCases()).orElse(List.of());
        return compareSummaries(baselineCases, Optional.ofNullable(currentTestCases).orElse(List.of()),
                baselineReport.getExecutionTime(), currentExecTimeSec);
    }

    /**
     * Compares two CATS report directories or summary JSON files.
     *
     * @param baselinePath baseline report directory or summary JSON file
     * @param targetPath   target report directory or summary JSON file
     * @return apples-to-apples comparison result
     * @throws IOException if either report cannot be read
     */
    public static ComparisonResult compareReports(Path baselinePath, Path targetPath) throws IOException {
        CatsTestReport baseline = loadReport(baselinePath);
        CatsTestReport target = loadReport(targetPath);
        return compareSummaries(
                Optional.ofNullable(baseline.getTestCases()).orElse(List.of()),
                Optional.ofNullable(target.getTestCases()).orElse(List.of()),
                baseline.getExecutionTime(),
                target.getExecutionTime());
    }

    /**
     * Compares two lists of {@link CatsTestCaseSummary} apples-to-apples across their shared
     * {@code (path, httpMethod, fuzzer)} scope.
     */
    public static ComparisonResult compareSummaries(List<CatsTestCaseSummary> baselineCases,
                                                    List<CatsTestCaseSummary> targetCases,
                                                    long baselineExecTimeSec,
                                                    long targetExecTimeSec) {
        Map<ScopeKey, List<CatsTestCaseSummary>> baselineByScope = groupByScope(baselineCases);
        Map<ScopeKey, List<CatsTestCaseSummary>> targetByScope = groupByScope(targetCases);

        Set<ScopeKey> sharedScopes = new LinkedHashSet<>(baselineByScope.keySet());
        sharedScopes.retainAll(targetByScope.keySet());

        int onlyInBaseline = baselineByScope.size() - sharedScopes.size();
        int onlyInTarget = targetByScope.size() - sharedScopes.size();

        List<FindingDelta> newErrors = new ArrayList<>();
        List<FindingDelta> newWarnings = new ArrayList<>();
        List<FindingDelta> resolvedErrors = new ArrayList<>();
        List<FindingDelta> resolvedWarnings = new ArrayList<>();
        int unchangedErrors = 0;
        int unchangedWarnings = 0;

        for (ScopeKey scope : sharedScopes) {
            ScopeDiff diff = diffScope(scope, baselineByScope.get(scope), targetByScope.get(scope));
            newErrors.addAll(diff.newErrors());
            newWarnings.addAll(diff.newWarnings());
            resolvedErrors.addAll(diff.resolvedErrors());
            resolvedWarnings.addAll(diff.resolvedWarnings());
            unchangedErrors += diff.unchangedErrors();
            unchangedWarnings += diff.unchangedWarnings();
        }

        return new ComparisonResult(
                sharedScopes.size(),
                onlyInBaseline,
                onlyInTarget,
                List.copyOf(newErrors),
                List.copyOf(newWarnings),
                List.copyOf(resolvedErrors),
                List.copyOf(resolvedWarnings),
                unchangedErrors,
                unchangedWarnings,
                targetExecTimeSec - baselineExecTimeSec);
    }

    private static Map<ScopeKey, List<CatsTestCaseSummary>> groupByScope(List<CatsTestCaseSummary> cases) {
        Map<ScopeKey, List<CatsTestCaseSummary>> grouped = new LinkedHashMap<>();
        for (CatsTestCaseSummary tc : cases) {
            if (tc != null && tc.getPath() != null && tc.getFuzzer() != null) {
                grouped.computeIfAbsent(ScopeKey.from(tc), _ -> new ArrayList<>()).add(tc);
            }
        }
        return grouped;
    }

    private static ScopeDiff diffScope(ScopeKey scope, List<CatsTestCaseSummary> baseList,
                                       List<CatsTestCaseSummary> targetList) {
        Map<String, List<CatsTestCaseSummary>> baseErrors = groupByReason(baseList, "error");
        Map<String, List<CatsTestCaseSummary>> targetErrors = groupByReason(targetList, "error");
        Map<String, List<CatsTestCaseSummary>> baseWarnings = groupByReason(baseList, "warning");
        Map<String, List<CatsTestCaseSummary>> targetWarnings = groupByReason(targetList, "warning");

        List<FindingDelta> newErrors = new ArrayList<>();
        List<FindingDelta> resolvedErrors = new ArrayList<>();
        int unchangedErrors = compareReasonBuckets(scope, "error", baseErrors, targetErrors, newErrors, resolvedErrors);

        List<FindingDelta> newWarnings = new ArrayList<>();
        List<FindingDelta> resolvedWarnings = new ArrayList<>();
        int unchangedWarnings = compareReasonBuckets(scope, "warning", baseWarnings, targetWarnings, newWarnings, resolvedWarnings);

        return new ScopeDiff(newErrors, newWarnings, resolvedErrors, resolvedWarnings, unchangedErrors, unchangedWarnings);
    }

    private static Map<String, List<CatsTestCaseSummary>> groupByReason(List<CatsTestCaseSummary> list, String level) {
        return list.stream()
                .filter(tc -> level.equalsIgnoreCase(tc.getResult()))
                .collect(Collectors.groupingBy(
                        tc -> Optional.ofNullable(tc.getResultReason()).orElse("Unspecified"),
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private static int compareReasonBuckets(ScopeKey scope, String level,
                                            Map<String, List<CatsTestCaseSummary>> baseBuckets,
                                            Map<String, List<CatsTestCaseSummary>> targetBuckets,
                                            List<FindingDelta> addedOut,
                                            List<FindingDelta> resolvedOut) {
        int unchanged = 0;
        Set<String> allReasons = new LinkedHashSet<>(baseBuckets.keySet());
        allReasons.addAll(targetBuckets.keySet());

        for (String reason : allReasons) {
            List<CatsTestCaseSummary> baseItems = baseBuckets.getOrDefault(reason, List.of());
            List<CatsTestCaseSummary> targetItems = targetBuckets.getOrDefault(reason, List.of());
            int overlap = Math.min(baseItems.size(), targetItems.size());
            unchanged += overlap;
            if (targetItems.size() > baseItems.size()) {
                CatsTestCaseSummary sample = targetItems.getFirst();
                addedOut.add(new FindingDelta(scope.httpMethod(), scope.path(), scope.fuzzer(),
                        level, reason, sample.getHttpResponseCode(), targetItems.size() - baseItems.size()));
            } else if (baseItems.size() > targetItems.size()) {
                CatsTestCaseSummary sample = baseItems.getFirst();
                resolvedOut.add(new FindingDelta(scope.httpMethod(), scope.path(), scope.fuzzer(),
                        level, reason, sample.getHttpResponseCode(), baseItems.size() - targetItems.size()));
            }
        }
        return unchanged;
    }

    /**
     * Renders a human-readable summary of the baseline comparison to the console logger.
     */
    public static void renderToConsole(PrettyLogger logger, ComparisonResult result, boolean failOnNewWarnings) {
        logger.noFormat("");
        logger.noFormat(AnsiUtils.bold("=== CATS Baseline Comparison (Apples-to-Apples) ==="));
        logger.noFormat("Shared (path, method, fuzzer) scopes compared: {} (only in baseline: {}, only in current: {})",
                result.sharedScopes(), result.onlyInBaselineScopes(), result.onlyInTargetScopes());
        logger.noFormat("New Errors (Regressions): {} | Resolved Errors: {} | Unchanged Errors: {}",
                result.newErrorCount(), result.resolvedErrorCount(), result.unchangedErrors());
        logger.noFormat("New Warnings:             {} | Resolved Warnings: {} | Unchanged Warnings: {}",
                result.newWarningCount(), result.resolvedWarningCount(), result.unchangedWarnings());
        if (!result.newErrors().isEmpty()) {
            logger.noFormat(AnsiUtils.boldRed("New Error Regressions:"));
            result.newErrors().forEach(delta -> logger.noFormat("  - [{}] {} {} • {} • HTTP {} ({}) (+{})",
                    delta.level().toUpperCase(Locale.ROOT), delta.httpMethod(), delta.path(),
                    delta.fuzzer(), delta.httpResponseCode(), delta.reason(), delta.count()));
        }
        if (!result.newWarnings().isEmpty()) {
            logger.noFormat(AnsiUtils.boldYellow("New Warnings:"));
            result.newWarnings().forEach(delta -> logger.noFormat("  - [{}] {} {} • {} • HTTP {} ({}) (+{})",
                    delta.level().toUpperCase(Locale.ROOT), delta.httpMethod(), delta.path(),
                    delta.fuzzer(), delta.httpResponseCode(), delta.reason(), delta.count()));
        }
        if (!result.resolvedErrors().isEmpty() || !result.resolvedWarnings().isEmpty()) {
            logger.noFormat(AnsiUtils.boldGreen("Resolved Issues:"));
            result.resolvedErrors().forEach(delta -> logger.noFormat("  - [FIXED ERROR] {} {} • {} ({}) (-{})",
                    delta.httpMethod(), delta.path(), delta.fuzzer(), delta.reason(), delta.count()));
            result.resolvedWarnings().forEach(delta -> logger.noFormat("  - [FIXED WARN]  {} {} • {} ({}) (-{})",
                    delta.httpMethod(), delta.path(), delta.fuzzer(), delta.reason(), delta.count()));
        }
        boolean regressed = result.hasRegressions(failOnNewWarnings);
        logger.noFormat("Baseline Verdict: {}", regressed ? AnsiUtils.boldRed("REGRESSION_DETECTED") : AnsiUtils.boldGreen("PASS"));
    }

    /**
     * Normalized {@code (path, httpMethod, fuzzer)} scope key for apples-to-apples matching.
     */
    public record ScopeKey(String path, String httpMethod, String fuzzer) {
        static ScopeKey from(CatsTestCaseSummary summary) {
            return new ScopeKey(
                    Optional.ofNullable(summary.getPath()).orElse("").trim(),
                    Optional.ofNullable(summary.getHttpMethod()).orElse("").trim().toUpperCase(Locale.ROOT),
                    Optional.ofNullable(summary.getFuzzer()).orElse("").trim());
        }
    }

    private record ScopeDiff(List<FindingDelta> newErrors, List<FindingDelta> newWarnings,
                             List<FindingDelta> resolvedErrors, List<FindingDelta> resolvedWarnings,
                             int unchangedErrors, int unchangedWarnings) {
    }

    /**
     * Represents a regression or fix within a shared {@code (path, httpMethod, fuzzer)} scope.
     */
    public record FindingDelta(String httpMethod, String path, String fuzzer, String level,
                               String reason, int httpResponseCode, int count) {
    }

    /**
     * Complete apples-to-apples comparison outcome.
     */
    public record ComparisonResult(int sharedScopes, int onlyInBaselineScopes, int onlyInTargetScopes,
                                   List<FindingDelta> newErrors, List<FindingDelta> newWarnings,
                                   List<FindingDelta> resolvedErrors, List<FindingDelta> resolvedWarnings,
                                   int unchangedErrors, int unchangedWarnings, long executionTimeDeltaSec) {
        public int newErrorCount() {
            return newErrors.stream().mapToInt(FindingDelta::count).sum();
        }

        public int newWarningCount() {
            return newWarnings.stream().mapToInt(FindingDelta::count).sum();
        }

        public int resolvedErrorCount() {
            return resolvedErrors.stream().mapToInt(FindingDelta::count).sum();
        }

        public int resolvedWarningCount() {
            return resolvedWarnings.stream().mapToInt(FindingDelta::count).sum();
        }

        public boolean hasRegressions(boolean includeNewWarnings) {
            return newErrorCount() > 0 || (includeNewWarnings && newWarningCount() > 0);
        }
    }
}
