package com.endava.cats.tui;

import com.endava.cats.tui.model.TestResultSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats and word-wraps a {@link TestResultSnapshot} for the full-screen TUI detail view.
 */
final class CatsTuiDetailFormatter {
    private CatsTuiDetailFormatter() {
    }

    static List<String> format(TestResultSnapshot test, int terminalColumns) {
        if (test == null) {
            return List.of("No test selected");
        }
        List<String> lines = new ArrayList<>();
        add(lines, "Scenario", test.scenario(), terminalColumns);
        add(lines, "Expected Result", test.expectedResult(), terminalColumns);
        add(lines, "Result", test.result(), terminalColumns);
        add(lines, "Result Reason", test.resultReason(), terminalColumns);
        add(lines, "Result Details", test.resultDetails(), terminalColumns);
        if (test.resultIgnoreDetails() != null && !test.resultIgnoreDetails().isBlank()) {
            add(lines, "Warning", test.resultIgnoreDetails(), terminalColumns);
        }
        add(lines, "Test Trace Id", test.traceId(), terminalColumns);
        add(lines, "Contract Path", test.contractPath(), terminalColumns);
        add(lines, "Full Request Path", test.fullRequestPath(), terminalColumns);
        add(lines, "Server", test.server(), terminalColumns);
        lines.add("Valid JSON: " + test.validJson());
        if (test.mutationTargets() != null && !test.mutationTargets().isEmpty()) {
            add(lines, "Mutation Targets", String.join(", ", test.mutationTargets()), terminalColumns);
        }
        if (test.resourceCorrelations() != null && !test.resourceCorrelations().isEmpty()) {
            lines.add("Resource Correlations:");
            test.resourceCorrelations().forEach(corr -> appendWrappedLine(lines, "  " + safe(corr), terminalColumns));
        }
        lines.add("");
        lines.add("REQUEST DETAILS");
        add(lines, "Http Method", test.request().httpMethod(), terminalColumns);
        add(lines, "URL", test.request().url(), terminalColumns);
        add(lines, "Timestamp", test.request().timestamp(), terminalColumns);
        lines.add("Headers:");
        test.request().headers().forEach(header -> appendWrappedLine(lines,
                "  " + safe(header.name()) + ": " + safe(header.value()), terminalColumns));
        lines.add("Payload:");
        appendMultiline(lines, test.request().payload(), terminalColumns);
        lines.add("");
        lines.add("RESPONSE");
        lines.add("HTTP Response Code: %d    Response Time: %dms".formatted(
                test.response().responseCode(), test.response().responseTimeInMs()));
        lines.add("Content Type: %s    Content Length: %d bytes".formatted(
                safe(test.response().contentType()), test.response().contentLengthInBytes()));
        if (test.response().bodyTruncated()) {
            String declaredLength = test.response().declaredContentLength() < 0
                    ? "unknown" : test.response().declaredContentLength() + " bytes";
            lines.add("Response body truncated    Declared Content Length: " + declaredLength);
        }
        lines.add("Number of Words: %d    Number of Lines: %d".formatted(
                test.response().numberOfWords(), test.response().numberOfLines()));
        lines.add("Headers:");
        test.response().headers().forEach(header -> appendWrappedLine(lines,
                "  " + safe(header.name()) + ": " + safe(header.value()), terminalColumns));
        lines.add("Body:");
        appendMultiline(lines, test.response().body(), terminalColumns);
        lines.add("");
        add(lines, "CATS Replay", test.replayCommand(), terminalColumns);
        if (test.curlCommand() != null && !test.curlCommand().isBlank()) {
            lines.add("cURL Command:");
            appendMultiline(lines, test.curlCommand(), terminalColumns);
        }
        return List.copyOf(lines);
    }

    private static void add(List<String> lines, String label, String value, int terminalColumns) {
        appendWrappedLine(lines, label + ": " + safe(value), terminalColumns);
    }

    private static void appendMultiline(List<String> lines, String value, int terminalColumns) {
        String safeValue = safe(value);
        if (safeValue.isBlank()) {
            lines.add("  <empty>");
            return;
        }
        safeValue.lines().forEach(line -> appendWrappedLine(lines, "  " + line, terminalColumns));
    }

    private static void appendWrappedLine(List<String> lines, String value, int terminalColumns) {
        if (value.isEmpty()) {
            lines.add("");
            return;
        }
        int lineWidth = Math.max(20, terminalColumns - 4);
        int offset = 0;
        while (offset < value.length()) {
            int end = Math.min(value.length(), offset + lineWidth);
            if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) {
                end--;
            }
            lines.add(value.substring(offset, end));
            offset = end;
        }
    }

    static String safe(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || codePoint == '\t' || !Character.isISOControl(codePoint)) {
                safe.appendCodePoint(codePoint);
            } else {
                safe.append('\uFFFD');
            }
        });
        return safe.toString();
    }
}
