package com.endava.cats.command;

import com.endava.cats.command.model.ConfigOptions;
import com.endava.cats.util.AnsiUtils;
import com.endava.cats.util.JsonUtils;
import com.endava.cats.util.OpenApiUtils;
import com.endava.cats.util.VersionProvider;
import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import io.quarkus.arc.Unremovable;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Zero-touch OpenAPI contract architecture, resource dependency graph, Mermaid diagram, and 0-100 Fuzzability
 * Readiness analyzer.
 */
@CommandLine.Command(
        name = "analyze",
        aliases = {"blueprint"},
        mixinStandardHelpOptions = true,
        usageHelpAutoWidth = true,
        description = "Analyze an OpenAPI contract's resource dependency graph, producer-consumer chains, and 0-100 Fuzzability Readiness Score without sending any HTTP requests.",
        abbreviateSynopsis = true,
        exitCodeListHeading = "%n@|bold,underline Exit Codes:|@%n",
        exitCodeList = {"@|bold  0|@:Contract analyzed successfully",
                "@|bold 2|@:Usage error: user input for the command was incorrect",
                "@|bold 1|@:Contract could not be parsed"},
        footerHeading = "%n@|bold,underline Examples:|@%n",
        footer = {"  Analyze an OpenAPI contract and print its Fuzzability Blueprint:",
                "    cats analyze -c openapi.yml",
                "", "  Export a Mermaid.js resource dependency diagram for documentation:",
                "    cats analyze -c openapi.yml --mermaid",
                "", "  Output the full blueprint as JSON:",
                "    cats analyze -c openapi.yml -j"},
        synopsisHeading = "%nUsage: ",
        versionProvider = VersionProvider.class)
@Unremovable
public class AnalyzeCommand implements Runnable, CommandLine.IExitCodeGenerator {
    private final PrettyLogger logger = PrettyLoggerFactory.getConsoleLogger();

    @CommandLine.Mixin
    ConfigOptions configOptions;

    @CommandLine.Option(names = {"-c", "--contract"}, required = true,
            description = "The OpenAPI contract/spec to analyze.")
    String contract;

    @CommandLine.Option(names = {"--mermaid"},
            description = "Include a Mermaid.js flowchart of the resource producer-consumer dependency graph.")
    boolean mermaid;

    @CommandLine.Option(names = {"-j", "--json"},
            description = "Output the analysis blueprint as JSON.")
    boolean json;

    @Getter
    private int exitCode = CommandLine.ExitCode.OK;

    @Getter
    private BlueprintReport lastReport;

    @Override
    public void run() {
        try {
            OpenAPI openAPI = OpenApiUtils.readOpenApi(contract);
            if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
                throw new IllegalArgumentException("Provided OpenAPI specification has no paths");
            }
            lastReport = buildBlueprint(openAPI);
            renderBlueprint(lastReport);
            exitCode = CommandLine.ExitCode.OK;
        } catch (IOException | RuntimeException e) {
            logger.error("Failed to analyze OpenAPI contract: {}", e.getMessage());
            exitCode = CommandLine.ExitCode.SOFTWARE;
        }
    }

    BlueprintReport buildBlueprint(OpenAPI openAPI) {
        OperationDependencyOrder.PathDependencyAnalysis depAnalysis =
                OperationDependencyOrder.analyzePathDependencies(openAPI);
        List<ProducerConsumerChain> chains = new ArrayList<>();
        depAnalysis.consumersByProducer().forEach((producer, consumers) -> {
            String producedParams = String.join(", ", depAnalysis.paramsByProducer().getOrDefault(producer, Set.of()));
            String label = producedParams.isEmpty() ? producer : producer + "  (produces " + producedParams + ")";
            chains.add(new ProducerConsumerChain(label, List.copyOf(consumers)));
        });
        Set<String> orphanParameters = new LinkedHashSet<>();
        depAnalysis.unresolvedByPath().forEach((path, vars) -> {
            String methods = OperationDependencyOrder.methodsForPath(openAPI, path);
            vars.forEach(v -> orphanParameters.add("{" + v + "} in " + methods + " " + path));
        });

        OperationMetrics opMetrics = collectOperationMetrics(openAPI);
        SchemaMetrics schemaMetrics = collectSchemaMetrics(openAPI);
        ScoreBreakdown score = computeScore(
                depAnalysis.totalPathVars(), depAnalysis.resolvedPathVars(), opMetrics, schemaMetrics);
        List<String> recommendations = buildRecommendations(orphanParameters, opMetrics, schemaMetrics);
        String mermaidDiagram = buildMermaidDiagram(chains, orphanParameters);

        return new BlueprintReport(
                contract,
                depAnalysis.orderedPaths().size(),
                opMetrics.totalOperations(),
                score.totalScore(),
                score.grade(),
                score,
                depAnalysis.orderedPaths(),
                List.copyOf(chains),
                List.copyOf(orphanParameters),
                recommendations,
                mermaidDiagram);
    }

    private static OperationMetrics collectOperationMetrics(OpenAPI openAPI) {
        int totalOps = 0;
        int opsWith2xxSchema = 0;
        int opsWith4xxDocs = 0;
        for (PathItem item : openAPI.getPaths().values()) {
            if (item == null || item.readOperations() == null) {
                continue;
            }
            for (Operation op : item.readOperations()) {
                totalOps++;
                if (hasStructured2xxResponse(op)) {
                    opsWith2xxSchema++;
                }
                if (hasDocumented4xxResponse(op)) {
                    opsWith4xxDocs++;
                }
            }
        }
        return new OperationMetrics(totalOps, opsWith2xxSchema, opsWith4xxDocs);
    }

    private static boolean hasStructured2xxResponse(Operation op) {
        if (op.getResponses() == null) {
            return false;
        }
        for (Map.Entry<String, ApiResponse> entry : op.getResponses().entrySet()) {
            if (entry.getKey().startsWith("2") && entry.getValue() != null &&
                    entry.getValue().getContent() != null && !entry.getValue().getContent().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDocumented4xxResponse(Operation op) {
        if (op.getResponses() == null) {
            return false;
        }
        return op.getResponses().keySet().stream().anyMatch(code -> code.startsWith("4") || "default".equalsIgnoreCase(code));
    }

    @SuppressWarnings("rawtypes")
    private static SchemaMetrics collectSchemaMetrics(OpenAPI openAPI) {
        Map<String, Schema> schemas = OpenApiUtils.getSchemas(openAPI, List.of(JsonUtils.JSON_WILDCARD));
        int totalProps = 0;
        int constrainedProps = 0;
        int exampleProps = 0;
        for (Schema<?> schema : schemas.values()) {
            if (schema == null || schema.getProperties() == null) {
                continue;
            }
            for (Object propObj : schema.getProperties().values()) {
                if (propObj instanceof Schema<?> prop) {
                    totalProps++;
                    if (hasValidationConstraint(prop)) {
                        constrainedProps++;
                    }
                    if (prop.getExample() != null || prop.getExamples() != null || prop.getDefault() != null) {
                        exampleProps++;
                    }
                }
            }
        }
        return new SchemaMetrics(totalProps, constrainedProps, exampleProps);
    }

    private static boolean hasValidationConstraint(Schema<?> prop) {
        return prop.getMinimum() != null || prop.getMaximum() != null ||
                prop.getMinLength() != null || prop.getMaxLength() != null ||
                StringUtils.isNotBlank(prop.getPattern()) ||
                (prop.getEnum() != null && !prop.getEnum().isEmpty()) ||
                StringUtils.isNotBlank(prop.getFormat());
    }

    private static ScoreBreakdown computeScore(int totalPathVars, int resolvedPathVars,
                                               OperationMetrics opMetrics, SchemaMetrics schemaMetrics) {
        int correlationScore = totalPathVars == 0 ? 30
                : (int) Math.round(30.0 * resolvedPathVars / totalPathVars);
        int constraintScore = schemaMetrics.totalProperties() == 0 ? 15
                : (int) Math.min(25, Math.round(25.0 * schemaMetrics.constrainedProperties() / schemaMetrics.totalProperties() * 1.4));
        int exampleScore = schemaMetrics.totalProperties() == 0 ? 10
                : (int) Math.min(15, Math.round(15.0 * schemaMetrics.exampleProperties() / schemaMetrics.totalProperties() * 1.5));
        int responseSchemaScore = opMetrics.totalOperations() == 0 ? 0
                : (int) Math.round(15.0 * opMetrics.opsWith2xxSchema() / opMetrics.totalOperations());
        int errorDocsScore = opMetrics.totalOperations() == 0 ? 0
                : (int) Math.round(15.0 * opMetrics.opsWith4xxDocs() / opMetrics.totalOperations());

        int total = Math.clamp((long) correlationScore + constraintScore + exampleScore + responseSchemaScore + errorDocsScore, 0, 100);
        String grade = total >= 85 ? "A" : total >= 70 ? "B" : total >= 55 ? "C" : "D";
        return new ScoreBreakdown(total, grade, correlationScore, constraintScore,
                exampleScore, responseSchemaScore, errorDocsScore);
    }

    private static List<String> buildRecommendations(Set<String> orphanParams, OperationMetrics opMetrics,
                                                     SchemaMetrics schemaMetrics) {
        List<String> recs = new ArrayList<>();
        if (!orphanParams.isEmpty()) {
            String sampleFlags = orphanParams.stream()
                    .map(s -> s.substring(0, s.indexOf(" in ")).replace("{", "").replace("}", ""))
                    .distinct()
                    .limit(4)
                    .map(p -> p + ":<value>")
                    .collect(Collectors.joining(","));
            recs.add("Pass --urlParams \"" + sampleFlags + "\" (or run 'cats init') for path parameters without a POST producer.");
        }
        if (schemaMetrics.totalProperties() > 0 && schemaMetrics.constrainedProperties() * 3 < schemaMetrics.totalProperties()) {
            recs.add("Add minLength/maxLength, minimum/maximum, pattern, or enum constraints to schema properties to unlock deeper boundary fuzzing.");
        }
        if (schemaMetrics.totalProperties() > 0 && schemaMetrics.exampleProperties() * 4 < schemaMetrics.totalProperties()) {
            recs.add("Add 'example:' values to domain-specific properties so ExamplesFieldsFuzzer can exercise realistic payloads.");
        }
        if (opMetrics.opsWith4xxDocs() * 2 < opMetrics.totalOperations()) {
            recs.add("Use '-b -k' on your first fuzzing run since several operations do not document 4XX error responses yet.");
        }
        if (recs.isEmpty()) {
            recs.add("Contract is well-structured for automated stateful and negative fuzzing.");
        }
        return List.copyOf(recs);
    }

    private static String buildMermaidDiagram(List<ProducerConsumerChain> chains, Set<String> orphanParams) {
        StringBuilder sb = new StringBuilder("flowchart LR\n");
        int nodeIdx = 1;
        for (ProducerConsumerChain chain : chains) {
            String prodId = "P" + (nodeIdx++);
            sb.append("  ").append(prodId).append("[\"").append(chain.producer()).append("\"]\n");
            for (String consumer : chain.consumers()) {
                String consId = "C" + (nodeIdx++);
                sb.append("  ").append(prodId).append(" --> ").append(consId).append("(\"").append(consumer).append("\")\n");
            }
        }
        for (String orphan : orphanParams) {
            String orphId = "O" + (nodeIdx++);
            sb.append("  ").append(orphId).append("{{\"Orphan: ").append(orphan).append("\"}}\n");
        }
        return sb.toString().stripTrailing();
    }

    private void renderBlueprint(BlueprintReport report) {
        if (json) {
            logger.noFormat(JsonUtils.GSON.toJson(report));
            return;
        }
        logger.noFormat("");
        logger.noFormat(AnsiUtils.bold("=== CATS OpenAPI Fuzzability & Dependency Blueprint ==="));
        logger.noFormat("Contract: {} ({} paths, {} operations)",
                report.contract(), report.totalPaths(), report.totalOperations());
        logger.noFormat("Fuzzability Readiness Score: {} / 100 (Grade {})",
                AnsiUtils.boldGreen(String.valueOf(report.fuzzabilityScore())),
                AnsiUtils.bold(report.grade()));
        ScoreBreakdown s = report.scoreBreakdown();
        logger.noFormat("  - Resource Correlation Coverage: {} / 30", s.correlationScore());
        logger.noFormat("  - Schema Constraint Richness:    {} / 25", s.constraintScore());
        logger.noFormat("  - Example & Default Coverage:    {} / 15", s.exampleScore());
        logger.noFormat("  - 2XX Response Schemas:          {} / 15", s.responseSchemaScore());
        logger.noFormat("  - 4XX Error Documentation:       {} / 15", s.errorDocsScore());

        if (!report.producerConsumerChains().isEmpty()) {
            logger.noFormat("");
            logger.noFormat(AnsiUtils.bold("Automatic Producer -> Consumer Dependency Tree:"));
            for (ProducerConsumerChain chain : report.producerConsumerChains()) {
                logger.noFormat("  {}", AnsiUtils.boldGreen(chain.producer()));
                List<String> consumers = chain.consumers();
                for (int i = 0; i < consumers.size(); i++) {
                    String branch = (i == consumers.size() - 1) ? "└── " : "├── ";
                    logger.noFormat("    {}{}", branch, consumers.get(i));
                }
            }
        }
        if (!report.orphanPathParameters().isEmpty()) {
            logger.noFormat("");
            logger.noFormat(AnsiUtils.boldYellow("Orphan Path Parameters (no POST/collection producer in spec):"));
            report.orphanPathParameters().forEach(o -> logger.noFormat("  - {}", o));
        }
        logger.noFormat("");
        logger.noFormat(AnsiUtils.bold("Recommendations:"));
        report.recommendations().forEach(r -> logger.noFormat("  * {}", r));

        if (mermaid) {
            logger.noFormat("");
            logger.noFormat(AnsiUtils.bold("Mermaid.js Resource Dependency Graph:"));
            logger.noFormat("```mermaid");
            logger.noFormat(report.mermaidDiagram());
            logger.noFormat("```");
        }
    }

    private record OperationMetrics(int totalOperations, int opsWith2xxSchema, int opsWith4xxDocs) {
    }

    private record SchemaMetrics(int totalProperties, int constrainedProperties, int exampleProperties) {
    }

    /**
     * Breakdown of the 0-100 Fuzzability Readiness Score.
     */
    public record ScoreBreakdown(int totalScore, String grade, int correlationScore, int constraintScore,
                                 int exampleScore, int responseSchemaScore, int errorDocsScore) {
    }

    /**
     * Producer operation and the downstream parameterized paths it feeds.
     */
    public record ProducerConsumerChain(String producer, List<String> consumers) {
    }

    /**
     * Complete static analysis blueprint for an OpenAPI contract.
     */
    public record BlueprintReport(String contract, int totalPaths, int totalOperations, int fuzzabilityScore,
                                  String grade, ScoreBreakdown scoreBreakdown, List<String> executionOrder,
                                  List<ProducerConsumerChain> producerConsumerChains,
                                  List<String> orphanPathParameters, List<String> recommendations,
                                  String mermaidDiagram) {
    }
}
