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

import java.util.Set;

@CommandLine.Command(
        name = "stats",
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        exitCodeOnInvalidInput = 191,
        exitCodeOnExecutionException = 192,
        description = "Prints statistics about and OpenAPI spec file",
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
                    .build();

            if (json) {
                displayJson(stats);
            } else {
                displayText(stats);
            }
        } catch (Exception e) {
            logger.fatal("Something went wrong while running stats {}", e.toString());
            logger.debug("Exception details {}", e);
        }
    }

    void displayJson(Stats stats) {
        logger.noFormat(JsonUtils.GSON.toJson(stats));
    }


    void displayText(Stats stats) {
        logger.config(Ansi.ansi().bold().a("API title: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.title));
        logger.config(Ansi.ansi().bold().a("API description: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.description));
        logger.config(Ansi.ansi().bold().a("API version: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.version));
        logger.config(Ansi.ansi().bold().a("API docs url: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.docsUrl));
        logger.config(Ansi.ansi().bold().a("OpenAPI spec version: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.openApiVersion));
        logger.config(Ansi.ansi().bold().a("Total number of paths: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.pathSize));
        logger.config(Ansi.ansi().bold().a("Total number of operations: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.operationSize));
        logger.config(Ansi.ansi().bold().a("{} deprecated operations: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.deprecatedOperations.size()), Ansi.ansi().fgBlue().a(stats.deprecatedOperations));
        logger.config(Ansi.ansi().bold().a("{} deprecated headers: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.deprecatedHeaders.size()), Ansi.ansi().fgBlue().a(stats.deprecatedHeaders));
        logger.config(Ansi.ansi().bold().a("Servers: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.servers));
        logger.config(Ansi.ansi().bold().a("{} global {} schemes: {}").reset().toString(),
                stats.securitySchemes.size(),
                Ansi.ansi().fgBlue().a("security").reset().bold().toString(),
                stats.securitySchemes);
        logger.config(Ansi.ansi().bold().a("{} global {}: {}").reset().toString(),
                stats.tags.size(),
                Ansi.ansi().fgBlue().a("tags").reset().bold().toString(),
                stats.tags);
        logger.config(Ansi.ansi().bold().a("API versioning: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.apiVersions));
        logger.config(Ansi.ansi().bold().a("Consumes content types: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.consumesContentTypes));
        logger.config(Ansi.ansi().bold().a("Produces content types: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.producesContentTypes));
        logger.config(Ansi.ansi().bold().a("Used HTTP methods: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.usedHttpMethods));
        logger.config(Ansi.ansi().bold().a("Rate Limiting headers identified: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.rateLimitHeaders));
        logger.config(Ansi.ansi().bold().a("Tracing headers identified: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.traceIdHeaders));
        logger.config(Ansi.ansi().bold().a("All response codes: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.responseCodes));
        logger.config(Ansi.ansi().bold().a("Global extensions: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.extensions));
        logger.config(Ansi.ansi().bold().a("Paths potentially missing pagination support: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.pathsMissingPagination));
        logger.config(Ansi.ansi().bold().a("Possible monitoring endpoints: {}").reset().toString(),
                Ansi.ansi().fgBlue().a(stats.monitoringEndpoints));

        this.renderComponentSection("requestBodies", stats.requestBodies);
        this.renderComponentSection("responses", stats.responses);
        this.renderComponentSection("parameters", stats.parameters);
        this.renderComponentSection("headers", stats.headers);
        this.renderComponentSection("schemas", stats.schemas);
        this.renderComponentSection("examples", stats.examples);

    }

    void renderComponentSection(String textToPrint, Set<String> componentsSection) {
        String outputMessage = Ansi.ansi().bold().a("{} ") +
                Ansi.ansi().fgBlue().a(textToPrint).reset().bold().toString()
                + Ansi.ansi().a(" defined in components").reset().toString()
                + (detailed ? ": {}" : "");

        logger.config(outputMessage, componentsSection.size(), componentsSection);
    }

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
