package com.endava.cats.fuzzer.contract;

import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

@QuarkusTest
class PaginationConsistencyLinterTest {
    private TestCaseListener testCaseListener;
    private PaginationConsistencyLinter linter;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        linter = new PaginationConsistencyLinter(testCaseListener);
    }

    @Test
    void shouldReportInfoWhenPaginationParametersAndEnvelopesAreConsistent() {
        Schema<?> envelope = new Schema<>().type("object").properties(Map.of(
                "items", new Schema<>().type("array").items(new Schema<>().type("string")),
                "total", new Schema<>().type("integer")
        ));

        Operation getBooks = paginatedGetOperation(List.of("page", "size"), envelope);
        Operation getAuthors = paginatedGetOperation(List.of("page", "size"), envelope);

        OpenAPI openAPI = new OpenAPI().paths(new Paths()
                .addPathItem("/books", new PathItem().get(getBooks))
                .addPathItem("/authors", new PathItem().get(getAuthors)));

        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("All paginated GET operations use consistent parameter conventions and structured responses")
        );
    }

    @Test
    void shouldReportWarnWhenPaginationParamsMixStylesOrReturnBareArray() {
        Schema<?> bareArray = new Schema<>().type("array").items(new Schema<>().type("string"));
        Schema<?> envelope = new Schema<>().type("object").properties(Map.of(
                "items", bareArray,
                "total", new Schema<>().type("integer")
        ));

        Operation getBooks = paginatedGetOperation(List.of("page", "size"), bareArray);
        Operation getAuthors = paginatedGetOperation(List.of("offset", "limit"), envelope);

        OpenAPI openAPI = new OpenAPI().paths(new Paths()
                .addPathItem("/books", new PathItem().get(getBooks))
                .addPathItem("/authors", new PathItem().get(getAuthors)));

        FuzzingData data = FuzzingData.builder().openApi(openAPI).build();
        linter.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultWarn(
                Mockito.any(),
                Mockito.eq(data),
                Mockito.eq("Pagination consistency issues detected"),
                Mockito.argThat((String msg) -> msg.contains("GET /books declares pagination parameters [page, size] but its 2xx response is a bare array")
                        && msg.contains("Inconsistent pagination query parameter sets across endpoints"))
        );
    }

    private static Operation paginatedGetOperation(List<String> paramNames, Schema<?> responseSchema) {
        List<Parameter> params = paramNames.stream()
                .map(name -> new Parameter().name(name).in("query").schema(new Schema<>().type("integer")))
                .toList();
        ApiResponse resp = new ApiResponse().content(new Content().addMediaType("application/json",
                new MediaType().schema(responseSchema)));
        return new Operation().parameters(params).responses(new ApiResponses().addApiResponse("200", resp));
    }

    @Test
    void shouldReturnDescriptionAndClassName() {
        Assertions.assertThat(linter).hasToString("PaginationConsistencyLinter");
        Assertions.assertThat(linter.description())
                .isEqualTo("verifies that paginated GET operations across the contract use consistent pagination parameter names and return pagination metadata rather than a bare array");
    }
}
