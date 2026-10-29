package com.endava.cats.fuzzer.contract;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@QuarkusTest
class OneOfAmbiguousVariantsLinterTest {
    private TestCaseListener testCaseListener;
    private OneOfAmbiguousVariantsLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new OneOfAmbiguousVariantsLinter(testCaseListener);
    }

    @ParameterizedTest
    @ValueSource(strings = {"src/test/resources/oneof-exclusive-variants.yml", "src/test/resources/petstore.yml"})
    void shouldReportInfoWhenOneOfVariantsAreMutuallyExclusive(String contract) throws Exception {
        FuzzingData data = FuzzingData.builder().openApi(parse(contract)).build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data), Mockito.eq("All oneOf schemas have mutually exclusive variants"));
        Mockito.verify(testCaseListener, Mockito.never()).reportResultError(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyString());
    }

    @Test
    void shouldReportErrorWhenOneOfVariantsOverlap() throws Exception {
        FuzzingData data = FuzzingData.builder().openApi(parse("src/test/resources/oneof-ambiguous-variants.yml")).build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Ambiguous oneOf variants found"),
                Mockito.argThat((String msg) -> msg.contains("Schema 'PaymentMethod' oneOf is ambiguous: every payload valid for 'SavedCard' also matches 'Card' (e.g. {\"token\":\"string\",\"last4\":\"string\"})")
                        && msg.contains("amount' oneOf is ambiguous: every payload valid for 'oneOf[0]' also matches 'oneOf[1]' (e.g. 1)")
                        && msg.contains("Schema 'Identifier' oneOf is ambiguous: every payload valid for 'oneOf[1]' also matches 'oneOf[0]' (e.g. any valid string value)")
                        && msg.contains("Schema 'Filter' oneOf is ambiguous: every payload valid for 'oneOf[0]' also matches 'oneOf[1]' (e.g. {})")
                        && msg.contains("Add a discriminator, a distinguishing const/enum property, or additionalProperties: false"))
        );
    }

    @Test
    void shouldNotReportVariantsWithDifferentPropertyTypes() {
        Schema<?> byNumber = new Schema<>().type("object").required(List.of("id")).properties(Map.of("id", new Schema<>().type("integer")));
        Schema<?> byName = new Schema<>().type("object").required(List.of("id", "name"))
                .properties(Map.of("id", new Schema<>().type("string"), "name", new Schema<>().type("string")));
        Schema<?> lookup = new Schema<>().oneOf(List.of(byNumber, byName));
        FuzzingData data = FuzzingData.builder().openApi(new OpenAPI().components(new Components().addSchemas("Lookup", lookup))).build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data), Mockito.eq("All oneOf schemas have mutually exclusive variants"));
    }

    @Test
    void shouldSkipWhenOpenApiNotAvailable() {
        linter.process(FuzzingData.builder().build());

        Mockito.verify(testCaseListener, Mockito.times(1)).skipTest(Mockito.any(), Mockito.eq("OpenAPI specification not available"));
    }

    @Test
    void shouldReturnSimpleClassNameAndDescription() {
        Assertions.assertThat(linter).hasToString("OneOfAmbiguousVariantsLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("detects oneOf schemas without a discriminator where one variant accepts every instance of another variant, making payloads match more than one variant");
    }

    private static OpenAPI parse(String contract) throws Exception {
        return new OpenAPIParser().readContents(Files.readString(Path.of(contract)), null, null).getOpenAPI();
    }
}
