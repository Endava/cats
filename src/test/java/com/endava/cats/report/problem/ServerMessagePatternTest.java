package com.endava.cats.report.problem;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@io.quarkus.test.junit.QuarkusTest
class ServerMessagePatternTest {
    @ParameterizedTest
    @CsvSource({
            "Cluster c1 cannot be found., Cluster <*> cannot be found., true",
            "Cluster <*> cannot be found., Cluster c1 cannot be found., true",
            "same message, same message, true",
            "a b, a b c, false",
            "a b, a c, false",
            "a <*> b, a x y b, false",
            "a<*> b, ax b, false",
            "'   a   b ', 'a b', true",
            "'', '', false",
            ", a, false"
    })
    void shouldMatchOnlyEqualLengthTokenPatterns(String baseline, String target, boolean expected) {
        Assertions.assertThat(ServerMessagePattern.matches(baseline, target)).isEqualTo(expected);
    }
}
