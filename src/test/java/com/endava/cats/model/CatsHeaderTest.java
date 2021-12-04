package com.endava.cats.model;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@QuarkusTest
class CatsHeaderTest {

    @Test
    void givenAUUIDParameter_whenCreatingANewCatsHeaderInstance_thenTheCatsHeaderInstanceIsProperlyCreatedAccordingToTheParameterData() {
        Parameter parameter = new Parameter();
        StringSchema schema = new StringSchema();
        schema.setFormat("uuid");
        parameter.setName("header");
        parameter.setSchema(schema);

        CatsHeader header = CatsHeader.fromHeaderParameter(parameter);

        Assertions.assertThat(header.getName()).isEqualTo("header");
        Assertions.assertThat(UUID.fromString(header.getValue())).isNotNull();
    }

    @Test
    void givenADateTimeParameter_whenCreatingANewCatsHeaderInstance_thenTheCatsHeaderInstanceIsProperlyCreatedAccordingToTheParameterData() {
        Parameter parameter = new Parameter();
        StringSchema schema = new StringSchema();
        schema.setFormat("date-time");
        parameter.setName("header");
        parameter.setSchema(schema);

        CatsHeader header = CatsHeader.fromHeaderParameter(parameter);

        Assertions.assertThat(header.getName()).isEqualTo("header");
        Assertions.assertThat(DateTime.parse(header.getValue())).isNotNull();
    }

    @Test
    void givenAStringParameter_whenCreatingANewCatsHeaderInstance_thenTheCatsHeaderInstanceIsProperlyCreatedAccordingToTheParameterData() {
        Parameter parameter = new Parameter();
        StringSchema schema = new StringSchema();
        schema.setFormat("string");
        parameter.setName("header");
        parameter.setSchema(schema);

        CatsHeader header = CatsHeader.fromHeaderParameter(parameter);

        Assertions.assertThat(header.getName()).isEqualTo("header");
        Assertions.assertThat(header.getValue()).isNotNull();
    }

    @Test
    void givenAStringParameterWithPattern_whenCreatingANewCatsHeaderInstance_thenTheCatsHeaderInstanceIsProperlyCreatedAccordingToTheParameterData() {
        Parameter parameter = new Parameter();
        ObjectSchema schema = new ObjectSchema();
        schema.setPattern("[A-Z]");
        parameter.setName("header");
        parameter.setSchema(schema);

        CatsHeader header = CatsHeader.fromHeaderParameter(parameter);

        Assertions.assertThat(header.getName()).isEqualTo("header");
        Assertions.assertThat(header.getValue()).isNotNull();
    }

    @Test
    void givenAStringParameterWithExample_whenCreatingANewCatsHeaderInstance_thenTheCatsHeaderInstanceIsProperlyCreatedAccordingToTheParameterData() {
        Parameter parameter = new Parameter();
        StringSchema schema = new StringSchema();
        schema.setExample("headerValue");
        parameter.setName("header");
        parameter.setSchema(schema);

        CatsHeader header = CatsHeader.fromHeaderParameter(parameter);

        Assertions.assertThat(header.getName()).isEqualTo("header");
        Assertions.assertThat(header.getValue()).isEqualTo("headerValue");
    }

    @Test
    void givenASimpleParameter_whenCreatingANewCatsHeaderInstance_thenTheCatsHeaderInstanceIsProperlyCreatedAccordingToTheParameterData() {
        Parameter parameter = new Parameter();
        ObjectSchema schema = new ObjectSchema();
        parameter.setName("header");
        parameter.setSchema(schema);

        CatsHeader header = CatsHeader.fromHeaderParameter(parameter);

        Assertions.assertThat(header.getName()).isEqualTo("header");
        Assertions.assertThat(header.getValue()).isEqualTo("header");
    }

    @Test
    void shouldMakeCatsHeaderRequired() {
        Parameter parameter = new Parameter();
        parameter.setRequired(true);
        parameter.setSchema(new NumberSchema());
        CatsHeader header = CatsHeader.fromHeaderParameter(parameter);

        Assertions.assertThat(header.isRequired()).isTrue();
    }

    @Test
    void shouldTruncateValue() {
        CatsHeader header = CatsHeader.builder().value(StringUtils.repeat("a", 51)).build();
        Assertions.assertThat(header.getTruncatedValue()).isEqualTo("aaaaaaaaaaaaaaaaaaaa...[Total length:51]");
    }

    @Test
    void shouldNotTruncateValue() {
        CatsHeader header = CatsHeader.builder().value(StringUtils.repeat("a", 50)).build();
        Assertions.assertThat(header.getTruncatedValue()).isEqualTo(StringUtils.repeat("a", 50));
    }

    @Test
    void shouldReturnTruncatedValueAsNull() {
        CatsHeader header = CatsHeader.builder().value(null).build();
        Assertions.assertThat(header.getTruncatedValue()).isNull();
    }

    @Test
    void shouldGetValueMinLengthAsHalfOfMaxLengthWhenMinLengthZero() {
        Parameter parameter = new Parameter();
        StringSchema schema = new StringSchema();
        schema.setMinLength(0);
        schema.setMaxLength(10);
        parameter.setName("header");
        parameter.setSchema(schema);

        CatsHeader header = CatsHeader.fromHeaderParameter(parameter);
        Assertions.assertThat(header.getValue()).hasSizeLessThanOrEqualTo(10).hasSizeGreaterThanOrEqualTo(10 / 2);
    }

    @Test
    void shouldGenerateValueBasedOnPattern() {
        Parameter parameter = new Parameter();
        Schema schema = new Schema();
        schema.setPattern("[A-Z]+");
        parameter.setName("header");
        parameter.setSchema(schema);
        CatsHeader header = CatsHeader.fromHeaderParameter(parameter);

        Assertions.assertThat(header.getValue()).matches("[A-Z]+").hasSizeGreaterThan(1);
    }

    @Test
    void shouldGetNameAndValue() {
        Parameter parameter = new Parameter();
        StringSchema schema = new StringSchema();
        schema.setMaxLength(20);
        parameter.setName("header");
        parameter.setSchema(schema);
        CatsHeader header = CatsHeader.fromHeaderParameter(parameter);

        Assertions.assertThat(header.nameAndValue()).contains("header", "{", "}", "=");
        Assertions.assertThat(header.toString()).contains("header", "required", "{", "}", "=");
    }
}
