package com.endava.cats.command.triage;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.junit.QuarkusTest;
import com.endava.cats.model.ProblemKind;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
class ReproducerScriptRendererTest {

    @Test
    void shouldRequireMaskedVariablesAndKeepCommentsOnASingleLine() {
        String curl = CatsRequest.builder()
                .httpMethod("POST")
                .url("http://localhost:8080/orders?api_key=$$APIKEY")
                .headers(List.of(new KeyValuePair<>("Authorization", "$$Authorization")))
                .payload("{\"note\":\"it's\"}")
                .build()
                .toCurlCommand();
        TriageOccurrence occurrence = TriageOccurrence.builder().operation("POST /orders").statusCodes(List.of(500)).testCount(2)
                .category("SERVER_CRASH_5XX").diagnosis("NPE\nrm -rf ~").remediation("r").fuzzers(List.of("HappyPath")).fields(List.of())
                .sampleTestIds(List.of("Test 1", "Test 2")).curlReproducer(curl).build();
        TriageProblem problem = new TriageProblem("1a2b3c4d", ProblemKind.SERVER_ERROR, "Server error (HTTP 500): NPE\nrm -rf ~", "error",
                List.of("Server error"), 2, "t", "template", List.of(occurrence));
        TriageReport report = new TriageReport("cats-report", 1, 2, 2, 0, 1, 1, 50, List.of(problem), List.of(), "", "", "");

        String script = ReproducerScriptRenderer.render(report);

        Assertions.assertThat(script)
                .contains(": \"${Authorization:?export Authorization=<value>", ": \"${APIKEY:?export APIKEY=<value>",
                        "# Diagnosis: NPE rm -rf ~", "# Problem 1 (1a2b3c4d): Server error (HTTP 500): NPE rm -rf ~", "-H 'Authorization: '\"${Authorization}\"''", "api_key='\"${APIKEY}\"''")
                .doesNotContain("\nrm -rf ~", "$$");
    }
}
