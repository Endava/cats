package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class CursorAndPaginationTokenFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private CursorAndPaginationTokenFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            Runnable testLogic = invocation.getArgument(2);
            testLogic.run();
            return null;
        }).when(testCaseListener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.when(testCaseListener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        FieldsIteratorExecutor executor = new FieldsIteratorExecutor(
                serviceCaller, testCaseListener, Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        fuzzer = new CursorAndPaginationTokenFieldsFuzzer(executor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cursor", "next_cursor", "prevCursor", "page_token", "nextPageToken", "continuationToken", "starting_after", "ending_before", "scrollId", "search_after"})
    void shouldRecognizeCursorFields(String fieldName) {
        Assertions.assertThat(CursorAndPaginationTokenFieldsFuzzer.isCursorField(fieldName)).isTrue();
    }

    @Test
    void shouldFuzzCursorFieldsAndExpect4xxOr2xx() {
        CatsResponse response = CatsResponse.builder().responseCode(400).body("{}").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(response);

        FuzzingData data = FuzzingData.builder()
                .path("/events")
                .method(HttpMethod.GET)
                .reqSchema(new Schema<>())
                .requestContentTypes(List.of("application/json"))
                .payload("{\"next_cursor\":\"abc123\",\"limit\":10}")
                .allFieldsByHttpMethod(Set.of("next_cursor", "limit"))
                .requestPropertyTypes(Map.of("next_cursor", new StringSchema(), "limit", new Schema<>().type("integer")))
                .build();

        fuzzer.fuzz(data);

        Mockito.verify(serviceCaller, Mockito.times(4)).call(Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(4)).reportResult(
                Mockito.any(), Mockito.eq(data), Mockito.eq(response), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX));
    }

    @Test
    void shouldSkipNonCursorFields() {
        FuzzingData data = FuzzingData.builder()
                .path("/events")
                .method(HttpMethod.GET)
                .payload("{\"status\":\"OPEN\"}")
                .allFieldsByHttpMethod(Set.of("status"))
                .requestPropertyTypes(Map.of("status", new StringSchema()))
                .build();

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldReturnDescriptionAndToString() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("CursorAndPaginationTokenFieldsFuzzer");
    }
}
