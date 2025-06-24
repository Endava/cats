package com.endava.cats.openapi.handler.collector;

import com.endava.cats.openapi.handler.api.SchemaLocation;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class StringSchemaCollectorTest {

    private StringSchemaCollector collector;
    private final SchemaLocation locA = new SchemaLocation("A", "post", "/path/A");
    private final SchemaLocation locB = new SchemaLocation("B", "post", "/path/B");

    @BeforeEach
    void setUp() {
        collector = new StringSchemaCollector();
    }

    @Test
    void shouldStartWithEmptyMap() {
        assertThat(collector.getStringSchemas())
                .isNotNull()
                .isEmpty();
    }

    @Test
    void shouldCollectStringSchema() {
        Schema<?> stringSchema = new StringSchema();

        collector.handle(locA, stringSchema);

        Map<SchemaLocation, Schema<?>> result = collector.getStringSchemas();
        assertThat(result)
                .hasSize(1)
                .containsEntry(locA, stringSchema);
    }

    @Test
    void shouldIgnoreNonStringSchema() {
        Schema<?> intSchema = new Schema<Integer>().type("integer");

        collector.handle(locA, intSchema);

        assertThat(collector.getStringSchemas())
                .isEmpty();
    }

    @Test
    void shouldAccumulateMultipleStringSchemas() {
        Schema<?> s1 = new StringSchema();
        Schema<?> s2 = new StringSchema();

        collector.handle(locA, s1);
        collector.handle(locB, s2);

        assertThat(collector.getStringSchemas().keySet())
                .containsExactly(locA, locB);
        assertThat(collector.getStringSchemas())
                .containsEntry(locA, s1)
                .containsEntry(locB, s2);
    }

    @Test
    void shouldIgnoreNullSchema() {
        collector.handle(locA, null);

        assertThat(collector.getStringSchemas())
                .isEmpty();
    }

    @Test
    void shouldIgnoreUnsupportedSchemaType() {
        Schema<?> unsupportedSchema = new Schema<>().type("unsupported");

        collector.handle(locA, unsupportedSchema);

        assertThat(collector.getStringSchemas())
                .isEmpty();
    }

    @Test
    void shouldHandleDuplicateSchemaLocations() {
        Schema<?> stringSchema1 = new StringSchema();
        Schema<?> stringSchema2 = new StringSchema();

        collector.handle(locA, stringSchema1);
        collector.handle(locA, stringSchema2);

        assertThat(collector.getStringSchemas())
                .hasSize(1)
                .containsEntry(locA, stringSchema2);
    }

    @Test
    void shouldIgnoreDateSchema() {
        Schema<?> dateSchema = new Schema<>().type("string").format("date");

        collector.handle(locA, dateSchema);

        assertThat(collector.getStringSchemas())
                .isEmpty();
    }

    @Test
    void shouldIgnoreDateTimeSchema() {
        Schema<?> dateTimeSchema = new Schema<>().type("string").format("date-time");

        collector.handle(locA, dateTimeSchema);

        assertThat(collector.getStringSchemas())
                .isEmpty();
    }

    @Test
    void shouldIgnoreUriSchema() {
        Schema<?> uriSchema = new Schema<>().type("string").format("uri");

        collector.handle(locA, uriSchema);

        assertThat(collector.getStringSchemas())
                .isEmpty();
    }

    @Test
    void shouldIgnoreUUIDSchema() {
        Schema<?> uuidSchema = new Schema<>().type("string").format("uuid");

        collector.handle(locA, uuidSchema);

        assertThat(collector.getStringSchemas())
                .isEmpty();
    }

    @Test
    void shouldIgnoreDecimalSchema() {
        Schema<?> decimalSchema = new Schema<>().type("number").format("decimal");

        collector.handle(locA, decimalSchema);

        assertThat(collector.getStringSchemas())
                .isEmpty();
    }

    @Test
    void shouldCollectValidStringSchema() {
        Schema<?> validStringSchema = new Schema<>().type("string");

        collector.handle(locA, validStringSchema);

        assertThat(collector.getStringSchemas())
                .hasSize(1)
                .containsEntry(locA, validStringSchema);
    }
}