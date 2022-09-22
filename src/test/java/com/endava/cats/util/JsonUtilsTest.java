package com.endava.cats.util;

import com.endava.cats.model.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class JsonUtilsTest {

    @Test
    void givenAPayloadWithPrimitiveAndNonPrimitiveFields_whenCheckingIfPropertiesArePrimitive_thenTheCheckIsProperlyPerformed() {
        String payload = "{\"field\":\"value\", \"anotherField\":{\"subfield\": \"otherValue\"}}";

        Assertions.assertThat(JsonUtils.isPrimitive(payload, "field")).isTrue();
        Assertions.assertThat(JsonUtils.isPrimitive(payload, "anotherField")).isFalse();
        Assertions.assertThat(JsonUtils.isPrimitive(payload, "anotherField#subfield")).isTrue();
    }

    @Test
    void givenAnInvalidJson_whenCallingIsValidJson_thenTheMethodReturnsFalse() {
        String payload = "\"field\":\"a";

        Assertions.assertThat(JsonUtils.isValidJson(payload)).isFalse();
    }

    @Test
    void givenAValidJson_whenCallingIsValidJson_thenTheMethodReturnsTrue() {
        String payload = "{\"field\":\"value\", \"anotherField\":{\"subfield\": \"otherValue\"}}";

        Assertions.assertThat(JsonUtils.isValidJson(payload)).isTrue();
    }

    @Test
    void shouldReturnNotValidJson() {
        boolean result = JsonUtils.isValidJson("{no");

        Assertions.assertThat(result).isFalse();
    }

    @Test
    void shouldDeleteNode() {
        String payload = "{\"field\": 2}";
        String result = JsonUtils.deleteNode(payload, "$#field");

        Assertions.assertThat(result).isEqualTo("{}");
    }

    @Test
    void shouldReturnSamePayloadWhenFieldNotFound() {
        String result = JsonUtils.deleteNode(null, "$#field");

        Assertions.assertThat(result).isNull();
    }

    @Test
    void shouldBePrimitive() {
        boolean result = JsonUtils.isPrimitive("{\"test\":3}", "test");

        Assertions.assertThat(result).isTrue();
    }

    @Test
    void shouldBePrimitiveWithArray() {
        boolean result = JsonUtils.isPrimitive("[{\"test\":3}]", "test");

        Assertions.assertThat(result).isTrue();
    }

    @Test
    void shouldNotBePrimitive() {
        boolean result = JsonUtils.isPrimitive("{\"test\":{\"inner\": 4}}", "test");

        Assertions.assertThat(result).isFalse();
    }

    @Test
    void shouldNotBePrimitiveWithArray() {
        boolean result = JsonUtils.isPrimitive("[{\"test\":{\"inner\": 4}}]", "test");

        Assertions.assertThat(result).isFalse();
    }

    @Test
    void shouldBeObject() {
        boolean result = JsonUtils.isObject("[{\"test\":{\"inner\": 4}}]", "test");

        Assertions.assertThat(result).isTrue();
    }

    @Test
    void shouldNotBeObjectWhenInvalidPath() {
        boolean result = JsonUtils.isObject("[{\"test\":{\"inner\": 4}}]", "test_bad");

        Assertions.assertThat(result).isFalse();
    }
}
