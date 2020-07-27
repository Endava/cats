package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.TestCaseExporter;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import com.google.gson.JsonObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;

@ExtendWith(SpringExtension.class)
public class CustomFuzzerTest {
    @MockBean
    private ServiceCaller serviceCaller;

    @SpyBean
    private TestCaseListener testCaseListener;

    @MockBean
    private ExecutionStatisticsListener executionStatisticsListener;

    @MockBean
    private TestCaseExporter testCaseExporter;

    @MockBean
    private BuildProperties buildProperties;

    @MockBean
    private CatsUtil catsUtil;

    private CustomFuzzer customFuzzer;


    @BeforeEach
    public void setup() {
        customFuzzer = new CustomFuzzer(serviceCaller, testCaseListener, catsUtil);
    }


    @Test
    public void givenAnEmptyCustomFuzzerFile_whenTheFuzzerRuns_thenNothingHappens() {
        FuzzingData data = FuzzingData.builder().build();
        ReflectionTestUtils.setField(customFuzzer, "customFuzzerFile", "empty");
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        spyCustomFuzzer.fuzz(data);

        Mockito.verify(spyCustomFuzzer, Mockito.never()).processCustomFuzzerFile(data);
        Assertions.assertThat(customFuzzer.description()).isNotNull();
        Assertions.assertThat(customFuzzer).hasToString(customFuzzer.getClass().getSimpleName());
    }

    @Test
    public void givenACustomFuzzerFileWithSimpleTestCases_whenTheFuzzerRuns_thenCustomTestCasesAreExecuted() throws Exception {
        Map<String, List<String>> responses = new HashMap<>();
        responses.put("200", Collections.singletonList("response"));
        FuzzingData data = FuzzingData.builder().path("path1").payload("{'field':'oldValue'}").
                responses(responses).responseCodes(Collections.singleton("200")).build();
        CatsResponse catsResponse = CatsResponse.builder().body("{}").responseCode(200).build();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("field", "oldValue");

        ReflectionTestUtils.setField(customFuzzer, "customFuzzerFile", "custom");
        Mockito.when(catsUtil.parseYaml(any())).thenReturn(createCustomFuzzerFile());
        Mockito.when(catsUtil.parseAsJsonElement(data.getPayload())).thenReturn(jsonObject);
        Mockito.when(catsUtil.getJsonElementBasedOnFullyQualifiedName(Mockito.eq(jsonObject), Mockito.eq("field"))).thenReturn(jsonObject);
        Mockito.when(serviceCaller.call(Mockito.any(), Mockito.any())).thenReturn(catsResponse);
        CustomFuzzer spyCustomFuzzer = Mockito.spy(customFuzzer);
        spyCustomFuzzer.fuzz(data);

        Mockito.verify(spyCustomFuzzer, Mockito.times(1)).processCustomFuzzerFile(data);
        Mockito.verify(testCaseListener, Mockito.times(3)).reportResult(Mockito.any(), Mockito.eq(data), Mockito.eq(catsResponse), Mockito.eq(ResponseCodeFamily.TWOXX));
        Assertions.assertThat(jsonObject.toString()).contains("newValue");
    }

    private Map<String, Map<String, Object>> createCustomFuzzerFile() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        Map<String, Object> path = new HashMap<>();
        Map<String, Object> tests = new HashMap<>();
        tests.put("k1", "v1");
        tests.put("field", Arrays.asList("newValue", "newValue2"));
        tests.put("expectedResponseCode", "200");

        path.put("test1", tests);
        path.put("test2", tests);

        result.put("path1", path);
        return result;
    }
}
