package com.endava.cats.fuzzer.contract;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.model.CatsGlobalContext;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import javax.enterprise.inject.Instance;
import java.util.List;
import java.util.stream.Stream;

@QuarkusTest
class NamingsContractInfoFuzzerTest {

    private TestCaseListener testCaseListener;
    private NamingsContractInfoFuzzer namingsContractInfoFuzzer;

    @BeforeEach
    void setup() {
        Instance<TestCaseExporter> exporters = Mockito.mock(Instance.class);
        Mockito.when(exporters.stream()).thenReturn(Stream.of(Mockito.mock(TestCaseExporter.class)));
        testCaseListener = Mockito.spy(new TestCaseListener(Mockito.mock(CatsGlobalContext.class), Mockito.mock(ExecutionStatisticsListener.class), exporters,
                Mockito.mock(IgnoreArguments.class), Mockito.mock(ReportingArguments.class)));
        namingsContractInfoFuzzer = new NamingsContractInfoFuzzer(testCaseListener, Mockito.mock(ProcessingArguments.class));
    }

    @ParameterizedTest
    @CsvSource({"/petsPath", "/pets_path", "/pets-path-link", "/pets/Paths", "/pets/complex-Paths", "/pets/{petid10}", "/pets/{pet-id}", "/admin/admin/pets/admin"})
    void shouldReportError(String path) {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses());
        pathItem.setPost(operation);
        FuzzingData data = FuzzingData.builder().path(path).method(HttpMethod.POST).pathItem(pathItem).reqSchemaName("Cats").build();

        namingsContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.eq("Path does not follow RESTful API naming good practices: {}"), Mockito.contains(path.substring(path.lastIndexOf("/") + 1)));
    }

    @ParameterizedTest
    @CsvSource({"/pets-paths", "/pets_paths", "/pets-path-links", "/pets/paths", "/pets/complex-paths", "/pets/{petId}", "/pets/{pet_id}", "/pets/{ped_id}/run", "/pets/run", "/admin/pets"})
    void shouldReportInfo(String path) {
        PathItem pathItem = new PathItem();
        Operation operation = new Operation();
        operation.setResponses(new ApiResponses());
        pathItem.setPost(operation);
        FuzzingData data = FuzzingData.builder().path(path).method(HttpMethod.POST).pathItem(pathItem).reqSchemaName("Cats").build();

        namingsContractInfoFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.eq("Path follows the RESTful API naming good practices."));
    }

    @ParameterizedTest
    @CsvSource({"first_Payload-test", "secondpayload_tesAaa"})
    void shouldReportErrorWhenJsonObjectsNotMatchingCamelCase(String schemaName) {
        FuzzingData data = ContractFuzzerDataUtil.prepareFuzzingData(schemaName, "200");

        namingsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportError(Mockito.any(), Mockito.eq("Path does not follow RESTful API naming good practices: {}"),
                Mockito.contains(String.format("The following request/response objects are not matching CamelCase, snake_case or hyphen-case: %s", schemaName)));
    }

    @ParameterizedTest
    @CsvSource({"first_payload", "SecondPayload", "third-payload", "body_120"})
    void shouldMatchRestNamingStandards(String schemaName) {
        FuzzingData data = ContractFuzzerDataUtil.prepareFuzzingData(schemaName, "200");

        namingsContractInfoFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportInfo(Mockito.any(), Mockito.eq("Path follows the RESTful API naming good practices."));
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
