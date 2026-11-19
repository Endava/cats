package com.endava.cats.fuzzer.special.mutators.impl;

import com.endava.cats.fuzzer.special.mutators.api.CrossLayerMutator;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.util.CatsRandom;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class CrossLayerMutatorsTest {

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
    }

    @Test
    void shouldMutateBothBodyAndHeadersAcrossAllCrossLayerMutators() {
        LocaleAndCaseExpansionCrossMutator localeMutator = new LocaleAndCaseExpansionCrossMutator();
        CharsetAndEncodingCrossMutator charsetMutator = new CharsetAndEncodingCrossMutator();
        ConditionalAndIdempotencyCrossMutator conditionalMutator = new ConditionalAndIdempotencyCrossMutator();
        List<CrossLayerMutator> mutators = List.of(localeMutator, charsetMutator, conditionalMutator);

        String inputJson = "{\"name\":\"Alice\",\"count\":5}";
        Collection<CatsHeader> initialHeaders = Set.of(CatsHeader.from("X-Trace", "1", false, null));

        for (CrossLayerMutator mutator : mutators) {
            Assertions.assertThat(mutator.description()).isNotBlank();
            Assertions.assertThat(mutator.mutate(inputJson, "name")).isNotEqualTo(inputJson);
            Collection<CatsHeader> mutatedHeaders = mutator.mutate(initialHeaders);
            Assertions.assertThat(mutatedHeaders).hasSize(2);
        }

        FuzzingData data = FuzzingData.builder()
                .requestPropertyTypes(Map.of("name", new Schema<String>().type("string").maxLength(16)))
                .build();
        Assertions.assertThat(localeMutator.mutate(inputJson, "name", data)).isNotEqualTo(inputJson);
    }
}
