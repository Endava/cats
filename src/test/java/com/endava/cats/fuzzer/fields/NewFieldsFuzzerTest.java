package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.IgnoreArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsDSLParser;
import com.endava.cats.util.CatsUtil;
import com.google.gson.JsonElement;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(SpringExtension.class)
class NewFieldsFuzzerTest {

    @MockBean
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private IgnoreArguments ignoreArguments;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @SpyBean
    private CatsUtil catsUtil;

    @SpyBean
    private CatsDSLParser catsDSLParser;

    private NewFieldsFuzzer newFieldsFuzzer;

    private FuzzingData data;
    private CatsResponse catsResponse;

    @BeforeEach
    void setup() {
        newFieldsFuzzer = new NewFieldsFuzzer(serviceCaller, testCaseListener, catsUtil);
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

        Assertions.assertThat(element.getAsJsonObject().get(NewFieldsFuzzer.NEW_FIELD)).isNotNull();
        Assertions.assertThat(element.getAsJsonObject().get(NewFieldsFuzzer.NEW_FIELD + "random")).isNull();
    }

    @Test
    void shouldAddANewFieldToFuzzToArray() {
        String payload = "[{ 'field': 'value1'}, {'field': 'value2'}]";
        data = FuzzingData.builder().payload(payload).build();
        JsonElement element = newFieldsFuzzer.addNewField(data);

        Assertions.assertThat(element.getAsJsonArray().get(0).getAsJsonObject().get(NewFieldsFuzzer.NEW_FIELD)).isNotNull();
        Assertions.assertThat(element.getAsJsonArray().get(0).getAsJsonObject().get(NewFieldsFuzzer.NEW_FIELD + "random")).isNull();
    }

    private void setup(HttpMethod method) {
        catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        data = FuzzingData.builder().path("path1").method(method).payload("{'field':'oldValue'}").
                responses(responses).responseCodes(Collections.singleton("200")).build();

        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(catsResponse);
    }
}
