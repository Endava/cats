package com.endava.cats.report.error;

import com.endava.cats.model.ErrorSignature;
import com.endava.cats.model.RequestVocabulary;
import com.endava.cats.report.error.extract.ErrorBodyExtraction;
import com.endava.cats.report.error.mask.ErrorSignatureMasker;
import com.endava.cats.report.error.template.ErrorSample;
import com.endava.cats.report.error.template.ErrorTemplate;
import com.endava.cats.report.error.template.ErrorTemplateMiner;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Runs the cross-language error-body fixtures of {@code src/test/resources/error-bodies} (see {@code expectations.yml}):
 * extraction and masking per body and template equivalence per stack.
 */
@QuarkusTest
class ErrorBodyFixturesTest {
    private static final String ROOT = "/error-bodies/";
    private static final int MIN_STACKS = 10;
    private static final int MIN_CASES_PER_STACK = 6;

    static List<Fixture> fixtures() {
        List<Map<String, Object>> entries = new Yaml().load(resource("expectations.yml"));
        return entries.stream().map(Fixture::of).toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void shouldExtractAndMaskTheRootCause(Fixture fixture) {
        ErrorSignature signature = fixture.signature();

        Assertions.assertThat(ErrorBodyExtraction.extract(fixture.body()).errorType()).isEqualTo(fixture.expectedErrorType());
        Assertions.assertThat(String.join(" ", signature.tokens())).isEqualTo(fixture.expectedTokens());
        Assertions.assertThat(signature.stackFrame()).isEqualTo(fixture.expectedStackFrame());
    }

    @Test
    void shouldGiveTheSameTemplateExactlyToTheFixturesOfTheSameGroup() {
        Map<String, List<Fixture>> byStack = byStack();

        Assertions.assertThat(byStack).allSatisfy((stack, stackFixtures) -> {
            List<ErrorTemplate> templates = ErrorTemplateMiner.mine(stackFixtures.stream().map(Fixture::sample).toList());
            Map<String, String> templateIdsByFile = IntStream.range(0, templates.size()).boxed()
                    .collect(Collectors.toMap(index -> stackFixtures.get(index).file(), index -> templates.get(index).id()));
            Assertions.assertThat(groupsOf(stackFixtures, templateIdsByFile)).as(stack).isEqualTo(groupsOfLabels(stackFixtures));
        });
    }

    @Test
    void shouldCoverEnoughStacksAndCases() {
        Assertions.assertThat(byStack()).hasSizeGreaterThanOrEqualTo(MIN_STACKS)
                .allSatisfy((stack, stackFixtures) -> Assertions.assertThat(stackFixtures).as(stack).hasSizeGreaterThanOrEqualTo(MIN_CASES_PER_STACK));
    }

    private static Map<String, List<Fixture>> byStack() {
        return fixtures().stream().collect(Collectors.groupingBy(Fixture::stack, TreeMap::new, Collectors.toList()));
    }

    private static Map<String, List<String>> groupsOf(List<Fixture> stackFixtures, Map<String, String> templateIdsByFile) {
        return stackFixtures.stream().collect(Collectors.groupingBy(fixture -> templateIdsByFile.get(fixture.file()),
                        Collectors.mapping(Fixture::file, Collectors.toList())))
                .values().stream().collect(Collectors.toMap(files -> files.getFirst(), files -> files));
    }

    private static Map<String, List<String>> groupsOfLabels(List<Fixture> stackFixtures) {
        return stackFixtures.stream().collect(Collectors.groupingBy(Fixture::group, Collectors.mapping(Fixture::file, Collectors.toList())))
                .values().stream().collect(Collectors.toMap(files -> files.getFirst(), files -> files));
    }

    private static String resource(String name) {
        try (InputStream stream = Objects.requireNonNull(ErrorBodyFixturesTest.class.getResourceAsStream(ROOT + name), name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    record Fixture(String file, int status, Map<String, String> request, String group, Map<String, String> expect) {

        @SuppressWarnings("unchecked")
        static Fixture of(Map<String, Object> entry) {
            return new Fixture((String) entry.get("file"), (Integer) entry.get("status"), (Map<String, String>) entry.get("request"),
                    (String) entry.get("group"), (Map<String, String>) entry.get("expect"));
        }

        String stack() {
            return file.substring(0, file.indexOf('/'));
        }

        String body() {
            return resource(file);
        }

        ErrorSignature signature() {
            return ErrorSignatureMasker.signatureOf(body(), null,
                    RequestVocabulary.from(request.get("payload"), request.get("url"), request.get("contractPath")));
        }

        ErrorSample sample() {
            return new ErrorSample("", status, signature());
        }

        String expectedErrorType() {
            return expect.get("errorType");
        }

        String expectedTokens() {
            return expect.get("tokens");
        }

        String expectedStackFrame() {
            return expect.get("stackFrame");
        }

        @Override
        public String toString() {
            return file;
        }
    }
}
