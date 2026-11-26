package com.endava.cats.generator.format.impl;

import com.endava.cats.generator.format.api.DataFormat;
import com.endava.cats.generator.format.api.InvalidDataFormatGenerator;
import com.endava.cats.generator.format.api.OpenAPIFormat;
import com.endava.cats.generator.format.api.PropertySanitizer;
import com.endava.cats.generator.format.api.ValidDataFormatGenerator;
import com.endava.cats.util.CatsRandom;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

/**
 * Generates valid and invalid Universally Unique Lexicographically Sortable Identifiers (ULID).
 */
@Singleton
public class UlidGenerator implements ValidDataFormatGenerator, InvalidDataFormatGenerator, OpenAPIFormat {
    private static final char[] CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int TIMESTAMP_CHARS = 10;
    private static final int RANDOM_CHARS = 16;

    @Override
    public boolean appliesTo(String format, String propertyName) {
        return "ulid".equalsIgnoreCase(PropertySanitizer.sanitize(format))
                || PropertySanitizer.sanitize(propertyName).endsWith("ulid");
    }

    @Override
    public List<String> matchingFormats() {
        return List.of("ulid");
    }

    @Override
    public Object generate(Schema<?> schema) {
        char[] ulid = new char[TIMESTAMP_CHARS + RANDOM_CHARS];
        encodeTimestamp(Instant.now().toEpochMilli(), ulid);
        for (int i = TIMESTAMP_CHARS; i < ulid.length; i++) {
            ulid[i] = CROCKFORD_BASE32[CatsRandom.instance().nextInt(CROCKFORD_BASE32.length)];
        }
        return DataFormat.matchesPatternOrNull(schema, new String(ulid));
    }

    private static void encodeTimestamp(long epochMillis, char[] target) {
        long value = epochMillis;
        for (int i = TIMESTAMP_CHARS - 1; i >= 0; i--) {
            target[i] = CROCKFORD_BASE32[(int) (value & 31L)];
            value >>>= 5;
        }
    }

    @Override
    public String getAlmostValidValue() {
        return "01ARZ3NDEKTSV4RRFFQ69G5FAU";
    }

    @Override
    public String getTotallyWrongValue() {
        return "not-a-ulid-value";
    }
}
