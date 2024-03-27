package com.endava.cats.generator.format.api;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

@QuarkusTest
class ValidDataFormatTest {
    @Inject
    ValidDataFormat validDataFormat;


    @ParameterizedTest
    @CsvSource({"city,true", "CITY,true", "cityName,true", "city_name,true", "other#city,true",
            "iban,true", "IBAN,true", "other#iban,true",
            "bic,true", "BIC,true", "swift,true", "bic_code,true", "other#swift_code,true", "other#bank_identifier_code,true", "other#bank_identifier,true", "other#bank_code,true"
    })
    void shouldReturnOneGeneratorWithProperty(String propertyName) {
        List<ValidDataFormatGenerator> validDataFormatList = validDataFormat.getGenerators(new Schema<>(), propertyName);
        Assertions.assertThat(validDataFormatList).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"city",
            "iban",
            "swift", "bic"})
    void shouldReturnOneGeneratorWithFormat(String format) {
        Schema<?> schema = new Schema<>();
        schema.setFormat(format);
        List<ValidDataFormatGenerator> validDataFormatList = validDataFormat.getGenerators(schema, "");
        Assertions.assertThat(validDataFormatList).hasSize(1);
    }
}
