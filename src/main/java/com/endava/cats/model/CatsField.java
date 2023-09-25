package com.endava.cats.model;

import io.swagger.v3.oas.models.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * An internal representation of a field from OpenAPI.
 */
@Builder
@ToString(of = {"name", "required"})
@EqualsAndHashCode(of = "name")
@Getter
public class CatsField {
    private final String name;
    private final boolean required;
    private final boolean readOnly;
    private final boolean writeOnly;
    private final Schema schema;
}
