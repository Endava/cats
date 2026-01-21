package com.endava.cats.report;

import com.endava.cats.annotations.DryRun;
import com.endava.cats.util.AnsiUtils;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Listener for tracking execution statistics of CATS tests, including errors, warnings, successes, and skipped tests.
 */
@ApplicationScoped
@DryRun
public class ExecutionStatisticsListener {

    /**
     * Map to track the count of errors per path.
     */
    private final Map<String, Long> errors = new HashMap<>();

    /**
     * Map to track the count of warnings per path.
     */
    private final Map<String, Long> warns = new HashMap<>();

    /**
     * Map to track the count of successful executions per path.
     */
    private final Map<String, Long> success = new HashMap<>();

    /**
     * Map to track the distribution of HTTP response codes.
     */
    private final Map<Integer, Integer> responseCodes = new HashMap<>();

    /**
     * Count of skipped tests.
     */
    @Getter
    private int skipped;

    /**
     * Count of authentication errors.
     */
    @Getter
    private int authErrors;

    /**
     * Count of I/O errors.
     */
    @Getter
    private int ioErrors;

    /**
     * Increases the count of authentication errors.
     */
    public void increaseAuthErrors() {
        this.authErrors++;
    }

    /**
     * Increases the count of I/O errors.
     */
    public void increaseIoErrors() {
        this.ioErrors++;
    }

    /**
     * Increases the count of skipped tests.
     */
    public void increaseSkipped() {
        this.skipped++;
    }

    /**
     * Increases the count of errors for a specific path.
     *
     * @param path The path for which errors are increased.
     */
    public void increaseErrors(String path) {
        this.errors.merge(path, 1L, Long::sum);
    }

    /**
     * Increases the count of warnings for a specific path.
     *
     * @param path The path for which warnings are increased.
     */
    public void increaseWarns(String path) {
        this.warns.merge(path, 1L, Long::sum);
    }

    /**
     * Increases the count of successful executions for a specific path.
     *
     * @param path The path for which successful executions are increased.
     */
    public void increaseSuccess(String path) {
        this.success.merge(path, 1L, Long::sum);
    }

    /**
     * Records an HTTP response code occurrence.
     *
     * @param responseCode The HTTP response code to record.
     */
    public void recordResponseCode(int responseCode) {
        this.responseCodes.merge(responseCode, 1, Integer::sum);
    }

    /**
     * Gets the distribution of HTTP response codes.
     *
     * @return A map of response codes to their occurrence counts.
     */
    public Map<Integer, Integer> getResponseCodeDistribution() {
        return new HashMap<>(this.responseCodes);
    }

    /**
     * Gets the top failing paths sorted by error count in descending order.
     *
     * @param limit The maximum number of paths to return.
     * @return A map of paths to their error counts, limited to the specified number.
     */
    public Map<String, Long> getTopFailingPaths(int limit) {
        return this.errors.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        java.util.LinkedHashMap::new
                ));
    }

    /**
     * Gets the total count of errors across all paths.
     *
     * @return The total count of errors.
     */
    public long getErrors() {
        return this.errors.values().stream().reduce(0L, Long::sum);
    }

    /**
     * Gets the total count of warnings across all paths.
     *
     * @return The total count of warnings.
     */
    public long getWarns() {
        return this.warns.values().stream().reduce(0L, Long::sum);
    }

    /**
     * Gets the total count of successful executions across all paths.
     *
     * @return The total count of successful executions.
     */
    public long getSuccess() {
        return this.success.values().stream().reduce(0L, Long::sum);
    }

    /**
     * Gets the total count of all executions (successes + warnings + errors).
     *
     * @return The total count of all executions.
     */
    public long getAll() {
        return this.getSuccess() + this.getWarns() + this.getErrors();
    }

    /**
     * Checks if there are many authentication errors, considering their ratio to the total number of executions.
     *
     * @return {@code true} if there are many authentication errors, {@code false} otherwise.
     */
    public boolean areManyAuthErrors() {
        return getAll() > 0 && authErrors <= getAll() && authErrors >= this.getAll() / 2;
    }

    /**
     * Checks if there are many I/O errors, considering their ratio to the total number of executions.
     *
     * @return {@code true} if there are many I/O errors, {@code false} otherwise.
     */
    public boolean areManyIoErrors() {
        return getAll() > 0 && ioErrors >= this.getAll() / 2;
    }

    /**
     * Generates a string representation of the execution results for a specific path, including errors, warnings, and successes.
     *
     * @param path The path for which to generate the result string.
     * @return A formatted string representation of the execution results.
     */
    public String resultAsStringPerPath(String path) {
        String errorsString = AnsiUtils.boldRed("E " + errors.getOrDefault(path, 0L));
        String warnsString = AnsiUtils.boldYellow("W " + warns.getOrDefault(path, 0L));
        String successString = AnsiUtils.boldGreen("S " + success.getOrDefault(path, 0L));
        return "%s, %s, %s".formatted(errorsString, warnsString, successString);
    }

    /**
     * Gets the total count of all executions (successes + warnings + errors) for a specific path.
     *
     * @param path The path for which to get the total count of executions.
     * @return The total count of executions for the specified path.
     */
    public long getExecutionsPerPath(String path) {
        return this.errors.getOrDefault(path, 0L) + this.warns.getOrDefault(path, 0L) + this.success.getOrDefault(path, 0L);
    }
}
