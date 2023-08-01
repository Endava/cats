package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.NamingArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.context.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
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
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@QuarkusTest
class NamingsContractInfoFuzzerTest {

    private TestCaseListener testCaseListener;
    private NamingsLinterFuzzer namingsContractInfoFuzzer;

    @Inject
    NamingArguments namingArguments;

    @BeforeEach
    void setup() {
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        Mockito.when(exporters.stream()).thenReturn(Stream.of(Mockito.mock(TestCaseExporter.class)));
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), exporters,
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        namingsContractInfoFuzzer = new NamingsLinterFuzzer(testCaseListener, Mockito.mock(ProcessingArguments.class), namingArguments);
        ReflectionTestUtils.setField(namingArguments, "pathNaming", NamingArguments.Naming.CAMEL);
        ReflectionTestUtils.setField(namingArguments, "pathVariablesNaming", NamingArguments.Naming.CAMEL);
        ReflectionTestUtils.setField(namingArguments, "queryParamsNaming", NamingArguments.Naming.SNAKE);
        ReflectionTestUtils.setField(namingArguments, "jsonPropertiesNaming", NamingArguments.Naming.CAMEL);
        ReflectionTestUtils.setField(namingArguments, "jsonObjectsNaming", NamingArguments.Naming.PASCAL);
        ReflectionTestUtils.setField(namingArguments, "headersNaming", NamingArguments.Naming.HTTP_HEADER);

    }

    @ParameterizedTest
    @CsvSource({"/petsPath", "/pets_path", "/pets-path-link", "/pets/Paths", "/pets/complex-Paths", "/pets/{petid10-}", "/pets/{pet-id}", "/admin/admin/pets/admin"})
    void shouldReportError(String path) {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses());
        pathItem.setPost(operation);

        FuzzingData data = FuzzingData.builder().path(path).method(HttpMethod.POST).reqSchema(new Schema().$ref("Cats"))
                .schemaMap(Map.of("Cats",new Schema().$ref("Cats"))).pathItem(pathItem).headers(Set.of()).reqSchemaName("Cats").build();

        namingsContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.eq("Path does not follow RESTful API naming good practices: {}"), Mockito.contains(path.substring(path.lastIndexOf("/") + 1)));
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
                .schemaMap(Map.of("Cats",new Schema().$ref("Cats"))).headers(Set.of()).reqSchemaName("Cats").build();

        namingsContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Path follows the RESTful API naming good practices."));
    }

    @ParameterizedTest
    @CsvSource({"first_Payload-test", "secondpayload_tesAaa"})
    void shouldReportErrorWhenJsonObjectsNotMatchingCamelCase(String schemaName) {
        FuzzingData data = ContractFuzzerDataUtil.prepareFuzzingData(schemaName, "200");

        namingsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.eq("Path does not follow RESTful API naming good practices: {}"),
                Mockito.contains(String.format("JSON objects not matching PascalCase: %s, %s", schemaName, schemaName)));
    }

    @ParameterizedTest
    @CsvSource({"first_payload,SNAKE", "SecondPayload,PASCAL", "third-payload,KEBAB", "body_120,KEBAB"})
    void shouldMatchRestNamingStandards(String schemaName, NamingArguments.Naming naming) {
        ReflectionTestUtils.setField(namingArguments, "jsonObjectsNaming", naming);
        FuzzingData data = ContractFuzzerDataUtil.prepareFuzzingData(schemaName, "200");

        namingsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultInfo(Mockito.any(), Mockito.any(), Mockito.eq("Path follows the RESTful API naming good practices."));
    }


    @Test
    void shouldReturnSimpleClassNameForToString() {
        Assertions.assertThat(namingsContractInfoFuzzer).hasToString(namingsContractInfoFuzzer.getClass().getSimpleName());
    }

    @Test
    void shouldReturnMeaningfulDescription() {
        Assertions.assertThat(namingsContractInfoFuzzer.description()).isEqualTo("verifies that all OpenAPI contract elements follow RESTful API naming good practices");
    }
}
