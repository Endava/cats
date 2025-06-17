package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilterArguments;
import com.endava.cats.args.ProcessingArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
class RemoveFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    @InjectSpy
    private TestCaseListener testCaseListener;
    private FilterArguments filterArguments;
    private ProcessingArguments processingArguments;
    private RemoveFieldsFuzzer removeFieldsFuzzer;

    private FuzzingData data;
    private CatsResponse catsResponse;

    @BeforeEach
    void setup() {
        filterArguments = Mockito.mock(FilterArguments.class);
        processingArguments = Mockito.mock(ProcessingArguments.class);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        removeFieldsFuzzer = new RemoveFieldsFuzzer(serviceCaller, testCaseListener, filterArguments, processingArguments);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
    }

    @Test
    void shouldSkipFuzzerIfSkippedTests() {
        data = Mockito.mock(FuzzingData.class);
        Mockito.when(processingArguments.getFieldsFuzzingStrategy()).thenReturn(ProcessingArguments.SetFuzzingStrategy.ONEBYONE);
        Mockito.when(data.getAllFields(Mockito.any(), Mockito.anyInt())).thenReturn(Collections.singleton(Collections.singleton("id")));
        Mockito.when(filterArguments.getSkipFields()).thenReturn(Collections.singletonList("id"));
        removeFieldsFuzzer.fuzz(data);

        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void givenARequest_whenApplyingTheRemoveFieldsFuzzer_thenTestCasesAreCorrectlyExecuted() {
        setup("{\"field\":\"oldValue\"}");
        removeFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
        Mockito.verify(testCaseListener, Mockito.times(2)).skipTest(Mockito.any(), Mockito.eq("Field is from a different ANY_OF or ONE_OF payload"));
    }

    @Test
    void shouldRunFuzzerWhenPayloadIsArray() {
        setup("[{\"field\":\"oldValue\"}, {\"field\":\"newValue\"}]");
        removeFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
        Mockito.verify(testCaseListener, Mockito.times(2)).skipTest(Mockito.any(), Mockito.eq("Field is from a different ANY_OF or ONE_OF payload"));
    }

    @Test
    void givenARemoveFieldsFuzzerInstance_whenCallingTheMethodInheritedFromTheBaseClass_thenTheMethodsAreProperlyOverridden() {
        Assertions.assertThat(removeFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(removeFieldsFuzzer).hasToString(removeFieldsFuzzer.getClass().getSimpleName());
        Assertions.assertThat(removeFieldsFuzzer.skipForHttpMethods()).containsExactly(HttpMethod.GET, HttpMethod.DELETE);
    }

    private void setup(String payload) {
        catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        Schema schema = new ObjectSchema();
        schema.setProperties(this.createPropertiesMap());
        schema.setRequired(Collections.singletonList("field"));
        Mockito.when(processingArguments.getFieldsFuzzingStrategy()).thenReturn(ProcessingArguments.SetFuzzingStrategy.ONEBYONE);
        data = FuzzingData.builder().path("path1").method(HttpMethod.POST).payload(payload).
                responses(responses).reqSchema(schema).schemaMap(this.createPropertiesMap()).responseCodes(Collections.singleton("200"))
                .requestContentTypes(List.of("application/json")).requestPropertyTypes(this.createPropertiesMap()).build();
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
    }

    private Map<String, Schema> createPropertiesMap() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("field", new StringSchema());
        schemaMap.put("anotherField#test", new StringSchema());
        schemaMap.put("anotherField", new StringSchema());

        return schemaMap;
    }
}
