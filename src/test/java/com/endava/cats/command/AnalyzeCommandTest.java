package com.endava.cats.command;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

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
}
