package com.endava.cats.openapi.handler.collector;

import com.endava.cats.openapi.handler.api.SchemaHandler;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A collector that processes string schemas during a deep traversal of the OpenAPI document.
 */
@Singleton
@Getter
public class StringSchemaCollector implements SchemaHandler {
    private Map<SchemaLocation, Schema<?>> stringSchemas = new LinkedHashMap<>();

    @Override
    public void handle(SchemaLocation schemaLocation, Schema<?> schema) {
        if (CatsModelUtils.isStringSchema(schema)) {
            stringSchemas.put(schemaLocation, schema);
        }
    }
}
