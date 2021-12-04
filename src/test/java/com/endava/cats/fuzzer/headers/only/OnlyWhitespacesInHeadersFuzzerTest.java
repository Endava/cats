package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OnlyWhitespacesInHeadersFuzzerTest {
    private OnlyWhitespacesInHeadersFuzzer onlyWhitespacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        onlyWhitespacesInHeadersFuzzer = new OnlyWhitespacesInHeadersFuzzer(new CatsUtil(null), null, null);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        Assertions.assertThat(onlyWhitespacesInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(onlyWhitespacesInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(onlyWhitespacesInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(onlyWhitespacesInHeadersFuzzer.fuzzStrategy().get(0).getData()).isEqualTo("\u1680");
    }
}
