package com.endava.cats.io;

import com.google.gson.JsonElement;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class LongTypeSerializerTest {

    @Test
    void shouldSerializeLongAsString() {
        LongTypeSerializer serializer = new LongTypeSerializer();
        JsonElement element = serializer.serialize(10L, Long.class, null);
        Assertions.assertThat(element.getAsString()).isEqualTo("10");
        Assertions.assertThat(element.getAsLong()).isEqualTo(10L);
    }
}
