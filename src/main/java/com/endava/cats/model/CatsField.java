package com.endava.cats.model;

import io.swagger.v3.oas.models.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Builder
@ToString(of = {"name", "required"})
@EqualsAndHashCode(of = "name")
@Getter
public class CatsField {
    private final String name;
    private final boolean required;
    private final Schema schema;
}
