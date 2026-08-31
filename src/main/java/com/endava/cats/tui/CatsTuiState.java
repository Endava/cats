package com.endava.cats.tui;

import com.endava.cats.tui.event.CatsExecutionEvent;
import com.endava.cats.tui.model.RunConfigurationSnapshot;
import com.endava.cats.tui.model.RunSummarySnapshot;
import com.endava.cats.tui.model.TestResultSnapshot;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.table.TableState;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Render-thread-owned state for a CATS TUI session.
 */
final class CatsTuiState {
    private final Deque<TestResultSnapshot> results = new ArrayDeque<>();
    private final int maximumRetainedResults;
    private final Map<String, CategoryStatistics> categories = new LinkedHashMap<>();
    private final Map<String, CategoryStatistics> paths = new LinkedHashMap<>();
    private final Map<String, IssueStatistics> issues = new LinkedHashMap<>();
    private final Map<Integer, Long> liveResponseCodes = new LinkedHashMap<>();
    private final TableState tableState = new TableState();
    private final TableState issuesTableState = new TableState();
    private final TableState pathsTableState = new TableState();
    private final TableState slowestTableState = new TableState();
    private List<TestResultSnapshot> filteredResults = List.of();
    private List<TestResultSnapshot> cachedSlowestResults = List.of();
    private Instant startedAt;
    private Instant finishedAt;
    private RunConfigurationSnapshot configuration;
    private RunSummarySnapshot summary;
    private Screen screen = Screen.OVERVIEW;
    private ResultFilter filter = ResultFilter.ALL;
    private String status = "Starting CATS";
    private String currentPath = "Waiting for contract";
    private String currentFuzzer = "Waiting for execution";
    private String failureMessage;
    private int completedPaths;
    private int selectedIndex;
    private int selectedIssueIndex;
    private int selectedPathIndex;
    private int selectedSlowestIndex;
    private int detailOffset;
    private int fuzzerOffset;
    private long success;
    private long warnings;
    private long errors;
    private long skipped;
    private long skippedFromReporting;
    private long observedTests;
    private long discardedResults;
    private long timedResults;
    private long totalResponseTime;
    private long maximumResponseTime;
    private boolean filteredResultsDirty = true;
    private boolean slowestResultsDirty = true;
    private boolean running = true;
    private int terminalColumns = 80;
    private int terminalRows = 24;
    private boolean searchEditing;
    private String searchQuery = "";
    private String drilldownIssue;
    private String drilldownPath;
    private Screen resultsReturnScreen = Screen.OVERVIEW;
    private Screen detailReturnScreen = Screen.RESULTS;

    CatsTuiState() {
        this(10_000);
    }

    CatsTuiState(int maximumRetainedResults) {
        if (maximumRetainedResults < 1) {
            throw new IllegalArgumentException("maximumRetainedResults must be greater than zero");
        }
        this.maximumRetainedResults = maximumRetainedResults;
    }

    void drain(ConcurrentLinkedQueue<CatsExecutionEvent> events) {
        CatsExecutionEvent event;
        while ((event = events.poll()) != null) {
            accept(event);
        }
    }

    void accept(CatsExecutionEvent event) {
        switch (event) {
            case CatsExecutionEvent.SessionStarted started -> {
                startedAt = started.occurredAt();
                status = "Loading configuration";
            }
            case CatsExecutionEvent.ConfigurationLoaded loaded -> {
                configuration = loaded.configuration();
                status = "Fuzzing";
            }
            case CatsExecutionEvent.PathStarted path -> {
                currentPath = singleLine(path.path());
                status = "Fuzzing";
            }
            case CatsExecutionEvent.PathCompleted _ -> completedPaths++;
            case CatsExecutionEvent.FuzzerStarted fuzzer ->
                    currentFuzzer = singleLine(fuzzer.fuzzer()) + " (" + singleLine(fuzzer.httpMethod()) + ")";
            case CatsExecutionEvent.FuzzerCompleted _ -> {
                // The next fuzzer event replaces the current label.
            }
            case CatsExecutionEvent.TestCompleted completed -> record(completed.test());
            case CatsExecutionEvent.SessionCompleted completed -> {
                summary = completed.summary();
                finishedAt = completed.occurredAt();
                running = false;
                status = "Finished";
            }
            case CatsExecutionEvent.SessionCancelled cancelled -> {
                failureMessage = cancelled.message();
                finishedAt = cancelled.occurredAt();
                running = false;
                status = "Cancelled";
            }
            case CatsExecutionEvent.SessionFailed failed -> {
                failureMessage = failed.message();
                finishedAt = failed.occurredAt();
                running = false;
                status = "Failed";
            }
        }
    }

    EventResult handleKey(KeyEvent event) {
        if (searchEditing) {
            return handleSearchInput(event);
        }
        if (screen == Screen.RESULTS && event.isChar('/')) {
            searchEditing = true;
            return EventResult.HANDLED;
        }
        if (event.isChar('1')) {
            screen = Screen.OVERVIEW;
            clampFuzzerOffset();
            return EventResult.HANDLED;
        }
        if (event.isChar('2')) {
            openResults(Screen.OVERVIEW, null, null);
            return EventResult.HANDLED;
        }
        if (event.isChar('3')) {
            screen = Screen.SUMMARY;
            clampFuzzerOffset();
            return EventResult.HANDLED;
        }
        if (event.isChar('4')) {
            screen = Screen.ISSUES;
            ensureIssueSelection();
            return EventResult.HANDLED;
        }
        if (event.isChar('5')) {
            screen = Screen.PATHS;
            ensurePathSelection();
            return EventResult.HANDLED;
        }
        if (event.isChar('6')) {
            screen = Screen.SLOWEST;
            ensureSlowestSelection();
            return EventResult.HANDLED;
        }
        if (event.isCharIgnoreCase('q')) {
            if (running) {
                status = "Cancellation requested";
            }
            return EventResult.UNHANDLED;
        }
        if (screen == Screen.RESULTS) {
            return handleResultsKey(event);
        }
        if (screen == Screen.DETAIL) {
            return handleDetailKey(event);
        }
        if (screen == Screen.ISSUES) {
            return handleIssuesKey(event);
        }
        if (screen == Screen.PATHS) {
            return handlePathsKey(event);
        }
        if (screen == Screen.SLOWEST) {
            return handleSlowestKey(event);
        }
        if (screen == Screen.OVERVIEW || screen == Screen.SUMMARY) {
            return handleFuzzerKey(event);
        }
        return EventResult.UNHANDLED;
    }

    private EventResult handleSearchInput(KeyEvent event) {
        if (event.isCancel()) {
            searchQuery = "";
            searchEditing = false;
            refreshFilteredResults();
            return EventResult.HANDLED;
        }
        if (event.isConfirm()) {
            searchEditing = false;
            return EventResult.HANDLED;
        }
        if (event.isDeleteBackward()) {
            if (!searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.offsetByCodePoints(searchQuery.length(), -1));
                refreshFilteredResults();
            }
            return EventResult.HANDLED;
        }
        if (event.code() == dev.tamboui.tui.event.KeyCode.CHAR && !event.hasCtrl() && !event.hasAlt()
                && !Character.isISOControl(event.codePoint()) && searchQuery.codePointCount(0, searchQuery.length()) < 120) {
            searchQuery += event.string();
            refreshFilteredResults();
            return EventResult.HANDLED;
        }
        return EventResult.HANDLED;
    }

    private EventResult handleIssuesKey(KeyEvent event) {
        List<IssueStatisticsRow> rows = issueStatistics();
        if (event.isUp() || event.isCharIgnoreCase('k')) {
            selectedIssueIndex = Math.max(0, selectedIssueIndex - 1);
            syncSelection(issuesTableState, selectedIssueIndex, rows.size());
            return EventResult.HANDLED;
        }
        if (event.isDown() || event.isCharIgnoreCase('j')) {
            selectedIssueIndex = Math.min(Math.max(0, rows.size() - 1), selectedIssueIndex + 1);
            syncSelection(issuesTableState, selectedIssueIndex, rows.size());
            return EventResult.HANDLED;
        }
        if (event.isPageUp()) {
            selectedIssueIndex = Math.max(0, selectedIssueIndex - aggregatePageSize());
            syncSelection(issuesTableState, selectedIssueIndex, rows.size());
            return EventResult.HANDLED;
        }
        if (event.isPageDown()) {
            selectedIssueIndex = Math.min(Math.max(0, rows.size() - 1), selectedIssueIndex + aggregatePageSize());
            syncSelection(issuesTableState, selectedIssueIndex, rows.size());
            return EventResult.HANDLED;
        }
        if ((event.isConfirm() || event.isSelect()) && !rows.isEmpty()) {
            openResults(Screen.ISSUES, rows.get(selectedIssueIndex).reason(), null);
            return EventResult.HANDLED;
        }
        if (event.isCancel() || event.isLeft()) {
            screen = Screen.OVERVIEW;
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private EventResult handlePathsKey(KeyEvent event) {
        List<PathStatisticsRow> rows = pathStatistics();
        if (event.isUp() || event.isCharIgnoreCase('k')) {
            selectedPathIndex = Math.max(0, selectedPathIndex - 1);
            syncSelection(pathsTableState, selectedPathIndex, rows.size());
            return EventResult.HANDLED;
        }
        if (event.isDown() || event.isCharIgnoreCase('j')) {
            selectedPathIndex = Math.min(Math.max(0, rows.size() - 1), selectedPathIndex + 1);
            syncSelection(pathsTableState, selectedPathIndex, rows.size());
            return EventResult.HANDLED;
        }
        if (event.isPageUp()) {
            selectedPathIndex = Math.max(0, selectedPathIndex - aggregatePageSize());
            syncSelection(pathsTableState, selectedPathIndex, rows.size());
            return EventResult.HANDLED;
        }
        if (event.isPageDown()) {
            selectedPathIndex = Math.min(Math.max(0, rows.size() - 1), selectedPathIndex + aggregatePageSize());
            syncSelection(pathsTableState, selectedPathIndex, rows.size());
            return EventResult.HANDLED;
        }
        if ((event.isConfirm() || event.isSelect()) && !rows.isEmpty()) {
            openResults(Screen.PATHS, null, rows.get(selectedPathIndex).path());
            return EventResult.HANDLED;
        }
        if (event.isCancel() || event.isLeft()) {
            screen = Screen.OVERVIEW;
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private EventResult handleSlowestKey(KeyEvent event) {
        List<TestResultSnapshot> rows = slowestResults();
        if (event.isUp() || event.isCharIgnoreCase('k')) {
            selectedSlowestIndex = Math.max(0, selectedSlowestIndex - 1);
            syncSelection(slowestTableState, selectedSlowestIndex, rows.size());
            return EventResult.HANDLED;
        }
        if (event.isDown() || event.isCharIgnoreCase('j')) {
            selectedSlowestIndex = Math.min(Math.max(0, rows.size() - 1), selectedSlowestIndex + 1);
            syncSelection(slowestTableState, selectedSlowestIndex, rows.size());
            return EventResult.HANDLED;
        }
        if (event.isPageUp()) {
            selectedSlowestIndex = Math.max(0, selectedSlowestIndex - aggregatePageSize());
            syncSelection(slowestTableState, selectedSlowestIndex, rows.size());
            return EventResult.HANDLED;
        }
        if (event.isPageDown()) {
            selectedSlowestIndex = Math.min(Math.max(0, rows.size() - 1), selectedSlowestIndex + aggregatePageSize());
            syncSelection(slowestTableState, selectedSlowestIndex, rows.size());
            return EventResult.HANDLED;
        }
        if ((event.isConfirm() || event.isSelect()) && !rows.isEmpty()) {
            TestResultSnapshot selected = rows.get(selectedSlowestIndex);
            openResults(Screen.SLOWEST, null, null);
            selectedIndex = filteredResults().indexOf(selected);
            ensureSelection();
            detailOffset = 0;
            detailReturnScreen = Screen.SLOWEST;
            screen = Screen.DETAIL;
            return EventResult.HANDLED;
        }
        if (event.isCancel() || event.isLeft()) {
            screen = Screen.OVERVIEW;
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private EventResult handleFuzzerKey(KeyEvent event) {
        if (screen == Screen.SUMMARY && (event.isCancel() || event.isLeft())) {
            screen = Screen.OVERVIEW;
            return EventResult.HANDLED;
        }
        int pageSize = fuzzerPageSize(screen);
        int maximumOffset = Math.max(0, fuzzerCount() - pageSize);
        if (event.isUp() || event.isCharIgnoreCase('k')) {
            fuzzerOffset = Math.max(0, fuzzerOffset - 1);
            return EventResult.HANDLED;
        }
        if (event.isDown() || event.isCharIgnoreCase('j')) {
            fuzzerOffset = Math.min(maximumOffset, fuzzerOffset + 1);
            return EventResult.HANDLED;
        }
        if (event.isPageUp()) {
            fuzzerOffset = Math.max(0, fuzzerOffset - pageSize);
            return EventResult.HANDLED;
        }
        if (event.isPageDown()) {
            fuzzerOffset = Math.min(maximumOffset, fuzzerOffset + pageSize);
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private EventResult handleResultsKey(KeyEvent event) {
        if (event.isUp() || event.isCharIgnoreCase('k')) {
            selectPrevious();
            return EventResult.HANDLED;
        }
        if (event.isDown() || event.isCharIgnoreCase('j')) {
            selectNext();
            return EventResult.HANDLED;
        }
        if (event.isConfirm() || event.isSelect()) {
            if (selectedTest() != null) {
                detailOffset = 0;
                detailReturnScreen = Screen.RESULTS;
                screen = Screen.DETAIL;
            }
            return EventResult.HANDLED;
        }
        if (event.isCharIgnoreCase('a')) {
            setFilter(ResultFilter.ALL);
            return EventResult.HANDLED;
        }
        if (event.isCharIgnoreCase('e')) {
            setFilter(ResultFilter.ERROR);
            return EventResult.HANDLED;
        }
        if (event.isCharIgnoreCase('w')) {
            setFilter(ResultFilter.WARNING);
            return EventResult.HANDLED;
        }
        if (event.isCharIgnoreCase('s')) {
            setFilter(ResultFilter.SUCCESS);
            return EventResult.HANDLED;
        }
        if (event.isCharIgnoreCase('i')) {
            setFilter(ResultFilter.SKIPPED);
            return EventResult.HANDLED;
        }
        if (event.isCancel() || event.isLeft()) {
            if (!searchQuery.isEmpty()) {
                searchQuery = "";
                refreshFilteredResults();
            } else {
                screen = resultsReturnScreen;
            }
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private EventResult handleDetailKey(KeyEvent event) {
        if (event.isCancel() || event.isLeft()) {
            screen = detailReturnScreen;
            return EventResult.HANDLED;
        }
        if (event.isUp() || event.isCharIgnoreCase('k')) {
            detailOffset = Math.max(0, detailOffset - 1);
            return EventResult.HANDLED;
        }
        if (event.isDown() || event.isCharIgnoreCase('j')) {
            detailOffset = Math.min(maxDetailOffset(), detailOffset + 1);
            return EventResult.HANDLED;
        }
        if (event.isPageUp()) {
            detailOffset = Math.max(0, detailOffset - detailPageSize());
            return EventResult.HANDLED;
        }
        if (event.isPageDown()) {
            detailOffset = Math.min(maxDetailOffset(), detailOffset + detailPageSize());
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void record(TestResultSnapshot test) {
        TestResultSnapshot previousSelection = selectedTest();
        observedTests++;
        if (results.size() == maximumRetainedResults) {
            results.removeFirst();
            discardedResults++;
        }
        results.addLast(test);
        filteredResultsDirty = true;
        slowestResultsDirty = true;
        ResultFilter outcome = ResultFilter.from(test.result());
        switch (outcome) {
            case ERROR -> errors++;
            case WARNING -> warnings++;
            case SUCCESS -> success++;
            case SKIPPED -> {
                skipped++;
                if (isSkippedFromReporting(test.result())) {
                    skippedFromReporting++;
                }
            }
            case ALL -> {
                // Unknown results are still retained and counted in the total.
            }
        }

        long responseTime = test.response().responseTimeInMs();
        if (outcome != ResultFilter.SKIPPED) {
            totalResponseTime += responseTime;
            maximumResponseTime = Math.max(maximumResponseTime, responseTime);
            timedResults++;
            liveResponseCodes.merge(test.response().responseCode(), 1L, Long::sum);
        }
        categories.computeIfAbsent(display(test.fuzzer()), _ -> new CategoryStatistics()).record(outcome, responseTime);
        String contractPath = display(test.contractPath());
        paths.computeIfAbsent(contractPath, _ -> new CategoryStatistics()).record(outcome, responseTime);
        if ((outcome == ResultFilter.ERROR || outcome == ResultFilter.WARNING)
                && test.resultReason() != null && !test.resultReason().isBlank()) {
            issues.computeIfAbsent(singleLine(test.resultReason()), _ -> new IssueStatistics())
                    .record(outcome, contractPath, test.response().responseCode());
        }
        restoreSelection(previousSelection);
    }

    private static boolean isSkippedFromReporting(String result) {
        return "skip_reporting".equalsIgnoreCase(result);
    }

    private void restoreSelection(TestResultSnapshot previousSelection) {
        List<TestResultSnapshot> visible = filteredResults();
        if (previousSelection != null) {
            int previousIndex = visible.indexOf(previousSelection);
            if (previousIndex >= 0) {
                selectedIndex = previousIndex;
            }
        }
        ensureSelection();
        if (screen == Screen.DETAIL) {
            detailOffset = Math.min(detailOffset, maxDetailOffset());
        }
    }

    private void setFilter(ResultFilter newFilter) {
        filter = newFilter;
        refreshFilteredResults();
    }

    private void openResults(Screen returnScreen, String issue, String path) {
        resultsReturnScreen = returnScreen;
        drilldownIssue = issue;
        drilldownPath = path;
        if (issue != null || path != null || returnScreen == Screen.SLOWEST) {
            // A filter selected on the general test screen must not hide a drill-down target.
            filter = ResultFilter.ALL;
        }
        searchQuery = "";
        searchEditing = false;
        screen = Screen.RESULTS;
        refreshFilteredResults();
    }

    private void refreshFilteredResults() {
        selectedIndex = 0;
        filteredResultsDirty = true;
        ensureSelection();
    }

    private void selectPrevious() {
        if (selectedIndex > 0) {
            selectedIndex--;
        }
        syncTableSelection();
    }

    private void selectNext() {
        if (selectedIndex + 1 < filteredResults().size()) {
            selectedIndex++;
        }
        syncTableSelection();
    }

    private void ensureSelection() {
        int size = filteredResults().size();
        if (size == 0) {
            selectedIndex = 0;
            tableState.clearSelection();
        } else {
            selectedIndex = Math.min(selectedIndex, size - 1);
            syncTableSelection();
        }
    }

    private void syncTableSelection() {
        if (!filteredResults().isEmpty()) {
            tableState.select(selectedIndex);
        }
    }

    private static void syncSelection(TableState state, int index, int size) {
        if (size == 0) {
            state.clearSelection();
        } else {
            state.select(Math.min(index, size - 1));
        }
    }

    private void ensureIssueSelection() {
        int size = issueStatistics().size();
        selectedIssueIndex = size == 0 ? 0 : Math.min(selectedIssueIndex, size - 1);
        syncSelection(issuesTableState, selectedIssueIndex, size);
    }

    private void ensurePathSelection() {
        int size = pathStatistics().size();
        selectedPathIndex = size == 0 ? 0 : Math.min(selectedPathIndex, size - 1);
        syncSelection(pathsTableState, selectedPathIndex, size);
    }

    private void ensureSlowestSelection() {
        int size = slowestResults().size();
        selectedSlowestIndex = size == 0 ? 0 : Math.min(selectedSlowestIndex, size - 1);
        syncSelection(slowestTableState, selectedSlowestIndex, size);
    }

    private int maxDetailOffset() {
        return Math.max(0, detailLines().size() - detailPageSize());
    }

    void updateViewport(int columns, int rows) {
        terminalColumns = Math.max(1, columns);
        terminalRows = Math.max(1, rows);
        detailOffset = Math.min(detailOffset, maxDetailOffset());
        if (screen == Screen.OVERVIEW || screen == Screen.SUMMARY) {
            clampFuzzerOffset();
        }
    }

    private void clampFuzzerOffset() {
        fuzzerOffset = Math.min(fuzzerOffset, Math.max(0, fuzzerCount() - fuzzerPageSize(screen)));
    }

    private int detailPageSize() {
        // The detail screen uses the full viewport except for one spacer, the two-row footer and panel borders.
        return Math.max(1, terminalRows - 5);
    }

    private int aggregatePageSize() {
        // Root framing, the two-row footer and the table border/header consume ten rows.
        return Math.max(1, terminalRows - 10);
    }

    double pathProgress() {
        int totalPaths = configuration == null ? 0 : configuration.configuredPaths();
        return totalPaths == 0 ? 0 : Math.min(1.0, (double) completedPaths / totalPaths);
    }

    String pathProgressLabel() {
        int totalPaths = configuration == null ? 0 : configuration.configuredPaths();
        return completedPaths + " / " + (totalPaths == 0 ? "?" : totalPaths) + " paths";
    }

    String elapsed() {
        if (startedAt == null) {
            return "0s";
        }
        Duration duration = Duration.between(startedAt, finishedAt == null ? Instant.now() : finishedAt);
        long seconds = Math.max(0, duration.toSeconds());
        return "%02d:%02d:%02d".formatted(seconds / 3600, seconds % 3600 / 60, seconds % 60);
    }

    String categorySummary() {
        return categorySummary(6);
    }

    String categorySummary(int limit) {
        if (categories.isEmpty()) {
            return "No completed tests yet";
        }
        return categories.entrySet().stream()
                .sorted(Map.Entry.<String, CategoryStatistics>comparingByValue(
                        Comparator.comparingLong(CategoryStatistics::errors)
                                .thenComparingLong(CategoryStatistics::total)).reversed())
                .limit(limit)
                .map(entry -> "%s: %d tests, %d Errors, %d Skipped, Average Response Time %dms".formatted(entry.getKey(),
                        entry.getValue().total(), entry.getValue().errors(), entry.getValue().skipped(),
                        entry.getValue().averageResponseTime()))
                .collect(Collectors.joining("\n"));
    }

    List<FuzzerStatisticsRow> fuzzerStatistics(int limit) {
        return fuzzerStatistics(0, limit);
    }

    List<FuzzerStatisticsRow> fuzzerStatistics(int offset, int limit) {
        return categories.entrySet().stream()
                .sorted(Map.Entry.<String, CategoryStatistics>comparingByValue(
                        Comparator.comparingLong(CategoryStatistics::errors)
                                .thenComparingLong(CategoryStatistics::total)).reversed())
                .skip(Math.max(0, offset))
                .limit(limit)
                .map(entry -> new FuzzerStatisticsRow(entry.getKey(), entry.getValue().total(),
                        entry.getValue().success(), entry.getValue().warnings(), entry.getValue().errors(),
                        entry.getValue().skipped(), entry.getValue().averageResponseTime()))
                .toList();
    }

    int fuzzerCount() {
        return categories.size();
    }

    int fuzzerOffset() {
        return fuzzerOffset;
    }

    int fuzzerPageSize(Screen targetScreen) {
        // Root header/footer consume seven rows. The overview has thirteen additional fixed rows;
        // the summary has twelve. A table needs another three rows for its borders and header.
        return Math.max(1, terminalRows - (targetScreen == Screen.OVERVIEW ? 23 : 22));
    }

    List<IssueStatisticsRow> issueStatistics() {
        return issues.entrySet().stream()
                .map(entry -> new IssueStatisticsRow(entry.getKey(), entry.getValue().total,
                        entry.getValue().errors, entry.getValue().warnings, entry.getValue().paths.size(),
                        entry.getValue().responseCodes.isEmpty() ? "n/a"
                                : entry.getValue().responseCodes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                                        .map(code -> code.getKey() + ":" + code.getValue())
                                        .collect(Collectors.joining(", "))))
                .sorted(Comparator.comparingLong(IssueStatisticsRow::total).reversed()
                        .thenComparing(IssueStatisticsRow::reason))
                .toList();
    }

    List<PathStatisticsRow> pathStatistics() {
        return paths.entrySet().stream()
                .map(entry -> new PathStatisticsRow(entry.getKey(), entry.getValue().total(),
                        entry.getValue().success(), entry.getValue().warnings(), entry.getValue().errors(),
                        entry.getValue().skipped(), entry.getValue().averageResponseTime()))
                .sorted(Comparator.comparingLong(PathStatisticsRow::errors).reversed()
                        .thenComparing(Comparator.comparingLong(PathStatisticsRow::total).reversed())
                        .thenComparing(PathStatisticsRow::path))
                .toList();
    }

    List<TestResultSnapshot> slowestResults() {
        if (slowestResultsDirty) {
            cachedSlowestResults = results.stream()
                    .filter(test -> ResultFilter.from(test.result()) != ResultFilter.SKIPPED)
                    .sorted(Comparator.comparingLong((TestResultSnapshot test) -> test.response().responseTimeInMs())
                            .reversed().thenComparing(TestResultSnapshot::id, Comparator.nullsLast(String::compareTo)))
                    .toList();
            slowestResultsDirty = false;
        }
        return cachedSlowestResults;
    }

    String resultsContext() {
        if (drilldownIssue != null) {
            return "Result Reason: " + drilldownIssue;
        }
        if (drilldownPath != null) {
            return "Path: " + drilldownPath;
        }
        return "All Tests";
    }

    String responseCodeSummary() {
        Map<Integer, ? extends Number> source = summary == null ? liveResponseCodes : summary.responseCodeDistribution();
        if (source.isEmpty()) {
            return "-";
        }
        return source.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("    "));
    }

    long averageResponseTime() {
        return timedResults == 0 ? 0 : totalResponseTime / timedResults;
    }

    List<TestResultSnapshot> filteredResults() {
        if (filteredResultsDirty) {
            filteredResults = results.stream()
                    .filter(filter::matches)
                    .filter(this::matchesDrilldown)
                    .filter(this::matchesSearch)
                    .toList();
            filteredResultsDirty = false;
        }
        return filteredResults;
    }

    private boolean matchesDrilldown(TestResultSnapshot test) {
        return (drilldownIssue == null || drilldownIssue.equals(singleLine(test.resultReason())))
                && (drilldownPath == null || drilldownPath.equals(display(test.contractPath())));
    }

    private boolean matchesSearch(TestResultSnapshot test) {
        if (searchQuery.isBlank()) {
            return true;
        }
        String needle = searchQuery.toLowerCase(Locale.ROOT);
        return java.util.stream.Stream.of(test.id(), test.fuzzer(), test.contractPath(), test.path(), test.scenario(),
                        test.resultReason(), test.result(), test.request().httpMethod(),
                        String.valueOf(test.response().responseCode()))
                .map(CatsTuiState::safe).map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(needle));
    }

    TestResultSnapshot selectedTest() {
        List<TestResultSnapshot> visible = filteredResults();
        return visible.isEmpty() ? null : visible.get(Math.min(selectedIndex, visible.size() - 1));
    }

    List<String> visibleDetailLines() {
        List<String> lines = detailLines();
        int from = Math.min(detailOffset, lines.size());
        int to = Math.min(lines.size(), from + detailPageSize());
        return lines.subList(from, to);
    }

    private List<String> detailLines() {
        TestResultSnapshot test = selectedTest();
        if (test == null) {
            return List.of("No test selected");
        }
        List<String> lines = new ArrayList<>();
        add(lines, "Scenario", test.scenario());
        add(lines, "Expected Result", test.expectedResult());
        add(lines, "Result", test.result());
        add(lines, "Result Reason", test.resultReason());
        add(lines, "Result Details", test.resultDetails());
        if (test.resultIgnoreDetails() != null && !test.resultIgnoreDetails().isBlank()) {
            add(lines, "Warning", test.resultIgnoreDetails());
        }
        add(lines, "Test Trace Id", test.traceId());
        add(lines, "Contract Path", test.contractPath());
        add(lines, "Full Request Path", test.fullRequestPath());
        add(lines, "Server", test.server());
        lines.add("Valid JSON: " + test.validJson());
        lines.add("");
        lines.add("REQUEST DETAILS");
        add(lines, "Http Method", test.request().httpMethod());
        add(lines, "URL", test.request().url());
        add(lines, "Timestamp", test.request().timestamp());
        lines.add("Headers:");
        test.request().headers().forEach(header -> appendWrappedLine(lines,
                "  " + safe(header.name()) + ": " + safe(header.value())));
        lines.add("Payload:");
        appendMultiline(lines, test.request().payload());
        lines.add("");
        lines.add("RESPONSE");
        lines.add("HTTP Response Code: %d    Response Time: %dms".formatted(
                test.response().responseCode(), test.response().responseTimeInMs()));
        lines.add("Content Type: %s    Content Length: %d bytes".formatted(
                safe(test.response().contentType()), test.response().contentLengthInBytes()));
        lines.add("Number of Words: %d    Number of Lines: %d".formatted(
                test.response().numberOfWords(), test.response().numberOfLines()));
        lines.add("Headers:");
        test.response().headers().forEach(header -> appendWrappedLine(lines,
                "  " + safe(header.name()) + ": " + safe(header.value())));
        lines.add("Body:");
        appendMultiline(lines, test.response().body());
        lines.add("");
        add(lines, "CATS Replay", test.replayCommand());
        return lines;
    }

    private void add(List<String> lines, String label, String value) {
        appendWrappedLine(lines, label + ": " + safe(value));
    }

    private void appendMultiline(List<String> lines, String value) {
        String safeValue = safe(value);
        if (safeValue.isBlank()) {
            lines.add("  <empty>");
            return;
        }
        safeValue.lines().forEach(line -> appendWrappedLine(lines, "  " + line));
    }

    private void appendWrappedLine(List<String> lines, String value) {
        if (value.isEmpty()) {
            lines.add("");
            return;
        }
        int lineWidth = Math.max(20, terminalColumns - 4);
        for (int offset = 0; offset < value.length(); offset += lineWidth) {
            int end = Math.min(value.length(), offset + lineWidth);
            if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) {
                end--;
            }
            lines.add(value.substring(offset, end));
            offset = end - lineWidth;
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || codePoint == '\t' || !Character.isISOControl(codePoint)) {
                safe.appendCodePoint(codePoint);
            } else {
                safe.append('�');
            }
        });
        return safe.toString();
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "Unknown" : singleLine(value);
    }

    static String singleLine(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)) {
                sanitized.append(' ');
            } else {
                sanitized.appendCodePoint(codePoint);
            }
        });
        return sanitized.toString();
    }

    RunConfigurationSnapshot configuration() {
        return configuration;
    }

    RunSummarySnapshot summary() {
        return summary;
    }

    Screen screen() {
        return screen;
    }

    ResultFilter filter() {
        return filter;
    }

    TableState tableState() {
        return tableState;
    }

    TableState issuesTableState() {
        return issuesTableState;
    }

    TableState pathsTableState() {
        return pathsTableState;
    }

    TableState slowestTableState() {
        return slowestTableState;
    }

    String searchQuery() {
        return searchQuery;
    }

    boolean searchEditing() {
        return searchEditing;
    }

    int selectedIndex() {
        return selectedIndex;
    }

    int detailOffset() {
        return detailOffset;
    }

    int detailLineCount() {
        return detailLines().size();
    }

    String status() {
        return status;
    }

    String currentPath() {
        return currentPath;
    }

    String currentFuzzer() {
        return currentFuzzer;
    }

    String failureMessage() {
        return failureMessage;
    }

    long completedTests() {
        return observedTests;
    }

    long success() {
        return summary == null ? success : summary.success();
    }

    long warnings() {
        return summary == null ? warnings : summary.warnings();
    }

    long errors() {
        return summary == null ? errors : summary.errors();
    }

    long skipped() {
        return summary == null ? skipped : summary.skipped() + summary.skippedFromReporting();
    }

    long totalRequests() {
        return summary == null ? success + warnings + errors + skippedFromReporting : summary.totalRequests();
    }

    long reportedResults() {
        return summary == null ? success + warnings + errors : summary.reportedResults();
    }

    long skippedFromReporting() {
        return summary == null ? skippedFromReporting : summary.skippedFromReporting();
    }

    int authenticationErrors() {
        return summary == null ? 0 : summary.authenticationErrors();
    }

    int ioErrors() {
        return summary == null ? 0 : summary.ioErrors();
    }

    boolean qualityGatePassed() {
        return summary != null && summary.qualityGatePassed();
    }

    String qualityGateDescription() {
        return summary == null ? "Pending until completion" : display(summary.qualityGateDescription());
    }

    String topFailingPaths() {
        if (summary == null || summary.topFailingPaths().isEmpty()) {
            return "No failing paths yet";
        }
        return summary.topFailingPaths().entrySet().stream()
                .limit(5)
                .map(entry -> singleLine(entry.getKey()) + " (" + entry.getValue() + ")")
                .collect(Collectors.joining(", "));
    }

    long retainedResults() {
        return results.size();
    }

    long discardedResults() {
        return discardedResults;
    }

    long maximumResponseTime() {
        return maximumResponseTime;
    }

    boolean running() {
        return running;
    }

    enum Screen {
        OVERVIEW,
        SUMMARY,
        RESULTS,
        DETAIL,
        ISSUES,
        PATHS,
        SLOWEST
    }

    enum ResultFilter {
        ALL,
        ERROR,
        WARNING,
        SUCCESS,
        SKIPPED;

        boolean matches(TestResultSnapshot test) {
            return this == ALL || this == from(test.result());
        }

        static ResultFilter from(String result) {
            String normalized = String.valueOf(result).toLowerCase(Locale.ROOT).trim();
            if (normalized.equals("error")) {
                return ERROR;
            }
            if (normalized.equals("warn") || normalized.equals("warning")) {
                return WARNING;
            }
            if (normalized.equals("success")) {
                return SUCCESS;
            }
            if (normalized.equals("skipped") || normalized.equals("skip_reporting")) {
                return SKIPPED;
            }
            return ALL;
        }
    }

    record FuzzerStatisticsRow(String fuzzer, long total, long success, long warnings, long errors,
                               long skipped, long averageResponseTime) {
    }

    record IssueStatisticsRow(String reason, long total, long errors, long warnings, int pathCount,
                              String responseCodes) {
    }

    record PathStatisticsRow(String path, long total, long success, long warnings, long errors,
                             long skipped, long averageResponseTime) {
    }

    private static final class IssueStatistics {
        private long total;
        private long errors;
        private long warnings;
        private final Set<String> paths = new LinkedHashSet<>();
        private final Map<Integer, Long> responseCodes = new LinkedHashMap<>();

        void record(ResultFilter outcome, String path, int responseCode) {
            total++;
            if (outcome == ResultFilter.ERROR) {
                errors++;
            }
            if (outcome == ResultFilter.WARNING) {
                warnings++;
            }
            paths.add(path);
            if (responseCode > 0) {
                responseCodes.merge(responseCode, 1L, Long::sum);
            }
        }
    }

    private static final class CategoryStatistics {
        private long total;
        private long success;
        private long warnings;
        private long errors;
        private long skipped;
        private long timed;
        private long totalResponseTime;

        void record(ResultFilter outcome, long responseTime) {
            total++;
            if (outcome == ResultFilter.ERROR) {
                errors++;
            }
            if (outcome == ResultFilter.WARNING) {
                warnings++;
            }
            if (outcome == ResultFilter.SUCCESS) {
                success++;
            }
            if (outcome == ResultFilter.SKIPPED) {
                skipped++;
            } else {
                totalResponseTime += responseTime;
                timed++;
            }
        }

        long total() {
            return total;
        }

        long errors() {
            return errors;
        }

        long success() {
            return success;
        }

        long warnings() {
            return warnings;
        }

        long skipped() {
            return skipped;
        }

        long averageResponseTime() {
            return timed == 0 ? 0 : totalResponseTime / timed;
        }
    }
}
