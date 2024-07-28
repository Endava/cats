package com.endava.cats.util;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@QuarkusTest
class OffsetDatetimeTypeAdapterTest {

    private OffsetDatetimeTypeAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OffsetDatetimeTypeAdapter();
    }

    @Test
    void testWrite() throws IOException {
        OffsetDateTime dateTime = OffsetDateTime.of(2023, 7, 24, 12, 34, 56, 0, ZoneOffset.UTC);
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);

        adapter.write(jsonWriter, dateTime);

        Assertions.assertThat(stringWriter).hasToString("\"2023-07-24T12:34:56Z\"");
    }

    @Test
    void testRead() throws IOException {
        String json = "\"2023-07-24T12:34:56+01:00\"";
        JsonReader jsonReader = new JsonReader(new StringReader(json));

        OffsetDateTime result = adapter.read(jsonReader);

        OffsetDateTime expected = OffsetDateTime.of(2023, 7, 24, 12, 34, 56, 0, ZoneOffset.ofHours(1));
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @Test
    void testWriteThenRead() throws IOException {
        OffsetDateTime original = OffsetDateTime.of(2023, 7, 24, 12, 34, 56, 0, ZoneOffset.ofHours(-5));
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);

        adapter.write(jsonWriter, original);

        JsonReader jsonReader = new JsonReader(new StringReader(stringWriter.toString()));
        OffsetDateTime result = adapter.read(jsonReader);

        Assertions.assertThat(result).isEqualTo(original);
    }
}
