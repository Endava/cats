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
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@QuarkusTest
class SchemaValueConformanceLinterTest {
    private TestCaseListener testCaseListener;
    private SchemaValueConformanceLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new SchemaValueConformanceLinter(testCaseListener);
    }

    @Test
    void shouldReportInfoWhenAllValuesConform() {
        Schema<?> size = new Schema<>().type("integer").minimum(BigDecimal.ONE).maximum(BigDecimal.TEN)._default(5).example(3);
        Schema<?> code = new Schema<>().type("string").maxLength(3).pattern("[A-Z]").example("USD");
        Schema<?> tags = new Schema<>().type("array").maxItems(2).items(new Schema<>().type("string")).example(List.of("a", "b"));
        Schema<?> order = new Schema<>().type("object").properties(Map.of("size", size, "code", code, "tags", tags))
                .example(Map.of("size", 2, "code", "EUR"));
        FuzzingData data = FuzzingData.builder().openApi(new OpenAPI().components(new Components().addSchemas("Order", order))).build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data), Mockito.eq("All default and example values conform to their schemas"));
    }

    @Test
    void shouldReportErrorWhenDefaultsAndExamplesViolateTheirSchema() throws Exception {
        FuzzingData data = FuzzingData.builder().openApi(parse("src/test/resources/schema-value-conformance-31.yml")).build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Default values not conforming to their schema found"),
                Mockito.argThat((String msg) -> msg.contains("Schema 'Order.createdAt' default \"Current datetime\": value is not a valid 'date-time'")
                        && msg.contains("Schema 'Order.size' default 500: value 500 is greater than maximum 100")
                        && msg.contains("Schema 'Order.id' example \"4f553d2e-e328-420f-9458-bd7120fce0d9 \": value is not a valid 'uuid'")
                        && msg.contains("Schema 'Order.price' example \"12.5\": value is string but schema type is [number]")
                        && msg.contains("Schema 'Order.code' example \"usdx\": value length 4 is greater than maxLength 3")
                        && msg.contains("Schema 'Order.code' example \"usdx\": value does not match pattern '^[A-Z]+$'")
                        && msg.contains("Schema 'Order.deliveryDate' example \"2024-13-45\": value is not a valid 'date'")
                        && msg.contains("value has 3 items, more than maxItems 2")
                        && msg.contains("value contains duplicate items but uniqueItems is true")
                        && msg.contains("'[2]' is string but schema type is [integer]")
                        && msg.contains("Schema 'Order.owner' example \"prev owner\": value is string but schema type is [object]")
                        && msg.contains("'version' is string but schema type is [integer]")
                        && !msg.contains("Order.settings")
                        && msg.contains("'zip' length 7 is greater than maxLength 5")
                        && msg.contains("'kind' \"other\" is not one of [home, work]")
                        && msg.contains("Schema 'Order.discount' example 0: value 0 is not greater than exclusiveMinimum 0")
                        && msg.contains("Schema 'Order.discount' example 0.75: value 0.75 is not a multiple of 0.5")
                        && msg.contains("Parameter 'limit' (in: query) of GET /orders example \"abc\": value is string but schema type is [integer]")
                        && !msg.contains("Order.quantity")
                        && !msg.contains("Order.note")
                        && !msg.contains("Order.status")
                        && !msg.contains("Parameter 'tags'"))
        );
    }

    @Test
    void shouldReportWarnWhenOnlyExamplesViolateTheirSchema() throws Exception {
        FuzzingData data = FuzzingData.builder().openApi(parse("src/test/resources/schema-value-conformance-30.yml")).build();

        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Example values not conforming to their schema found"),
                Mockito.eq("Schema 'Order.size' example 100: value 100 is greater than exclusive maximum 100")
        );
    }

    @Test
    void shouldSkipWhenOpenApiNotAvailable() {
        linter.process(FuzzingData.builder().build());

        Mockito.verify(testCaseListener, Mockito.times(1)).skipTest(Mockito.any(), Mockito.eq("OpenAPI specification not available"));
    }

    @Test
    void shouldReturnSimpleClassNameAndDescription() {
        Assertions.assertThat(linter).hasToString("SchemaValueConformanceLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("detects default and example values that violate the type, format, length, range, pattern or item constraints of their own schema");
    }

    private static OpenAPI parse(String contract) throws Exception {
        return new OpenAPIParser().readContents(Files.readString(Path.of(contract)), null, null).getOpenAPI();
    }
}
