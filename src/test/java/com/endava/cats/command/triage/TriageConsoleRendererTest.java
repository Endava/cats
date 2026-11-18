package com.endava.cats.command.triage;

import com.endava.cats.model.ProblemKind;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

@QuarkusTest
class TriageConsoleRendererTest {
    @Test
    void shouldKeepConsoleCompactAndExplainHowToGetFullReproducers() {
        PrettyLogger logger = Mockito.mock(PrettyLogger.class);

        TriageConsoleRenderer.render(logger, report(), false);

        Mockito.verify(logger, Mockito.never()).noFormat(Mockito.contains("curl"));
        Mockito.verify(logger).noFormat("  Reproducers: add --show-reproducers or --export-reproducers <file>.");
        Mockito.verify(logger).noFormat(Mockito.contains("Grouping Reduction:"));
        Mockito.verify(logger, Mockito.never()).noFormat(Mockito.contains("Signal-to-Noise"));
        Mockito.verify(logger).noFormat("        Sample Tests: Test 1");
        Mockito.verify(logger).noFormat(Mockito.contains("1 test · 1 operation"));
    }

    @Test
    void shouldPrintTheFullReproducerOnlyWhenRequested() {
        PrettyLogger logger = Mockito.mock(PrettyLogger.class);

        TriageConsoleRenderer.render(logger, report(), true);

        Mockito.verify(logger).noFormat("        Reproducer  : curl -i http://localhost/items");
        Mockito.verify(logger, Mockito.never()).noFormat(Mockito.contains("add --show-reproducers"));
    }

    private static TriageReport report() {
        var occurrence = TriageOccurrence.builder().operation("GET /items").statusCodes(List.of(500)).testCount(1)
                .category("SERVER_CRASH_5XX").diagnosis("Server returned 500").remediation("Inspect the response")
                .fields(List.of()).fuzzers(List.of("HappyPath")).sampleTestIds(List.of("Test 1"))
                .curlReproducer("curl -i http://localhost/items").build();
        var problem = new TriageProblem("01234567", ProblemKind.SERVER_ERROR, "Server error", "error", List.of("Server error"),
                1, null, null, List.of(occurrence));
        return new TriageReport("report", 1, 1, 1, 0, 1, 1, 0, List.of(problem), List.of(), "", "", "");
    }
}
