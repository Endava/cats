package com.endava.cats.command.triage;

import com.endava.cats.fuzzer.special.FunctionalFlowDocument.FieldCorrelationRule;
import com.endava.cats.fuzzer.special.FunctionalFlowParser;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@QuarkusTest
class CorrelationsYamlRendererTest {

    @Test
    void shouldRenderEmptyDocumentWhenNoRules() {
        Assertions.assertThat(CorrelationsYamlRenderer.render(List.of())).contains("correlations: []");
    }

    @Test
    void shouldGroupRulesByPathAndMergeRulesSharingTheSameTrigger(@TempDir Path tempDir) throws Exception {
        List<SynthesizedCorrelationRule> rules = List.of(
                rule("/plans", "planType == 'RECURRING'", "REQUIRE", "billingSchedule", "#(alphanumeric(8))"),
                rule("/plans", "planType == 'RECURRING'", "REQUIRE", "seats", 5),
                rule("/plans", "planType == 'RECURRING'", "REMOVE", "seats", null),
                rule("/plans", "planType == 'RECURRING'", "REMOVE", "oneTimeFee", null),
                rule("/plans", null, "REQUIRE", "items[*].quantity", 1),
                rule("/plans", "name is not null", "UNIQUE", "name", "#(alphanumeric(10))"),
                rule("/orders", "delivery.mode == 'DIGITAL'", "REMOVE", "shippingAddress", null));

        String yaml = CorrelationsYamlRenderer.render(rules);
        Path file = tempDir.resolve("correlations.yml");
        Files.writeString(file, yaml);
        List<FieldCorrelationRule> parsed = FunctionalFlowParser.parseCorrelationFile(file.toFile());

        Assertions.assertThat(yaml).contains("seats: 5", "\"items[*].quantity\": 1", "# from: source error");
        Assertions.assertThat(parsed).hasSize(4);
        Assertions.assertThat(parsed.getFirst()).satisfies(rule -> {
            Assertions.assertThat(rule.paths()).containsExactly("/plans");
            Assertions.assertThat(rule.when()).isEqualTo("planType == 'RECURRING'");
            Assertions.assertThat(rule.set()).containsOnlyKeys("billingSchedule", "seats").containsEntry("seats", 5L);
            Assertions.assertThat(rule.omit()).containsExactly("oneTimeFee");
        });
        Assertions.assertThat(parsed.get(1).when()).isNull();
        Assertions.assertThat(parsed.get(1).set()).containsEntry("items[*].quantity", 1L);
        Assertions.assertThat(parsed.get(2).when()).isEqualTo("name is not null");
        Assertions.assertThat(parsed.getLast().paths()).containsExactly("/orders");
        Assertions.assertThat(parsed.getLast().omit()).containsExactly("shippingAddress");
    }

    @Test
    void shouldKeepInjectedSourceTextOnASingleCommentLine() {
        SynthesizedCorrelationRule rule = new SynthesizedCorrelationRule("/plans", null, null, null, "REQUIRE", "name",
                "x", "bad\nset:\n  admin: true");

        Assertions.assertThat(CorrelationsYamlRenderer.render(List.of(rule)))
                .contains("# from: bad set:   admin: true")
                .doesNotContain("\n  admin: true");
    }

    private static SynthesizedCorrelationRule rule(String path, String when, String action, String field, Object value) {
        return new SynthesizedCorrelationRule(path, when, null, null, action, field, value, "source error");
    }
}
