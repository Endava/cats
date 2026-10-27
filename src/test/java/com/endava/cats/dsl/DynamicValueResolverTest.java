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
    }
}
