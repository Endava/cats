package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.enterprise.inject.Instance;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@QuarkusTest
class PathPluralsLinterFuzzerTest {

    private TestCaseListener testCaseListener;
    private PathPluralsLinterFuzzer pathPluralsLinterFuzzer;

    @BeforeEach
    void setup() {
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        Mockito.when(exporters.stream()).thenReturn(Stream.of(Mockito.mock(TestCaseExporter.class)));
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), exporters,
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        pathPluralsLinterFuzzer = new PathPluralsLinterFuzzer(testCaseListener);

    }

    @ParameterizedTest
    @CsvSource({"/users", "/users/{userId}/posts", "/users/{userId}/posts/cancel", "/users/addUser", "/tasks/cancelTask", "/{userId}/permissions/csv"})
    void shouldMatchPluralsNamingStandards(String path) {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses());
        pathItem.setPost(operation);

        FuzzingData data = FuzzingData.builder().path(path).method(HttpMethod.POST).reqSchema(new Schema().$ref("Cats"))
                .schemaMap(Map.of("Cats", new Schema().$ref("Cats"))).pathItem(pathItem).headers(Set.of()).reqSchemaName("Cats").build();
        pathPluralsLinterFuzzer.fuzz(data);

        pathPluralsLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Path elements use pluralization to describe resources."));
    }

    @ParameterizedTest
    @CsvSource({"/v1/petsPath", "/admin/admin/pets/admin", "/findById", "/executeTask", "/addUser", "/user", "/{userId}/permissions/csv/csv"})
    void shouldNotMatchPluralsNounsNamingStandards(String path) {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses());
        pathItem.setPost(operation);

        FuzzingData data = FuzzingData.builder().path(path).method(HttpMethod.POST).reqSchema(new Schema().$ref("Cats"))
                .schemaMap(Map.of("Cats", new Schema().$ref("Cats"))).pathItem(pathItem).headers(Set.of()).reqSchemaName("Cats").build();
        pathPluralsLinterFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1))
                .reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Path elements not plural"),
                        Mockito.eq("Some of the following path elements are not using pluralization: {}"), Mockito.contains(path.substring(path.lastIndexOf("/") + 1)));
    }


    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(pathPluralsLinterFuzzer).hasToString(pathPluralsLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(pathPluralsLinterFuzzer.description()).isEqualTo("verifies that path elements uses pluralization to describe resources");
    }
}
