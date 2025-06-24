package com.endava.cats.openapi.handler.collector;

import com.endava.cats.openapi.handler.api.SchemaLocation;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class EnumCollectorTest {

    private EnumCollector enumCollector;

    @BeforeEach
    void setup() {
        enumCollector = new EnumCollector();
    }

    @Test
    void shouldAddEnumWhenPresent() {
        Schema<Object> schema = new Schema<>();
        schema.setEnum(List.of("VALUE_ONE", "VALUE_TWO"));
        SchemaLocation location = new SchemaLocation("components", "schemas", "MyEnum");

        enumCollector.handle(location, schema);

        Map<SchemaLocation, List<String>> enums = enumCollector.getEnums();
        assertThat(enums).hasSize(1).containsKey(location);
        assertThat(enums.get(location)).containsExactly("VALUE_ONE", "VALUE_TWO");
    }

    @Test
    void shouldNotAddWhenEnumIsNull() {
        Schema<Object> schema = new Schema<>();
        schema.setEnum(null);
        SchemaLocation location = new SchemaLocation("components", "schemas", "NullEnum");

        enumCollector.handle(location, schema);

        assertThat(enumCollector.getEnums()).isEmpty();
    }

    @Test
    void shouldNotAddWhenEnumIsEmpty() {
        Schema<Object> schema = new Schema<>();
        schema.setEnum(List.of());
        SchemaLocation location = new SchemaLocation("components", "schemas", "EmptyEnum");

        enumCollector.handle(location, schema);

        assertThat(enumCollector.getEnums()).isEmpty();
    }
}
