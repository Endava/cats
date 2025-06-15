package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

@QuarkusTest
class PathNounsLinterTest {

    private TestCaseListener testCaseListener;
    private PathNounsLinter pathNounsLinterFuzzer;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), Mockito.mock(TestReportsGenerator.class),
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        pathNounsLinterFuzzer = new PathNounsLinter(testCaseListener);
    }

    @ParameterizedTest
    @CsvSource({"/users", "/users/cancel", "/users/{userId}/posts", "/users/{userId}/posts/cancel"})
    void shouldMatchNounsNamingStandards(String path) {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses());
        pathItem.setPost(operation);

        FuzzingData data = FuzzingData.builder().path(path).method(HttpMethod.POST).reqSchema(new Schema().$ref("Cats"))
                .schemaMap(Map.of("Cats", new Schema().$ref("Cats"))).pathItem(pathItem).headers(Set.of()).reqSchemaName("Cats").build();
        pathNounsLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Path elements use nouns to describe resources."));
    }

    @ParameterizedTest
    @CsvSource({"/findById", "/executeTask", "/addUser", "/users/addUser", "/tasks/cancelTask"})
    void shouldNotMatchNounsNamingStandards(String path) {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses());
        pathItem.setPost(operation);

        FuzzingData data = FuzzingData.builder().path(path).method(HttpMethod.POST).reqSchema(new Schema().$ref("Cats"))
                .schemaMap(Map.of("Cats", new Schema().$ref("Cats"))).pathItem(pathItem).headers(Set.of()).reqSchemaName("Cats").build();
        pathNounsLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Path elements not nouns"),
                        Mockito.eq("The following path elements are not nouns: {}"), Mockito.contains(path.substring(path.lastIndexOf("/") + 1)));
    }


    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(pathNounsLinterFuzzer).hasToString(pathNounsLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(pathNounsLinterFuzzer.description()).isEqualTo("verifies that path elements use nouns to describe resources");
    }
}
