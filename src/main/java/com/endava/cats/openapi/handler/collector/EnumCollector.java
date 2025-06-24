package com.endava.cats.openapi.handler.collector;

import com.endava.cats.openapi.handler.api.SchemaHandler;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.util.CatsModelUtils;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Singleton;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects every enum together with its fully qualified name.
 */
@Getter
@Singleton
public class EnumCollector implements SchemaHandler {
    private final Map<SchemaLocation, List<String>> enums = new LinkedHashMap<>();

    @Override
    public void handle(SchemaLocation schemaLocation, Schema<?> s) {
        if (CatsModelUtils.isEnumSchema(s)) {
            enums.put(schemaLocation, s.getEnum().stream().map(Object::toString).toList());
        }
    }
}
