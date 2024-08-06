package com.endava.cats.openapi;

import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.util.CatsModelUtils;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.converter.SwaggerConverter;
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.util.DeserializationUtils;
import org.openapitools.codegen.utils.ModelUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Holds operations for interacting with OpenAPI objects.
 */
public abstract class OpenApiUtils {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(OpenApiUtils.class);
    private static final Pattern VERSION_PATH = Pattern.compile("(?:v|version)(\\d+(?:\\.\\d+){0,2})");

    private static final Pattern VERSION_HEADER = Pattern.compile("(v\\d+)");

    private static final Set<String> PAGINATION = Set.of("limit", "offset", "page", "size", "pagesize", "pagenumber", "sort", "perpage", "datefrom", "dateto", "datestart", "dateend");

    private static final Set<String> MONITORING_MATCHES = Set.of("[version\\d*\\.?|v\\d+\\.?]/status", "/status", ".*/health", ".*/monitoring/status", ".*/ping", ".*/healthz", ".*/monitor", ".*/monitoring");

    static {
        DeserializationUtils.getOptions().setMaxYamlCodePoints(99999999);
    }

    private OpenApiUtils() {
        //ntd
    }

    /**
     * Reads an OpenAPI spec file. Can parse both 2.x as well as 3.x specs.
     *
     * @param location the location of the OpenAPI spec
     * @return an OpenAPI object with all details from the OpenAPI spec
     * @throws IOException if there is a problem accessing the spec file
     */
    public static OpenAPI readOpenApi(String location) throws IOException {
        return readAsParseResult(location).getSwaggerParseResult().getOpenAPI();
    }

    /**
     * Reads an OpenAPI spec file. Can parse both 2.x and 3.x specs.
     *
     * @param location the location of the OpenAPI spec
     * @return an SwaggerParseResult having both OpenAPI spec details and parse result error messages
     * @throws IOException if there is a problem accessing the spec file
     */
    public static OpenApiParseResult readAsParseResult(String location) throws IOException {
        OpenApiParseResult openApiParseResult = new OpenApiParseResult();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);

        SwaggerParseResult parseResult = getOpenAPI(new OpenAPIV3Parser(), location, options);
        boolean isOpenAPIValidSpec = parseResult.getOpenAPI() != null;

        if (isOpenAPIValidSpec) {
            openApiParseResult.setVersion(OpenApiParseResult.OpenApiVersion.fromSwaggerVersion(parseResult.getOpenAPI().getSpecVersion()));
        } else {
            //it might be a Swagger 2.0 spec
            parseResult = getOpenAPI(new SwaggerConverter(), location, options);
            openApiParseResult.setVersion(OpenApiParseResult.OpenApiVersion.V20);
        }
        LOGGER.debug("Messages {}", parseResult.getMessages());
        openApiParseResult.setSwaggerParseResult(parseResult);
        return openApiParseResult;
    }

    private static SwaggerParseResult getOpenAPI(SwaggerParserExtension parserExtension, String location, ParseOptions options) throws IOException {
        if (location.startsWith("http")) {
            LOGGER.debug("Load remote contract {}", location);
            return parserExtension.readLocation(location, null, options);
        } else {
            LOGGER.debug("Load local contract {}", location);
            return parserExtension.readContents(Files.readString(Paths.get(location)), null, options);
        }
    }

    /**
     * Retrieves the media type from the given content based on the specified content type.
     * The content is a map where keys are content types, and values are associated media types.
     *
     * @param content     The content map containing content types and associated media types.
     * @param contentType The content type for which the media type is to be retrieved.
     * @return The media type associated with the specified content type, or the default media type if not found.
     */
    public static MediaType getMediaTypeFromContent(Content content, String contentType) {
        content.forEach((key, value) -> LOGGER.debug("key {} contentType {}", key, contentType));
        return content.entrySet().stream()
                .filter(contentEntry -> contentEntry.getKey().matches(contentType))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> content.get(contentType));
    }

    /**
     * Retrieves and aggregates schemas from the specified OpenAPI definition based on the provided content types.
     * The method considers schemas from request bodies and responses in the OpenAPI definition.
     *
     * @param openAPI         The OpenAPI definition containing components such as schemas, request bodies, and responses.
     * @param contentTypeList A list of content types for which schemas should be retrieved.
     * @return A map of schemas, where keys are schema names and values are corresponding Schema objects.
     */
    public static Map<String, Schema> getSchemas(OpenAPI openAPI, List<String> contentTypeList) {
        Map<String, Schema> schemas = Optional.ofNullable(openAPI.getComponents().getSchemas()).orElseGet(HashMap::new);

        for (String contentType : contentTypeList) {
            Optional.ofNullable(openAPI.getComponents().getRequestBodies())
                    .orElseGet(Collections::emptyMap)
                    .forEach((key, value) -> addToSchemas(schemas, key, value.get$ref(), value.getContent(), contentType));
        }
        Optional.ofNullable(openAPI.getComponents().getResponses())
                .orElseGet(Collections::emptyMap)
                .forEach((key, value) -> addToSchemas(schemas, key, value.get$ref(), value.getContent(), ProcessingArguments.JSON_WILDCARD));

        return schemas;
    }

    /**
     * Retrieves the examples from the specified OpenAPI definition.
     *
     * @param openAPI The OpenAPI definition containing components such as examples.
     * @return A map of examples, where keys are example names and values are corresponding Example objects.
     */
    public static Map<String, Example> getExamples(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getExamples()).orElse(Collections.emptyMap());
    }


    private static void addToSchemas(Map<String, Schema> schemas, String schemaName, String ref, Content content, String contentType) {
        Schema<?> schemaToAdd = new Schema();
        if (ref == null && hasContentType(content, List.of(contentType))) {
            LOGGER.debug("Getting schema {} for content-type {}", schemaName, contentType);
            Schema<?> refSchema = getMediaTypeFromContent(content, contentType).getSchema();

            if (refSchema != null) {
                if (refSchema.get$ref() != null) {
                    ref = refSchema.get$ref();
                    String schemaKey = ref.substring(ref.lastIndexOf('/') + 1);
                    schemaToAdd = schemas.get(schemaKey);
                } else if (CatsModelUtils.isArraySchema(refSchema)) {
                    ref = refSchema.getItems().get$ref();
                    refSchema.set$ref(ref);
                    schemaToAdd = refSchema;
                }
            }
        } else if (content != null && !content.isEmpty() && schemas.get(schemaName) == null) {
            /*it means it wasn't already added with another content type*/
            LOGGER.warn("Content-Type not supported. Found: {} for {}", content.keySet(), schemaName);
        }
        schemas.putIfAbsent(schemaName, schemaToAdd);
    }

    /**
     * Gets ref definition name based on full reference.
     *
     * @param ref the full reference
     * @return the simple reference name
     */
    public static String getSimpleRef(String ref) {
        if (!ref.contains("/") || ref.startsWith("#/paths")) {
            return ref;
        }
        return ModelUtils.getSimpleRef(ref);
    }

    /**
     * Checks if the provided Content object contains any of the specified content types.
     *
     * @param content     The Content object to check for content types.
     * @param contentType A list of content types to search for in the Content object.
     * @return true if any of the specified content types are found, false otherwise.
     */
    public static boolean hasContentType(Content content, List<String> contentType) {
        return content != null && content.keySet().stream()
                .anyMatch(contentKey -> contentType.stream()
                        .anyMatch(contentItem -> contentKey.matches(contentItem) || contentKey.equalsIgnoreCase(contentItem))
                );
    }

    /**
     * Extracts path elements from the given path, excluding version path elements.
     *
     * @param path The path from which to extract elements.
     * @return An array of path elements excluding version path elements.
     */
    public static String[] getPathElements(String path) {
        return Arrays.stream(path.substring(1).split("/"))
                .filter(pathElement -> !VERSION_PATH.matcher(pathElement).matches())
                .toArray(String[]::new);
    }

    /**
     * Returns all variables from the given path. An element is considered a variable
     * if it's enclosed by {}.
     *
     * @param path the given path
     * @return a set of path variables in the form: {var1}, {var2}, etc.
     */
    public static Set<String> getPathVariables(String path) {
        Set<String> parameters = new HashSet<>();
        Pattern pattern = Pattern.compile("\\{(.*?)}");
        Matcher matcher = pattern.matcher(path);

        while (matcher.find()) {
            parameters.add(matcher.group(1));
        }

        return parameters;
    }

    /**
     * Checks if the given path element is not a path variable.
     *
     * @param pathElement The path element to check.
     * @return true if the path element is not a path variable, false otherwise.
     */
    public static boolean isNotAPathVariable(String pathElement) {
        return !isAPathVariable(pathElement);
    }

    /**
     * Checks if the given path element represents a path variable.
     *
     * @param pathElement The path element to check.
     * @return true if the path element represents a path variable, false otherwise.
     */
    public static boolean isAPathVariable(String pathElement) {
        return pathElement.startsWith("{");
    }

    /**
     * Retrieves the total number of operations (HTTP methods) defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing path information.
     * @return The total number of operations across all paths.
     */
    public static int getNumberOfOperations(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getPaths()).orElse(new io.swagger.v3.oas.models.Paths())
                .values()
                .stream()
                .mapToInt(OpenApiUtils::countPathOperations)
                .sum();
    }

    /**
     * Retrieves a set of names representing the request bodies defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing components such as request bodies.
     * @return A set of names representing the request bodies.
     */
    public static Set<String> getRequestBodies(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getRequestBodies())
                .orElse(Collections.emptyMap()).keySet();
    }

    /**
     * Retrieves a set of names representing the schemas defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing components such as schemas.
     * @return A set of names representing the schemas.
     */
    public static Set<String> getSchemas(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getSchemas())
                .orElse(new HashMap<>()).keySet();
    }

    /**
     * Retrieves a set of names representing the responses defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing components such as responses.
     * @return A set of names representing the responses.
     */
    public static Set<String> getResponses(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getResponses())
                .orElse(Collections.emptyMap()).keySet();
    }

    /**
     * Retrieves a set of names representing the security schemes defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing components such as security schemes.
     * @return A set of names representing the security schemes.
     */
    public static Set<String> getSecuritySchemes(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getSecuritySchemes())
                .orElse(Collections.emptyMap()).keySet();
    }

    /**
     * Retrieves a set of names representing the parameters defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing components such as parameters.
     * @return A set of names representing the parameters.
     */
    public static Set<String> getParameters(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getParameters())
                .orElse(Collections.emptyMap()).keySet();
    }

    /**
     * Retrieves a set of header names defined in the components section of the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing components information.
     * @return A set of header names defined in the components section.
     */
    public static Set<String> getHeaders(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getHeaders())
                .orElse(Collections.emptyMap()).keySet();
    }

    /**
     * Retrieves a set of formatted server information from the OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing server information.
     * @return A set of formatted server information, including URL and description.
     */
    public static Set<String> getServers(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getServers()).orElse(Collections.emptyList()).stream()
                .map(server -> server.getUrl() + " - " + Optional.ofNullable(server.getDescription()).orElse("missing description"))
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves a set of deprecated headers from the operations defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing paths and operations.
     * @return A set of deprecated headers across all operations.
     */
    public static Set<String> getDeprecatedHeaders(OpenAPI openAPI) {
        return openAPI.getPaths().values().stream()
                .flatMap(pathItem -> deprecatedHeadersFromOperations(pathItem.getGet(), pathItem.getPut(), pathItem.getPost(),
                        pathItem.getHead(), pathItem.getPatch(), pathItem.getDelete(),
                        pathItem.getOptions(), pathItem.getTrace()).stream())
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves the information (metadata) from the given OpenAPI specification.
     * If information is not present, a default information object is returned.
     *
     * @param openAPI The OpenAPI specification containing information about the API.
     * @return The information object from the OpenAPI specification or a default information object.
     */
    public static Info getInfo(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getInfo()).orElse(defaultInfo());
    }

    /**
     * Retrieves the documentation URL from the external documentation section of the given OpenAPI specification.
     * If the external documentation section is not present, a default documentation URL is returned.
     *
     * @param openAPI The OpenAPI specification containing external documentation information.
     * @return The documentation URL from the OpenAPI specification or a default documentation URL.
     */
    public static String getDocumentationUrl(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getExternalDocs()).orElse(OpenApiUtils.defaultDocumentation()).getUrl();
    }

    /**
     * Retrieves a set of extension keys from the extensions section of the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing extension information.
     * @return A set of extension keys from the OpenAPI specification.
     */
    public static Set<String> getExtensions(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getExtensions()).orElse(Collections.emptyMap()).keySet();
    }

    /**
     * Retrieves a set of identifiers for deprecated operations from the paths defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing path and operation information.
     * @return A set of identifiers for deprecated operations.
     */
    public static Set<String> getDeprecatedOperations(OpenAPI openAPI) {
        return openAPI.getPaths().entrySet().stream()
                .flatMap(entry -> entry.getValue().readOperationsMap().entrySet().stream()
                        .filter(operationEntry -> operationEntry.getValue().getDeprecated() != null && operationEntry.getValue().getDeprecated())
                        .map(operationEntry -> Optional.ofNullable(operationEntry.getValue().getOperationId()).orElse(operationEntry.getKey() + " " + entry.getKey()))
                        .collect(Collectors.toSet()).stream())
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves a set of monitoring endpoints from the paths defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing path information.
     * @return A set of monitoring endpoints based on predefined matches.
     */
    public static Set<String> getMonitoringEndpoints(OpenAPI openAPI) {
        return openAPI.getPaths().keySet().stream()
                .filter(path -> MONITORING_MATCHES.stream().anyMatch(toMatch -> {
                            Pattern pattern = Pattern.compile(toMatch);
                            return pattern.matcher(path).matches();
                        }
                )).collect(Collectors.toSet());
    }

    /**
     * Retrieves a set of paths that are missing proper pagination support in the GET operation parameters.
     *
     * @param openAPI The OpenAPI specification containing path and operation information.
     * @return A set of paths missing pagination support in the GET operation parameters.
     */
    public static Set<String> getPathsMissingPaginationSupport(OpenAPI openAPI) {
        return openAPI.getPaths().entrySet()
                .stream()
                .filter(entry -> entry.getKey().contains("/"))
                .filter(entry -> !entry.getKey().substring(entry.getKey().lastIndexOf("/")).contains("{"))
                .filter(entry -> entry.getValue().getGet() != null)
                .filter(entry -> entry.getValue().getGet().getParameters() != null)
                .filter(entry -> entry.getValue().getGet()
                        .getParameters()
                        .stream()
                        .filter(parameter -> "query".equalsIgnoreCase(parameter.getIn()))
                        .filter(parameter -> PAGINATION.contains(parameter.getName()
                                .replaceAll("[-_]", "")
                                .toLowerCase(Locale.ROOT)))
                        .count() < 2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves a set of all produces headers from the operations defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing path and operation information.
     * @return A set of all produces headers across all operations.
     */
    public static Set<String> getAllProducesHeaders(OpenAPI openAPI) {
        return openAPI.getPaths().values().stream()
                .flatMap(pathItem -> responseContentTypeFromOperation(pathItem.getGet(), pathItem.getPut(), pathItem.getPost(),
                        pathItem.getHead(), pathItem.getPatch(), pathItem.getDelete(),
                        pathItem.getOptions(), pathItem.getTrace()).stream())
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves a set of all consumes headers from the operations defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing path and operation information.
     * @return A set of all consumes headers across all operations.
     */
    public static Set<String> getAllConsumesHeaders(OpenAPI openAPI) {
        return openAPI.getPaths().values().stream()
                .flatMap(pathItem -> requestContentTypeFromOperation(pathItem.getGet(), pathItem.getPut(), pathItem.getPost(),
                        pathItem.getHead(), pathItem.getPatch(), pathItem.getDelete(),
                        pathItem.getOptions(), pathItem.getTrace()).stream())
                .collect(Collectors.toSet());
    }

    private static Set<String> responseContentTypeFromOperation(Operation... operations) {
        return Arrays.stream(operations)
                .filter(Objects::nonNull)
                .map(Operation::getResponses)
                .filter(Objects::nonNull)
                .flatMap(apiResponses -> apiResponses.values().stream())
                .map(ApiResponse::getContent)
                .filter(Objects::nonNull)
                .flatMap(content -> content.keySet().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static Set<String> requestContentTypeFromOperation(Operation... operations) {
        return Arrays.stream(operations)
                .filter(Objects::nonNull)
                .map(Operation::getRequestBody)
                .filter(Objects::nonNull)
                .map(RequestBody::getContent)
                .filter(Objects::nonNull)
                .flatMap(content -> content.keySet().stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves a sorted set of all response codes from the operations defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing path and operation information.
     * @return A sorted set of all response codes across all operations.
     */
    public static Set<String> getAllResponseCodes(OpenAPI openAPI) {
        return openAPI.getPaths().values().stream()
                .flatMap(pathItem -> responseCodesFromOperations(pathItem.getGet(),
                        pathItem.getPut(), pathItem.getPost(),
                        pathItem.getHead(), pathItem.getPatch(), pathItem.getDelete(),
                        pathItem.getOptions(), pathItem.getTrace()).stream())
                .sorted()
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    /**
     * Retrieves a set of response codes from the given operations.
     *
     * @param operations The operations to extract response codes from.
     * @return A set of response codes from the specified operations.
     */
    public static Set<String> responseCodesFromOperations(Operation... operations) {
        return Arrays.stream(operations)
                .filter(Objects::nonNull)
                .map(Operation::getResponses)
                .filter(Objects::nonNull)
                .flatMap(apiResponses -> apiResponses.keySet().stream())
                .collect(Collectors.toSet());

    }

    /**
     * Retrieves a set of used HTTP methods from the operations defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing path and operation information.
     * @return A set of used HTTP methods across all operations.
     */
    public static Set<String> getUsedHttpMethods(OpenAPI openAPI) {
        return openAPI.getPaths().values().stream().flatMap(
                        pathItem -> pathItem.readOperationsMap().keySet().stream())
                .map(Enum::name)
                .collect(Collectors.toSet());
    }

    /**
     * Searches for headers in the OpenAPI specification that contain specified header names.
     *
     * @param openAPI             The OpenAPI specification containing path and operation information.
     * @param containsHeaderNames The header names to search for.
     * @return A set of headers that contain the specified header names.
     */
    public static Set<String> searchHeader(OpenAPI openAPI, String... containsHeaderNames) {
        Set<String> result = getHeaders(openAPI).stream()
                .filter(header -> Arrays.stream(containsHeaderNames)
                        .anyMatch(headerNameToContain -> header.toLowerCase(Locale.ROOT)
                                .replaceAll("[_-]", "")
                                .contains(headerNameToContain)))
                .collect(HashSet::new, HashSet::add, HashSet::addAll);

        result.addAll(openAPI.getPaths().values().stream()
                .flatMap(pathItem -> headersFromOperation(containsHeaderNames, pathItem.getGet(), pathItem.getPut(), pathItem.getPost(),
                        pathItem.getHead(), pathItem.getPatch(), pathItem.getDelete(),
                        pathItem.getOptions(), pathItem.getTrace()).stream())
                .collect(Collectors.toSet()));

        return result;
    }

    /**
     * Retrieves a set of all tags from the OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing tag information.
     * @return A set of all tag names.
     */
    public static Set<String> getAllTags(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getTags())
                .orElse(Collections.emptyList())
                .stream()
                .map(Tag::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves headers from the given operations that match specified header names.
     *
     * @param headerNames The header names to match.
     * @param operations  The operations to extract headers from.
     * @return A set of headers from the specified operations that match the specified header names.
     */
    public static Set<String> headersFromOperation(String[] headerNames, Operation... operations) {
        return Arrays.stream(operations)
                .filter(Objects::nonNull)
                .map(Operation::getParameters)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(parameter -> "header".equals(parameter.getIn()))
                .map(Parameter::getName)
                .filter(Objects::nonNull)
                .filter(parameter ->
                        Arrays.stream(headerNames)
                                .anyMatch(headerNameToContain -> parameter.toLowerCase(Locale.ROOT)
                                        .replaceAll("[_-]", "")
                                        .contains(headerNameToContain)))
                .collect(Collectors.toSet());
    }

    private static Set<String> deprecatedHeadersFromOperations(Operation... operations) {
        return Arrays.stream(operations)
                .filter(Objects::nonNull)
                .map(Operation::getParameters)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(parameter -> "header".equals(parameter.getIn()))
                .filter(parameter -> parameter.getDeprecated() != null && parameter.getDeprecated())
                .map(Parameter::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves query parameters from the given operations.
     *
     * @param operations The operations to extract query parameters from.
     * @return A set of query parameter names from the specified operations.
     */
    public static Set<String> queryParametersFromOperations(Operation... operations) {
        return Arrays.stream(operations)
                .filter(Objects::nonNull)
                .map(Operation::getParameters)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(parameter -> "query".equals(parameter.getIn()))
                .map(Parameter::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves API versions from the paths and servers defined in the given OpenAPI specification.
     *
     * @param openAPI The OpenAPI specification containing path and server information.
     * @return A set of API versions extracted from paths and servers.
     */
    public static Set<String> getApiVersions(OpenAPI openAPI) {
        Set<String> versions = openAPI.getPaths().keySet()
                .stream()
                .flatMap(path -> Arrays.stream(path.split("/"))
                        .filter(pathElement -> VERSION_PATH.matcher(pathElement).matches()))
                .collect(Collectors.toSet());

        openAPI.getServers().forEach(server ->
                versions.addAll(Arrays.stream(server.getUrl().split("/"))
                        .filter(pathElement ->
                                VERSION_PATH.matcher(pathElement).matches())
                        .toList()));

        Set<String> versionHeaders = new HashSet<>();
        versionHeaders.addAll(getAllConsumesHeaders(openAPI));
        versionHeaders.addAll(getAllProducesHeaders(openAPI));

        versionHeaders.forEach(header -> {
            Matcher matcher = VERSION_HEADER.matcher(header);
            if (matcher.find()) {
                versions.add(matcher.group(1));
            }
        });

        return versions;
    }

    private static Info defaultInfo() {
        return new Info().description("missing description").version("missing version").title("missing title");
    }

    private static ExternalDocumentation defaultDocumentation() {
        return new ExternalDocumentation().url("missing url");
    }

    /**
     * Counts the number of operations defined in the given PathItem.
     *
     * @param pathItem The PathItem containing operations.
     * @return The number of operations defined in the specified PathItem.
     */
    public static int countPathOperations(PathItem pathItem) {
        return pathItem.readOperationsMap().size();
    }
}
