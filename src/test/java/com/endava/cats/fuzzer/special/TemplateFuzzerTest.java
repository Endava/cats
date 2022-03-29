package com.endava.cats.fuzzer.special;

import com.endava.cats.args.MatchArguments;
import com.endava.cats.args.UserArguments;
import com.endava.cats.dsl.CatsDSLParser;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.CatsHeader;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseExporter;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
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
    @InjectSpy
    private TestCaseListener testCaseListener;
    private CatsUtil catsUtil;

    private TemplateFuzzer templateFuzzer;

    @BeforeEach
    void setup() {
        matchArguments = Mockito.mock(MatchArguments.class);
        userArguments = Mockito.mock(UserArguments.class);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        catsUtil = new CatsUtil(new CatsDSLParser());
        serviceCaller = Mockito.mock(ServiceCaller.class);
        templateFuzzer = new TemplateFuzzer(serviceCaller, testCaseListener, catsUtil, userArguments, matchArguments);
        ReflectionTestUtils.setField(testCaseListener, "testCaseExporter", Mockito.mock(TestCaseExporter.class));
        CatsUtil.setCatsLogLevel("SEVERE");//we do this in order to avoid surefire breaking due to \uFFFe
    }

    @Test
    void shouldNotRunWhenNoTargetFields() {
        FuzzingData data = FuzzingData.builder().targetFields(Collections.emptySet()).build();

        templateFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldNotRunWhenTargetFieldsButNoMatch() {
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("test"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Collections.emptySet())
                .path("http://url")
                .build();
        templateFuzzer.fuzz(data);
        Mockito.verifyNoInteractions(testCaseListener);
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
        Mockito.verify(testCaseListener, Mockito.times(45)).skipTest(Mockito.any(), Mockito.eq("Skipping test as response does not match given matchers!"));
    }

    @ParameterizedTest
    @CsvSource({"true,true", "false,false", "false,true"})
    void shouldRunWhenMatchArgumentsAndResponseMatched(boolean isAnyMatch, boolean isResponseMatch) {
        Mockito.when(matchArguments.isAnyMatchArgumentSupplied()).thenReturn(isAnyMatch);
        Mockito.when(matchArguments.isMatchResponse(Mockito.any())).thenReturn(isResponseMatch);
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("field"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Collections.emptySet())
                .path("http://url")
                .method(HttpMethod.POST)
                .build();
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(45)).reportError(Mockito.any(), Mockito.eq("Service call completed. Please check response details."), Mockito.any());
    }

    @ParameterizedTest
    @CsvSource({"http://localhost/field", "http://localhost/path?field&test=value"})
    void shouldRunWhenPathParam(String url) {
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("field"))
                .processedPayload("{\"anotherField\":\"value\"}")
                .headers(Collections.emptySet())
                .path(url)
                .method(HttpMethod.POST)
                .build();
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(38)).reportError(Mockito.any(), Mockito.eq("Service call completed. Please check response details."), Mockito.any());
    }


    @Test
    void shouldRunWhenTargetFieldInHeader() {
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("header"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Set.of(CatsHeader.builder().name("header").value("value").build(), CatsHeader.builder().name("header2").value("value").build()))
                .method(HttpMethod.POST)
                .path("http://url")
                .build();
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(45)).reportError(Mockito.any(), Mockito.eq("Service call completed. Please check response details."), Mockito.any());

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
        Mockito.verifyNoInteractions(testCaseListener);
    }

    @Test
    void shouldRunWithUserDictionary() {
        FuzzingData data = FuzzingData.builder()
                .targetFields(Set.of("header"))
                .processedPayload("{\"field\":\"value\"}")
                .headers(Set.of(CatsHeader.builder().name("header").value("value").build()))
                .method(HttpMethod.POST)
                .path("http://url")
                .build();
        Mockito.when(userArguments.getWords()).thenReturn(new File("src/test/resources/dict.txt"));
        templateFuzzer.fuzz(data);
        Mockito.verify(testCaseListener, Mockito.times(2)).reportError(Mockito.any(), Mockito.eq("Service call completed. Please check response details."), Mockito.any());
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

        Mockito.verify(testCaseListener, Mockito.times(2)).reportError(Mockito.any(), Mockito.eq("Something went wrong {}"), Mockito.any());
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
}
