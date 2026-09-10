package com.endava.cats.report;

import com.endava.cats.model.CatsConfiguration;
import com.endava.cats.model.CatsTestCaseSummary;
import com.endava.cats.model.CatsTestReport;
import com.endava.cats.model.ExecutionSummary;
import com.endava.cats.model.ProcessingError;
import com.endava.cats.model.RunOutcome;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Typed data exposed to all summary report templates. */
@Builder
@Getter
public final class SummaryReportContext {
    private final String warnings;
    private final String success;
    private final String errors;
    private final String errorsJunit;
    private final String failuresJunit;
    private final String total;
    private final String totalRequests;
    private final String skippedFromReporting;
    private final String skipped;
    private final String authErrors;
    private final String ioErrors;
    private final String timestamp;
    private final String timestampIso;
    private final List<CatsTestCaseSummary> testCases;
    private final List<CatsTestReport.JunitTestSuite> testSuites;
    private final String execution;
    private final long time;
    private final String version;
    private final boolean javascript;
    private final String os;
    private final String averageResponseTime;
    private final String runStatus;
    private final String runStatusDetails;
    private final String runStatusResult;
    private final String qualityGateStatus;
    private final String qualityGateResult;
    private final String qualityGateDescription;
    private final long randomSeed;
    private final String resourceReuseStatus;
    private final List<StopLimit> stopLimits;
    private final boolean hasStopLimits;
    private final List<ProcessingErrorEntry> processingErrors;
    private final boolean hasProcessingErrors;
    private final double warnPercentage;
    private final double errorPercentage;
    private final double successPercentage;
    private final String contractName;
    private final String baseUrl;
    private final List<String> httpMethods;
    private final int fuzzers;
    private final long totalFuzzers;
    private final int paths;
    private final int totalPaths;
    private final List<Map<String, Object>> groupedTestCases;
    private final List<ResponseCodeEntry> responseCodeDistribution;
    private final boolean hasResponseCodes;
    private final List<FailingPathEntry> topFailingPaths;
    private final boolean hasFailingPaths;

    /**
     * Creates the view consumed by the HTML and JUnit summary templates.
     *
     * @param report JSON-compatible report model
     * @param executionSummary authoritative execution snapshot
     * @param configuration effective run configuration, if available
     * @param javascript whether the selected report uses JavaScript
     * @param osDetails operating-system description
     * @param averageResponseTime average response time in milliseconds
     * @return typed template context
     */
    public static SummaryReportContext create(CatsTestReport report, ExecutionSummary executionSummary,
                                              CatsConfiguration configuration, boolean javascript,
                                              String osDetails, double averageResponseTime) {
        List<StopLimit> stopLimits = stopLimits(report);
        List<ProcessingErrorEntry> processingErrors = report.getProcessingErrors().stream()
                .map(SummaryReportContext::processingError)
                .toList();
        List<ResponseCodeEntry> responseCodes = responseCodes(executionSummary.responseCodeDistribution());
        List<FailingPathEntry> failingPaths = executionSummary.topFailingPaths().entrySet().stream()
                .map(entry -> new FailingPathEntry(entry.getKey(), entry.getValue()))
                .toList();

        SummaryReportContextBuilder builder = builder()
                .warnings(formatLargeNumber(report.getWarnings()))
                .success(formatLargeNumber(report.getSuccess()))
                .errors(formatLargeNumber(report.getErrors()))
                .errorsJunit(formatLargeNumber(report.getErrorsJunit()))
                .failuresJunit(formatLargeNumber(report.getFailuresJunit()))
                .total(formatLargeNumber(report.getTotalTests()))
                .totalRequests(formatLargeNumber(report.getTotalRequests()))
                .skippedFromReporting(formatLargeNumber(report.getSkippedFromReporting()))
                .skipped(formatLargeNumber(report.getSkipped()))
                .authErrors(formatLargeNumber(report.getAuthErrors()))
                .ioErrors(formatLargeNumber(report.getIoErrors()))
                .timestamp(report.getTimestamp())
                .timestampIso(OffsetDateTime.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_DATE_TIME))
                .testCases(report.getTestCases())
                .testSuites(report.getTestSuites())
                .execution(Duration.ofSeconds(report.getExecutionTime()).toString().toLowerCase(Locale.ROOT).substring(2))
                .time(report.getExecutionTime())
                .version(report.getCatsVersion())
                .javascript(javascript)
                .os(osDetails)
                .averageResponseTime(new DecimalFormat("#0.0").format(averageResponseTime))
                .runStatus(formatRunStatus(report.getRunStatus()))
                .runStatusDetails(report.getRunStatusDetails())
                .runStatusResult(runStatusResult(executionSummary.outcome().status()))
                .qualityGateStatus(report.isQualityGatePassed() ? "PASSED" : "FAILED")
                .qualityGateResult(report.isQualityGatePassed() ? "success" : "error")
                .qualityGateDescription(report.getQualityGateDescription())
                .randomSeed(report.getRandomSeed())
                .resourceReuseStatus(report.isSuccessfulResourceReuseEnabled() ? "Enabled" : "Disabled")
                .stopLimits(stopLimits)
                .hasStopLimits(!stopLimits.isEmpty())
                .processingErrors(processingErrors)
                .hasProcessingErrors(!processingErrors.isEmpty())
                .warnPercentage(percentage(report.getWarnings(), report.getTotalTests()))
                .errorPercentage(percentage(report.getErrors(), report.getTotalTests()))
                .successPercentage(percentage(report.getSuccess(), report.getTotalTests()))
                .groupedTestCases(ClusterCompute.createClusters(report.getTestCases()))
                .responseCodeDistribution(responseCodes)
                .hasResponseCodes(!responseCodes.isEmpty())
                .topFailingPaths(failingPaths)
                .hasFailingPaths(!failingPaths.isEmpty())
                .httpMethods(List.of());

        if (configuration != null) {
            builder.contractName(configuration.contract())
                    .baseUrl(configuration.basePath())
                    .httpMethods(configuration.httpMethods().stream()
                            .map(Enum::name).map(String::toLowerCase).toList())
                    .fuzzers(configuration.fuzzers())
                    .totalFuzzers(configuration.totalFuzzers())
                    .paths(configuration.pathsToRun())
                    .totalPaths(configuration.totalPaths());
        }
        return builder.build();
    }

    private static String formatLargeNumber(long value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,###", symbols).format(value);
    }

    private static double percentage(long count, long total) {
        return total == 0 ? 0 : (double) count / total * 100;
    }

    private static String runStatusResult(RunOutcome.Status runStatus) {
        return switch (runStatus) {
            case COMPLETED -> "success";
            case LIMIT_REACHED -> "info";
            case CANCELLED -> "warn";
            case FAILED -> "error";
        };
    }

    private static String formatRunStatus(String runStatus) {
        String normalized = runStatus.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static List<StopLimit> stopLimits(CatsTestReport report) {
        return List.of(
                        new StopLimit("Tests", report.getStopAfterTests(), ""),
                        new StopLimit("Errors", report.getStopAfterErrors(), ""),
                        new StopLimit("Time", report.getStopAfterTimeInSec(), " seconds"))
                .stream()
                .filter(limit -> limit.value() > 0)
                .toList();
    }

    private static ProcessingErrorEntry processingError(ProcessingError error) {
        String operation;
        if (StringUtils.isBlank(error.httpMethod())) {
            operation = StringUtils.defaultIfBlank(error.path(), "Global");
        } else {
            operation = error.httpMethod() + (StringUtils.isBlank(error.path()) ? "" : " " + error.path());
        }
        return new ProcessingErrorEntry(operation, error.message());
    }

    private static List<ResponseCodeEntry> responseCodes(Map<Integer, Integer> distribution) {
        return distribution.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ResponseCodeEntry(entry.getKey(), entry.getValue(),
                        responseCodeFamily(entry.getKey())))
                .toList();
    }

    private static String responseCodeFamily(int code) {
        if (code >= 200 && code < 300) {
            return "2xx";
        }
        if (code >= 300 && code < 400) {
            return "3xx";
        }
        if (code >= 400 && code < 500) {
            return "4xx";
        }
        if (code >= 500 && code < 600) {
            return "5xx";
        }
        return "other";
    }

    /** Configured execution limit rendered in the report. */
    public record StopLimit(String name, long value, String suffix) {
    }

    /** Contract-processing limitation rendered in the report. */
    public record ProcessingErrorEntry(String operation, String message) {
    }

    /** Response-code statistic rendered in the report. */
    public record ResponseCodeEntry(int code, int count, String family) {
    }

    /** Failing-path statistic rendered in the report. */
    public record FailingPathEntry(String path, long count) {
    }
}
