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

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Test
    void shouldReportAmbiguousPrimitiveAndObjectVariantsWithoutFlaggingUnsupportedSchemas() {
        Schema<?> boolA = new Schema<>().type("boolean");
        Schema<?> boolB = new Schema<>().type("boolean");
        Schema<?> boolOneOf = new Schema<>().oneOf(List.of(boolA, boolB));

        Schema<?> nullA = new Schema<>().types(Set.of("null"));
        Schema<?> nullB = new Schema<>().types(Set.of("null"));
        Schema<?> nullOneOf = new Schema<>().oneOf(List.of(nullA, nullB));

        Schema<?> objWithVariousProps = new Schema<>().type("object")
                .required(List.of("arr", "flag", "nested", "sharedRef", "num"))
                .properties(Map.of(
                        "arr", new Schema<>().type("array").items(new Schema<>().type("string")),
                        "flag", new Schema<>().type("boolean"),
                        "nested", new Schema<>().type("object").properties(Map.of("k", new Schema<>().type("string"))),
                        "sharedRef", new Schema<>().$ref("#/components/schemas/SharedType"),
                        "num", new Schema<>().type("integer")));
        Schema<?> subsumingObj = new Schema<>()
                .required(List.of("sharedRef", "num"))
                .properties(Map.of(
                        "sharedRef", new Schema<>().$ref("#/components/schemas/SharedType"),
                        "num", new Schema<>().type("number"),
                        "arr", new Schema<>(),
                        "flag", new Schema<>().type("boolean")));
        Schema<?> objOneOf = new Schema<>().oneOf(List.of(objWithVariousProps, subsumingObj));

        Schema<?> unsupportedVariant = new Schema<>().type("object").minProperties(1);
        Schema<?> closedWithFalseSchema = new Schema<>().type("object")
                .additionalProperties(new Schema<>().booleanSchemaValue(false))
                .required(List.of("a"))
                .properties(Map.of("a", new Schema<>().type("string")));
        Schema<?> typedApVariant = new Schema<>().type("object")
                .additionalProperties(new Schema<>().type("string"));
        Schema<?> conflictingAllOf = new Schema<>().type("string")
                .allOf(List.of(new Schema<>().type("integer")));
        Schema<?> emptyUntyped = new Schema<>();
        Schema<?> safeOneOf = new Schema<>().oneOf(List.of(
                unsupportedVariant, closedWithFalseSchema, typedApVariant, conflictingAllOf, emptyUntyped));

        OpenAPI openAPI = new OpenAPI().components(new Components()
                .addSchemas("SharedType", new Schema<>().type("string"))
                .addSchemas("BoolOneOf", boolOneOf)
                .addSchemas("NullOneOf", nullOneOf)
                .addSchemas("ObjOneOf", objOneOf)
                .addSchemas("SafeOneOf", safeOneOf));

        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.process(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("Ambiguous oneOf variants found"),
                Mockito.argThat((String msg) -> msg.contains("Schema 'BoolOneOf' oneOf is ambiguous")
                        && msg.contains("Schema 'NullOneOf' oneOf is ambiguous")
                        && msg.contains("Schema 'ObjOneOf' oneOf is ambiguous")
                        && !msg.contains("SafeOneOf")));
    }

    @Test
    void shouldAvoidAmbiguityClaimsForConstrainedOrUnsupportedVariants() {
        List<Schema<?>> constrainedSchemas = List.of(
                new Schema<>().type("string")._const("fixed"),
                new Schema<>().type("string").format("uuid"),
                new Schema<>().type("string").minLength(1),
                new Schema<>().type("string").maxLength(10),
                new Schema<>().type("number").minimum(BigDecimal.ZERO),
                new Schema<>().type("number").maximum(BigDecimal.TEN),
                new Schema<>().type("number").exclusiveMinimumValue(BigDecimal.ZERO),
                new Schema<>().type("number").exclusiveMaximumValue(BigDecimal.TEN),
                new Schema<>().type("number").multipleOf(BigDecimal.TWO),
                new Schema<>().type("array").minItems(1),
                new Schema<>().type("array").maxItems(5),
                new Schema<>().type("array").uniqueItems(true),
                new Schema<>().type("object").maxProperties(5),
                new Schema<>().type("object").not(new Schema<>().type("null")),
                new Schema<>().type("object")._if(new Schema<>().type("object")),
                new Schema<>().type("object").propertyNames(new Schema<>().type("string")),
                new Schema<>().type("object").unevaluatedProperties(new Schema<>().type("string")),
                new Schema<>().type("object").patternProperties(Map.of("^x-", new Schema<>().type("string"))),
                new Schema<>().type("object").dependentRequired(Map.of("a", List.of("b"))),
                new Schema<>().type("object").dependentSchemas(Map.of("a", new Schema<>().type("object"))));
        for (Schema<?> constrained : constrainedSchemas) {
            Mockito.clearInvocations(testCaseListener);
            Schema<?> pair = new Schema<>().oneOf(List.of(constrained, constrained));
            FuzzingData constrainedData = FuzzingData.builder().openApi(new OpenAPI().components(
                    new Components().addSchemas("ConstrainedPair", pair))).build();
            linter.process(constrainedData);
            Mockito.verify(testCaseListener).reportResultInfo(Mockito.any(), Mockito.eq(constrainedData),
                    Mockito.eq("All oneOf schemas have mutually exclusive variants"));
            Mockito.verify(testCaseListener, Mockito.never()).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString());
        }
    }

    private static OpenAPI parse(String contract) throws Exception {
        return new OpenAPIParser().readContents(Files.readString(Path.of(contract)), null, null).getOpenAPI();
    }

}
