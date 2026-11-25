package com.endava.cats.dsl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;

import java.util.Map;

@QuarkusTest
class DynamicValueResolverTest {

    @Test
    void shouldResolveAnExactVariable() {
        Assertions.assertThat(DynamicValueResolver.resolve("${token}", Map.of("token", "token-123")))
                .isEqualTo("token-123");
    }

    @Test
    void shouldResolveAVariableEmbeddedInAValue() {
        Assertions.assertThat(DynamicValueResolver.resolve("Bearer ${token}", Map.of("token", "token-123")))
                .isEqualTo("Bearer token-123");
    }

    @Test
    void shouldResolveVariablesBeforeEvaluatingDsl() {
        Assertions.assertThat(DynamicValueResolver.resolve(
                        "T(java.lang.String).valueOf(${number})", Map.of("number", "42")))
                .isEqualTo("42");
    }

    @Test
    void shouldApplyExistingParserBehaviorToUnknownVariables() {
        Assertions.assertThat(DynamicValueResolver.resolve("Bearer ${missing}", Map.of()))
                .isEqualTo("Bearer missing");
    }

    @Test
    void shouldHandleNullValueAndNullContext() {
        Assertions.assertThat(DynamicValueResolver.resolve(null, Map.of("token", "123"))).isNull();
        Assertions.assertThat(DynamicValueResolver.resolve("static-value", null)).isEqualTo("static-value");
    }

    @Test
    void shouldResolveShorthandFunctionsWithDefaultLengthsAndAlphaAlias() {
        Assertions.assertThat(DynamicValueResolver.resolve("#(numeric)", Map.of())).matches("\\d{12}");
        Assertions.assertThat(DynamicValueResolver.resolve("#(alpha)", Map.of())).matches("[A-Za-z]{12}");
        Assertions.assertThat(DynamicValueResolver.resolve("#(alpha(6))", Map.of())).matches("[A-Za-z]{6}");
        Assertions.assertThat(DynamicValueResolver.resolve("#(alphanumeric)", Map.of())).matches("[A-Za-z0-9]{12}");
        Assertions.assertThat(DynamicValueResolver.resolve("#(ascii)", Map.of())).hasSize(12);
        Assertions.assertThat(DynamicValueResolver.resolve("#(upper(alphabetic))", Map.of())).matches("[A-Z]{12}");
        Assertions.assertThat(DynamicValueResolver.resolve("#(lower(alphabetic(6)))", Map.of())).matches("[a-z]{6}");
        Assertions.assertThat(DynamicValueResolver.resolve("PREFIX-#(upper(alpha(4)))-${suffix}", Map.of("suffix", "99")))
                .matches("PREFIX-[A-Z]{4}-99");
    }

    @Test
    void shouldResolveEmbeddedEnvVariablesAndTypedScalarExpressions() {
        Assertions.assertThat(DynamicValueResolver.resolve("Bearer $$API_TOKEN", Map.of("API_TOKEN", "secret-jwt")))
                .isEqualTo("Bearer secret-jwt");
        Assertions.assertThat(DynamicValueResolver.resolve("Basic $$USER_B64", Map.of("USER_B64", "YWRtaW46cGFzcw==")))
                .isEqualTo("Basic YWRtaW46cGFzcw==");
        Assertions.assertThat(DynamicValueResolver.resolveAsTypedValue("#(randomInt(5,6))", Map.of()))
                .isEqualTo(5L);
        Assertions.assertThat(DynamicValueResolver.resolveAsTypedValue("#(bool)", Map.of()))
                .isInstanceOf(Boolean.class);
        Assertions.assertThat(DynamicValueResolver.coerceScalarIfOriginalWasTyped("T(Double).valueOf(1.5)", "1.5"))
                .isEqualTo(1.5d);
        Assertions.assertThat(DynamicValueResolver.coerceScalarIfOriginalWasTyped(null, null)).isNull();
    }

    @Test
    void shouldSupportExplicitTypeConversionShorthandsForEnvAndVariables() {
        Map<String, String> ctx = Map.of("EXPIRY", "08", "RATE", "12", "FLAG", "TRUE", "CODE", "42");
        Assertions.assertThat(DynamicValueResolver.resolveAsTypedValue("#(int($$EXPIRY))", ctx)).isEqualTo(8L);
        Assertions.assertThat(DynamicValueResolver.resolveAsTypedValue("#(float($$RATE))", ctx)).isEqualTo(12.0d);
        Assertions.assertThat(DynamicValueResolver.resolveAsTypedValue("#(float(10000000))", ctx)).isEqualTo(10000000.0d);
        Assertions.assertThat(DynamicValueResolver.resolveAsTypedValue("#(float(0.00001))", ctx)).isEqualTo(0.00001d);
        Assertions.assertThat(DynamicValueResolver.resolveAsTypedValue("#(bool($$FLAG))", ctx)).isEqualTo(true);
        Assertions.assertThat(DynamicValueResolver.resolveAsTypedValue("#(str(${CODE}))", ctx)).isEqualTo("42");
        Assertions.assertThat(DynamicValueResolver.resolveAsTypedValue("#(string(123))", ctx)).isEqualTo("123");
    }
}
