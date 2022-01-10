package com.endava.cats.io;

import com.endava.cats.model.util.LongTypeSerializer;
import com.google.gson.JsonElement;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LongTypeSerializerTest {

    @Test
    void shouldSerializeLongAsString() {
        LongTypeSerializer serializer = new LongTypeSerializer();
        JsonElement element = serializer.serialize(10L, Long.class, null);
        Assertions.assertThat(element.getAsString()).isEqualTo("10");
        Assertions.assertThat(element.getAsLong()).isEqualTo(10L);
    }
}
