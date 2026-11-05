package com.endava.cats.command.triage;

import com.endava.cats.model.CatsRequest;
import com.endava.cats.util.KeyValuePair;
import io.quarkus.test.junit.QuarkusTest;
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
        RootCauseCluster cluster = new RootCauseCluster("SERVER_CRASH_5XX", 0, "POST /orders", 500, 2,
                "NPE\nrm -rf ~", "r", List.of("HappyPath"), List.of("Test 1", "Test 2"), curl, null, null, null);
        TriageReport report = new TriageReport("cats-report", 2, 2, 0, 1, 50, List.of(cluster), List.of(), "", "", "");

        String script = ReproducerScriptRenderer.render(report);

        Assertions.assertThat(script)
                .contains(": \"${Authorization:?export Authorization=<value>", ": \"${APIKEY:?export APIKEY=<value>",
                        "# Diagnosis: NPE rm -rf ~", "-H 'Authorization: '\"${Authorization}\"''", "api_key='\"${APIKEY}\"''")
                .doesNotContain("\nrm -rf ~", "$$");
    }
}
