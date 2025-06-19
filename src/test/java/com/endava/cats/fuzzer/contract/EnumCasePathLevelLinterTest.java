package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.NamingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaWalker;
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
class EnumCasePathLevelLinterTest {

    private EnumCasePathLevelLinter enumCasePathLevelLinter;
    private EnumCollector enumCollector;
    private TestCaseListener testCaseListener;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        NamingArguments namingArguments = Mockito.mock(NamingArguments.class);
        enumCollector = Mockito.mock(EnumCollector.class);
        enumCasePathLevelLinter = new EnumCasePathLevelLinter(testCaseListener, namingArguments, enumCollector);
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(enumCasePathLevelLinter.description()).isEqualTo("Verifies enum-case consistency only for inline schemas of the current path/method.");
    }

    @Test
    void shouldReturnRunKey() {
        FuzzingData data = FuzzingData.builder().path("/testPath").method(HttpMethod.GET).build();
        Assertions.assertThat(enumCasePathLevelLinter.runKey(data)).isEqualTo("/testPathGET");
    }

    @Test
    void shouldSelectEnumsForPathLevelComponents() {
        FuzzingData data = FuzzingData.builder().path("/testPath").method(HttpMethod.GET).build();
        SchemaWalker.SchemaLocation expectedLocation = new SchemaWalker.SchemaLocation("/testPath", "GET", null);
        Map<SchemaWalker.SchemaLocation, List<String>> mockEnums = Map.of(
                expectedLocation, List.of("ENUM_VALUE")
        );
        Mockito.when(enumCollector.getEnums()).thenReturn(mockEnums);

        Map<SchemaWalker.SchemaLocation, List<String>> result = enumCasePathLevelLinter.selectEnums(data);

        Assertions.assertThat(result).containsKey(expectedLocation);
        Assertions.assertThat(result.get(expectedLocation)).contains("ENUM_VALUE");
    }

    @Test
    void shouldHandleEmptyEnums() {
        FuzzingData data = FuzzingData.builder().path("/testPath").method(HttpMethod.GET).build();
        Mockito.when(enumCollector.getEnums()).thenReturn(Map.of());

        Map<SchemaWalker.SchemaLocation, List<String>> result = enumCasePathLevelLinter.selectEnums(data);

        Assertions.assertThat(result).isEmpty();
    }

    @Test
    void shouldExecuteTestListener() {
        FuzzingData data = FuzzingData.builder().path("/testPath").method(HttpMethod.GET).build();
        SchemaWalker.SchemaLocation expectedLocation = new SchemaWalker.SchemaLocation("GET", "/testPath", null);
        Map<SchemaWalker.SchemaLocation, List<String>> mockEnums = Map.of(
                expectedLocation, List.of("ENUM_VALUE")
        );
        Mockito.when(enumCollector.getEnums()).thenReturn(mockEnums);

        enumCasePathLevelLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }
}
