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
}