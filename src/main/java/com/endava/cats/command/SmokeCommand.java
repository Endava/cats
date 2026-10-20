package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.AuthArguments;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.pool.RuntimeResourcePool;
import com.endava.cats.tui.event.CatsExecutionEvent;
import com.endava.cats.tui.model.TestResultSnapshot;
import com.endava.cats.util.AnsiUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import lombok.Getter;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a fast 1-request-per-operation contract-vs-live-server health, route, and authentication smoke check.
 */
@CommandLine.Command(
        name = "smoke",
        aliases = {"probe"},
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Fast 1-request-per-operation health, route, and authentication check against a live server.",
        abbreviateSynopsis = true,
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Smoke check passed (server reachable and no 5XX crashes)",
                "@|bold 2|@:Usage error: user input for the command was incorrect",
                "@|bold 1|@:Smoke check detected 5XX server crashes, I/O failures, or complete authentication failure"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Run a fast smoke check against a staging server:",
                "    cats smoke -c openapi.yml -s https://staging.example.com -H \"Authorization=Bearer $$TOKEN\"",
                "", "  Output the smoke check report as JSON:",
                "    cats smoke -c openapi.yml -s http://localhost:8080 -j"},
        synopsisHeading = "%nUsage: ",
        versionProvider = VersionProvider.class)
@Unremovable
public class SmokeCommand implements Runnable, CommandLine.IExitCodeGenerator {
    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();

    @CommandLine.Mixin
    ConfigOptions configOptions;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline API Options:|@%n", exclusive = false)
    ApiArguments apiArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Authentication Options:|@%n", exclusive = false)
    AuthArguments authArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Files Options:|@%n", exclusive = false)
    FilesArguments filesArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Filter Options:|@%n", exclusive = false)
    FilterArguments filterArguments;

    @Inject
    @CommandLine.ArgGroup(heading = "%n@|bold,underline Reporting Options:|@%n", exclusive = false)
    ReportingArguments reportingArguments;

    @CommandLine.Option(names = {"--includeDelete"},
            description = "Include DELETE operations during the smoke check. Default: @|bold,underline ${DEFAULT-VALUE}|@")
    boolean includeDelete;

    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output the smoke check diagnostic report as JSON.")
    boolean json;

    @CommandLine.ParentCommand
    CatsCommand catsCommand;

    @Getter
    private int exitCode = CommandLine.ExitCode.OK;

    @Getter
    private SmokeReport lastReport;

    @Override
    public void run() {
        configureSmokeExecution();
        List<TestResultSnapshot> capturedTests = new ArrayList<>();
        try (var _ = catsCommand.executionEventPublisher.subscribe(event -> {
            if (event instanceof CatsExecutionEvent.TestCompleted(_, var test)) {
                capturedTests.add(test);
            }
        })) {
            catsCommand.run();
        }
        lastReport = buildReport(capturedTests);
        exitCode = computeExitCode(lastReport);
        renderReport(lastReport);
    }

    private void configureSmokeExecution() {
        filterArguments.customFilter("HappyPath");
        if (filterArguments.getCheckArguments() != null) {
            filterArguments.getCheckArguments().setIncludeContract(false);
        }
        if (!includeDelete) {
            filterArguments.setSkippedHttpMethods(List.of(HttpMethod.DELETE));
        }
    }

    SmokeReport buildReport(List<TestResultSnapshot> tests) {
        Map<String, OperationProbe> byOperation = new LinkedHashMap<>();
        for (TestResultSnapshot test : tests) {
            if ("skip_reporting".equalsIgnoreCase(test.result()) || "skipped".equalsIgnoreCase(test.result())) {
                continue;
            }
            String method = test.request() != null && test.request().httpMethod() != null ? test.request().httpMethod() : "GET";
            String path = test.contractPath() != null ? test.contractPath() : test.path();
            int code = test.response() != null ? test.response().responseCode() : 0;
            long timeMs = test.response() != null ? test.response().responseTimeInMs() : 0;
            String key = method + " " + path;
            OperationProbe probe = new OperationProbe(method, path, code, timeMs, diagnoseStatusCode(code));
            OperationProbe existing = byOperation.get(key);
            if (existing == null || (existing.statusCode() >= 400 && code >= 200 && code < 300)) {
                byOperation.put(key, probe);
            }
        }
        List<OperationProbe> probes = List.copyOf(byOperation.values());
        int ok2xx = countInRange(probes, 200, 300);
        int authErrors = (int) probes.stream().filter(p -> p.statusCode() == 401 || p.statusCode() == 403).count();
        int notFound404 = (int) probes.stream().filter(p -> p.statusCode() == 404).count();
        int validation4xx = (int) probes.stream()
                .filter(p -> p.statusCode() >= 400 && p.statusCode() < 500 && p.statusCode() != 401
                        && p.statusCode() != 403 && p.statusCode() != 404)
                .count();
        int server5xx = countInRange(probes, 500, 600);
        int ioErrors = (int) probes.stream().filter(p -> p.statusCode() < 100 || p.statusCode() >= 900).count();
        List<String> unresolved = collectUnresolvedParameters();
        String verdict = determineVerdict(probes.size(), ok2xx, authErrors, server5xx, ioErrors);
        return new SmokeReport(probes, probes.size(), ok2xx, authErrors, notFound404,
                validation4xx, server5xx, ioErrors, unresolved, verdict);
    }

    private List<String> collectUnresolvedParameters() {
        if (catsCommand == null || catsCommand.runtimeResourcePool == null) {
            return List.of();
        }
        return catsCommand.runtimeResourcePool.unresolvedOperations().stream()
                .map(op -> "%s %s (missing: %s)".formatted(
                        op.outcome().method(), op.outcome().path(),
                        op.parameters().stream()
                                .map(p -> p.target().name())
                                .collect(java.util.stream.Collectors.joining(", "))))
                .toList();
    }

    private static int countInRange(List<OperationProbe> probes, int minInclusive, int maxExclusive) {
        return (int) probes.stream()
                .filter(p -> p.statusCode() >= minInclusive && p.statusCode() < maxExclusive)
                .count();
    }

    static String diagnoseStatusCode(int code) {
        if (code >= 200 && code < 300) {
            return "2XX OK";
        }
        if (code >= 300 && code < 400) {
            return "3XX Redirect";
        }
        if (code == 401 || code == 403) {
            return "Auth Required / Denied";
        }
        if (code == 404) {
            return "Not Found (Route / ID)";
        }
        if (code >= 400 && code < 500) {
            return "4XX Validation Rejected";
        }
        if (code >= 500 && code < 600) {
            return "5XX Server Crash";
        }
        return "I/O / Connection Error";
    }

    private static String determineVerdict(int total, int ok2xx, int authErrors, int server5xx, int ioErrors) {
        if (total == 0) {
            return "NO_OPERATIONS_EXECUTED";
        }
        if (ioErrors == total) {
            return "UNREACHABLE_SERVER";
        }
        if (authErrors == total) {
            return "AUTH_FAILURE_ALL_ENDPOINTS";
        }
        if (server5xx > 0) {
            return "SERVER_CRASHES_DETECTED";
        }
        if (ok2xx > 0) {
            return "READY";
        }
        return "NEEDS_SEED_OR_AUTH_CONFIG";
    }

    private static int computeExitCode(SmokeReport report) {
        if (report.totalOperations() == 0 || report.ioErrors() > 0 || report.server5xx() > 0) {
            return CommandLine.ExitCode.SOFTWARE;
        }
        if (report.authErrors() == report.totalOperations()) {
            return CommandLine.ExitCode.SOFTWARE;
        }
        return CommandLine.ExitCode.OK;
    }

    private void renderReport(SmokeReport report) {
        if (json) {
            logger.noFormat(JsonUtils.GSON.toJson(report));
            return;
        }
        logger.noFormat("");
        logger.noFormat(AnsiUtils.bold("=== CATS Smoke Check Diagnostic Report ==="));
        logger.noFormat(String.format("%-8s %-44s %-8s %-9s %s", "METHOD", "PATH", "STATUS", "LATENCY", "DIAGNOSIS"));
        logger.noFormat("-".repeat(92));
        for (OperationProbe probe : report.operations()) {
            logger.noFormat(String.format("%-8s %-44s %-8d %-9s %s",
                    probe.method(), truncate(probe.path(), 44), probe.statusCode(),
                    probe.responseTimeMs() + "ms", probe.diagnosis()));
        }
        logger.noFormat("-".repeat(92));
        logger.noFormat(String.format(
                "Summary: %d operations probed | 2XX OK: %d | Auth (401/403): %d | 404 Not Found: %d | 4XX Validation: %d | 5XX Crash: %d | I/O Errors: %d",
                report.totalOperations(), report.ok2xx(), report.authErrors(),
                report.notFound404(), report.validation4xx(), report.server5xx(), report.ioErrors()));
        logger.noFormat("Verdict: " + AnsiUtils.bold(report.verdict()));
        if (!report.unresolvedParameters().isEmpty()) {
            logger.noFormat("Unresolved Operations (consider --urlParams or cats init):");
            report.unresolvedParameters().forEach(line -> logger.noFormat("  - " + line));
        }
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen - 3) + "...";
    }

    /**
     * Per-operation smoke probe outcome.
     */
    public record OperationProbe(String method, String path, int statusCode, long responseTimeMs, String diagnosis) {
    }

    /**
     * Aggregated smoke check report.
     */
    public record SmokeReport(List<OperationProbe> operations, int totalOperations, int ok2xx, int authErrors,
                              int notFound404, int validation4xx, int server5xx, int ioErrors,
                              List<String> unresolvedParameters, String verdict) {
    }
}
