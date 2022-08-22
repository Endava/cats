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
import java.util.List;
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
//        options.setFlatten(true);

        OpenAPI openAPI = getOpenAPI(new OpenAPIV3Parser(), location, options);

        if (openAPI == null) {
            openAPI = getOpenAPI(new SwaggerConverter(), location, options);
        }
        return openAPI;
    }

    public static OpenAPI getOpenAPI(SwaggerParserExtension parserExtension, String location, ParseOptions options) throws IOException {
        if (location.startsWith("http")) {
            LOGGER.debug("Load remote contract {}", location);
            return parserExtension.readLocation(location, null, options).getOpenAPI();
        } else {
            LOGGER.debug("Load local contract {}", location);
            return parserExtension.readContents(Files.readString(Paths.get(location)), null, options).getOpenAPI();
        }
    }

    public static MediaType getMediaTypeFromContent(Content content, String contentType) {
        content.forEach((key, value) -> LOGGER.debug("key {} contentType {}", key, contentType));
        return content.entrySet().stream()
                .filter(contentEntry -> contentEntry.getKey().startsWith(contentType))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> content.get(contentType));
    }

    public static Map<String, Schema> getSchemas(OpenAPI openAPI, List<String> contentTypeList) {
        Map<String, Schema> schemas = Optional.ofNullable(openAPI.getComponents().getSchemas()).orElseGet(HashMap::new);

        for (String contentType : contentTypeList) {
            Optional.ofNullable(openAPI.getComponents().getRequestBodies()).orElseGet(Collections::emptyMap).forEach((key, value) -> addToSchemas(schemas, key, value.get$ref(), value.getContent(), contentType));

            Optional.ofNullable(openAPI.getComponents().getResponses()).orElseGet(Collections::emptyMap).forEach((key, value) -> addToSchemas(schemas, key, value.get$ref(), value.getContent(), contentType));
        }
        return schemas;
    }

    private static void addToSchemas(Map<String, Schema> schemas, String schemaName, String ref, Content content, String contentType) {
        Schema<?> schemaToAdd = new Schema();
        if (ref == null && hasContentType(content, List.of(contentType))) {
            LOGGER.debug("Getting schema {} for content-type {}", schemaName, contentType);
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
        } else if (content != null && schemas.get(schemaName) == null) {
            /*it means it wasn't already added with another content type*/
            LOGGER.warn("Content-Type not supported. Found: {} for {}", content.keySet(), schemaName);
        }
        schemas.put(schemaName, schemaToAdd);
    }

    public static boolean hasContentType(Content content, List<String> contentType) {
        return content != null && content.keySet().stream().anyMatch(contentKey -> contentType.stream().anyMatch(contentKey::startsWith));
    }
}
