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
class SearchAndFilterExpressionFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private SearchAndFilterExpressionFieldsFuzzer fuzzer;

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
        fuzzer = new SearchAndFilterExpressionFieldsFuzzer(executor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"search", "q", "query", "filter", "keyword", "keywords", "pattern", "regex", "match", "term", "where", "like", "contains", "nested#search_term"})
    void shouldMatchSearchAndFilterFields(String fieldName) {
        boolean expectedMatch = !fieldName.endsWith("search_term");
        Assertions.assertThat(SearchAndFilterExpressionFieldsFuzzer.isSearchOrFilterField(fieldName)).isEqualTo(expectedMatch);
    }

    @Test
    void shouldFuzzSearchFieldsAndExpect4xxOr2xx() {
        CatsResponse response = CatsResponse.builder().responseCode(400).body("{}").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(response);

        FuzzingData data = FuzzingData.builder()
                .path("/items")
                .method(HttpMethod.GET)
                .reqSchema(new Schema<>())
                .requestContentTypes(List.of("application/json"))
                .payload("{\"q\":\"shoes\",\"category\":\"apparel\"}")
                .allFieldsByHttpMethod(Set.of("q", "category"))
                .requestPropertyTypes(Map.of("q", new StringSchema(), "category", new StringSchema()))
                .build();

        fuzzer.fuzz(data);

        Mockito.verify(serviceCaller, Mockito.times(3)).call(Mockito.any());
        Mockito.verify(testCaseListener, Mockito.times(3)).reportResult(
                Mockito.any(), Mockito.eq(data), Mockito.eq(response), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX));
    }

    @Test
    void shouldSkipNonSearchFields() {
        FuzzingData data = FuzzingData.builder()
                .path("/items")
                .method(HttpMethod.GET)
                .payload("{\"category\":\"apparel\"}")
                .allFieldsByHttpMethod(Set.of("category"))
                .requestPropertyTypes(Map.of("category", new StringSchema()))
                .build();

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(serviceCaller);
    }

    @Test
    void shouldFuzzArrayOfStringFilterParameter() {
        CatsResponse response = CatsResponse.builder().responseCode(200).body("{}").build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(response);
        Schema<?> arraySchema = new Schema<>().type("array").items(new StringSchema());

        FuzzingData data = FuzzingData.builder()
                .path("/items")
                .method(HttpMethod.GET)
                .reqSchema(new Schema<>())
                .requestContentTypes(List.of("application/json"))
                .payload("{\"filter\":[\"status:active\"]}")
                .allFieldsByHttpMethod(Set.of("filter"))
                .requestPropertyTypes(Map.of("filter", arraySchema))
                .build();

        fuzzer.fuzz(data);

        Mockito.verify(serviceCaller, Mockito.times(3)).call(Mockito.any());
    }

    @Test
    void shouldReturnDescriptionAndToString() {
        Assertions.assertThat(fuzzer.description()).isNotBlank();
        Assertions.assertThat(fuzzer).hasToString("SearchAndFilterExpressionFieldsFuzzer");
    }
}
