package com.endava.cats.fuzzer.contract;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class ResponsesWithBodiesLinterTest {

    private TestCaseListener testCaseListener;

    private ResponsesWithBodiesLinter responsesWithBodiesLinterFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        responsesWithBodiesLinterFuzzer = new ResponsesWithBodiesLinter(testCaseListener);
    }

    @Test
    void shouldReturnInfo() {
        FuzzingData data = FuzzingData.builder().responses(Map.of("200", List.of("1", "2"))).build();
        responsesWithBodiesLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("All HTTP response codes have a response body"));
    }

    @Test
    void shouldReturnError() {
        FuzzingData data = FuzzingData.builder().responses(Map.of("200", List.of())).build();
        responsesWithBodiesLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(),
                Mockito.eq("Missing response body for some HTTP response codes"), Mockito.eq("The following HTTP response codes are missing a response body: {}"), Mockito.eq(List.of("200")));
    }

    @ParameterizedTest
    @CsvSource({"100", "101", "204", "205", "301", "302", "303", "304", "307", "308"})
    void shouldSkipCodes(String code) {
        FuzzingData data = FuzzingData.builder().responses(Map.of(code, List.of())).build();
        responsesWithBodiesLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("All HTTP response codes have a response body"));
    }

    @Test
    void shouldAllowNonJsonStreamContentAndLocationOn201() {
        PathItem pathItem = new PathItem();
        Operation postOp = new Operation();
        ApiResponses responses = new ApiResponses();
        responses.addApiResponse("200", new ApiResponse()
                .content(new Content().addMediaType("text/event-stream",
                        new MediaType().schema(new StringSchema()))));
        responses.addApiResponse("201", new ApiResponse());
        postOp.setResponses(responses);
        pathItem.setPost(postOp);

        FuzzingData data = FuzzingData.builder()
                .path("/chat")
                .method(HttpMethod.POST)
                .pathItem(pathItem)
                .responses(Map.of("200", List.of(), "201", List.of()))
                .responseHeaders(Map.of("201", Set.of("Location")))
                .build();

        responsesWithBodiesLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("All HTTP response codes have a response body"));
    }

    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(responsesWithBodiesLinterFuzzer).hasToString(responsesWithBodiesLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(responsesWithBodiesLinterFuzzer.description()).isEqualTo("verifies that HTTP response codes (except for 204 and 304) have a response body");
    }

    @Test
    void shouldSkipForHttpHead() {
        Assertions.assertThat(responsesWithBodiesLinterFuzzer.skipForHttpMethods()).containsOnly(HttpMethod.HEAD);
    }

    @Test
    void shouldHandleApiResponseHeadersAndExamplesAndBinaryMediaTypes() {
        PathItem pathItem = new PathItem();
        Operation getOp = new Operation();
        ApiResponses responses = new ApiResponses();

        ApiResponse resp201 = new ApiResponse()
                .headers(Map.of("Location", new Header()));
        ApiResponse resp200WithExample = new ApiResponse()
                .content(new Content().addMediaType("application/json", new MediaType().example("{\"ok\":true}")));
        ApiResponse resp202WithBinary = new ApiResponse()
                .content(new Content().addMediaType("application/octet-stream", new MediaType()));
        ApiResponse resp203EmptyMedia = new ApiResponse()
                .content(new Content().addMediaType("application/json", new MediaType()));
        ApiResponse resp206NoContent = new ApiResponse();

        responses.addApiResponse("200", resp200WithExample);
        responses.addApiResponse("201", resp201);
        responses.addApiResponse("202", resp202WithBinary);
        responses.addApiResponse("203", resp203EmptyMedia);
        responses.addApiResponse("206", resp206NoContent);
        getOp.setResponses(responses);
        pathItem.setGet(getOp);

        FuzzingData data = FuzzingData.builder()
                .path("/files")
                .method(HttpMethod.GET)
                .pathItem(pathItem)
                .responses(Map.of(
                        "200", List.of(),
                        "201", List.of(),
                        "202", List.of(),
                        "203", List.of(),
                        "206", List.of(),
                        "207", List.of()))
                .responseHeaders(Map.of())
                .build();

        responsesWithBodiesLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(
                Mockito.any(), Mockito.eq(data),
                Mockito.eq("Missing response body for some HTTP response codes"),
                Mockito.eq("The following HTTP response codes are missing a response body: {}"),
                Mockito.argThat((List<String> missing) -> missing.containsAll(List.of("203", "206", "207"))
                        && !missing.contains("200") && !missing.contains("201") && !missing.contains("202")));
    }
}
