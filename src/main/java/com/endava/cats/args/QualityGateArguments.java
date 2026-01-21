package com.endava.cats.args;

import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.inject.Singleton;
import lombok.Getter;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Holds arguments related to quality gates and exit code behavior.
 * Allows flexible control over when CATS should exit with error codes based on test results.
 */
@Singleton
@Getter
public class QualityGateArguments {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(QualityGateArguments.class);

    @CommandLine.Option(names = {"--failOn"},
            description = "Comma-separated list of conditions that should cause CATS to exit with code 1. " +
                    "Valid values: @|bold error|@, @|bold warn|@. Default: @|bold error|@. " +
                    "Example: @|bold --failOn error,warn|@ will exit 1 on any error or warning")
    private String failOn;

    @CommandLine.Option(names = {"--qualityGate"},
            description = "Comma-separated list of threshold conditions. Format: @|bold metric<threshold|@ or @|bold metric>threshold|@. " +
                    "Valid metrics: @|bold errors|@, @|bold warns|@. " +
                    "Example: @|bold --qualityGate \"errors<5,warns<20\"|@ will exit 1 if errors >= 5 or warns >= 20")
    private String qualityGate;

    /**
     * Evaluates whether CATS should exit with an error code based on the configured quality gates.
     *
     * @param errors   the number of errors reported
     * @param warnings the number of warnings reported
     * @return true if quality gate is violated (should exit with error), false otherwise
     */
    public boolean shouldFailBuild(int errors, int warnings) {
        // Check qualityGate thresholds first (more specific)
        if (qualityGate != null && !qualityGate.trim().isEmpty()) {
            return evaluateQualityGate(errors, warnings);
        }

        // Fall back to failOn behavior
        if (failOn != null && !failOn.trim().isEmpty()) {
            return evaluateFailOn(errors, warnings);
        }

        // Default behavior: fail on any error
        return errors > 0;
    }

    /**
     * Evaluates the --failOn argument.
     *
     * @param errors   the number of errors
     * @param warnings the number of warnings
     * @return true if should fail based on failOn conditions
     */
    private boolean evaluateFailOn(int errors, int warnings) {
        List<String> conditions = Arrays.stream(failOn.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();

        boolean failOnError = conditions.contains("error");
        boolean failOnWarn = conditions.contains("warn");

        if (failOnError && errors > 0) {
            logger.debug("Failing build due to {} errors (--failOn error)", errors);
            return true;
        }

        if (failOnWarn && warnings > 0) {
            logger.debug("Failing build due to {} warnings (--failOn warn)", warnings);
            return true;
        }

        return false;
    }

    /**
     * Evaluates the --qualityGate argument.
     *
     * @param errors   the number of errors
     * @param warnings the number of warnings
     * @return true if any quality gate threshold is violated
     */
    private boolean evaluateQualityGate(int errors, int warnings) {
        Map<String, Integer> metrics = new HashMap<>();
        metrics.put("errors", errors);
        metrics.put("warns", warnings);
        metrics.put("warnings", warnings); // Support both "warns" and "warnings"

        List<String> gates = Arrays.stream(qualityGate.split(","))
                .map(String::trim)
                .toList();

        for (String gate : gates) {
            if (gate.isEmpty()) {
                continue;
            }

            if (evaluateSingleGate(gate, metrics)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Evaluates a single quality gate condition.
     *
     * @param gate    the gate condition (e.g., "errors<5" or "warns>10")
     * @param metrics the current metric values
     * @return true if the gate is violated
     */
    private boolean evaluateSingleGate(String gate, Map<String, Integer> metrics) {
        // Parse gate: metric<threshold or metric>threshold
        String operator;
        String[] parts;

        if (gate.contains("<")) {
            operator = "<";
            parts = gate.split("<", 2);
        } else if (gate.contains(">")) {
            operator = ">";
            parts = gate.split(">", 2);
        } else {
            logger.warn("Invalid quality gate format: {}. Expected format: metric<threshold or metric>threshold", gate);
            return false;
        }

        if (parts.length != 2) {
            logger.warn("Invalid quality gate format: {}. Expected format: metric<threshold or metric>threshold", gate);
            return false;
        }

        String metric = parts[0].trim().toLowerCase(Locale.ROOT);
        String thresholdStr = parts[1].trim();

        if (!metrics.containsKey(metric)) {
            logger.warn("Unknown metric in quality gate: {}. Valid metrics: errors, warns", metric);
            return false;
        }

        try {
            int threshold = Integer.parseInt(thresholdStr);
            int actualValue = metrics.get(metric);

            boolean violated = switch (operator) {
                case "<" -> actualValue >= threshold; // Fail if actual >= threshold (want actual < threshold)
                case ">" -> actualValue <= threshold; // Fail if actual <= threshold (want actual > threshold)
                default -> false;
            };

            if (violated) {
                logger.debug("Quality gate violated: {} (actual: {}, threshold: {}, operator: {})",
                        gate, actualValue, threshold, operator);
            }

            return violated;
        } catch (NumberFormatException e) {
            logger.warn("Invalid threshold value in quality gate: {}. Expected integer.", thresholdStr);
            return false;
        }
    }

    /**
     * Gets a human-readable description of the configured quality gates.
     *
     * @return description of quality gates, or null if none configured
     */
    public String getQualityGateDescription() {
        if (qualityGate != null && !qualityGate.trim().isEmpty()) {
            return "Quality gate: " + qualityGate;
        }
        if (failOn != null && !failOn.trim().isEmpty()) {
            return "Fail on: " + failOn;
        }
        return "Default: fail on any error";
    }
}
