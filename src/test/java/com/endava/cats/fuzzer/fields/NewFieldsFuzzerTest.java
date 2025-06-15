package com.endava.cats.fuzzer.fields;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import com.endava.cats.util.JsonUtils;
import com.google.gson.JsonElement;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
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

import static com.endava.cats.util.CatsDSLWords.NEW_FIELD;

@QuarkusTest
class NewFieldsFuzzerTest {
    @InjectSpy
    private TestCaseListener testCaseListener;
    private ServiceCaller serviceCaller;

    private NewFieldsFuzzer newFieldsFuzzer;

    private FuzzingData data;
    private CatsResponse catsResponse;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        newFieldsFuzzer = new NewFieldsFuzzer(serviceCaller, testCaseListener);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
    }

    @Test
    void shouldRunForEmptyPayload() {
        newFieldsFuzzer.fuzz(Mockito.mock(FuzzingData.class));

        Mockito.verify(testCaseListener).reportResult(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldHaveProperDescriptionAndToString() {
        Assertions.assertThat(newFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(newFieldsFuzzer).hasToString(newFieldsFuzzer.getClass().getSimpleName());
    }

    @Test
    void givenAPOSTRequest_whenCallingTheNewFieldsFuzzer_thenTestCasesAreCorrectlyExecutedAndExpectA4XX() {
        setup(HttpMethod.POST);
        newFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    @Test
    void givenAGETRequest_whenCallingTheNewFieldsFuzzer_thenTestCasesAreCorrectlyExecutedAndExpectA2XX() {
        setup(HttpMethod.GET);
        newFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.TWOXX));
    }

    @Test
    void shouldAddANewFieldToFuzzToSingleElement() {
        setup(HttpMethod.POST);
        String element = newFieldsFuzzer.addNewField(data);

        Assertions.assertThat(element).contains(NEW_FIELD).doesNotContain(NEW_FIELD + "random");
    }

    @Test
    void shouldAddANewFieldToFuzzToArray() {
        String payload = "[{ 'field': 'value1'}, {'field': 'value2'}]";
        data = FuzzingData.builder().payload(payload).reqSchema(new StringSchema()).build();
        JsonElement element = JsonUtils.parseAsJsonElement(newFieldsFuzzer.addNewField(data));

        Assertions.assertThat(element.getAsJsonArray().get(0).getAsJsonObject().get(NEW_FIELD)).isNotNull();
        Assertions.assertThat(element.getAsJsonArray().get(0).getAsJsonObject().get(NEW_FIELD + "random")).isNull();
    }

    @Test
    void shouldAddNewFieldWhenPayloadEmpty() {
        data = FuzzingData.builder().path("{}").reqSchema(new StringSchema()).build();
        String fuzzedJson = newFieldsFuzzer.addNewField(data);

        Assertions.assertThat(fuzzedJson).isEqualTo("{\"catsFuzzyField\":\"catsFuzzyField\"}");
    }

    @Test
    void shouldNotRunWhenPayloadIsArrayOfPrimitives() {
        String payload = "[1, 2, 3]";
        data = FuzzingData.builder().payload(payload).reqSchema(new StringSchema()).build();
        newFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(0)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    @Test
    void shouldNotRunForPrimitivePayload() {
        String payload = "1";
        data = FuzzingData.builder().payload(payload).reqSchema(new StringSchema()).build();
        newFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(0)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX));
    }

    private void setup(HttpMethod method) {
        catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        data = FuzzingData.builder().path("path1").method(method).payload("{'field':'oldValue'}").
                responses(responses).responseCodes(Collections.singleton("200")).reqSchema(new StringSchema())
                .requestContentTypes(List.of("application/json")).build();

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
    }
}
