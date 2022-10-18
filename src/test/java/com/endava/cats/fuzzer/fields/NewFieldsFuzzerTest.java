package com.endava.cats.fuzzer.fields;

import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
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
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
    }

    @Test
    void shouldNotRunWithEmptyPayload() {
        newFieldsFuzzer.fuzz(Mockito.mock(FuzzingData.class));

        Mockito.verifyNoInteractions(testCaseListener);
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

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.FOURXX));
    }

    @Test
    void givenAGETRequest_whenCallingTheNewFieldsFuzzer_thenTestCasesAreCorrectlyExecutedAndExpectA2XX() {
        setup(HttpMethod.GET);
        newFieldsFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(1)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));
    }

    @Test
    void shouldAddANewFieldToFuzzToSingleElement() {
        setup(HttpMethod.POST);
        JsonElement element = newFieldsFuzzer.addNewField(data);

        Assertions.assertThat(element.getAsJsonObject().get(NEW_FIELD)).isNotNull();
        Assertions.assertThat(element.getAsJsonObject().get(NEW_FIELD + "random")).isNull();
    }

    @Test
    void shouldAddANewFieldToFuzzToArray() {
        String payload = "[{ 'field': 'value1'}, {'field': 'value2'}]";
        data = FuzzingData.builder().payload(payload).reqSchema(new StringSchema()).build();
        JsonElement element = newFieldsFuzzer.addNewField(data);

        Assertions.assertThat(element.getAsJsonArray().get(0).getAsJsonObject().get(NEW_FIELD)).isNotNull();
        Assertions.assertThat(element.getAsJsonArray().get(0).getAsJsonObject().get(NEW_FIELD + "random")).isNull();
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
