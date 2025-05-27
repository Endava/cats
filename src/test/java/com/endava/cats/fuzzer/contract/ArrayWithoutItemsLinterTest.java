package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseExporterHtmlJs;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.enterprise.inject.Instance;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.stream.Stream;

@QuarkusTest
class ArrayWithoutItemsLinterTest {
    private TestCaseListener testCaseListener;
    private ArrayWithoutItemsLinter linter;

    @BeforeEach
    void setup() {
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        TestCaseExporter exporter = Mockito.mock(TestCaseExporterHtmlJs.class);
        Mockito.when(exporters.stream()).thenReturn(Stream.of(exporter));
        testCaseListener = Mockito.spy(new TestCaseListener(null, Mockito.mock(ExecutionStatisticsListener.class), exporters,
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        linter = new ArrayWithoutItemsLinter(testCaseListener);
    }

    @Test
    void shouldWarnWhenArraySchemaHasNoItems() {
        PathItem pathItem = setupPathItem(null);

        FuzzingData data = FuzzingData.builder()
                .path("/users")
                .method(com.endava.cats.http.HttpMethod.GET)
                .pathItem(pathItem)
                .build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Array schemas missing 'items' definitions"),
                Mockito.contains("Response 200 content-type 'application/json'")
        );
    }

    private static PathItem setupPathItem(Schema<?> items) {
        ArraySchema schema = new ArraySchema();
        schema.setItems(items);
        MediaType media = new MediaType();
        media.setSchema(schema);
        Content content = new Content();
        content.addMediaType("application/json", media);

        ApiResponse response = new ApiResponse();
        response.setContent(content);

        ApiResponses responses = new ApiResponses();
        responses.put("200", response);

        Operation operation = new Operation();
        operation.setResponses(responses);

        PathItem pathItem = new PathItem();
        pathItem.setGet(operation);
        return pathItem;
    }

    @Test
    void shouldReportInfoWhenArrayHasItemsDefined() {
        PathItem pathItem = setupPathItem(new Schema<>());

        FuzzingData data = FuzzingData.builder()
                .path("/valid")
                .method(com.endava.cats.http.HttpMethod.GET)
                .pathItem(pathItem)
                .build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener).reportResultInfo(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("All array schemas define an 'items' schema")
        );
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("ArrayWithoutItemsLinter");
        Assertions.assertThat(linter.description()).isEqualTo("detects array schemas that do not define an 'items' property, which results in ambiguous data structures for arrays");
    }
}