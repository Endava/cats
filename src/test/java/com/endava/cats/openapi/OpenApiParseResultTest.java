package com.endava.cats.openapi;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.SpecVersion;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@QuarkusTest
class OpenApiParseResultTest {

    @ParameterizedTest
    @CsvSource({"V30,V30", "V31,V31"})
    void shouldProperlyParseVersion(SpecVersion specVersion, OpenApiParseResult.OpenApiVersion expected) {
        OpenApiParseResult.OpenApiVersion result = OpenApiParseResult.OpenApiVersion.fromSwaggerVersion(specVersion);
        Assertions.assertThat(result).isEqualTo(expected);
    }
}
