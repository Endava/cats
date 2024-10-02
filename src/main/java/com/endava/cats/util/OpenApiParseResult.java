package com.endava.cats.util;

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

    /**
     * Holds version of OpenAPI Specs.
     */
    public enum OpenApiVersion {
        /**
         * Used for OpenAPI 3.0.
         */
        V30,
        /**
         * Used for OpenAPI 3.1.
         */
        V31,
        /**
         * Used for older Swagger specs.
         */
        V20;

        /**
         * Converts a Swagger specification version to an equivalent OpenAPI version.
         *
         * @param specVersion the Swagger specification version to convert
         * @return the equivalent OpenAPI version
         */
        public static OpenApiVersion fromSwaggerVersion(SpecVersion specVersion) {
            return switch (specVersion) {
                case V30 -> OpenApiVersion.V30;
                case V31 -> OpenApiVersion.V31;
            };
        }
    }
}
