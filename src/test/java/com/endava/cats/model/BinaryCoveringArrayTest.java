package com.endava.cats.model;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

@QuarkusTest
class BinaryCoveringArrayTest {

    @ParameterizedTest
    @CsvSource({
            "2, 3",
            "3, 3",
            "4, 4",
            "10, 5",
            "15, 6",
            "20, 7",
            "35, 7",
            "56, 8",
            "126, 9",
            "200, 10"
    })
    void shouldProduceOptimalKleitmanSpencerRowCountsForStrength2(int optionalFields, int expectedNonZeroRows) {
        List<boolean[]> matrix = BinaryCoveringArray.generateMatrix(optionalFields, 2);

        Assertions.assertThat(matrix).hasSize(expectedNonZeroRows);
        Assertions.assertThat(BinaryCoveringArray.optimalStrength2TotalRows(optionalFields))
                .isEqualTo(expectedNonZeroRows + 1);
    }

    @Test
    void shouldExhaustivelyCoverEveryPairForAllFactorCountsUpToForty() {
        for (int factors = 2; factors <= 40; factors++) {
            List<boolean[]> matrix = BinaryCoveringArray.generateMatrix(factors, 2);
            assertAllPairsCovered(matrix, factors);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20})
    void shouldExhaustivelyCoverEveryTripleForStrength3(int factors) {
        List<boolean[]> matrix = BinaryCoveringArray.generateMatrix(factors, 3);

        if (factors >= 3) {
            assertAllTriplesCovered(matrix, factors);
        } else {
            assertAllPairsCovered(matrix, factors);
        }
        if (factors == 20) {
            Assertions.assertThat(matrix.size()).isBetween(11, 24);
        }
    }

    @Test
    void shouldBeDeterministicRegardlessOfInputSetIterationOrder() {
        List<String> baseFields = IntStream.rangeClosed(1, 20)
                .mapToObj("field_%02d"::formatted)
                .toList();
        List<String> shuffledFields = new ArrayList<>(baseFields);
        Collections.shuffle(shuffledFields, new Random(42L));

        Set<Set<String>> first = BinaryCoveringArray.generate(baseFields, 2);
        Set<Set<String>> second = BinaryCoveringArray.generate(shuffledFields, 2);

        Assertions.assertThat(first)
                .isEqualTo(second)
                .containsExactlyElementsOf(second);
    }

    @Test
    void shouldPruneNestedChildrenWhenParentIsRemoved() {
        Set<String> raw = Set.of("address", "address#street", "address#zip#code", "phone", "user#name");

        Set<String> pruned = BinaryCoveringArray.pruneNestedFields(raw);

        Assertions.assertThat(pruned)
                .containsExactlyInAnyOrder("address", "phone", "user#name")
                .doesNotContain("address#street", "address#zip#code");
    }

    @Test
    void shouldDeduplicateRowsAndPruneNestedFieldsInGeneratedSets() {
        List<String> fields = List.of("address", "address#street", "address#city", "phone", "email");

        Set<Set<String>> sets = BinaryCoveringArray.generate(fields, 2);

        Assertions.assertThat(sets).isNotEmpty();
        for (Set<String> subset : sets) {
            if (subset.contains("address")) {
                Assertions.assertThat(subset).doesNotContain("address#street", "address#city");
            }
        }
    }

    @Test
    void shouldCapFactorsAtTwoHundred() {
        List<String> fields = IntStream.rangeClosed(1, 250)
                .mapToObj("opt_%03d"::formatted)
                .toList();

        Set<Set<String>> sets = BinaryCoveringArray.generate(fields, 2);

        Assertions.assertThat(sets).hasSize(10);
        Set<String> allReferencedFields = sets.stream()
                .flatMap(Set::stream)
                .collect( java.util.stream.Collectors.toSet());
        Assertions.assertThat(allReferencedFields)
                .hasSizeLessThanOrEqualTo(BinaryCoveringArray.MAX_FACTORS)
                .doesNotContain("opt_201", "opt_250");
    }

    @Test
    void shouldCapFactorsAtFiftyForStrength3() {
        List<String> fields = IntStream.rangeClosed(1, 65)
                .mapToObj("opt_%02d"::formatted)
                .toList();

        Set<Set<String>> sets = BinaryCoveringArray.generate(fields);
        Set<Set<String>> strength3Sets = BinaryCoveringArray.generate(fields, 3);

        Assertions.assertThat(sets).hasSize(9);
        Set<String> referencedStrength3 = strength3Sets.stream()
                .flatMap(Set::stream)
                .collect(java.util.stream.Collectors.toSet());
        Assertions.assertThat(referencedStrength3)
                .hasSizeLessThanOrEqualTo(BinaryCoveringArray.MAX_STRENGTH_3_FACTORS)
                .doesNotContain("opt_51", "opt_65");
    }

    @Test
    void shouldReturnEmptySetForZeroOrOneOptionalField() {
        Assertions.assertThat(BinaryCoveringArray.generate(null, 2)).isEmpty();
        Assertions.assertThat(BinaryCoveringArray.generate(List.of(), 2)).isEmpty();
        Assertions.assertThat(BinaryCoveringArray.generate(List.of("onlyField"), 2)).isEmpty();
        Assertions.assertThat(BinaryCoveringArray.generateMatrix(0, 2)).isEmpty();
        Assertions.assertThat(BinaryCoveringArray.generateMatrix(1, 2)).isEmpty();
        Assertions.assertThat(BinaryCoveringArray.optimalStrength2TotalRows(1)).isEqualTo(1);
        Assertions.assertThat(BinaryCoveringArray.binomial(5, -1)).isZero();
        Assertions.assertThat(BinaryCoveringArray.binomial(5, 6)).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4, -1})
    void shouldRejectUnsupportedInteractionStrength(int invalidStrength) {
        List<String> fields = List.of("a", "b");
        Assertions.assertThatThrownBy(() -> BinaryCoveringArray.generate(fields, invalidStrength))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Interaction strength must be 2 or 3");
    }

    private static void assertAllPairsCovered(List<boolean[]> nonZeroRows, int factors) {
        for (int i = 0; i < factors; i++) {
            for (int j = i + 1; j < factors; j++) {
                int mask = 0b0001; // (0,0) is covered by the baseline all-present row 0
                for (boolean[] row : nonZeroRows) {
                    int bitI = row[i] ? 1 : 0;
                    int bitJ = row[j] ? 1 : 0;
                    int combo = (bitI << 1) | bitJ;
                    mask |= (1 << combo);
                }
                Assertions.assertThat(mask)
                        .as("Factors=%d pair (%d, %d) must cover all 4 combinations (0,0),(0,1),(1,0),(1,1)", factors, i, j)
                        .isEqualTo(0b1111);
            }
        }
    }

    private static void assertAllTriplesCovered(List<boolean[]> nonZeroRows, int factors) {
        for (int i = 0; i < factors; i++) {
            for (int j = i + 1; j < factors; j++) {
                for (int k = j + 1; k < factors; k++) {
                    int mask = 0b0000_0001; // (0,0,0) is covered by the baseline all-present row 0
                    for (boolean[] row : nonZeroRows) {
                        int bI = row[i] ? 1 : 0;
                        int bJ = row[j] ? 1 : 0;
                        int bK = row[k] ? 1 : 0;
                        int combo = (bI << 2) | (bJ << 1) | bK;
                        mask |= (1 << combo);
                    }
                    Assertions.assertThat(mask)
                            .as("Factors=%d triple (%d, %d, %d) must cover all 8 combinations", factors, i, j, k)
                            .isEqualTo(0b1111_1111);
                }
            }
        }
    }
}
