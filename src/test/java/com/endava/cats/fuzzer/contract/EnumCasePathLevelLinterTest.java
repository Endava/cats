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
class EnumCasePathLevelLinterTest {

    private EnumCasePathLevelLinter enumCasePathLevelLinter;
    private EnumCollector enumCollector;
    @Inject
    NamingArguments namingArguments;
    private TestCaseListener testCaseListener;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        enumCollector = Mockito.mock(EnumCollector.class);
        enumCasePathLevelLinter = new EnumCasePathLevelLinter(testCaseListener, namingArguments, enumCollector);
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(enumCasePathLevelLinter.description())
                .isEqualTo("verifies enum-case consistency only for inline schemas of the current path/method");
    }

    @Test
    void shouldReturnRunKey() {
        FuzzingData data = FuzzingData.builder().path("/testPath").method(HttpMethod.GET).build();
        enumCasePathLevelLinter.fuzz(data); // Indirectly tests runKey
        Assertions.assertThat(enumCasePathLevelLinter.getContext().runKeyProvider().apply(data))
                .isEqualTo("/testPathGET");
    }

    @Test
    void shouldCollectEnumsForPathLevelComponents() {
        SchemaLocation expectedLocation = new SchemaLocation("/testPath", "GET", null, null);
        Map<SchemaLocation, List<String>> mockEnums = Map.of(
                expectedLocation, List.of("ENUM_VALUE")
        );
        Mockito.when(enumCollector.getEnums()).thenReturn(mockEnums);

        Map<SchemaLocation, List<String>> result = enumCasePathLevelLinter.getContext().collector().get();

        Assertions.assertThat(result).containsKey(expectedLocation);
        Assertions.assertThat(result.get(expectedLocation)).contains("ENUM_VALUE");
    }

    @Test
    void shouldHandleEmptyEnums() {
        Mockito.when(enumCollector.getEnums()).thenReturn(Map.of());

        Map<SchemaLocation, List<String>> result = enumCasePathLevelLinter.getContext().collector().get();

        Assertions.assertThat(result).isEmpty();
    }

    @Test
    void shouldExecuteTestListener() {
        FuzzingData data = FuzzingData.builder().path("/testPath").method(HttpMethod.GET).build();
        SchemaLocation expectedLocation = new SchemaLocation("/testPath", "GET", null, null);
        Map<SchemaLocation, List<String>> mockEnums = Map.of(
                expectedLocation, List.of("ENUM_VALUE")
        );
        Mockito.when(enumCollector.getEnums()).thenReturn(mockEnums);

        enumCasePathLevelLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldFilterValidateAndFormatViolations() {
        // Mock SchemaLocation to match the test path and method
        SchemaLocation mockLoc = Mockito.mock(SchemaLocation.class);
        Mockito.when(mockLoc.matchesPathAndMethod("/testPath", HttpMethod.GET)).thenReturn(true);
        Mockito.when(mockLoc.toString()).thenReturn("/testPath:GET");

        List<String> enumValues = List.of("VALID_ENUM", "invalidEnum");
        Map<SchemaLocation, List<String>> enums = Map.of(mockLoc, enumValues);
        Mockito.when(enumCollector.getEnums()).thenReturn(enums);

        FuzzingData data = FuzzingData.builder().path("/testPath").method(HttpMethod.GET).build();

        enumCasePathLevelLinter.fuzz(data);

        Mockito.verify(testCaseListener).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Enum-case mismatches detected"),
                Mockito.argThat((String msg) -> msg != null && msg.contains("/testPath:GET => [VALID_ENUM, invalidEnum]"))
        );
    }
}