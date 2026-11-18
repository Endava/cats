package com.endava.cats.util;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.RequestTarget;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class HeaderPresentationTest {
    @ParameterizedTest
    @CsvSource({"N/A", "####", "UNKNOWN"})
    void shouldReturnEmptyCurlForLinterResultsWithoutAnHttpMethod(String method) {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setRequest(CatsRequest.builder().httpMethod(method).url("####").payload("{}").build());

        assertThat(new HeaderPresentation(new ReportingArguments()).curl(testCase)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldKeepRequestAndResponseMaskingSeparateAndHonorExplicitMasks(boolean explicitlyMasked) {
        ReportingArguments arguments = new ReportingArguments();
        arguments.registerSensitiveHeaders(Set.of("Authorization"));
        if (explicitlyMasked) {
            ReflectionTestUtils.setField(arguments, "maskHeaders", Set.of("AUTHORIZATION"));
        }
        HeaderPresentation presentation = new HeaderPresentation(arguments);
        CatsTestCase testCase = probe();
        CatsResponse response = CatsResponse.builder().headers(List.of(new KeyValuePair<>("Authorization", "Basic Y2F0czpjYXRz"))).build();

        assertThat(presentation.requestHeaders(testCase)).containsExactly(new KeyValuePair<>("Authorization",
                explicitlyMasked ? "$$Authorization" : "Basic Y2F0czpjYXRz"));
        assertThat(presentation.responseHeaders(response)).containsExactly(new KeyValuePair<>("Authorization", "$$Authorization"));
        assertThat(presentation.curl(testCase)).contains(explicitlyMasked ? "${Authorization}" : "Authorization: Basic Y2F0czpjYXRz");
        assertThat(testCase.getRequest().getHeaders().getFirst().getValue()).isEqualTo("Basic Y2F0czpjYXRz");
    }

    @Test
    void shouldPreserveDuplicateHeaderOrderAndRawBodiesWhileUsingSharedShellQuoting() {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setFullRequestPath("https://service.test/actual");
        testCase.setRequest(CatsRequest.builder().httpMethod("POST").url("https://service.test/template").payload("{\"name\":\"O'Brien\"}")
                .headers(List.of(new KeyValuePair<>("X-Value", "first"), new KeyValuePair<>("X-Value", "second"),
                        new KeyValuePair<>("Authorization", "Bearer private-token"))).build());
        HeaderPresentation presentation = new HeaderPresentation(new ReportingArguments());

        String curl = presentation.curl(testCase);

        assertThat(curl).containsSubsequence("X-Value: first", "X-Value: second").contains("${Authorization}", "O'\\''Brien", "https://service.test/actual")
                .doesNotContain("private-token", "/template");
        assertThat(testCase.getRequest().getPayload()).isEqualTo("{\"name\":\"O'Brien\"}");
        assertThat(testCase.getRequest().getUrl()).endsWith("/template");
        assertThat(testCase.getRequest().getHeaders().getLast().getValue()).isEqualTo("Bearer private-token");
    }

    private static CatsTestCase probe() {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setFuzzer("EncodedAuthenticationHeaders");
        testCase.setRequest(CatsRequest.builder().httpMethod("GET").url("https://service.test/items")
                .headers(List.of(new KeyValuePair<>("Authorization", "Basic Y2F0czpjYXRz"))).build());
        testCase.getRequestProvenance().addMutationTargets(List.of(RequestTarget.header("Authorization")));
        return testCase;
    }
}
