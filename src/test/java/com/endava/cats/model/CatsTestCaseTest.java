package com.endava.cats.model;

import com.endava.cats.util.KeyValuePair;
import com.github.mustachejava.DefaultMustacheFactory;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.StringWriter;
import java.util.List;
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
        catsTestCase.setJs(true);
        catsTestCase.setFullRequestPath("http://localhost/customers/customer-42/orders/order-7");
        catsTestCase.setMaskingSerializer(new Gson());
        catsTestCase.setRequest(CatsRequest.builder()
                .httpMethod("GET")
                .url(catsTestCase.getFullRequestPath())
                .payload("{}")
                .headers(List.of())
                .build());
        catsTestCase.setResponse(CatsResponse.builder()
                .responseCode(200)
                .responseTimeInMs(17)
                .responseContentType("application/json")
                .httpMethod("GET")
                .body("{\"result\":\"ok\"}")
                .headers(List.of(new KeyValuePair<>("Content-Type", "application/json")))
                .contentLengthInBytes(15)
                .numberOfWordsInResponse(1)
                .numberOfLinesInResponse(1)
                .responseValidationField("customerId")
                .build());
        catsTestCase.getRequestProvenance().addMutationTargets(
                List.of(RequestTarget.body("customerId"), RequestTarget.header("X-Test"),
                        RequestTarget.requestBody()));
        catsTestCase.getRequestProvenance().addRuntimeCorrelation(ResourceCorrelation.builder()
                .sourceMethod("POST")
                .sourcePath("/customers")
                .sourceLocation("response.body.$.id")
                .target(RequestTarget.path("customerId"))
                .value("customer-42")
                .build());
        catsTestCase.getRequestProvenance().addRuntimeCorrelation(ResourceCorrelation.builder()
                .sourceMethod("POST")
                .sourcePath("/customers/{customerId}/orders")
                .sourceLocation("response.body.$.orderId")
                .target(RequestTarget.path("orderId"))
                .value("order-7")
                .build());

        String json = new Gson().toJson(catsTestCase);
        StringWriter html = new StringWriter();
        new DefaultMustacheFactory().compile("test-case.mustache")
                .execute(html, Map.of("TEST_CASE", catsTestCase)).flush();

        Assertions.assertThat(catsTestCase.hasRuntimeCorrelations()).isTrue();
        Assertions.assertThat(catsTestCase.hasMutationTargets()).isTrue();
        List<RequestTarget> mutationTargets = catsTestCase.getMutationTargets();
        List<ResourceCorrelation> runtimeCorrelations = catsTestCase.getRuntimeCorrelations();
        Assertions.assertThatThrownBy(() -> mutationTargets.add(RequestTarget.body("other")))
                .isInstanceOf(UnsupportedOperationException.class);
        Assertions.assertThatThrownBy(runtimeCorrelations::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        JsonObject provenance = JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonObject("requestProvenance");
        Assertions.assertThat(provenance.getAsJsonArray("mutationTargets")).hasSize(3);
        Assertions.assertThat(provenance.getAsJsonArray("mutationTargets").get(0).getAsJsonObject().get("location").getAsString())
                .isEqualTo("BODY");
        Assertions.assertThat(provenance.getAsJsonArray("mutationTargets").get(1).getAsJsonObject().get("name").getAsString())
                .isEqualTo("X-Test");
        Assertions.assertThat(provenance.getAsJsonArray("runtimeCorrelations")).hasSize(2);
        JsonObject firstCorrelation = provenance.getAsJsonArray("runtimeCorrelations").get(0).getAsJsonObject();
        Assertions.assertThat(firstCorrelation.get("sourceLocation").getAsString()).isEqualTo("response.body.$.id");
        Assertions.assertThat(firstCorrelation.getAsJsonObject("target").get("location").getAsString())
                .isEqualTo("PATH");
        Assertions.assertThat(firstCorrelation.getAsJsonObject("target").get("name").getAsString())
                .isEqualTo("customerId");
        Assertions.assertThat(firstCorrelation.get("value").getAsString()).isEqualTo("customer-42");
        String htmlReport = html.toString();
        Assertions.assertThat(htmlReport).contains("Runtime Correlations",
                "POST /customers", "response.body.$.id", "path customerId", "customer-42",
                "POST /customers/{customerId}/orders", "response.body.$.orderId", "path orderId", "order-7",
                "Mutation Targets", "Body field", "customerId", "Header", "X-Test",
                "Request body -&gt; Entire body", "Response", "Body", "Headers", "Details",
                "HTTP status", "200", "Response time", "17ms", "Content type", "application/json",
                "Content length", "15 bytes", "Words", "Lines", "&quot;result&quot;: &quot;ok&quot;");
        Assertions.assertThat(StringUtils.countMatches(htmlReport, "class=\"mutation-target-row\""))
                .isEqualTo(3);
        Assertions.assertThat(StringUtils.countMatches(htmlReport, "class=\"runtime-correlation-row\""))
                .isEqualTo(2);
        Assertions.assertThat(htmlReport.indexOf("Runtime Correlations"))
                .isGreaterThan(htmlReport.indexOf("CATS Replay"));

        catsTestCase.setJs(false);
        StringWriter staticHtml = new StringWriter();
        new DefaultMustacheFactory().compile("test-case.mustache")
                .execute(staticHtml, Map.of("TEST_CASE", catsTestCase)).flush();

        Assertions.assertThat(staticHtml.toString())
                .contains("Response Body", "Response Headers", "Response Details",
                        "HTTP status", "Response time", "Content type")
                .doesNotContain("showReportTab");
    }

}
