package com.endava.cats.command.triage;

import com.endava.cats.model.ProblemKind;
import com.endava.cats.model.FindingConfidence;
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

    @Test
    void shouldShowTheSampleExpectationEvidenceAndRecordedConfidence() {
        PrettyLogger logger = Mockito.mock(PrettyLogger.class);
        TriageOccurrence occurrence = report().problems().getFirst().occurrences().getFirst().toBuilder()
                .sample(new TriageSample("Test 1", 200, List.of("4XX"), FindingConfidence.TENTATIVE, "Payload was reflected"))
                .fuzzers(List.of("One", "Two", "Three", "Four", "Five"))
                .build();

        TriageConsoleRenderer.render(logger, report(occurrence), false);

        Mockito.verify(logger).noFormat("        Sample      : Test 1 — expected 4XX; received HTTP 200");
        Mockito.verify(logger).noFormat("        Confidence  : TENTATIVE (recorded by the detector)");
        Mockito.verify(logger).noFormat("        Evidence    : Payload was reflected");
        Mockito.verify(logger).noFormat("        Fuzzers     : One, Two, Three, Four (+1 more; see JSON)");
    }

    @Test
    void shouldNotCallTransportCodesHttpStatuses() {
        PrettyLogger logger = Mockito.mock(PrettyLogger.class);
        TriageOccurrence occurrence = report().problems().getFirst().occurrences().getFirst().toBuilder()
                .statusCodes(List.of(953)).category("TRANSPORT_FAILURE").build();

        TriageConsoleRenderer.render(logger, report(occurrence), false);

        Mockito.verify(logger).noFormat(Mockito.contains("CATS code 953"));
        Mockito.verify(logger, Mockito.never()).noFormat(Mockito.contains("HTTP 953"));
    }

    private static TriageReport report() {
        var occurrence = TriageOccurrence.builder().operation("GET /items").statusCodes(List.of(500)).testCount(1)
                .category("SERVER_CRASH_5XX").diagnosis("Server returned 500").remediation("Inspect the response")
                .fields(List.of()).fuzzers(List.of("HappyPath")).sampleTestIds(List.of("Test 1"))
                .curlReproducer("curl -i http://localhost/items").build();
        return report(occurrence);
    }

    private static TriageReport report(TriageOccurrence occurrence) {
        var problem = new TriageProblem("01234567", ProblemKind.SERVER_ERROR, "Server error", "error", List.of("Server error"),
                1, null, null, List.of(occurrence));
        return new TriageReport("report", 1, 1, 1, 0, 1, 1, 0, List.of(problem), List.of(), "", "", "");
    }
}
