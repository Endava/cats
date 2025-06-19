package com.endava.cats.fuzzer.special;

import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.StopArguments;
import com.endava.cats.args.UserArguments;
import com.endava.cats.fuzzer.special.mutators.api.BodyMutator;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.report.TestReportsGenerator;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

@QuarkusTest
class TemplateFuzzerTest {
    private UserArguments userArguments;
    private MatchArguments matchArguments;
    private ServiceCaller serviceCaller;
    private StopArguments stopArguments;
    @InjectSpy
    private TestCaseListener testCaseListener;
    @Inject
    Instance<BodyMutator> mutators;
    @Inject
    ExecutionStatisticsListener executionStatisticsListener;

    private TemplateFuzzer templateFuzzer;

    @BeforeEach
    void setup() {
        matchArguments = Mockito.mock(MatchArguments.class);
        userArguments = Mockito.mock(UserArguments.class);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        stopArguments = Mockito.mock(StopArguments.class);
        templateFuzzer = new TemplateFuzzer(serviceCaller, testCaseListener, userArguments, matchArguments, stopArguments, mutators, executionStatisticsListener);
        ReflectionTestUtils.setField(testCaseListener, "testReportsGenerator", Mockito.mock(TestReportsGenerator.class));
        Mockito.doNothing().when(testCaseListener).notifySummaryObservers(Mockito.any());
        CatsUtil.setCatsLogLevel("SEVERE");//we do this in order to avoid surefire breaking due to \uFFFe
    }

    @Test
    void shouldNotRunWhenNoTargetFields() {
        FuzzingData data = FuzzingData.builder().contractPath("/path").method(HttpMethod.POST).targetFields(Collections.emptySet()).build();

        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldNotRunWhenTargetFieldsButNoMatch() {
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("test"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Collections.emptySet())
                .method(HttpMethod.POST)
                .contractPath("/path")
                .path("http://url")
                .build();
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldSkipWhenMatchArgumentsButNoMatch() {
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(true);
        Mockito.when(matchArguments.isMatchResponse(Mockito.any())).thenReturn(false);
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("field"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Collections.emptySet())
                .path("http://url")
                .method(HttpMethod.POST)
                .build();
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(44)).skipTest(Mockito.any(), Mockito.eq("Skipping test as response does not match given matchers!"));
    }

    @ParameterizedTest
    @CsvSource({
            "true,true,true",
            "true,true,false",
            "true,false,true",
            "false,true,true",
            "false,true,false",
            "false,false,true",
            "false,false,false"
    })
    void shouldRunWhenMatchArgumentsAndResponseMatched(boolean isAnyMatch, boolean isResponseMatch, boolean isInputMatch) throws Exception {
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(isAnyMatch);
        Mockito.when(matchArguments.isMatchResponse(Mockito.any())).thenReturn(isResponseMatch);
        Mockito.when(matchArguments.isInputReflected(Mockito.any(), Mockito.any())).thenReturn(isInputMatch);
        Mockito.when(matchArguments.getMatchString()).thenReturn(" arguments");
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.any())).thenReturn(CatsResponse.empty());
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("field"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Collections.emptySet())
                .path("http://url")
                .method(HttpMethod.POST)
                .build();
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(44)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Response matches arguments"), Mockito.anyString(), Mockito.any());
    }

    @ParameterizedTest
    @CsvSource({"http://localhost/field", "http://localhost/path?field&test=value"})
    void shouldRunWhenPathParam(String url) throws Exception {
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.any())).thenReturn(CatsResponse.empty());

        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("field"))
                .processedPayload("{\"anotherField\":\"value\"}")
                .headers(Collections.emptySet())
                .path(url)
                .method(HttpMethod.POST)
                .build();
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(37)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Response matches arguments"), Mockito.anyString(), Mockito.any());
    }


    @Test
    void shouldRunWhenTargetFieldInHeader() throws Exception {
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.any())).thenReturn(CatsResponse.empty());

        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("header"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Set.of(CatsHeader.builder().name("header").value("value").build(), CatsHeader.builder().name("header2").value("value").build()))
                .method(HttpMethod.POST)
                .path("http://url")
                .build();
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(44)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Response matches arguments"), Mockito.anyString(), Mockito.any());

    }

    @Test
    void shouldNotRunWhenUserDictionaryButInvalidFile() {
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("header"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Set.of(CatsHeader.builder().name("header").value("value").build()))
                .method(HttpMethod.POST)
                .path("http://url")
                .build();
        Mockito.when(userArguments.getWords()).thenReturn(new File("non_real"));
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(0)).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldRunWithUserDictionary() throws Exception {
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.any())).thenReturn(CatsResponse.empty());
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("header"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Set.of(CatsHeader.builder().name("header").value("value").build()))
                .method(HttpMethod.POST)
                .path("http://url")
                .build();
        Mockito.when(userArguments.getWords()).thenReturn(new File("src/test/resources/dict.txt"));
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(2)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Response matches arguments"), Mockito.anyString(), Mockito.any());
    }

    @ParameterizedTest
    @CsvSource({"{\"field\":\"value\"},http://url/FUZZ,2", "{\"field\":\"FUZZ\"},http://url/,2", "{\"field\":\"value\"},http://url/,0"})
    void shouldRunWithFuzzKeyword(String payload, String path, int times) throws Exception {
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.any())).thenReturn(CatsResponse.empty());
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("FUZZ"))
                .headers(Set.of())
                .processedPayload(payload)
                .method(HttpMethod.POST)
                .path(path)
                .build();
        Mockito.when(userArguments.getWords()).thenReturn(new File("src/test/resources/dict.txt"));
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(times)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Response matches arguments"), Mockito.anyString(), Mockito.any());
    }

    @Test
    void shouldReportErrorWhenServiceException() throws Exception {
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("header"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Set.of(CatsHeader.builder().name("header").value("value").build()))
                .method(HttpMethod.POST)
                .path("http://url")
                .build();
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.anySet())).thenThrow(IOException.class);
        Mockito.when(userArguments.getWords()).thenReturn(new File("src/test/resources/dict.txt"));

        templateFuzzer.fuzz(data);

        Mockito.verify(testCaseListener, Mockito.times(2)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Response matches arguments"), Mockito.any(), Mockito.any());
    }

    @Test
    void shouldNotReplacePathWhenInvalid() {
        FuzzingData data = FuzzingData.builder()
                .path("htp:url")
                .build();

        String replaced = templateFuzzer.replacePath(data, "test", "test");
        Assertions.assertThat(replaced).isEqualTo("htp:url");
    }

    @Test
    void shouldNotReplaceQuery() {
        FuzzingData data = FuzzingData.builder()
                .path("http://url?myq")
                .build();

        String replaced = templateFuzzer.replacePath(data, "test", "test");
        Assertions.assertThat(replaced).isEqualTo("http://url?myq");
    }

    @Test
    void shouldReplaceSingleParam() {
        FuzzingData data = FuzzingData.builder()
                .path("http://url?test")
                .build();

        String replaced = templateFuzzer.replacePath(data, "replaced", "test");
        Assertions.assertThat(replaced).isEqualTo("http://url?replaced");
    }

    @Test
    void shouldReplaceParamWithValue() {
        FuzzingData data = FuzzingData.builder()
                .path("http://url?test&field=value")
                .build();

        String replaced = templateFuzzer.replacePath(data, "replaced", "field");
        Assertions.assertThat(replaced).isEqualTo("http://url?test&field=replaced");
    }

    @Test
    void shouldHaveDescription() {
        Assertions.assertThat(templateFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldReplaceComplexPaths() {
        FuzzingData data = FuzzingData.builder()
                .path("http://localhost:8000/lookup?url=http%3A%2F%2Flocalhost%3A6001/users/ID")
                .build();
        Mockito.when(userArguments.isNameReplace()).thenReturn(true);
        String replaced = templateFuzzer.replacePath(data, "valueReplaced", "ID");

        Assertions.assertThat(replaced).isEqualTo("http://localhost:8000/lookup?url=http%3A%2F%2Flocalhost%3A6001/users/valueReplaced");
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldRunInContinuousMode(boolean nameReplace) throws Exception {
        Mockito.when(userArguments.isNameReplace()).thenReturn(nameReplace);
        Mockito.when(serviceCaller.callService(Mockito.any(), Mockito.any())).thenReturn(CatsResponse.empty());
        templateFuzzer.setRandom(true);
        Mockito.when(stopArguments.shouldStop(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyLong())).thenReturn(true);
        templateFuzzer.fuzz(FuzzingData.builder()
                .path("/test")
                .processedPayload("""
                        {
                            "field1": "value1",
                            "field2": "value2"
                        }
                        """)
                .targetFields(Set.of("field1"))
                .headers(Set.of())
                .method(HttpMethod.POST)
                .build());
        Mockito.verify(testCaseListener, Mockito.times(1)).reportResultError(Mockito.any(), Mockito.any(), Mockito.eq("Response matches arguments"), Mockito.any(), Mockito.any());
    }
}
