package com.endava.cats.model;


import com.github.mustachejava.DefaultMustacheFactory;
import com.google.gson.Gson;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.StringWriter;
import java.util.Map;

@QuarkusTest
class CatsTestCaseTest {

    @Test
    void shouldNotUpdateServer() {
        CatsTestCase catsTestCase = new CatsTestCase();
        catsTestCase.setFullRequestPath("http://localhost:8080/orders");
        catsTestCase.updateServer(null);

        Assertions.assertThat(catsTestCase.getFullRequestPath()).isEqualTo("http://localhost:8080/orders");
    }

    @Test
    void shouldUpdateServer() {
        CatsTestCase catsTestCase = new CatsTestCase();
        catsTestCase.setFullRequestPath("http://localhost:8080/orders");
        catsTestCase.setRequest(CatsRequest.builder().url("http://localhost:8080/orders").build());
        catsTestCase.setServer("http://localhost:8080");
        catsTestCase.updateServer("http://example.com");

        Assertions.assertThat(catsTestCase.getFullRequestPath()).isEqualTo("http://example.com/orders");
        Assertions.assertThat(catsTestCase.getRequest().getUrl()).isEqualTo("http://example.com/orders");
    }

    @ParameterizedTest
    @CsvSource({"skipped,false", "skip_reporting,false", "success,true", "other,true"})
    void shouldReportSkip(String result, boolean skip) {
        CatsTestCase catsTestCase = new CatsTestCase();
        catsTestCase.setResult(result);
        Assertions.assertThat(catsTestCase.isNotSkipped()).isEqualTo(skip);
    }

    @Test
    void shouldSetSkip() {
        CatsTestCase catsTestCase = new CatsTestCase();
        catsTestCase.setResultSkipped();
        Assertions.assertThat(catsTestCase.isNotSkipped()).isFalse();
    }

    @Test
    void shouldExposeRuntimeCorrelationsInJsonAndHtmlReports() throws Exception {
        CatsTestCase catsTestCase = new CatsTestCase();
        catsTestCase.setTestId("test 1");
        catsTestCase.setResultDetails("");
        catsTestCase.getRuntimeCorrelations().add(ResourceCorrelation.builder()
                .sourceMethod("POST")
                .sourcePath("/customers")
                .sourceLocation("response.body.$.id")
                .targetLocation("path")
                .targetField("customerId")
                .value("customer-42")
                .build());

        String json = new Gson().toJson(catsTestCase);
        StringWriter html = new StringWriter();
        new DefaultMustacheFactory().compile("test-case.mustache")
                .execute(html, Map.of("TEST_CASE", catsTestCase)).flush();

        Assertions.assertThat(catsTestCase.hasRuntimeCorrelations()).isTrue();
        Assertions.assertThat(json).contains("runtimeCorrelations", "response.body.$.id", "customer-42");
        Assertions.assertThat(html.toString()).contains("Runtime Correlations",
                "POST /customers response.body.$.id", "path customerId = customer-42");
    }

}
