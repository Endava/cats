package com.endava.cats.command;

import com.endava.cats.json.JsonUtils;
import com.endava.cats.openapi.OpenApiUtils;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.Builder;
import lombok.ToString;
import org.fusesource.jansi.Ansi;
import picocli.CommandLine;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Gets stats about an OpenAPI spec.
 */
@CommandLine.Command(
        name = "stats",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        description = "Prints statistics about and OpenAPI spec file",
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Successful program execution",
                "@|bold 191|@:Usage error: user input for the command was incorrect",
                "@|bold 192|@:Internal execution error: an exception occurred when executing command"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Print stats for an OpenAPI contract:",
                "    cats stats -c openapi.yml",
                "", "  Print stats for an OpenAPI contract, but exclude description:",
                "    cats stats -c openapi.yml --skip DESCRIPTION"},
        versionProvider = VersionProvider.class)
@Unremovable
public class StatsCommand implements Runnable {
    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();
    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output to console in JSON format.")
    private boolean json;

    @CommandLine.Option(names = {"-d", "--detailed"},
            description = "Enumerate elements in components section.")
    private boolean detailed;

    @CommandLine.Option(names = {"-c", "--contract"},
            description = "The OpenAPI contract", required = true)
    private String contract;

    @CommandLine.Option(names = {"-s", "--skip"},
            description = "Details to skip printing on console. JSON output will still include them.", split = ",")
    private List<Details> skip = Collections.emptyList();

    /**
     * Holds the type of details extracted from OpenAPI.
     */
    public enum Details {
        TITLE("API title: {}", "title"),
        DESCRIPTION("API description: {}", "description"),
        VERSION("API version: {}", "version"),
        DOCS("API docs url: {}", "docsUrl"),
        OPENAPI_VERSION("OpenAPI spec version: {}", "openApiVersion"),
        PATHS("Total number of paths: {}", "pathSize"),
        OPERATIONS("Total number of operations: {}", "operationSize"),
        DEPRECATED_OPERATIONS("{} deprecated operations: {}", "deprecatedOperations"),
        DEPRECATED_HEADERS("{} deprecated headers: {}", "deprecatedHeaders"),
        SERVERS("Servers: {}", "servers"),
        SECURITY_SCHEMES("{} global security schemes: {}", "securitySchemes"),
        TAGS("{} global tags: {}", "tags"),
        VERSIONING("API versioning: {}", "apiVersions"),
        CONSUMES("Consumes content types: {}", "consumesContentTypes"),
        PRODUCES("Produces content types: {}", "producesContentTypes"),
        HTTP_METHODS("Used HTTP methods: {}", "usedHttpMethods"),
        RATE_LIMIT("Rate Limiting headers identified: {}", "rateLimitHeaders"),
        TRACING("Tracing headers identified: {}", "traceIdHeaders"),
        IDEMPOTENCY("Idempotency headers identified: {}", "idempotencyHeaders"),
        AUTHENTICATION("Authentication headers identified: {}", "authenticationHeaders"),
        RESPONSE_CODES("All response codes: {}", "responseCodes"),
        EXTENSIONS("Global extensions: {}", "extensions"),
        PAGINATION("Paths potentially missing pagination support: {}", "pathsMissingPagination"),
        MONITORING("Possible monitoring endpoints: {}", "monitoringEndpoints");

        private final String text;
        private final String property;

        Details(String text, String property) {
            this.text = text;
            this.property = property;
        }

    }

    @Override
    public void run() {
        try {
            OpenAPI openAPI = OpenApiUtils.readOpenApi(this.contract);
            String pathSize = String.valueOf(openAPI.getPaths().size());
            String operationsSize = String.valueOf(OpenApiUtils.getNumberOfOperations(openAPI));
            Set<String> servers = OpenApiUtils.getServers(openAPI);
            Set<String> requestBodies = OpenApiUtils.getRequestBodies(openAPI);
            Set<String> schemas = OpenApiUtils.getSchemas(openAPI);
            Set<String> responses = OpenApiUtils.getResponses(openAPI);
            Set<String> securitySchemes = OpenApiUtils.getSecuritySchemes(openAPI);
            Set<String> parameters = OpenApiUtils.getParameters(openAPI);
            Set<String> headers = OpenApiUtils.getHeaders(openAPI);
            Info info = OpenApiUtils.getInfo(openAPI);
            Set<String> apiVersions = OpenApiUtils.getApiVersions(openAPI);
            Set<String> deprecatedOperations = OpenApiUtils.getDeprecatedOperations(openAPI);
            Set<String> consumesContentTypes = OpenApiUtils.getAllConsumesHeaders(openAPI);
            Set<String> producesContentTypes = OpenApiUtils.getAllProducesHeaders(openAPI);
            Set<String> rateLimitHeaders = OpenApiUtils.searchHeader(openAPI, "ratelimit");
            Set<String> traceIdHeaders = OpenApiUtils.searchHeader(openAPI, "traceid", "correlationid", "requestid", "sessionid");
            Set<String> idempotencyHeaders = OpenApiUtils.searchHeader(openAPI, "idempotency");
            Set<String> authenticationHeaders = OpenApiUtils.searchHeader(openAPI, "authorization", "authorisation", "token", "jwt", "apikey", "secret", "secretkey", "apisecret", "apitoken", "appkey", "appid");
            Set<String> tags = OpenApiUtils.getAllTags(openAPI);
            Set<String> responseCodes = OpenApiUtils.getAllResponseCodes(openAPI);
            Set<String> deprecatedHeaders = OpenApiUtils.getDeprecatedHeaders(openAPI);
            Set<String> examples = OpenApiUtils.getExamples(openAPI).keySet();
            Set<String> extensions = OpenApiUtils.getExtensions(openAPI);
            Set<String> pathsMissingPagination = OpenApiUtils.getPathsMissingPaginationSupport(openAPI);
            Set<String> possibleMonitoringEndpoints = OpenApiUtils.getMonitoringEndpoints(openAPI);
            Set<String> usedHttpMethods = OpenApiUtils.getUsedHttpMethods(openAPI);
            String docsUrl = OpenApiUtils.getDocumentationUrl(openAPI);

            Stats stats = new Stats.StatsBuilder().pathSize(pathSize).operationSize(operationsSize)
                    .servers(servers).requestBodies(requestBodies).schemas(schemas).responses(responses)
                    .securitySchemes(securitySchemes).parameters(parameters).headers(headers)
                    .title(info.getTitle()).description(info.getDescription()).version(info.getVersion())
                    .openApiVersion(openAPI.getSpecVersion().name()).apiVersions(apiVersions).deprecatedOperations(deprecatedOperations)
                    .consumesContentTypes(consumesContentTypes).producesContentTypes(producesContentTypes)
                    .rateLimitHeaders(rateLimitHeaders).traceIdHeaders(traceIdHeaders).tags(tags)
                    .responseCodes(responseCodes).deprecatedHeaders(deprecatedHeaders).examples(examples)
                    .docsUrl(docsUrl).extensions(extensions).pathsMissingPagination(pathsMissingPagination)
                    .monitoringEndpoints(possibleMonitoringEndpoints).usedHttpMethods(usedHttpMethods)
                    .authenticationHeaders(authenticationHeaders).idempotencyHeaders(idempotencyHeaders)
                    .build();

            if (json) {
                displayJson(stats);
            } else {
                displayText(stats);
            }
        } catch (Exception e) {
            logger.fatal("Something went wrong while running stats {}", e.toString());
        }
    }

    void displayJson(Stats stats) {
        logger.noFormat(JsonUtils.GSON.toJson(stats));
    }


    void displayText(Stats stats) {
        List<Details> toDisplay = Arrays.stream(Details.values())
                .filter(details -> !skip.contains(details))
                .toList();

        for (Details details : toDisplay) {
            if (details.text.startsWith("{}")) {
                logger.config(Ansi.ansi().bold().a(details.text).reset().toString(),
                        Ansi.ansi().fgBlue().a(this.getFieldSize(stats, details.property)).reset().bold(),
                        Ansi.ansi().fgBlue().a(this.getField(stats, details.property)).reset().bold());
            } else {
                logger.config(Ansi.ansi().bold().a(details.text).reset().toString(),
                        Ansi.ansi().fgBlue().a(this.getField(stats, details.property)).reset().bold());
            }
        }

        this.renderComponentSection("requestBodies", stats.requestBodies);
        this.renderComponentSection("responses", stats.responses);
        this.renderComponentSection("parameters", stats.parameters);
        this.renderComponentSection("headers", stats.headers);
        this.renderComponentSection("schemas", stats.schemas);
        this.renderComponentSection("examples", stats.examples);
    }

    private Object getField(Object obj, String fieldName) {
        try {
            Class<?> objClass = obj.getClass();
            Field field = objClass.getDeclaredField(fieldName);
            return field.get(obj);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.fatal("Field not available {}", fieldName);
        }
        return "";
    }

    private int getFieldSize(Object obj, String fieldName) {
        Collection<?> collection = (Collection<?>) this.getField(obj, fieldName);
        return collection.size();
    }

    void renderComponentSection(String textToPrint, Set<String> componentsSection) {
        String outputMessage = Ansi.ansi().bold().a("{} ") +
                Ansi.ansi().fgBlue().a(textToPrint).reset().bold().toString()
                + Ansi.ansi().a(" defined in components").reset().toString()
                + (detailed ? ": {}" : "");

        logger.config(outputMessage, componentsSection.size(), componentsSection);
    }

    /**
     * Entity to hold what data is extracted from OpenAPI spec.
     */
    @Builder
    @ToString
    public static class Stats {
        String pathSize;
        String operationSize;
        Set<String> examples;
        Set<String> servers;
        Set<String> requestBodies;
        Set<String> schemas;
        Set<String> responses;
        Set<String> securitySchemes;
        Set<String> parameters;
        Set<String> headers;
        String title;
        String version;
        String description;
        String openApiVersion;
        Set<String> apiVersions;
        Set<String> deprecatedOperations;
        Set<String> consumesContentTypes;
        Set<String> producesContentTypes;
        Set<String> rateLimitHeaders;
        Set<String> traceIdHeaders;
        Set<String> idempotencyHeaders;
        Set<String> authenticationHeaders;
        Set<String> tags;
        Set<String> responseCodes;
        Set<String> deprecatedHeaders;
        Set<String> extensions;
        Set<String> pathsMissingPagination;
        Set<String> monitoringEndpoints;
        Set<String> usedHttpMethods;
        String docsUrl;
    }
}
