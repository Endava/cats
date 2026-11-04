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
}
