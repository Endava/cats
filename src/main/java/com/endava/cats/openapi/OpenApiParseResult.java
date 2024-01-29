package com.endava.cats.openapi;

import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.Getter;
import lombok.Setter;

/**
 * Extends base class to accommodate versioning information for swagger 2.0
 */
@Setter
@Getter
public class OpenApiParseResult {

    private OpenApiVersion version;
    private SwaggerParseResult swaggerParseResult;

    public enum OpenApiVersion {
        V30, V31, V20;

        public static OpenApiVersion fromSwaggerVersion(SpecVersion specVersion) {
            return switch (specVersion) {
                case V30 -> OpenApiVersion.V30;
                case V31 -> OpenApiVersion.V31;
            };
        }
    }
}
