package com.endava.cats.util;

import com.endava.cats.args.ReportingArguments;
import com.endava.cats.generator.simple.EncodedAuthenticationGenerator;
import com.endava.cats.model.CatsRequest;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.CatsTestCase;
import com.endava.cats.model.RequestTarget;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class SyntheticAuthHeaderPolicyTest {
    @ParameterizedTest
    @MethodSource("syntheticValues")
    void shouldPreserveOnlySyntheticRequestValuesWithoutRegisteringAuthAsASecret(String value) {
        ReportingArguments arguments = new ReportingArguments();
        arguments.registerSensitiveHeaders(Set.of("Authorization"));
        CatsTestCase testCase = probe(value);
        testCase.getRequest().setUrl("https://service.test/items?access_token=private-query");
        testCase.setResponse(CatsResponse.builder().headers(List.of(new KeyValuePair<>("Authorization", value))).build());

        SensitiveDataPolicy.sanitize(testCase, arguments);

        assertThat(testCase.getRequest().getHeaders().getFirst().getValue()).isEqualTo(value);
        assertThat(testCase.getRequest().getUrl()).contains("access_token=$$ACCESS_TOKEN").doesNotContain("private-query");
        assertThat(testCase.getResponse().getHeaders().getFirst().getValue()).isEqualTo(SensitiveDataPolicy.REDACTED);
        assertThat(arguments.getReplayEnvironmentVariables()).containsExactly("ACCESS_TOKEN");
    }

    @ParameterizedTest
    @CsvSource({"HappyPath,HEADER,Authorization", "EncodedAuthenticationHeaders,BODY,Authorization",
            "EncodedAuthenticationHeaders,HEADER,X-Other"})
    void shouldRequireBothFuzzerAndAuthorizationMutation(String fuzzer, RequestTarget.Location location, String name) {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setFuzzer(fuzzer);
        testCase.getRequest().setHeaders(List.of(new KeyValuePair<>("Authorization", "Basic Y2F0czpjYXRz")));
        testCase.getRequestProvenance().addMutationTargets(List.of(new RequestTarget(location, name)));

        SensitiveDataPolicy.sanitize(testCase, new ReportingArguments());

        assertThat(testCase.getRequest().getHeaders().getFirst().getValue()).isEqualTo("$$Authorization");
    }

    @Test
    void shouldKeepExplicitMaskingAndRealOrMixedCredentialsMasked() {
        ReportingArguments explicitMask = new ReportingArguments();
        ReflectionTestUtils.setField(explicitMask, "maskHeaders", Set.of("AUTHORIZATION"));
        CatsTestCase synthetic = probe("Basic Y2F0czpjYXRz");
        SensitiveDataPolicy.sanitize(synthetic, explicitMask);
        assertThat(synthetic.getRequest().getHeaders().getFirst().getValue()).isEqualTo("$$Authorization");

        for (String value : List.of("Bearer private-token", "Basic Y2F0czpjYXRz-private", "Bearer e30.private.c2ln")) {
            CatsTestCase testCase = probe(value);
            SensitiveDataPolicy.sanitize(testCase, new ReportingArguments());
            assertThat(testCase.getRequest().getHeaders().getFirst().getValue()).isEqualTo("$$Authorization");
        }
    }

    @Test
    void shouldFailClosedWithoutContextAndRecognizeCaseInsensitiveHeaderNames() {
        assertThat(SyntheticAuthHeaderPolicy.isProbe(null)).isFalse();
        assertThat(SyntheticAuthHeaderPolicy.isProbe(new CatsTestCase())).isFalse();
        CatsTestCase testCase = probe("Basic Y2F0czpjYXRz");
        testCase.setFuzzer("EncodedAuthenticationHeadersFuzzer");
        testCase.getRequest().getHeaders().getFirst().setKey("authorization");
        assertThat(SyntheticAuthHeaderPolicy.isProbe(testCase)).isTrue();
        testCase.setRequest(CatsRequest.empty());
        assertThat(SyntheticAuthHeaderPolicy.isProbe(testCase)).isFalse();
        testCase.setRequest(null);
        assertThat(SyntheticAuthHeaderPolicy.isProbe(testCase)).isFalse();
    }

    private static Stream<String> syntheticValues() {
        return Stream.concat(EncodedAuthenticationGenerator.basicValues().stream(), EncodedAuthenticationGenerator.jwtValues().stream());
    }

    private static CatsTestCase probe(String value) {
        CatsTestCase testCase = new CatsTestCase();
        testCase.setFuzzer("EncodedAuthenticationHeaders");
        testCase.getRequest().setHeaders(List.of(new KeyValuePair<>("Authorization", value)));
        testCase.getRequestProvenance().addMutationTargets(List.of(RequestTarget.header("Authorization")));
        return testCase;
    }
}
