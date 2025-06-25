package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.openapi.handler.api.SchemaLocation;
import com.endava.cats.openapi.handler.collector.StringSchemaCollector;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

@QuarkusTest
class StringSchemaPathLevelLinterTest {

    private TestCaseListener testCaseListener;
    private StringSchemaPathLevelLinter stringSchemaPathLevelLinter;
    private StringSchemaCollector stringSchemaCollector;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), Mockito.mock(TestReportsGenerator.class),
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        stringSchemaCollector = Mockito.mock(StringSchemaCollector.class);
        stringSchemaPathLevelLinter = new StringSchemaPathLevelLinter(testCaseListener, stringSchemaCollector);
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(stringSchemaPathLevelLinter.description())
                .isEqualTo("verifies that string schemas specify either maxLength or enum for inline schemas of the current path/method");
    }

    @Test
    void shouldReturnRunKey() {
        FuzzingData data = FuzzingData.builder().path("/test").method(HttpMethod.GET).build();
        stringSchemaPathLevelLinter.fuzz(data); // Indirectly tests runKey
        Assertions.assertThat(stringSchemaPathLevelLinter.getContext().runKeyProvider().apply(data))
                .isEqualTo(data.getPath() + data.getMethod());
    }

    @Test
    void shouldCollectStringSchemasForPathLevelComponents() {
        Map<SchemaLocation, Schema<?>> mockSchemas = Map.of(
                new SchemaLocation(null, null, null, null), new Schema()
        );
        Mockito.when(stringSchemaCollector.getStringSchemas()).thenReturn(mockSchemas);

        Map<SchemaLocation, Schema<?>> result = stringSchemaPathLevelLinter.getContext().collector().get();

        Assertions.assertThat(result).containsKey(new SchemaLocation(null, null, null, null));
        Assertions.assertThat(result.get(new SchemaLocation(null, null, null, null))).isNotNull();
    }

    @Test
    void shouldHandleEmptyStringSchemas() {
        Mockito.when(stringSchemaCollector.getStringSchemas()).thenReturn(Map.of());

        Map<SchemaLocation, Schema<?>> result = stringSchemaPathLevelLinter.getContext().collector().get();

        Assertions.assertThat(result).isEmpty();
    }

    @Test
    void shouldExecuteTestListener() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<SchemaLocation, Schema<?>> mockSchemas = Map.of(
                new SchemaLocation(null, null, null, null), new Schema()
        );
        Mockito.when(stringSchemaCollector.getStringSchemas()).thenReturn(mockSchemas);

        stringSchemaPathLevelLinter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }
}
