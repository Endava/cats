package com.endava.cats.openapi;

import com.endava.cats.args.ProcessingArguments;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
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

public abstract class OpenApiUtils {
    private static final PrettyLogger LOGGER = PrettyLoggerFactory.getLogger(OpenApiUtils.class);
    private static final Pattern VERSION_PATH = Pattern.compile("(?:v|version)(\\d+(?:\\.\\d+){0,2})");

    private static final Pattern VERSION_HEADER = Pattern.compile("(v\\d+)");

    private static final Set<String> PAGINATION = Set.of("limit", "offset", "page", "size", "sort", "perpage", "datefrom", "dateto", "datestart", "dateend");

    private static final Set<String> MONITORING_MATCHES = Set.of("[version\\d*\\.?|v\\d+\\.?]/status", "/status", ".*/health", ".*/monitoring/status", ".*/ping", ".*/healthz");

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
                .filter(contentEntry -> contentEntry.getKey().matches(contentType))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseGet(() -> content.get(contentType));
    }

    public static Map<String, Schema> getSchemas(OpenAPI openAPI, List<String> contentTypeList) {
        Map<String, Schema> schemas = Optional.ofNullable(openAPI.getComponents().getSchemas()).orElseGet(HashMap::new);

        for (String contentType : contentTypeList) {
            Optional.ofNullable(openAPI.getComponents().getRequestBodies()).orElseGet(Collections::emptyMap).forEach((key, value) -> addToSchemas(schemas, key, value.get$ref(), value.getContent(), contentType));
        }
        Optional.ofNullable(openAPI.getComponents().getResponses()).orElseGet(Collections::emptyMap).forEach((key, value) -> addToSchemas(schemas, key, value.get$ref(), value.getContent(), ProcessingArguments.JSON_WILDCARD));

        return schemas;
    }

    public static Map<String, Example> getExamples(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getExamples()).orElse(Collections.emptyMap());
    }


    private static void addToSchemas(Map<String, Schema> schemas, String schemaName, String ref, Content content, String contentType) {
        Schema<?> schemaToAdd = new Schema();
        if (ref == null && hasContentType(content, List.of(contentType))) {
            LOGGER.debug("Getting schema {} for content-type {}", schemaName, contentType);
            Schema<?> refSchema = getMediaTypeFromContent(content, contentType).getSchema();

            if (refSchema instanceof ArraySchema arraySchema) {
                ref = arraySchema.getItems().get$ref();
                refSchema.set$ref(ref);
                schemaToAdd = refSchema;
            } else if (refSchema.get$ref() != null) {
                ref = refSchema.get$ref();
                String schemaKey = ref.substring(ref.lastIndexOf('/') + 1);
                schemaToAdd = schemas.get(schemaKey);
            }
        } else if (content != null && !content.isEmpty() && schemas.get(schemaName) == null) {
            /*it means it wasn't already added with another content type*/
            LOGGER.warn("Content-Type not supported. Found: {} for {}", content.keySet(), schemaName);
        }
        schemas.put(schemaName, schemaToAdd);
    }

    public static boolean hasContentType(Content content, List<String> contentType) {
        return content != null && content.keySet().stream()
                .anyMatch(contentKey -> contentType.stream()
                        .anyMatch(contentItem -> contentKey.matches(contentItem) || contentKey.equalsIgnoreCase(contentItem))
                );
    }

    public static String[] getPathElements(String path) {
        return Arrays.stream(path.substring(1).split("/"))
                .filter(pathElement -> !VERSION_PATH.matcher(pathElement).matches())
                .toArray(String[]::new);
    }

    public static boolean isNotAPathVariable(String pathElement) {
        return !isAPathVariable(pathElement);
    }

    public static boolean isAPathVariable(String pathElement) {
        return pathElement.startsWith("{");
    }


    public static int getNumberOfOperations(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getPaths()).orElse(new io.swagger.v3.oas.models.Paths())
                .values()
                .stream()
                .mapToInt(OpenApiUtils::countPathOperations)
                .sum();
    }

    public static Set<String> getRequestBodies(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getRequestBodies())
                .orElse(Collections.emptyMap()).keySet();
    }

    public static Set<String> getSchemas(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getSchemas())
                .orElse(new HashMap<>()).keySet();
    }

    public static Set<String> getResponses(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getResponses())
                .orElse(Collections.emptyMap()).keySet();
    }

    public static Set<String> getSecuritySchemes(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getSecuritySchemes())
                .orElse(Collections.emptyMap()).keySet();
    }

    public static Set<String> getParameters(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getParameters())
                .orElse(Collections.emptyMap()).keySet();
    }

    public static Set<String> getHeaders(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getComponents().getHeaders())
                .orElse(Collections.emptyMap()).keySet();
    }

    public static Set<String> getServers(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getServers()).orElse(Collections.emptyList()).stream()
                .map(server -> server.getUrl() + " - " + Optional.ofNullable(server.getDescription()).orElse("missing description"))
                .collect(Collectors.toSet());
    }

    public static Set<String> getDeprecatedHeaders(OpenAPI openAPI) {
        return openAPI.getPaths().values().stream()
                .flatMap(pathItem -> deprecatedHeadersFromOperations(pathItem.getGet(), pathItem.getPut(), pathItem.getPost(),
                        pathItem.getHead(), pathItem.getPatch(), pathItem.getDelete(),
                        pathItem.getOptions(), pathItem.getTrace()).stream())
                .collect(Collectors.toSet());
    }

    public static Info getInfo(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getInfo()).orElse(defaultInfo());
    }

    public static String getDocumentationUrl(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getExternalDocs()).orElse(OpenApiUtils.defaultDocumentation()).getUrl();
    }

    public static Set<String> getExtensions(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getExtensions()).orElse(Collections.emptyMap()).keySet();
    }

    public static Set<String> getDeprecatedOperations(OpenAPI openAPI) {
        return openAPI.getPaths().entrySet().stream()
                .flatMap(entry -> entry.getValue().readOperationsMap().entrySet().stream()
                        .filter(operationEntry -> operationEntry.getValue().getDeprecated() != null && operationEntry.getValue().getDeprecated())
                        .map(operationEntry -> Optional.ofNullable(operationEntry.getValue().getOperationId()).orElse(operationEntry.getKey() + " " + entry.getKey()))
                        .collect(Collectors.toSet()).stream())
                .collect(Collectors.toSet());
    }

    public static Set<String> getMonitoringEndpoints(OpenAPI openAPI) {
        return openAPI.getPaths().keySet().stream()
                .filter(path -> MONITORING_MATCHES.stream().anyMatch(toMatch -> {
                            Pattern pattern = Pattern.compile(toMatch);
                            return pattern.matcher(path).matches();
                        }
                )).collect(Collectors.toSet());
    }

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

    public static Set<String> getAllProducesHeaders(OpenAPI openAPI) {
        return openAPI.getPaths().values().stream()
                .flatMap(pathItem -> responseContentTypeFromOperation(pathItem.getGet(), pathItem.getPut(), pathItem.getPost(),
                        pathItem.getHead(), pathItem.getPatch(), pathItem.getDelete(),
                        pathItem.getOptions(), pathItem.getTrace()).stream())
                .collect(Collectors.toSet());
    }

    public static Set<String> getAllConsumesHeaders(OpenAPI openAPI) {
        return openAPI.getPaths().values().stream()
                .flatMap(pathItem -> requestContentTypeFromOperation(pathItem.getGet(), pathItem.getPut(), pathItem.getPost(),
                        pathItem.getHead(), pathItem.getPatch(), pathItem.getDelete(),
                        pathItem.getOptions(), pathItem.getTrace()).stream())
                .collect(Collectors.toSet());
    }


    public static Set<String> responseContentTypeFromOperation(Operation... operations) {
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

    public static Set<String> requestContentTypeFromOperation(Operation... operations) {
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

    public static Set<String> getAllResponseCodes(OpenAPI openAPI) {
        return openAPI.getPaths().values().stream()
                .flatMap(pathItem -> responseCodesFromOperations(pathItem.getGet(),
                        pathItem.getPut(), pathItem.getPost(),
                        pathItem.getHead(), pathItem.getPatch(), pathItem.getDelete(),
                        pathItem.getOptions(), pathItem.getTrace()).stream())
                .sorted()
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    public static Set<String> responseCodesFromOperations(Operation... operations) {
        return Arrays.stream(operations)
                .filter(Objects::nonNull)
                .map(Operation::getResponses)
                .filter(Objects::nonNull)
                .flatMap(apiResponses -> apiResponses.keySet().stream())
                .collect(Collectors.toSet());

    }

    public static Set<String> getUsedHttpMethods(OpenAPI openAPI) {
        return openAPI.getPaths().values().stream().flatMap(
                        pathItem -> pathItem.readOperationsMap().keySet().stream())
                .map(Enum::name)
                .collect(Collectors.toSet());
    }

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

    public static Set<String> getAllTags(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getTags())
                .orElse(Collections.emptyList())
                .stream()
                .map(Tag::getName)
                .collect(Collectors.toSet());
    }

    public static Set<String> headersFromOperation(String[] headerNames, Operation... operations) {
        return Arrays.stream(operations)
                .filter(Objects::nonNull)
                .map(Operation::getParameters)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(parameter -> parameter.getIn().equals("header"))
                .map(Parameter::getName)
                .filter(Objects::nonNull)
                .filter(parameter ->
                        Arrays.stream(headerNames)
                                .anyMatch(headerNameToContain -> parameter.toLowerCase(Locale.ROOT)
                                        .replaceAll("[_-]", "")
                                        .contains(headerNameToContain)))
                .collect(Collectors.toSet());
    }

    public static Set<String> deprecatedHeadersFromOperations(Operation... operations) {
        return Arrays.stream(operations)
                .filter(Objects::nonNull)
                .map(Operation::getParameters)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(parameter -> parameter.getIn().equals("header"))
                .filter(parameter -> parameter.getDeprecated() != null && parameter.getDeprecated())
                .map(Parameter::getName)
                .collect(Collectors.toSet());
    }

    public static Set<String> queryParametersFromOperations(Operation... operations) {
        return Arrays.stream(operations)
                .filter(Objects::nonNull)
                .map(Operation::getParameters)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(parameter -> parameter.getIn().equals("query"))
                .map(Parameter::getName)
                .collect(Collectors.toSet());
    }

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

    public static Info defaultInfo() {
        return new Info().description("missing description").version("missing version").title("missing title");
    }

    public static ExternalDocumentation defaultDocumentation() {
        return new ExternalDocumentation().url("missing url");
    }

    public static int countPathOperations(PathItem pathItem) {
        return pathItem.readOperationsMap().size();
    }
}
