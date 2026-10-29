package com.endava.cats.fuzzer.contract;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

@QuarkusTest
class PolymorphismDiscriminatorLinterTest {
    private TestCaseListener testCaseListener;
    private PolymorphismDiscriminatorLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new PolymorphismDiscriminatorLinter(testCaseListener);
    }

    @Test
    void shouldReportInfoWhenDiscriminatorIsValid() {
        Schema<?> cat = new Schema<>().type("object").properties(Map.of("petType", new Schema<>().type("string")));
        Schema<?> pet = new Schema<>()
                .oneOf(List.of(new Schema<>().$ref("#/components/schemas/Cat")))
                .discriminator(new Discriminator().propertyName("petType").mapping(Map.of("cat", "#/components/schemas/Cat")));

        OpenAPI openAPI = new OpenAPI().components(new Components().addSchemas("Cat", cat).addSchemas("Pet", pet));
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("All polymorphism discriminators are properly defined")
        );
    }

    @Test
    void shouldReportWarnWhenDiscriminatorPropertyMissingInSubSchemaOrMappingBroken() {
        Schema<?> dog = new Schema<>().type("object").properties(Map.of("barkVolume", new Schema<>().type("integer")));
        Schema<?> pet = new Schema<>()
                .oneOf(List.of(new Schema<>().$ref("#/components/schemas/Dog")))
                .discriminator(new Discriminator().propertyName("petType").mapping(Map.of("missing", "#/components/schemas/Unknown")));

        OpenAPI openAPI = new OpenAPI().components(new Components().addSchemas("Dog", dog).addSchemas("Pet", pet));
        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Polymorphism discriminator issues detected"),
                Mockito.argThat((String msg) -> msg.contains("discriminator mapping 'missing' -> '#/components/schemas/Unknown' does not resolve")
                        && msg.contains("Sub-schema 'Dog' in 'Pet' does not define discriminator property 'petType'"))
        );
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("PolymorphismDiscriminatorLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("validates that oneOf/anyOf discriminator definitions specify a non-blank propertyName present in sub-schemas and valid mapping references");
    }
}
