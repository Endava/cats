package com.endava.cats.fuzzer.headers.trailing;

import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TrailingControlCharsInHeadersFuzzerTest {
    private TrailingControlCharsInHeadersFuzzer trailingControlCharsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingControlCharsInHeadersFuzzer = new TrailingControlCharsInHeadersFuzzer(new CatsUtil(null), null, null);
    }

    @Test
    void givenANewTrailingSpacesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheTrailingSpacesInHeadersFuzzer() {
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\u0000");
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.getInvisibleChars()).doesNotContain("\r");

        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.matchResponseSchema()).isFalse();
    }
}
