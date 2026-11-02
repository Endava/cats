package com.endava.cats.command;

import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.math.BigDecimal;
import java.util.Map;

@QuarkusTest
class AnalyzeCommandTest {

    @Test
    void shouldAnalyzeContractAndProduceFuzzabilityBlueprintAndMermaidGraph() {
        AnalyzeCommand analyzeCommand = new AnalyzeCommand();
        analyzeCommand.contract = "src/test/resources/petstore.yml";
        analyzeCommand.mermaid = true;

        analyzeCommand.run();

        Assertions.assertThat(analyzeCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
        AnalyzeCommand.BlueprintReport report = analyzeCommand.getLastReport();
        Assertions.assertThat(report).isNotNull();
        Assertions.assertThat(report.totalPaths()).isPositive();
        Assertions.assertThat(report.totalOperations()).isPositive();
        Assertions.assertThat(report.fuzzabilityScore()).isBetween(1, 100);
        Assertions.assertThat(report.grade()).isIn("A", "B", "C", "D");
        Assertions.assertThat(report.executionOrder()).isNotEmpty();
        Assertions.assertThat(report.mermaidDiagram()).startsWith("flowchart LR");
        Assertions.assertThat(report.recommendations()).isNotEmpty();

        analyzeCommand.json = true;
        analyzeCommand.run();
        Assertions.assertThat(analyzeCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.OK);
    }

    @Test
    void shouldReturnSoftwareExitCodeWhenContractCannotBeRead() {
        AnalyzeCommand analyzeCommand = new AnalyzeCommand();
        analyzeCommand.contract = "src/test/resources/missing.yml";

        analyzeCommand.run();

        Assertions.assertThat(analyzeCommand.getExitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE);
    }

    @Test
    void shouldHandleEmptyPathsAndMinimalAndHighScoreContracts() {
        AnalyzeCommand analyzeCommand = new AnalyzeCommand();
        OpenAPI richApi = new OpenAPI()
                .components(new Components()
                        .addSchemas("Order", new Schema<>()
                                .type("object")
                                .properties(Map.of(
                                        "id", new Schema<>().type("string").format("uuid").example("123"),
                                        "amount", new Schema<>().type("integer").minimum(BigDecimal.ONE)._default(10)))))
                .paths(new Paths()
                        .addPathItem("/orders", new PathItem().post(
                                new Operation().responses(new ApiResponses()
                                        .addApiResponse("201", new ApiResponse()
                                                .content(new Content()
                                                        .addMediaType("application/json", new MediaType())))
                                        .addApiResponse("400", new ApiResponse())))));

        AnalyzeCommand.BlueprintReport richReport = analyzeCommand.buildBlueprint(richApi);
        Assertions.assertThat(richReport.grade()).isEqualTo("A");
        Assertions.assertThat(richReport.recommendations())
                .containsExactly("Contract is well-structured for automated stateful and negative fuzzing.");

        OpenAPI sparseApi = new OpenAPI()
                .components(new Components()
                        .addSchemas("Unconstrained", new Schema<>()
                                .type("object")
                                .properties(Map.of(
                                        "a", new Schema<>().type("string"),
                                        "b", new Schema<>().type("string"),
                                        "c", new Schema<>().type("string"),
                                        "d", new Schema<>().type("string")))))
                .paths(new Paths()
                        .addPathItem("/orphans/{orphanId}", new PathItem().get(
                                new Operation())));
        AnalyzeCommand.BlueprintReport sparseReport = analyzeCommand.buildBlueprint(sparseApi);
        Assertions.assertThat(sparseReport.grade()).isEqualTo("D");
        Assertions.assertThat(sparseReport.orphanPathParameters()).isNotEmpty();
        Assertions.assertThat(sparseReport.recommendations()).hasSizeGreaterThanOrEqualTo(3);
    }
}
