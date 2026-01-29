package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OnlySpacesInHeadersFuzzerTest {
    private OnlySpacesInHeadersFuzzer onlySpacesInHeadersFuzzer;

    @BeforeEach
    void setup() {
        onlySpacesInHeadersFuzzer = new OnlySpacesInHeadersFuzzer(null);
    }

    @Test
    void shouldHaveReplaceFuzzingStrategy() {
        Assertions.assertThat(onlySpacesInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(onlySpacesInHeadersFuzzer.getFuzzerContext().getFuzzStrategy().getFirst().getData()).isEqualTo(" ");
    }

    @Test
    void shouldReturn4xxForRequiredAnd2xxForOptionalResponseCodes() {
        Assertions.assertThat(onlySpacesInHeadersFuzzer.getFuzzerContext().getExpectedHttpCodeForRequiredHeadersFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(onlySpacesInHeadersFuzzer.getFuzzerContext().getExpectedHttpForOptionalHeadersFuzzed().apply(CatsHeader.builder().build())).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
    }

    @Test
    void shouldReturn4xxAnd2xxForOptionalHeaders() {
        Parameter parameter = new Parameter();
        parameter.setSchema(new Schema().format("date"));
        CatsHeader header = CatsHeader.fromHeaderParameter(parameter);
        Assertions.assertThat(onlySpacesInHeadersFuzzer.getFuzzerContext().getExpectedHttpForOptionalHeadersFuzzed()
                .apply(header)).isEqualTo(ResponseCodeFamilyPredefined.FOURXX_TWOXX);
    }

    @Test
    void shouldHaveToString() {
        Assertions.assertThat(onlySpacesInHeadersFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldMatchResponseSchema() {
        Assertions.assertThat(onlySpacesInHeadersFuzzer.getFuzzerContext().isMatchResponseSchema()).isTrue();
    }

    @Test
    void shouldHaveTypeOfDataToSend() {
        Assertions.assertThat(onlySpacesInHeadersFuzzer.getFuzzerContext().getTypeOfDataSentToTheService()).isNotBlank();
    }
}
