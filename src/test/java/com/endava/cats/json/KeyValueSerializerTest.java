package com.endava.cats.json;

import com.endava.cats.json.KeyValueSerializer;
import com.endava.cats.model.KeyValuePair;
import com.google.gson.JsonElement;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

@QuarkusTest
class KeyValueSerializerTest {

    @Test
    void shouldMaskValue() {
        KeyValueSerializer keyValueSerializer = new KeyValueSerializer(Set.of("header1"));
        JsonElement element = keyValueSerializer.serialize(new KeyValuePair<>("header1", "myValue"), KeyValuePair.class, null);
        Assertions.assertThat(element.toString()).contains("$$header1").doesNotContain("myValue");
    }

    @Test
    void shouldNotMaskValue() {
        KeyValueSerializer keyValueSerializer = new KeyValueSerializer(Set.of("notMasked"));
        JsonElement element = keyValueSerializer.serialize(new KeyValuePair<>("header1", "myValue"), KeyValuePair.class, null);
        Assertions.assertThat(element.toString()).contains("myValue").doesNotContain("$$header1");
    }
}
