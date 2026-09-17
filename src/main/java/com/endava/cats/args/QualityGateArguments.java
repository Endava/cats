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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds arguments related to quality gates and exit code behavior.
 * Allows flexible control over when CATS should exit with error codes based on test results.
 */
@Singleton
@Getter
public class QualityGateArguments {
    private static final Set<String> VALID_FAIL_ON_CONDITIONS = Set.of("error", "warn");
    private static final Pattern QUALITY_GATE_PATTERN = Pattern.compile("(?i)^(errors|warns|warnings)\\s*([<>])\\s*(\\d+)$");
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(QualityGateArguments.class);

    @CommandLine.Option(names = {"--failOn"}, converter = FailOnConverter.class,
            description = "Comma-separated list of conditions that should cause CATS to exit with code 1. " +
                    "Valid values: @|bold error|@, @|bold warn|@. Default: @|bold error|@. " +
                    "Example: @|bold --failOn error,warn|@ will exit 1 on any error or warning")
    private String failOn;

    @CommandLine.Option(names = {"--qualityGate"}, converter = QualityGateConverter.class,
            description = "Comma-separated list of threshold conditions. Format: @|bold metric<threshold|@ or @|bold metric>threshold|@. " +
                    "Valid metrics: @|bold errors|@, @|bold warns|@, @|bold warnings|@. Thresholds must be non-negative integers. " +
                    "Example: @|bold --qualityGate \"errors<5,warns<20\"|@ will exit 1 if errors >= 5 or warns >= 20")
    private String qualityGate;

    private static void validateFailOn(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        List<String> invalidConditions = Arrays.stream(value.split(",", -1))
                .map(String::trim)
                .map(condition -> condition.toLowerCase(Locale.ROOT))
                .filter(condition -> !VALID_FAIL_ON_CONDITIONS.contains(condition))
                .toList();
        if (!invalidConditions.isEmpty()) {
            throw new CommandLine.TypeConversionException("Invalid --failOn value '" + value + "'. Valid values: error, warn");
        }
    }

    private static void validateQualityGate(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String condition : value.split(",", -1)) {
            Matcher matcher = QUALITY_GATE_PATTERN.matcher(condition.trim());
            if (!matcher.matches()) {
                throw invalidQualityGate(condition);
            }
            try {
                Long.parseLong(matcher.group(3));
            } catch (NumberFormatException _) {
                throw invalidQualityGate(condition);
            }
        }
    }

    private static CommandLine.TypeConversionException invalidQualityGate(String condition) {
        return new CommandLine.TypeConversionException("Invalid --qualityGate condition '" + condition.trim() +
                "'. Expected errors, warns, or warnings followed by < or > and a non-negative integer threshold");
    }

    public static final class FailOnConverter implements CommandLine.ITypeConverter<String> {
        @Override
        public String convert(String value) {
            validateFailOn(value);
            return value;
        }
    }

    public static final class QualityGateConverter implements CommandLine.ITypeConverter<String> {
        @Override
        public String convert(String value) {
            validateQualityGate(value);
            return value;
        }
    }

    /**
     * Evaluates whether CATS should exit with an error code based on the configured quality gates.
     *
     * @param errors   the number of errors reported
     * @param warnings the number of warnings reported
     * @return true if quality gate is violated (should exit with error), false otherwise
     */
    public boolean shouldFailBuild(long errors, long warnings) {
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
    private boolean evaluateFailOn(long errors, long warnings) {
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
    private boolean evaluateQualityGate(long errors, long warnings) {
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("errors", errors);
        metrics.put("warns", warnings);
        metrics.put("warnings", warnings); // Support both "warns" and "warnings"

        List<String> gates = Arrays.stream(qualityGate.split(","))
                .map(String::trim)
                .toList();

        for (String gate : gates) {
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
    private boolean evaluateSingleGate(String gate, Map<String, Long> metrics) {
        // Parse gate: metric<threshold or metric>threshold
        Matcher matcher = QUALITY_GATE_PATTERN.matcher(gate.trim());
        if (!matcher.matches()) {
            throw invalidQualityGate(gate);
        }
        String metric = matcher.group(1).toLowerCase(Locale.ROOT);
        String operator = matcher.group(2);
        long threshold = Long.parseLong(matcher.group(3));
        long actualValue = metrics.get(metric);

        boolean violated = switch (operator) {
            case "<" -> actualValue >= threshold; // Fail if actual >= threshold (want actual < threshold)
            case ">" -> actualValue <= threshold; // Fail if actual <= threshold (want actual > threshold)
            default -> throw new IllegalStateException("Unsupported quality gate operator: " + operator);
        };

        if (violated) {
            logger.debug("Quality gate violated: {} (actual: {}, threshold: {}, operator: {})",
                    gate, actualValue, threshold, operator);
        }

        return violated;
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
