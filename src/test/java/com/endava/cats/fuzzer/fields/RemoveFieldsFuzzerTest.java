package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
class RemoveFieldsFuzzerTest {


    @MockBean
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private BuildProperties buildProperties;

    @SpyBean
    private CatsUtil catsUtil;

    private RemoveFieldsFuzzer removeFieldsFuzzer;

    private FuzzingData data;
    private CatsResponse catsResponse;

    @BeforeAll
    static void init() {
        System.setProperty("name", "cats");
        System.setProperty("version", "4.3.2");
        System.setProperty("time", "100011111");
    }

    @BeforeEach
    void setup() {
        removeFieldsFuzzer = new RemoveFieldsFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    void givenARequest_whenApplyingTheRemoveFieldsFuzzer_thenTestCasesAreCorrectlyExecuted() {
        setup("{\"field\":\"oldValue\"}");
        removeFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX));
        Mockito.verify(testCaseListener, Mockito.times(2)).skipTest(Mockito.any(), Mockito.eq("Field is from a different ANY_OF or ONE_OF payload"));
    }

    @Test
    void shouldRunFuzzerWhenPayloadIsArray() {
        setup("[{\"field\":\"oldValue\"}, {\"field\":\"newValue\"}]");
        removeFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX));
        Mockito.verify(testCaseListener, Mockito.times(2)).skipTest(Mockito.any(), Mockito.eq("Field is from a different ANY_OF or ONE_OF payload"));
    }

    @Test
    void givenARemoveFieldsFuzzerInstance_whenCallingTheMethodInheritedFromTheBaseClass_thenTheMethodsAreProperlyOverridden() {
        Assertions.assertThat(removeFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(removeFieldsFuzzer).hasToString(removeFieldsFuzzer.getClass().getSimpleName());
        Assertions.assertThat(removeFieldsFuzzer.skipFor()).containsExactly(HttpMethod.GET, HttpMethod.DELETE);
    }

    private void setup(String payload) {
        catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        Schema schema = new ObjectSchema();
        schema.setProperties(this.createPropertiesMap());
        schema.setRequired(Collections.singletonList("field"));
        ReflectionTestUtils.setField(removeFieldsFuzzer, "fieldsFuzzingStrategy", "ONEBYONE");
        data = FuzzingData.builder().path("path1").method(HttpMethod.POST).payload(payload).
                responses(responses).reqSchema(schema).catsUtil(catsUtil).schemaMap(this.createPropertiesMap()).responseCodes(Collections.singleton("200")).build();
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);
    }

    private Map<String, Schema> createPropertiesMap() {
        Map<String, Schema> schemaMap = new HashMap<>();
        schemaMap.put("field", new StringSchema());
        schemaMap.put("anotherField#test", new StringSchema());
        schemaMap.put("anotherField", new StringSchema());

        return schemaMap;
    }
}
