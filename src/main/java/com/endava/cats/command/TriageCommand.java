package com.endava.cats.command;

import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.model.ResourceCorrelation;
import com.endava.cats.util.AnsiUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.util.VersionProvider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Intelligent root-cause clustering, correlation-rule synthesis, and minimal curl reproducer generator
 * for CATS test reports.
 */
@CommandLine.Command(
        name = "triage",
        aliases = {"diagnose", "doctor"},
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Cluster failing CATS report tests into deduplicated root causes, synthesize --correlations YAML rules, and export minimal curl reproducers.",
        abbreviateSynopsis = true,
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Report triaged successfully",
                "@|bold 2|@:Usage error: user input for the command was incorrect",
                "@|bold 1|@:Report folder could not be read"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Triage the default cats-report folder and cluster all errors by root cause:",
                "    cats triage",
                "", "  Triage a custom report folder and export suggested --correlations rules:",
                "    cats triage -r target/cats-report --export-correlations correlations.yml",
                "", "  Export one minimal curl reproducer per root-cause cluster:",
                "    cats triage -r cats-report --export-reproducers reproducers.sh"},
        synopsisHeading = "%nUsage: ",
        versionProvider = VersionProvider.class)
@Unremovable
public class TriageCommand implements Runnable, CommandLine.IExitCodeGenerator {
    private static final Pattern TEST_FILE_PATTERN = Pattern.compile("Test(\\d+)\\.json$");
    private static final Pattern QUOTED_FIELD_PATTERN = Pattern.compile("['\"]([A-Za-z0-9_.#$-]+)['\"]");
    private static final Pattern ENUM_LIKE_CONSTANT_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");
    private static final Set<String> DISCRIMINATOR_KEY_SUFFIXES = Set.of(
            "type", "kind", "mode", "category", "channel", "method", "operator", "status", "action", "role", "state");

    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();

    @CommandLine.Mixin
    ConfigOptions configOptions;

    @CommandLine.Option(names = {"-r", "--report"}, defaultValue = "cats-report",
            description = "The CATS report folder containing Test*.json files (default: ${DEFAULT-VALUE}).")
    String reportFolder = "cats-report";

    @CommandLine.Option(names = {"--include-warnings"},
            description = "Include warning test cases in root-cause clustering in addition to error test cases.")
    boolean includeWarnings;

    @CommandLine.Option(names = {"--export-correlations"},
            description = "Write synthesized --correlations YAML rules inferred from 4XX validation errors to the given file.")
    String exportCorrelationsFile;

    @CommandLine.Option(names = {"--export-reproducers"},
            description = "Write a shell script with one minimal curl reproducer per root-cause cluster.")
    String exportReproducersFile;

    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output the triage report as JSON.")
    boolean json;

    @Getter
    private int exitCode = CommandLine.ExitCode.OK;

    @Getter
    private TriageReport lastReport;

    @Override
    public void run() {
        try {
            Path folder = Paths.get(reportFolder);
            if (!Files.isDirectory(folder)) {
                throw new IllegalArgumentException("Report directory does not exist: " + folder);
            }
            List<IndexedTestCase> testCases = loadTestCases(folder);
            lastReport = buildTriageReport(folder.toString(), testCases);
            if (StringUtils.isNotBlank(exportCorrelationsFile)) {
                Files.writeString(Paths.get(exportCorrelationsFile), lastReport.synthesizedCorrelationsYaml(), StandardCharsets.UTF_8);
            }
            if (StringUtils.isNotBlank(exportReproducersFile)) {
                Files.writeString(Paths.get(exportReproducersFile), buildReproducersScript(lastReport), StandardCharsets.UTF_8);
            }
            renderReport(lastReport);
            exitCode = CommandLine.ExitCode.OK;
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to triage CATS report: {}", e.getMessage());
            exitCode = CommandLine.ExitCode.SOFTWARE;
        }
    }

    List<IndexedTestCase> loadTestCases(Path folder) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(folder)) {
            files = stream
                    .filter(p -> TEST_FILE_PATTERN.matcher(p.getFileName().toString()).find())
                    .sorted(Comparator.comparingInt(TriageCommand::extractTestNumber))
                    .toList();
        }
        List<IndexedTestCase> result = new ArrayList<>(files.size());
        for (Path file : files) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                CatsTestCase tc = JsonUtils.GSON.fromJson(content, CatsTestCase.class);
                if (tc != null && tc.getRequest() != null && tc.getResponse() != null) {
                    result.add(new IndexedTestCase(extractTestNumber(file), tc));
                }
            } catch (RuntimeException _) {
                // ignore malformed files
            }
        }
        return result;
    }

    private static int extractTestNumber(Path path) {
        Matcher m = TEST_FILE_PATTERN.matcher(path.getFileName().toString());
        return m.find() ? Integer.parseInt(m.group(1)) : Integer.MAX_VALUE;
    }

    TriageReport buildTriageReport(String folderPath, List<IndexedTestCase> allTests) {
        Set<String> pathsWithSuccessfulPost = new LinkedHashSet<>();
        Map<String, Integer> firstDeleteTestByPath = new LinkedHashMap<>();
        int errorCount = 0;
        int warningCount = 0;

        for (IndexedTestCase indexed : allTests) {
            CatsTestCase tc = indexed.testCase();
            int code = tc.getResponse().getResponseCode();
            String method = Objects.requireNonNullElse(tc.getRequest().getHttpMethod(), "").toUpperCase(Locale.ROOT);
            String path = Objects.requireNonNullElse(tc.getContractPath(), Objects.requireNonNullElse(tc.getPath(), ""));
            if (code >= 200 && code < 300) {
                if ("POST".equals(method)) {
                    pathsWithSuccessfulPost.add(path);
                } else if ("DELETE".equals(method)) {
                    firstDeleteTestByPath.putIfAbsent(path, indexed.number());
                }
            }
            if ("error".equalsIgnoreCase(tc.getResult())) {
                errorCount++;
            } else if ("warn".equalsIgnoreCase(tc.getResult())) {
                warningCount++;
            }
        }

        Map<String, ClusterAccumulator> clustersByKey = new LinkedHashMap<>();
        List<SynthesizedCorrelationRule> correlationRules = new ArrayList<>();
        Set<String> seenRuleKeys = new LinkedHashSet<>();

        for (IndexedTestCase indexed : allTests) {
            CatsTestCase tc = indexed.testCase();
            boolean isError = "error".equalsIgnoreCase(tc.getResult());
            boolean isWarn = "warn".equalsIgnoreCase(tc.getResult());
            if (!isError && (!includeWarnings || !isWarn)) {
                continue;
            }
            ClusterClassification classification = classify(indexed, pathsWithSuccessfulPost, firstDeleteTestByPath);
            clustersByKey.computeIfAbsent(classification.clusterKey(),
                            _ -> new ClusterAccumulator(classification, buildCurlReproducer(tc)))
                    .add(tc.getTestId(), tc.getFuzzer());

            maybeSynthesizeCorrelationRule(tc, correlationRules, seenRuleKeys);
        }

        List<RootCauseCluster> clusters = clustersByKey.values().stream()
                .map(ClusterAccumulator::toCluster)
                .sorted(Comparator.comparingInt(RootCauseCluster::severityRank)
                        .thenComparing(Comparator.comparingInt(RootCauseCluster::occurrences).reversed()))
                .toList();

        int triagedCount = errorCount + (includeWarnings ? warningCount : 0);
        double noiseReductionPct = triagedCount == 0 || clusters.isEmpty()
                ? 0.0
                : Math.max(0.0, 100.0 * (triagedCount - clusters.size()) / triagedCount);

        String yaml = renderCorrelationsYaml(correlationRules);
        return new TriageReport(
                folderPath,
                allTests.size(),
                errorCount,
                warningCount,
                clusters.size(),
                Math.round(noiseReductionPct * 10.0) / 10.0,
                clusters,
                correlationRules,
                yaml);
    }

    private ClusterClassification classify(IndexedTestCase indexed,
                                           Set<String> pathsWithSuccessfulPost,
                                           Map<String, Integer> firstDeleteTestByPath) {
        CatsTestCase tc = indexed.testCase();
        int code = tc.getResponse().getResponseCode();
        String method = Objects.requireNonNullElse(tc.getRequest().getHttpMethod(), "").toUpperCase(Locale.ROOT);
        String path = Objects.requireNonNullElse(tc.getContractPath(), Objects.requireNonNullElse(tc.getPath(), ""));
        String errorSig = extractErrorSignature(tc);
        List<ResourceCorrelation> correlations = tc.getRequestProvenance() != null
                && tc.getRequestProvenance().getRuntimeCorrelations() != null
                ? tc.getRequestProvenance().getRuntimeCorrelations()
                : List.of();

        for (ResourceCorrelation corr : correlations) {
            if (corr.getTarget() != null && corr.getTarget().location() == RequestTarget.Location.HEADER) {
                String headerName = corr.getTarget().name();
                String key = "HEADER_CORRELATION_COLLISION|" + method + "|" + path + "|" + headerName;
                return new ClusterClassification(
                        key,
                        "HEADER_CORRELATION_COLLISION",
                        1,
                        method + " " + path,
                        code,
                        "Header [" + headerName + "] was overwritten by runtime correlation from [" + corr.getSourceLocation() + "]",
                        "Exclude per-request header '" + headerName + "' from resource pool correlation or pin it via -H / headers.yml.");
            }
        }

        if (code >= 500 && code < 600) {
            String key = "SERVER_CRASH_5XX|" + method + "|" + path + "|" + errorSig;
            return new ClusterClassification(
                    key,
                    "SERVER_CRASH_5XX",
                    0,
                    method + " " + path,
                    code,
                    "Unhandled " + code + " server error (" + errorSig + ")",
                    "Inspect server stacktrace for " + method + " " + path + " and replay with the minimal curl reproducer below.");
        }

        if (code == 404) {
            for (ResourceCorrelation corr : correlations) {
                if (corr.getTarget() != null && corr.getTarget().location() == RequestTarget.Location.PATH
                        && corr.getSourceLocation() != null && corr.getSourceLocation().endsWith(".name")) {
                    String key = "SUSPICIOUS_CORRELATION_404|" + method + "|" + path + "|" + corr.getSourceLocation();
                    return new ClusterClassification(
                            key,
                            "SUSPICIOUS_CORRELATION_404",
                            2,
                            method + " " + path,
                            code,
                            "Path parameter [" + corr.getTarget().name() + "] was correlated from non-ID field ["
                                    + corr.getSourceLocation() + " = " + corr.getValue() + "]",
                            "Ensure collection responses expose a primary resource identifier or map '" + corr.getTarget().name() + "' via --refData.");
                }
            }
            String parentDeletePath = findPrecedingDeletePath(path, indexed.number(), firstDeleteTestByPath);
            if (parentDeletePath != null) {
                String key = "RESOURCE_POOL_DEPLETED_404|" + method + "|" + path + "|after:" + parentDeletePath;
                return new ClusterClassification(
                        key,
                        "RESOURCE_POOL_DEPLETED_404",
                        2,
                        method + " " + path,
                        code,
                        "Resource pool depleted after earlier DELETE on [" + parentDeletePath + "] (starting at Test "
                                + firstDeleteTestByPath.get(parentDeletePath) + ")",
                        "Run non-DELETE operations across child paths before parent DELETEs or supply a persistent ID via --urlParams.");
            }
            String collectionPath = parentCollectionPath(path);
            boolean producerSucceeded = pathsWithSuccessfulPost.contains(collectionPath);
            String key = "UNRESOLVED_PATH_PARAM_404|" + method + "|" + path;
            String diagnosis = producerSucceeded
                    ? "Path parameter on " + path + " was not enriched with an active resource ID"
                    : "No successful 2XX POST producer recorded for [" + collectionPath + "]";
            return new ClusterClassification(
                    key,
                    "UNRESOLVED_PATH_PARAM_404",
                    2,
                    method + " " + path,
                    code,
                    diagnosis,
                    "Seed a valid resource via POST " + collectionPath + " or pass --urlParams <param>:<value>.");
        }

        if ((code == 400 || code == 422) && ("HappyPath".equals(tc.getFuzzer()) || "OnlyRequiredFields".equals(tc.getFuzzer())
                || tc.getExpectedResult() != null && tc.getExpectedResult().contains("2XX"))) {
            String key = "CROSS_FIELD_CONSTRAINT_4XX|" + method + "|" + path + "|" + errorSig;
            return new ClusterClassification(
                    key,
                    "CROSS_FIELD_CONSTRAINT_4XX",
                    1,
                    method + " " + path,
                    code,
                    "Domain / cross-field validation rejected request (" + errorSig + ")",
                    "Add the synthesized --correlations rule for " + path + " or pin dependent fields in --refData.");
        }

        String reason = StringUtils.defaultIfBlank(tc.getResultReason(), "Unexpected response " + code);
        String key = "CONTRACT_MISMATCH|" + method + "|" + path + "|" + code + "|" + reason;
        return new ClusterClassification(
                key,
                "CONTRACT_MISMATCH",
                3,
                method + " " + path,
                code,
                reason + " (HTTP " + code + "; " + errorSig + ")",
                "Align the OpenAPI response specification for " + method + " " + path + " or adjust fuzzer expectations.");
    }

    private static String findPrecedingDeletePath(String path, int testNumber, Map<String, Integer> firstDeleteTestByPath) {
        for (Map.Entry<String, Integer> entry : firstDeleteTestByPath.entrySet()) {
            String deletedPath = entry.getKey();
            if (entry.getValue() < testNumber && (path.equals(deletedPath) || path.startsWith(deletedPath + "/"))) {
                return deletedPath;
            }
        }
        String collection = parentCollectionPath(path);
        for (Map.Entry<String, Integer> entry : firstDeleteTestByPath.entrySet()) {
            if (entry.getValue() < testNumber && entry.getKey().startsWith(collection + "/")) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String parentCollectionPath(String path) {
        if (path == null) {
            return "";
        }
        int braceIdx = path.indexOf("/{");
        return braceIdx > 0 ? path.substring(0, braceIdx) : path;
    }

    private static String extractErrorSignature(CatsTestCase tc) {
        JsonElement body = tc.getResponse().getJsonBody();
        if (body != null && body.isJsonObject()) {
            JsonObject obj = body.getAsJsonObject();
            for (String key : List.of("message", "detail", "error", "title", "errorCode")) {
                if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                    String msg = obj.get(key).getAsString().trim();
                    if (!msg.isEmpty()) {
                        return msg.length() > 90 ? msg.substring(0, 90) + "..." : msg;
                    }
                }
            }
        }
        return StringUtils.defaultIfBlank(tc.getResultReason(), "HTTP " + tc.getResponse().getResponseCode());
    }

    private static void maybeSynthesizeCorrelationRule(CatsTestCase tc,
                                                       List<SynthesizedCorrelationRule> rules,
                                                       Set<String> seenRuleKeys) {
        int code = tc.getResponse().getResponseCode();
        if (code != 400 && code != 422) {
            return;
        }
        String payload = tc.getRequest().getPayload();
        if (StringUtils.isBlank(payload) || !JsonUtils.isValidJson(payload)) {
            return;
        }
        String errorSig = extractErrorSignature(tc);
        Matcher m = QUOTED_FIELD_PATTERN.matcher(errorSig);
        if (!m.find()) {
            return;
        }
        String referencedField = m.group(1);
        JsonElement parsed = JsonUtils.parseAsJsonElement(payload);
        if (!parsed.isJsonObject()) {
            return;
        }
        JsonObject root = parsed.getAsJsonObject();
        String discriminatorKey = null;
        String discriminatorVal = null;
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String k = entry.getKey();
            if (!k.equals(referencedField) && entry.getValue() != null && entry.getValue().isJsonPrimitive()
                    && isDiscriminatorCandidate(k, entry.getValue().getAsString())) {
                discriminatorKey = k;
                discriminatorVal = entry.getValue().getAsString();
                break;
            }
        }
        if (discriminatorKey == null) {
            return;
        }
        String action = errorSig.toLowerCase(Locale.ROOT).contains("must be null")
                || errorSig.toLowerCase(Locale.ROOT).contains("unrecognized")
                || errorSig.toLowerCase(Locale.ROOT).contains("not allowed")
                ? "REMOVE"
                : "REQUIRED";
        String path = Objects.requireNonNullElse(tc.getContractPath(), Objects.requireNonNullElse(tc.getPath(), ""));
        String dedupKey = path + "|" + discriminatorKey + "=" + discriminatorVal + "|" + referencedField + "|" + action;
        if (seenRuleKeys.add(dedupKey)) {
            rules.add(new SynthesizedCorrelationRule(path, discriminatorKey, discriminatorVal, referencedField, action, errorSig));
        }
    }

    private static boolean isDiscriminatorCandidate(String fieldName, String fieldValue) {
        if (StringUtils.isBlank(fieldName) || StringUtils.isBlank(fieldValue)) {
            return false;
        }
        String lowerName = fieldName.toLowerCase(Locale.ROOT);
        if (DISCRIMINATOR_KEY_SUFFIXES.stream().anyMatch(lowerName::endsWith)) {
            return true;
        }
        return ENUM_LIKE_CONSTANT_PATTERN.matcher(fieldValue).matches();
    }

    private static String renderCorrelationsYaml(List<SynthesizedCorrelationRule> rules) {
        if (rules.isEmpty()) {
            return "# No cross-field correlation rules needed\n";
        }
        Map<String, List<SynthesizedCorrelationRule>> byPath = new LinkedHashMap<>();
        for (SynthesizedCorrelationRule rule : rules) {
            byPath.computeIfAbsent(rule.path(), _ -> new ArrayList<>()).add(rule);
        }
        StringBuilder sb = new StringBuilder("# Auto-synthesized by `cats triage`\n");
        byPath.forEach((path, pathRules) -> {
            sb.append(path).append(":\n");
            for (int i = 0; i < pathRules.size(); i++) {
                SynthesizedCorrelationRule r = pathRules.get(i);
                sb.append("  - name: auto-rule-").append(i + 1).append("-").append(r.targetField()).append("\n");
                sb.append("    when:\n");
                sb.append("      ").append(r.whenField()).append(": ").append(r.whenValue()).append("\n");
                sb.append("    then:\n");
                sb.append("      ").append(r.targetField()).append(": ")
                        .append("REMOVE".equals(r.action()) ? "null" : "\"<REQUIRED_VALUE>\"").append("\n");
            }
        });
        return sb.toString();
    }

    private static String buildCurlReproducer(CatsTestCase tc) {
        String method = StringUtils.defaultIfBlank(tc.getRequest().getHttpMethod(), "GET").toUpperCase(Locale.ROOT);
        String url = Objects.requireNonNullElse(tc.getRequest().getUrl(), Objects.requireNonNullElse(tc.getFullRequestPath(), ""));
        StringBuilder curl = new StringBuilder("curl -i -X ").append(method).append(" '").append(url).append("'");
        if (tc.getRequest().getHeaders() != null) {
            for (KeyValuePair<String, Object> h : tc.getRequest().getHeaders()) {
                if (h.getKey() != null && h.getValue() != null) {
                    curl.append(" -H '").append(h.getKey()).append(": ").append(h.getValue()).append("'");
                }
            }
        }
        String payload = tc.getRequest().getPayload();
        if (StringUtils.isNotBlank(payload) && tc.getRequest().isSendBody()
                && ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
            curl.append(" -d '").append(payload.replace("'", "'\\''")).append("'");
        }
        return curl.toString();
    }

    private static String buildReproducersScript(TriageReport report) {
        StringBuilder sb = new StringBuilder("#!/usr/bin/env bash\n# Auto-generated by `cats triage`\nset -euo pipefail\n\n");
        int idx = 1;
        for (RootCauseCluster cluster : report.clusters()) {
            sb.append("# [").append(idx++).append("] ").append(cluster.category()).append(" | ")
                    .append(cluster.operation()).append(" (").append(cluster.occurrences()).append(" tests, e.g. ")
                    .append(cluster.sampleTests().getFirst()).append(")\n");
            sb.append("# ").append(cluster.diagnosis()).append("\n");
            sb.append(cluster.curlReproducer()).append("\n\n");
        }
        return sb.toString();
    }

    private void renderReport(TriageReport report) {
        if (json) {
            logger.noFormat(JsonUtils.GSON.toJson(report));
            return;
        }
        logger.noFormat("");
        logger.noFormat(AnsiUtils.bold("================================================================================"));
        logger.noFormat(AnsiUtils.bold("  CATS Intelligent Report Triage & Root-Cause Diagnosis"));
        logger.noFormat(AnsiUtils.bold("================================================================================"));
        logger.noFormat("  Report Folder      : " + report.reportFolder());
        logger.noFormat("  Tests Scanned      : " + report.totalTestsScanned()
                + "  (Errors: " + AnsiUtils.red(report.totalErrors())
                + ", Warnings: " + AnsiUtils.yellow(report.totalWarnings()) + ")");
        logger.noFormat("  Root-Cause Clusters: " + AnsiUtils.green(report.distinctClusters())
                + "  (Signal-to-Noise Reduction: " + AnsiUtils.bold(report.noiseReductionPercent() + "%") + ")");
        logger.noFormat("");

        int index = 1;
        for (RootCauseCluster cluster : report.clusters()) {
            String badge = switch (cluster.category()) {
                case "SERVER_CRASH_5XX" -> AnsiUtils.red("[" + cluster.category() + "]");
                case "HEADER_CORRELATION_COLLISION", "CROSS_FIELD_CONSTRAINT_4XX" -> AnsiUtils.yellow("[" + cluster.category() + "]");
                default -> AnsiUtils.blue("[" + cluster.category() + "]");
            };
            logger.noFormat("  " + index++ + ". " + badge + " " + AnsiUtils.bold(cluster.operation())
                    + " -> HTTP " + cluster.responseCode() + " (" + cluster.occurrences() + " tests)");
            logger.noFormat("     Diagnosis   : " + cluster.diagnosis());
            logger.noFormat("     Remediation : " + cluster.remediation());
            logger.noFormat("     Fuzzers     : " + String.join(", ", cluster.fuzzers()));
            logger.noFormat("     Sample Tests: " + String.join(", ", cluster.sampleTests()));
            logger.noFormat("     Reproducer  : " + cluster.curlReproducer());
            logger.noFormat("");
        }

        if (!report.synthesizedCorrelations().isEmpty()) {
            logger.noFormat(AnsiUtils.bold("  Suggested --correlations YAML Rules:"));
            logger.noFormat("  ------------------------------------------------------------------------------");
            for (String line : report.synthesizedCorrelationsYaml().split("\n")) {
                logger.noFormat("  " + line);
            }
        }
        logger.noFormat(AnsiUtils.bold("================================================================================"));
    }

    record IndexedTestCase(int number, CatsTestCase testCase) {
    }

    private record ClusterClassification(
            String clusterKey,
            String category,
            int severityRank,
            String operation,
            int responseCode,
            String diagnosis,
            String remediation) {
    }

    private static final class ClusterAccumulator {
        private final ClusterClassification classification;
        private final String curlReproducer;
        private final List<String> sampleTests = new ArrayList<>();
        private final Set<String> fuzzers = new LinkedHashSet<>();
        private int count;

        private ClusterAccumulator(ClusterClassification classification, String curlReproducer) {
            this.classification = classification;
            this.curlReproducer = curlReproducer;
        }

        private void add(String testId, String fuzzer) {
            count++;
            if (sampleTests.size() < 5 && testId != null) {
                sampleTests.add(testId);
            }
            if (fuzzer != null && fuzzers.size() < 6) {
                fuzzers.add(fuzzer);
            }
        }

        private RootCauseCluster toCluster() {
            return new RootCauseCluster(
                    classification.category(),
                    classification.severityRank(),
                    classification.operation(),
                    classification.responseCode(),
                    count,
                    classification.diagnosis(),
                    classification.remediation(),
                    List.copyOf(sampleTests),
                    List.copyOf(fuzzers),
                    curlReproducer);
        }
    }

    /**
     * Deduplicated root-cause cluster across one or more failing CATS tests.
     */
    public record RootCauseCluster(
            String category,
            int severityRank,
            String operation,
            int responseCode,
            int occurrences,
            String diagnosis,
            String remediation,
            List<String> sampleTests,
            List<String> fuzzers,
            String curlReproducer) {
    }

    /**
     * Auto-synthesized correlation rule inferred from a 4XX cross-field validation error.
     */
    public record SynthesizedCorrelationRule(
            String path,
            String whenField,
            String whenValue,
            String targetField,
            String action,
            String sourceError) {
    }

    /**
     * Complete triage report output.
     */
    public record TriageReport(
            String reportFolder,
            int totalTestsScanned,
            int totalErrors,
            int totalWarnings,
            int distinctClusters,
            double noiseReductionPercent,
            List<RootCauseCluster> clusters,
            List<SynthesizedCorrelationRule> synthesizedCorrelations,
            String synthesizedCorrelationsYaml) {
    }
}
