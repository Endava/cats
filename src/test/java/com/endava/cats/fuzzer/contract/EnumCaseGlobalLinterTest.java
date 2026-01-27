package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.NamingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.openapi.handler.collector.EnumCollector;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

@QuarkusTest
class EnumCaseGlobalLinterTest {

    private TestCaseListener testCaseListener;
    private EnumCaseGlobalLinter enumCaseGlobalLinter;
    private EnumCollector enumCollector;
    @Inject
    NamingArguments namingArguments;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        enumCollector = Mockito.mock(EnumCollector.class);
        enumCaseGlobalLinter = new EnumCaseGlobalLinter(testCaseListener, namingArguments, enumCollector);
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(enumCaseGlobalLinter.description())
                .isEqualTo("verifies that all string enum values follow a consistent case pattern across components/schemas");
    }

    @Test
    void shouldReturnRunKey() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        enumCaseGlobalLinter.fuzz(data); // Indirectly tests runKey
        Assertions.assertThat(enumCaseGlobalLinter.getContext().runKeyProvider().apply(data))
                .isEqualTo("global-enum-case-linter");
    }

    @Test
    void shouldCollectEnumsForGlobalComponents() {
        Map<SchemaLocation, List<String>> mockEnums = Map.of(
                new SchemaLocation(null, null, null, null), List.of("ENUM_VALUE")
        );
        Mockito.when(enumCollector.getEnums()).thenReturn(mockEnums);

        Map<SchemaLocation, List<String>> result = enumCaseGlobalLinter.getContext().collector().get();

        Assertions.assertThat(result).containsKey(new SchemaLocation(null, null, null, null));
        Assertions.assertThat(result.get(new SchemaLocation(null, null, null, null))).contains("ENUM_VALUE");
    }

    @Test
    void shouldHandleEmptyEnums() {
        Mockito.when(enumCollector.getEnums()).thenReturn(Map.of());

        Map<SchemaLocation, List<String>> result = enumCaseGlobalLinter.getContext().collector().get();

        Assertions.assertThat(result).isEmpty();
    }

    @Test
    void shouldExecuteTestListener() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.POST);
        Map<SchemaLocation, List<String>> mockEnums = Map.of(
                new SchemaLocation(null, null, null, null), List.of("ENUM_VALUE")
        );
        Mockito.when(enumCollector.getEnums()).thenReturn(mockEnums);

        enumCaseGlobalLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldFilterValidateAndFormatViolations() {
        // Use the injected NamingArguments
        // Mock SchemaLocation to be global
        SchemaLocation globalLoc = Mockito.mock(SchemaLocation.class);
        Mockito.when(globalLoc.isGlobalLocation()).thenReturn(true);
        Mockito.when(globalLoc.fqn()).thenReturn("components.schemas.Pet.status");

        List<String> enumValues = List.of("VALID_ENUM", "invalidEnum");
        Map<SchemaLocation, List<String>> enums = Map.of(globalLoc, enumValues);

        EnumCollector mockEnumCollector = Mockito.mock(EnumCollector.class);
        Mockito.when(mockEnumCollector.getEnums()).thenReturn(enums);

        EnumCaseGlobalLinter linter = new EnumCaseGlobalLinter(testCaseListener, namingArguments, mockEnumCollector);

        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(data.getMethod()).thenReturn(HttpMethod.GET);

        linter.fuzz(data);

        Mockito.verify(testCaseListener).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Enum-case mismatches detected"),
                Mockito.argThat((String msg) -> msg != null && msg.contains("Schema at location 'components.schemas.Pet.status' contains enum value(s) [VALID_ENUM, invalidEnum] that violate naming convention"))
        );
    }
}