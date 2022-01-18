package com.endava.cats.util;

import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.converter.SwaggerConverter;
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension;
import io.swagger.v3.parser.core.models.ParseOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public abstract class OpenApiUtils {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(OpenApiUtils.class);

    private OpenApiUtils() {
        //ntd
    }

    public static OpenAPI readOpenApi(String location) throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);

        OpenAPI openAPI = getOpenAPI(new OpenAPIV3Parser(), location, options);

        if (openAPI == null) {
            openAPI = getOpenAPI(new SwaggerConverter(), location, options);
        }
        return openAPI;
    }

    public static OpenAPI getOpenAPI(SwaggerParserExtension parserExtension, String location, ParseOptions options) throws IOException {
        if (location.startsWith("http")) {
            return parserExtension.readLocation(location, null, options).getOpenAPI();
        } else {
            return parserExtension.readContents(Files.readString(Paths.get(location)), null, options).getOpenAPI();
        }
    }

    public static MediaType getMediaTypeFromContent(Content content, String contentType) {
        if (content.get(contentType) != null) {
            return content.get(contentType);
        }
        return content.get("application/json");
    }

    public static Map<String, Schema> getSchemas(OpenAPI openAPI, String contentType) {
        Map<String, Schema> schemas = Optional.ofNullable(openAPI.getComponents().getSchemas())
                .orElseGet(HashMap::new);

        Optional.ofNullable(openAPI.getComponents().getRequestBodies())
                .orElseGet(Collections::emptyMap)
                .forEach((key, value) -> addToSchemas(schemas, key, value.get$ref(), value.getContent(), contentType));

        Optional.ofNullable(openAPI.getComponents().getResponses())
                .orElseGet(Collections::emptyMap)
                .forEach((key, value) -> addToSchemas(schemas, key, value.get$ref(), value.getContent(), contentType));

        return schemas;
    }

    private static void addToSchemas(Map<String, Schema> schemas, String schemaName, String ref, Content content, String contentType) {
        Schema<?> schemaToAdd = new Schema();
        if (ref == null && hasContentType(content, contentType)) {
            Schema<?> refSchema = getMediaTypeFromContent(content, contentType).getSchema();

            if (refSchema instanceof ArraySchema) {
                ref = ((ArraySchema) refSchema).getItems().get$ref();
                refSchema.set$ref(ref);
                schemaToAdd = refSchema;
            } else if (refSchema.get$ref() != null) {
                ref = refSchema.get$ref();
                String schemaKey = ref.substring(ref.lastIndexOf('/') + 1);
                schemaToAdd = schemas.get(schemaKey);
            }
        } else if (content != null) {
            LOGGER.warn("CATS only supports application/json as content-type. Found: {} for {}", content.keySet(), schemaName);
        }
        schemas.put(schemaName, schemaToAdd);
    }

    public static boolean hasContentType(Content content, String contentType) {
        return content != null && content.get(contentType) != null;
    }
}
