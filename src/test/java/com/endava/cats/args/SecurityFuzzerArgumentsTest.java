package com.endava.cats.args;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SecurityFuzzerArgumentsTest {

    @Test
    void shouldDefaultToFalse() {
        SecurityFuzzerArguments args = new SecurityFuzzerArguments();
        Assertions.assertThat(args.isIncludeAllInjectionPayloads()).isFalse();
    }

    @Test
    void shouldSetIncludeAllInjectionPayloads() {
        SecurityFuzzerArguments args = new SecurityFuzzerArguments();
        args.setIncludeAllInjectionPayloads(true);
        Assertions.assertThat(args.isIncludeAllInjectionPayloads()).isTrue();
    }
}
