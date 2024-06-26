package com.endava.cats.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * This class is used to serialize and deserialize OffsetDateTime objects.
 */
public class OffsetDatetimeTypeAdapter extends TypeAdapter<OffsetDateTime> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public void write(JsonWriter out, OffsetDateTime value) throws IOException {
        out.value(value.format(FORMATTER));
    }

    @Override
    public OffsetDateTime read(JsonReader in) throws IOException {
        return OffsetDateTime.parse(in.nextString(), FORMATTER);
    }
}
