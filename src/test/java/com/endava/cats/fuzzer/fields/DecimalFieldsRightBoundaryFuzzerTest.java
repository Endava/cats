package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.model.FuzzingData;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;

@QuarkusTest
class DecimalFieldsRightBoundaryFuzzerTest {

    private DecimalFieldsRightBoundaryFuzzer decimalFieldsRightBoundaryFuzzer;
    private FilesArguments filesArguments;

    @BeforeEach
    void setup() {
        filesArguments = Mockito.mock(FilesArguments.class);
        decimalFieldsRightBoundaryFuzzer = new DecimalFieldsRightBoundaryFuzzer(null, null, null, filesArguments);
    }

    @ParameterizedTest
    @CsvSource({"string", "integer", "date"})
    void shouldNotApplyToString(String format) {
        Assertions.assertThat(decimalFieldsRightBoundaryFuzzer.getSchemaTypesTheFuzzerWillApplyTo()
                        .stream().anyMatch(schema -> schema.equalsIgnoreCase(format)))
                .isFalse();
    }

    @Test
    void shouldHaveBoundaryDefined() {
        Assertions.assertThat(decimalFieldsRightBoundaryFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
    }

    @Test
    void shouldReturnNonNullDescription() {
        Assertions.assertThat(decimalFieldsRightBoundaryFuzzer.description()).isNotEmpty();
    }

    @Test
    void shouldReturnLongBoundaryValue() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(decimalFieldsRightBoundaryFuzzer.getBoundaryValue(nrSchema)).isInstanceOf(BigDecimal.class);
    }

    @ParameterizedTest
    @CsvSource({"field,field,false", "field,notRefData,true"})
    void shouldCheckSkip(String field, String refData, boolean expected) {
        Mockito.when(filesArguments.getRefData(Mockito.any())).thenReturn(Map.of(refData, "value"));
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(Map.of(field, new StringSchema()));
        boolean isPossible = decimalFieldsRightBoundaryFuzzer.isFuzzerWillingToFuzz(data, field);
        Assertions.assertThat(isPossible).isEqualTo(expected);
    }
}