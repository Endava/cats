package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.NamingArguments;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.openapi.handler.collector.EnumCollector;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
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

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        NamingArguments namingArguments = Mockito.mock(NamingArguments.class);
        enumCollector = Mockito.mock(EnumCollector.class);
        enumCaseGlobalLinter = new EnumCaseGlobalLinter(testCaseListener, namingArguments, enumCollector);
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(enumCaseGlobalLinter.description()).isEqualTo("Verifies that all string enum values follow a consistent case pattern across components/schemas.");
    }

    @Test
    void shouldReturnRunKey() {
        Assertions.assertThat(enumCaseGlobalLinter.runKey(null)).isEqualTo("global-enum-case-linter");
    }

    @Test
    void shouldSelectEnumsForGlobalComponents() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<SchemaLocation, List<String>> mockEnums = Map.of(
                new SchemaLocation(null, null, null), List.of("ENUM_VALUE")
        );
        Mockito.when(enumCollector.getEnums()).thenReturn(mockEnums);

        Map<SchemaLocation, List<String>> result = enumCaseGlobalLinter.selectEnums(data);

        Assertions.assertThat(result).containsKey(new SchemaLocation(null, null, null));
        Assertions.assertThat(result.get(new SchemaLocation(null, null, null))).contains("ENUM_VALUE");
    }

    @Test
    void shouldHandleEmptyEnums() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Mockito.when(enumCollector.getEnums()).thenReturn(Map.of());

        Map<SchemaLocation, List<String>> result = enumCaseGlobalLinter.selectEnums(data);

        Assertions.assertThat(result).isEmpty();
    }

    @Test
    void shouldExecuteTestListener() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<SchemaLocation, List<String>> mockEnums = Map.of(
                new SchemaLocation(null, null, null), List.of("ENUM_VALUE")
        );
        Mockito.when(enumCollector.getEnums()).thenReturn(mockEnums);

        enumCaseGlobalLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }
}
