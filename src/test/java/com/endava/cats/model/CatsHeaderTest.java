package com.endava.cats.model;

import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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
        CatsHeader header = CatsHeader.builder().value(StringUtils.repeat("a", 100)).build();

        Assertions.assertThat(header.getTruncatedValue()).isEqualTo("aaaaaaaaaaaaaaaaaaaa...[Total length:100]");
    }

}
