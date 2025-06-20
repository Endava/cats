package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.NamingArguments;
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
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Set;

@QuarkusTest
class PathCaseLinterTest {

    private TestCaseListener testCaseListener;
    private PathCaseLinter pathCaseLinterFuzzer;
    @Inject
    NamingArguments namingArguments;

    @BeforeEach
    void setup() {
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), Mockito.mock(TestReportsGenerator.class),
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        pathCaseLinterFuzzer = new PathCaseLinter(testCaseListener, namingArguments);
        ReflectionTestUtils.setField(namingArguments, "pathNaming", NamingArguments.Naming.CAMEL);
        ReflectionTestUtils.setField(namingArguments, "pathVariablesNaming", NamingArguments.Naming.CAMEL);
    }

    @ParameterizedTest
    @CsvSource({"/pets-paths,KEBAB,KEBAB", "/pets_paths,SNAKE,SNAKE", "/pets-path-links,KEBAB,KEBAB", "/pets/paths,KEBAB,KEBAB",
            "/pets/complex-paths,KEBAB,KEBAB", "/pets/{petId},KEBAB,CAMEL", "/pets/{pet_id},KEBAB,SNAKE",
            "/pets/{ped_id}/run,KEBAB,SNAKE", "/pets/run,KEBAB,KEBAB", "/admin/pets,KEBAB,KEBAB"})
    void shouldReportInfo(String path, NamingArguments.Naming pathNaming, NamingArguments.Naming pathVarsNaming) {
        ReflectionTestUtils.setField(namingArguments, "pathNaming", pathNaming);
        ReflectionTestUtils.setField(namingArguments, "pathVariablesNaming", pathVarsNaming);

        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses());
        pathItem.setPost(operation);
        FuzzingData data = FuzzingData.builder().path(path).method(HttpMethod.POST).pathItem(pathItem).reqSchema(new Schema().$ref("Cats"))
                .schemaMap(Map.of("Cats", new Schema().$ref("Cats"))).headers(Set.of()).reqSchemaName("Cats").build();

        pathCaseLinterFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Path elements follow naming conventions."));
    }

    @ParameterizedTest
    @CsvSource({"/pets_path", "/pets-path-link", "/pets/Paths", "/pets/complex-Paths", "/pets/{petid10-}", "/pets/{pet-id}"})
    void shouldReportError(String path) {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses());
        pathItem.setPost(operation);

        FuzzingData data = FuzzingData.builder().path(path).method(HttpMethod.POST).reqSchema(new Schema().$ref("Cats"))
                .schemaMap(Map.of("Cats", new Schema().$ref("Cats"))).pathItem(pathItem).headers(Set.of()).reqSchemaName("Cats").build();

        pathCaseLinterFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.eq("Path elements do not follow naming conventions: {}"), Mockito.contains(path.substring(path.lastIndexOf("/") + 1)));
    }


    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(pathCaseLinterFuzzer).hasToString(pathCaseLinterFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(pathCaseLinterFuzzer.description()).isEqualTo("verifies that path elements follow naming conventions");
    }
}
