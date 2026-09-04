package com.endava.cats.tui;

import com.endava.cats.tui.model.TestResultSnapshot;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.RichTextElement;
import dev.tamboui.toolkit.elements.TableElement;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;

import java.util.List;
import java.util.function.UnaryOperator;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.fill;
import static dev.tamboui.toolkit.Toolkit.length;
import static dev.tamboui.toolkit.Toolkit.lineGauge;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.richText;
import static dev.tamboui.toolkit.Toolkit.table;
import static dev.tamboui.toolkit.Toolkit.text;

/**
 * Builds CATS terminal screens from render-thread state.
 */
final class CatsTuiView {
    private static final Style LABEL_STYLE = Style.EMPTY.fg(Color.LIGHT_BLUE).bold();
    private static final Style VALUE_STYLE = Style.EMPTY.fg(Color.BRIGHT_WHITE);
    private static final Style SUCCESS_STYLE = Style.EMPTY.fg(Color.LIGHT_GREEN).bold();
    private static final Style WARNING_STYLE = Style.EMPTY.fg(Color.LIGHT_YELLOW).bold();
    private static final Style ERROR_STYLE = Style.EMPTY.fg(Color.LIGHT_RED).bold();
    private static final Style SKIPPED_STYLE = Style.EMPTY.fg(Color.LIGHT_MAGENTA).bold();
    private static final Style INFO_STYLE = Style.EMPTY.fg(Color.LIGHT_CYAN).bold();

    private CatsTuiView() {
    }

    static Element render(CatsTuiState state) {
        if (state.screen() == CatsTuiState.Screen.DETAIL) {
            return column(detail(state), footer(state))
                    .spacing(1).focusable().id("cats-root").onKeyEvent(state::handleKey);
        }
        return column(
                header(state),
                switch (state.screen()) {
                    case OVERVIEW -> overview(state);
                    case SUMMARY -> summary(state);
                    case RESULTS -> results(state);
                    case DETAIL -> detail(state);
                    case ISSUES -> issues(state);
                    case PATHS -> paths(state);
                    case SLOWEST -> slowest(state);
                },
                footer(state)
        ).spacing(1).focusable().id("cats-root").onKeyEvent(state::handleKey);
    }

    private static Element header(CatsTuiState state) {
        String heading = "CATS REST API Fuzzer  •  %s  •  %s  •  %d tests run".formatted(
                state.status(), state.elapsed(), state.reportedResults());
        return panel(text(heading).bold().cyan()).rounded().borderColor(Color.CYAN).length(3);
    }

    private static Element overview(CatsTuiState state) {
        Element finalPanel = state.failureMessage() == null
                ? fuzzerResults(state, state.fuzzerOffset(), state.fuzzerPageSize(CatsTuiState.Screen.OVERVIEW))
                : terminalMessage(state);

        return column(
                panel("Paths", lineGauge(state.pathProgress())
                        .label(state.pathProgressLabel() + "  ").filledColor(Color.LIGHT_CYAN)
                        .unfilledColor(Color.DARK_GRAY).thick())
                        .rounded().borderColor(Color.CYAN).length(3),
                panel("Run configuration", executionSummary(state)).rounded().borderColor(Color.BLUE).length(8),
                finalPanel
        ).spacing(1).fill();
    }

    private static Element summary(CatsTuiState state) {
        return column(
                panel("Execution summary", comprehensiveSummary(state))
                        .rounded().borderColor(Color.CYAN).length(11),
                fuzzerResults(state, state.fuzzerOffset(), state.fuzzerPageSize(CatsTuiState.Screen.SUMMARY))
        ).spacing(1).fill();
    }

    private static Element results(CatsTuiState state) {
        List<TestResultSnapshot> tests = state.filteredResults();
        String search = state.searchQuery().isEmpty() ? "" : " • /" + state.searchQuery()
                + (state.searchEditing() ? "█" : "");
        TableElement resultTable = table()
                .header("ID", "Fuzzer", "Scenario", "Result", "Result Reason")
                .widths(length(9), fill(), fill(2), length(9), fill())
                .state(state.tableState())
                .highlightColor(Color.CYAN)
                .highlightSymbol("› ")
                .title("Execution Details • " + filterLabel(state.filter()) + " • " + state.resultsContext() + search + " • "
                        + tests.size() + " retained matches • " + state.discardedResults() + " older discarded")
                .rounded()
                .borderColor(Color.CYAN)
                .fill();
        tests.forEach(test -> resultTable.row(Row.from(
                cell(display(test.id()), INFO_STYLE),
                cell(display(test.fuzzer()), LABEL_STYLE),
                cell("%s %s • %s".formatted(display(test.request().httpMethod()), display(test.path()),
                        display(test.scenario())), VALUE_STYLE),
                cell(display(test.result()), resultStyle(test.result())),
                cell(display(test.resultReason()), VALUE_STYLE))));

        if (tests.isEmpty()) {
            String message = state.searchEditing()
                    ? "Search: /" + state.searchQuery() + "█"
                    : "No retained tests match the active filters; press Esc to clear search or return";
            return panel("Execution Details • " + filterLabel(state.filter()) + " • " + state.resultsContext(), text(message).yellow())
                    .rounded().borderColor(Color.CYAN).fill();
        }
        return resultTable;
    }

    private static Element detail(CatsTuiState state) {
        TestResultSnapshot test = state.selectedTest();
        if (test == null) {
            return panel("Execution Details", text("No test selected")).rounded().fill();
        }
        String title = "%s • %s • %s %s • HTTP %d • %dms".formatted(test.id(), test.result(),
                test.request().httpMethod(), test.path(), test.response().responseCode(),
                test.response().responseTimeInMs());
        List<String> visibleLines = state.visibleDetailLines();
        String page = "Lines %d-%d of %d".formatted(
                Math.min(state.detailOffset() + 1, state.detailLineCount()),
                Math.min(state.detailOffset() + visibleLines.size(), state.detailLineCount()),
                state.detailLineCount());
        return panel(title, detailLines(visibleLines, test.result())).rounded()
                .borderColor(resultColor(test.result())).bottomTitle(page).fill();
    }

    private static Element fuzzerResults(CatsTuiState state, int offset, int limit) {
        List<CatsTuiState.FuzzerStatisticsRow> rows = state.fuzzerStatistics(offset, limit);
        if (rows.isEmpty()) {
            return panel("Fuzzers run", text("No tests run yet")).rounded().fill();
        }
        int first = Math.min(offset + 1, state.fuzzerCount());
        int last = Math.min(offset + rows.size(), state.fuzzerCount());
        TableElement resultTable = table()
                .header("Fuzzer", "Tests", "Success", "Warnings", "Errors", "Skipped", "Average Response Time")
                .widths(fill(), length(7), length(8), length(9), length(7), length(8), length(21))
                .title("Fuzzers run • %d-%d of %d".formatted(first, last, state.fuzzerCount()))
                .rounded()
                .borderColor(Color.BLUE)
                .fill();
        rows.forEach(row -> resultTable.row(Row.from(
                cell(row.fuzzer(), LABEL_STYLE),
                cell(String.valueOf(row.total()), INFO_STYLE),
                cell(String.valueOf(row.success()), SUCCESS_STYLE),
                cell(String.valueOf(row.warnings()), WARNING_STYLE),
                cell(String.valueOf(row.errors()), ERROR_STYLE),
                cell(String.valueOf(row.skipped()), SKIPPED_STYLE),
                cell(row.averageResponseTime() + "ms", VALUE_STYLE))));
        return resultTable;
    }

    private static Element issues(CatsTuiState state) {
        List<CatsTuiState.IssueStatisticsRow> rows = state.issueStatistics();
        if (rows.isEmpty()) {
            return panel("Execution Details by Result Reason",
                    text("No Errors or Warnings with a Result Reason yet").green())
                    .rounded().borderColor(Color.GREEN).fill();
        }
        TableElement resultTable = table()
                .header("Tests", "Errors", "Warnings", "Paths", "HTTP Response Codes", "Result Reason")
                .widths(length(7), length(8), length(9), length(7), length(20), fill())
                .state(state.issuesTableState()).highlightColor(Color.CYAN).highlightSymbol("› ")
                .title("Execution Details by Result Reason • " + countLabel(rows.size(), "reason")
                        + " • Enter shows retained tests")
                .rounded().borderColor(Color.RED).fill();
        rows.forEach(row -> resultTable.row(Row.from(
                cell(String.valueOf(row.total()), INFO_STYLE),
                cell(String.valueOf(row.errors()), ERROR_STYLE),
                cell(String.valueOf(row.warnings()), WARNING_STYLE),
                cell(String.valueOf(row.pathCount()), LABEL_STYLE),
                cell(display(row.responseCodes()), VALUE_STYLE),
                cell(display(row.reason()), VALUE_STYLE))));
        return resultTable;
    }

    private static Element paths(CatsTuiState state) {
        List<CatsTuiState.PathStatisticsRow> rows = state.pathStatistics();
        if (rows.isEmpty()) {
            return panel("Paths included", text("No tests run yet")).rounded().fill();
        }
        TableElement resultTable = table()
                .header("Path", "Tests", "Success", "Warnings", "Errors", "Skipped", "Average Response Time")
                .widths(fill(), length(7), length(8), length(9), length(7), length(8), length(21))
                .state(state.pathsTableState()).highlightColor(Color.CYAN).highlightSymbol("› ")
                .title("Paths included • " + countLabel(rows.size(), "path") + " • Enter shows retained tests")
                .rounded().borderColor(Color.BLUE).fill();
        rows.forEach(row -> resultTable.row(Row.from(
                cell(row.path(), LABEL_STYLE), cell(String.valueOf(row.total()), INFO_STYLE),
                cell(String.valueOf(row.success()), SUCCESS_STYLE),
                cell(String.valueOf(row.warnings()), WARNING_STYLE),
                cell(String.valueOf(row.errors()), ERROR_STYLE),
                cell(String.valueOf(row.skipped()), SKIPPED_STYLE),
                cell(row.averageResponseTime() + "ms", VALUE_STYLE))));
        return resultTable;
    }

    private static Element slowest(CatsTuiState state) {
        List<TestResultSnapshot> rows = state.slowestResults();
        if (rows.isEmpty()) {
            return panel("Executed tests sorted by response time", text("No response times available yet")).rounded().fill();
        }
        TableElement resultTable = table()
                .header("ID", "Response Time", "Result", "HTTP Response Code", "Fuzzer", "Path")
                .widths(length(9), length(14), length(9), length(20), fill(), fill(2))
                .state(state.slowestTableState()).highlightColor(Color.CYAN).highlightSymbol("› ")
                .title("Executed tests sorted by response time • " + countLabel(rows.size(), "test")
                        + " • Enter opens details")
                .rounded().borderColor(Color.MAGENTA).fill();
        rows.forEach(test -> resultTable.row(Row.from(
                cell(display(test.id()), INFO_STYLE),
                cell(test.response().responseTimeInMs() + "ms", WARNING_STYLE),
                cell(display(test.result()), resultStyle(test.result())),
                cell(String.valueOf(test.response().responseCode()), responseCodeStyle(test.response().responseCode())),
                cell(display(test.fuzzer()), LABEL_STYLE), cell(display(test.contractPath()), VALUE_STYLE))));
        return resultTable;
    }

    private static Element footer(CatsTuiState state) {
        String exitControl = state.running() ? "q cancel" : "q quit";
        String navigation = "1 overview  2 tests  3 summary  4 reasons  5 paths  6 timings  " + exitControl;
        String controls = switch (state.screen()) {
            case OVERVIEW, SUMMARY -> "j/k scroll fuzzers  PgUp/PgDn page";
            case RESULTS -> state.searchEditing()
                    ? "Search mode: type query  Enter apply  Backspace edit  Esc clear"
                    : "j/k select  Enter details  / search  a/e/w/s/i filter  Esc back";
            case DETAIL -> "j/k scroll  PgUp/PgDn page  Esc back";
            case ISSUES, PATHS -> "j/k select  PgUp/PgDn page  Enter matching tests  Esc home";
            case SLOWEST -> "j/k select  PgUp/PgDn page  Enter details  Esc home";
        };
        return column(text(navigation).cyan().dim().length(1), text(controls).cyan().dim().length(1)).length(2);
    }

    private static String countLabel(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    private static String filterLabel(CatsTuiState.ResultFilter filter) {
        return switch (filter) {
            case ALL -> "All";
            case ERROR -> "Errors";
            case WARNING -> "Warnings";
            case SUCCESS -> "Success";
            case SKIPPED -> "Skipped";
        };
    }

    private static Element terminalMessage(CatsTuiState state) {
        if ("Cancelled".equals(state.status())) {
            return panel("Cancelled", lines(state.failureMessage(), StyledElement::yellow))
                    .rounded().borderColor(Color.YELLOW).fill();
        }
        return panel("Failure", lines(state.failureMessage(), StyledElement::red))
                .rounded().borderColor(Color.RED).fill();
    }

    private static Color resultColor(String result) {
        return switch (CatsTuiState.ResultFilter.from(result)) {
            case ERROR -> Color.LIGHT_RED;
            case WARNING -> Color.LIGHT_YELLOW;
            case SUCCESS -> Color.LIGHT_GREEN;
            case SKIPPED -> Color.LIGHT_MAGENTA;
            case ALL -> Color.LIGHT_CYAN;
        };
    }

    private static Style resultStyle(String result) {
        return switch (CatsTuiState.ResultFilter.from(result)) {
            case ERROR -> ERROR_STYLE;
            case WARNING -> WARNING_STYLE;
            case SUCCESS -> SUCCESS_STYLE;
            case SKIPPED -> SKIPPED_STYLE;
            case ALL -> INFO_STYLE;
        };
    }

    private static Element executionSummary(CatsTuiState state) {
        if (state.configuration() == null) {
            return richLines(
                    labelled("OpenAPI specs: ", "loading"),
                    labelled("API base url: ", "loading"),
                    labelled("Configured: ", "loading"),
                    labelled("Path / Fuzzer: ", state.currentPath()),
                    labelled("Response time: ", "waiting for results"),
                    outcomeSummaryLine(state));
        }
        String methods = state.configuration().httpMethods().stream()
                .map(CatsTuiView::display).collect(java.util.stream.Collectors.joining(", "));
        return richLines(
                labelled("OpenAPI specs: ", display(state.configuration().contract())),
                labelled("API base url: ", display(state.configuration().basePath())),
                labelled("Configured: ", "%d/%d paths • %d/%d fuzzers • HTTP methods in scope: %s".formatted(
                        state.configuration().configuredPaths(), state.configuration().totalPaths(),
                        state.configuration().configuredFuzzers(), state.configuration().totalFuzzers(), methods)),
                labelled("Path / Fuzzer: ", "%s • %s".formatted(state.currentPath(), state.currentFuzzer())),
                labelledPairs("Response time: ", "average %dms • worst case %dms".formatted(
                                state.averageResponseTime(), state.maximumResponseTime()),
                        "    HTTP Response Codes: ", state.responseCodeSummary()),
                outcomeSummaryLine(state));
    }

    private static Line outcomeSummaryLine(CatsTuiState state) {
        return Line.from(
                Span.styled("Success: %d".formatted(state.success()), SUCCESS_STYLE), Span.raw("    "),
                Span.styled("Warnings: %d".formatted(state.warnings()), WARNING_STYLE), Span.raw("    "),
                Span.styled("Errors: %d".formatted(state.errors()), ERROR_STYLE), Span.raw("    "),
                Span.styled("Skipped: %d".formatted(state.skipped()), SKIPPED_STYLE));
    }

    private static Element comprehensiveSummary(CatsTuiState state) {
        return richLines(
                Line.from(Span.styled("Status: ", LABEL_STYLE),
                        Span.styled(display(state.status()), statusStyle(state)), Span.raw("    "),
                        Span.styled("Execution Time: ", LABEL_STYLE), Span.styled(state.elapsed(), VALUE_STYLE)),
                Line.from(Span.styled("Quality gate: ", LABEL_STYLE),
                        Span.styled(state.summary() == null ? "PENDING" : state.qualityGatePassed() ? "PASSED" : "FAILED",
                                state.summary() == null ? INFO_STYLE : state.qualityGatePassed() ? SUCCESS_STYLE : ERROR_STYLE),
                        Span.raw("    "), Span.styled(state.qualityGateDescription(), VALUE_STYLE)),
                labelledTriplet("Total requests: ", state.totalRequests(), "    Skipped from reporting: ",
                        state.skippedFromReporting(), "    Total Tests Run: ", state.reportedResults()),
                Line.from(Span.styled("Success: %d".formatted(state.success()), SUCCESS_STYLE), Span.raw("    "),
                        Span.styled("Warnings: %d".formatted(state.warnings()), WARNING_STYLE), Span.raw("    "),
                        Span.styled("Errors: %d".formatted(state.errors()), ERROR_STYLE)),
                Line.from(Span.styled("Skipped: %d".formatted(state.skipped()), SKIPPED_STYLE)),
                Line.from(Span.styled("Authentication errors: ", LABEL_STYLE),
                        Span.styled(String.valueOf(state.authenticationErrors()), countStyle(state.authenticationErrors())),
                        Span.raw("    "), Span.styled("I/O errors: ", LABEL_STYLE),
                        Span.styled(String.valueOf(state.ioErrors()), countStyle(state.ioErrors()))),
                labelled("HTTP Response Codes: ", state.responseCodeSummary()),
                labelled("Top Failing Paths: ", state.topFailingPaths()),
                labelledPairs("Test details retained: ", String.valueOf(state.retainedResults()),
                        "    Test details discarded: ", String.valueOf(state.discardedResults())));
    }

    private static Element detailLines(List<String> values, String result) {
        return column(values.stream().map(CatsTuiState::singleLine).map(line -> {
            String trimmed = line.trim();
            if ("REQUEST DETAILS".equals(trimmed) || "RESPONSE".equals(trimmed)) {
                return richLine(Span.styled(line, INFO_STYLE));
            }
            if (trimmed.endsWith(":")) {
                return richLine(Span.styled(line, LABEL_STYLE));
            }
            int separator = line.indexOf(':');
            if (separator > 0 && separator < 32) {
                Style valueStyle = line.startsWith("Result:") ? resultStyle(result)
                        : line.startsWith("HTTP Response Code:")
                                ? responseCodeStyle(parseLeadingInteger(line.substring(separator + 1)))
                        : VALUE_STYLE;
                return richLine(Span.styled(line.substring(0, separator + 1), LABEL_STYLE),
                        Span.styled(line.substring(separator + 1), valueStyle));
            }
            return richLine(Span.styled(line, VALUE_STYLE));
        }).toArray(Element[]::new));
    }

    private static Style statusStyle(CatsTuiState state) {
        return switch (state.status()) {
            case "Finished" -> SUCCESS_STYLE;
            case "Cancelled", "Cancellation requested" -> WARNING_STYLE;
            case "Failed" -> ERROR_STYLE;
            default -> INFO_STYLE;
        };
    }

    private static Style responseCodeStyle(int code) {
        if (code >= 500) {
            return ERROR_STYLE;
        }
        if (code >= 400) {
            return WARNING_STYLE;
        }
        if (code >= 200) {
            return SUCCESS_STYLE;
        }
        return VALUE_STYLE;
    }

    private static Style countStyle(long count) {
        return count == 0 ? SUCCESS_STYLE : ERROR_STYLE;
    }

    private static Cell cell(String value, Style style) {
        return Cell.from(value).style(style);
    }

    private static Element richLines(Line... lines) {
        return richText(Text.from(lines));
    }

    private static RichTextElement richLine(Span... spans) {
        return richText(Text.from(Line.from(spans)));
    }

    private static Line labelled(String label, Object value) {
        return Line.from(Span.styled(label, LABEL_STYLE), Span.styled(display(String.valueOf(value)), VALUE_STYLE));
    }

    private static Line labelledPairs(String firstLabel, String firstValue, String secondLabel, String secondValue) {
        return Line.from(Span.styled(firstLabel, LABEL_STYLE), Span.styled(display(firstValue), VALUE_STYLE),
                Span.styled(secondLabel, LABEL_STYLE), Span.styled(display(secondValue), VALUE_STYLE));
    }

    private static Line labelledTriplet(String firstLabel, long firstValue, String secondLabel, long secondValue,
                                        String thirdLabel, long thirdValue) {
        return Line.from(Span.styled(firstLabel, LABEL_STYLE), Span.styled(String.valueOf(firstValue), INFO_STYLE),
                Span.styled(secondLabel, LABEL_STYLE), Span.styled(String.valueOf(secondValue), INFO_STYLE),
                Span.styled(thirdLabel, LABEL_STYLE), Span.styled(String.valueOf(thirdValue), INFO_STYLE));
    }

    private static int parseLeadingInteger(String value) {
        String digits = value.stripLeading().chars().takeWhile(Character::isDigit)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        try {
            return digits.isEmpty() ? -1 : Integer.parseInt(digits);
        } catch (NumberFormatException _) {
            return -1;
        }
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "-" : CatsTuiState.singleLine(value);
    }

    private static Element lines(String value, UnaryOperator<dev.tamboui.toolkit.elements.TextElement> style) {
        List<String> values = value == null ? List.of("") : value.lines().toList();
        return column(values.stream().map(CatsTuiState::singleLine).map(Toolkit::text).map(style).toArray(Element[]::new));
    }
}
