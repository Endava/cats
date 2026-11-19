package com.endava.cats.fuzzer.special.mutators.api;

import com.endava.cats.model.CatsHeader;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@QuarkusTest
class CustomMutatorTest {

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
    }

    @Test
    void shouldSupportDuplicateKeyCustomMutatorType() {
        CustomMutator mutator = new CustomMutator(
                new CustomMutatorConfig("dup-key", CustomMutatorConfig.Type.DUPLICATE_KEY, List.of("injected")));
        String result = mutator.mutate("{\"user\":{\"role\":\"USER\"}}", "user#role");

        Assertions.assertThat(result).startsWith("{\"role\":\"injected\",");
        Assertions.assertThat(mutator.mutate("", "role")).isEmpty();
        Assertions.assertThat(mutator.description()).isEqualTo("dup-key");
    }

    @Test
    void shouldSupportReplaceHeaderAndTrailHeaderCustomMutatorTypes() {
        CustomMutator replaceHeader = new CustomMutator(
                new CustomMutatorConfig("hdr-replace", CustomMutatorConfig.Type.REPLACE_HEADER, List.of("evil")));
        CustomMutator trailHeader = new CustomMutator(
                new CustomMutatorConfig("hdr-trail", CustomMutatorConfig.Type.TRAIL_HEADER, List.of("-suffix")));

        Collection<CatsHeader> initial = Set.of(CatsHeader.from("X-Test", "orig", true, null));
        Assertions.assertThat(replaceHeader.mutate("{\"a\":1}", "a")).isEqualTo("{\"a\":1}");
        Assertions.assertThat(replaceHeader.mutate(initial))
                .singleElement()
                .extracting(CatsHeader::getValue)
                .isEqualTo("evil");
        Assertions.assertThat(trailHeader.mutate(initial))
                .singleElement()
                .extracting(CatsHeader::getValue)
                .isEqualTo("orig-suffix");
        Assertions.assertThat(replaceHeader.mutate(Set.of()))
                .singleElement()
                .extracting(CatsHeader::getName)
                .isEqualTo("X-Cats-Custom");
    }
}
