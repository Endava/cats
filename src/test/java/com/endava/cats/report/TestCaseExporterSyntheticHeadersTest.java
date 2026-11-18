package com.endava.cats.report;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.generator.simple.EncodedAuthenticationGenerator;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.RequestTarget;
import com.endava.cats.util.KeyValuePair;
import com.endava.cats.tui.model.TestResultSnapshot;
import com.endava.cats.util.SensitiveDataPolicy;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class TestCaseExporterSyntheticHeadersTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @EnumSource(value = ReportingArguments.ReportFormat.class, names = {"HTML_JS", "HTML_ONLY"})
    void shouldPreserveEveryPublicProbeInJsonHtmlAndCurlWhileMaskingOtherSecrets(ReportingArguments.ReportFormat format) throws Exception {
        ReportingArguments arguments = new ReportingArguments();
        TestCaseExporter exporter = exporter(format, arguments);
        List<String> values = Stream.concat(EncodedAuthenticationGenerator.basicValues().stream(), EncodedAuthenticationGenerator.jwtValues().stream()).toList();

        for (String value : values) {
            CatsTestCase testCase = testCase(value);
            SensitiveDataPolicy.sanitize(testCase, arguments);
            exporter.writeTestCase(testCase);
            assertPublicRequestAndMaskedResponse(testCase, value, arguments);
        }
        assertThat(arguments.getReplayEnvironmentVariables()).containsExactly("XApiKey");
    }

    @ParameterizedTest
    @ValueSource(strings = {"other-fuzzer", "no-provenance", "real-value", "mixed-value", "explicit-mask"})
    void shouldKeepTheExistingMaskingForRequestsOutsideTheNarrowException(String guard) throws Exception {
        ReportingArguments arguments = new ReportingArguments();
        CatsTestCase testCase = testCase("Basic Y2F0czpjYXRz", !"no-provenance".equals(guard));
        switch (guard) {
            case "other-fuzzer" -> testCase.setFuzzer("HappyPath");
            case "no-provenance" -> { /* No mutation metadata was recorded. */ }
            case "real-value" -> testCase.getRequest().getHeaders().getFirst().setValue("Bearer private-credential");
            case "mixed-value" -> testCase.getRequest().getHeaders().getFirst().setValue("Basic Y2F0czpjYXRz-private");
            case "explicit-mask" -> ReflectionTestUtils.setField(arguments, "maskHeaders", Set.of("authorization"));
            default -> throw new IllegalArgumentException(guard);
        }
        exporter(ReportingArguments.ReportFormat.HTML_JS, arguments).writeTestCase(testCase);

        assertThat(Files.readString(directory.resolve("Test1.json"))).contains("$$Authorization").doesNotContain("private-credential", "Y2F0czpjYXRz-private");
        assertThat(StringEscapeUtils.unescapeHtml4(Files.readString(directory.resolve("Test1.html"))))
                .contains("$$Authorization", "${Authorization}");
    }

    @Test
    void shouldKeepResponseMaskingEvenWhenAnExporterIsUsedBeforeSanitizing() throws Exception {
        CatsTestCase testCase = testCase("Basic Y2F0czpjYXRz");
        exporter(ReportingArguments.ReportFormat.HTML_JS, new ReportingArguments()).writeTestCase(testCase);

        JsonObject json = readJson();
        assertThat(json.getAsJsonObject("request").getAsJsonArray("headers").get(0).getAsJsonObject().get("value").getAsString())
                .isEqualTo("Basic Y2F0czpjYXRz");
        assertThat(json.getAsJsonObject("response").getAsJsonArray("headers").get(0).getAsJsonObject().get("value").getAsString())
                .isEqualTo("$$Authorization");
        assertThat(testCase.getResponseHeaders()).contains("$$Authorization").doesNotContain("Y2F0czpjYXRz");
    }

    private void assertPublicRequestAndMaskedResponse(CatsTestCase testCase, String value, ReportingArguments arguments) throws Exception {
        JsonObject json = readJson();
        assertThat(json.getAsJsonObject("request").getAsJsonArray("headers").get(0).getAsJsonObject().get("value").getAsString()).isEqualTo(value);
        assertThat(json.getAsJsonObject("response").getAsJsonArray("headers").get(0).getAsJsonObject().get("value").getAsString())
                .isEqualTo(SensitiveDataPolicy.REDACTED);
        assertThat(json.toString()).contains("$$XApiKey").doesNotContain("private-api-key", "requestMaskingSerializer");
        assertThat(StringEscapeUtils.unescapeHtml4(Files.readString(directory.resolve("Test1.html"))))
                .contains(value).doesNotContain("private-api-key", "$$Authorization");
        TestResultSnapshot snapshot = TestResultSnapshot.from(testCase, arguments);
        assertThat(snapshot.request().headers().getFirst().value()).isEqualTo(value);
        assertThat(snapshot.response().headers().getFirst().value()).isEqualTo(SensitiveDataPolicy.REDACTED);
        assertThat(snapshot.curlCommand()).contains("Authorization: " + value).doesNotContain("$Authorization");
        assertThat(CatsTestCase.readTestCaseFile(directory.resolve("Test1.json"))).hasValueSatisfying(saved ->
                assertThat(saved.getRequest().getHeaders().getFirst().getValue()).isEqualTo(value));
    }

    private JsonObject readJson() throws Exception {
        return JsonParser.parseString(Files.readString(directory.resolve("Test1.json"))).getAsJsonObject();
    }

    private TestCaseExporter exporter(ReportingArguments.ReportFormat format, ReportingArguments arguments) throws Exception {
        TestCaseExporter exporter = format == ReportingArguments.ReportFormat.HTML_JS
                ? new TestCaseExporterHtmlJs(arguments, new CatsGlobalContext(), new ProcessingArguments(), new StopArguments())
                : new TestCaseExporterHtmlOnly(arguments, new CatsGlobalContext(), new ProcessingArguments(), new StopArguments());
        exporter.appVersion = "test";
        exporter.initPath(directory.toString());
        return exporter;
    }

    private static CatsTestCase testCase(String value) {
        return testCase(value, true);
    }

    private static CatsTestCase testCase(String value, boolean mutation) {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setTestId("Test1");
        testCase.setFuzzer("EncodedAuthenticationHeaders");
        testCase.setRequest(CatsRequest.builder().httpMethod("GET").url("https://service.test/items").payload("{}")
                .headers(List.of(new KeyValuePair<>("Authorization", value), new KeyValuePair<>("X-Api-Key", "private-api-key"))).build());
        if (mutation) {
            testCase.getRequestProvenance().addMutationTargets(List.of(RequestTarget.header("Authorization")));
        }
        testCase.setResponse(CatsResponse.builder().responseCode(500).body("{}").headers(List.of(new KeyValuePair<>("Authorization", value))).build());
        return testCase;
    }
}
