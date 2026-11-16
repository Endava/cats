package com.endava.cats.model;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@QuarkusTest
class ExpectedResponseTest {
    @ParameterizedTest
    @CsvSource(value = {"405;405", "304;304", "412;412", "[2XX, 4XX];2XX|4XX", "2XX|4XX;2XX|4XX"}, delimiter = ';')
    void shouldReadDedicatedCodeDeclarations(String declaration, String codes) {
        Assertions.assertThat(ExpectedResponse.fromCodeDeclaration(declaration).codes()).containsExactly(codes.split("\\|"));
    }

    @ParameterizedTest
    @CsvSource({"'Should return 405'", "'a response that does not match'", "'[405'", "'405]'", "'405 or 422'"})
    void shouldNotTreatArbitraryDescriptionsAsDedicatedCodeDeclarations(String declaration) {
        Assertions.assertThat(ExpectedResponse.fromCodeDeclaration(declaration).outcome()).isEqualTo(ExpectedResponse.Outcome.UNKNOWN);
    }
    static Stream<Arguments> expectations() {
        return Stream.of(
                Arguments.of(List.of("405"), ExpectedResponse.Outcome.REJECTION),
                Arguments.of(List.of("400", "422"), ExpectedResponse.Outcome.REJECTION),
                Arguments.of(List.of("2XX"), ExpectedResponse.Outcome.SUCCESS),
                Arguments.of(List.of("200", "201", "202", "204"), ExpectedResponse.Outcome.SUCCESS),
                Arguments.of(List.of("2XX", "4XX"), ExpectedResponse.Outcome.OTHER),
                Arguments.of(List.of("302"), ExpectedResponse.Outcome.OTHER),
                Arguments.of(List.of("5XX"), ExpectedResponse.Outcome.OTHER),
                Arguments.of(List.of("1XX", "2XX", "3XX", "4XX", "5XX"), ExpectedResponse.Outcome.ANY),
                Arguments.of(List.of(), ExpectedResponse.Outcome.UNKNOWN),
                Arguments.of(null, ExpectedResponse.Outcome.UNKNOWN),
                Arguments.of(List.of("999"), ExpectedResponse.Outcome.UNKNOWN));
    }

    @ParameterizedTest
    @MethodSource("expectations")
    void shouldDescribeTheDeclaredHttpAlternativesWithoutBroadeningThem(List<String> codes, ExpectedResponse.Outcome outcome) {
        ExpectedResponse response = new ExpectedResponse(codes);

        Assertions.assertThat(response.outcome()).isEqualTo(outcome);
        Assertions.assertThat(response.codes()).isEqualTo(codes == null ? List.of() : codes);
    }

    @Test
    void shouldTakeAnImmutableSnapshotOfTheDeclaredAlternatives() {
        List<String> codes = new ArrayList<>(List.of("400", "422", "400"));
        ExpectedResponse response = new ExpectedResponse(codes);
        codes.clear();

        Assertions.assertThat(response.codes()).containsExactly("400", "422");
        Assertions.assertThatThrownBy(() -> response.codes().add("404")).isInstanceOf(UnsupportedOperationException.class);
    }
}
