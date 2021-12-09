package com.endava.cats.fuzzer.headers.leading;

import com.endava.cats.model.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LeadingWhitespacesInHeadersFuzzerTest {
    private LeadingWhitespacesInHeadersFuzzer leadingWhitespacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        leadingWhitespacesInHeadersFuzzer = new LeadingWhitespacesInHeadersFuzzer(null, null);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        Assertions.assertThat(leadingWhitespacesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingWhitespacesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(leadingWhitespacesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.prefix().name());
        Assertions.assertThat(leadingWhitespacesInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo("\u1680");
    }
}
