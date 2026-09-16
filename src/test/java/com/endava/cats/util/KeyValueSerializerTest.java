package com.endava.cats.util;

import com.endava.cats.args.ReportingArguments;
import com.google.gson.JsonElement;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
    void shouldAutomaticallyMaskSensitiveHeaders() {
        KeyValueSerializer serializer = new KeyValueSerializer(new ReportingArguments());

        JsonElement element = serializer.serialize(new KeyValuePair<>("Authorization", "Bearer secret"), KeyValuePair.class, null);

        Assertions.assertThat(element.toString()).contains("$$Authorization").doesNotContain("Bearer secret");
    }

    @Test
    void shouldShowAutomaticallyDetectedSecretsWhenExplicitlyRequested() {
        ReportingArguments arguments = new ReportingArguments();
        ReflectionTestUtils.setField(arguments, "showSecrets", true);
        KeyValueSerializer serializer = new KeyValueSerializer(arguments);

        JsonElement element = serializer.serialize(new KeyValuePair<>("Authorization", "Bearer secret"), KeyValuePair.class, null);

        Assertions.assertThat(element.toString()).contains("Bearer secret").doesNotContain("$$Authorization");
    }

    @Test
    void shouldNotMaskValue() {
        KeyValueSerializer keyValueSerializer = new KeyValueSerializer(Set.of("notMasked"));
        JsonElement element = keyValueSerializer.serialize(new KeyValuePair<>("header1", "myValue"), KeyValuePair.class, null);
        Assertions.assertThat(element.toString()).contains("myValue").doesNotContain("$$header1");
    }
}
