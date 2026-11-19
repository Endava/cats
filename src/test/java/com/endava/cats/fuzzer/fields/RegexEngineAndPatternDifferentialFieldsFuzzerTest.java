package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.args.MatchArguments;
import com.endava.cats.fuzzer.executor.FieldsIteratorExecutor;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.io.ServiceData;
import com.endava.cats.model.CatsResponse;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsRandom;
import com.endava.cats.util.JsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

@QuarkusTest
class RegexEngineAndPatternDifferentialFieldsFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener listener;
    private RegexEngineAndPatternDifferentialFieldsFuzzer fuzzer;

    @BeforeEach
    void setup() {
        CatsRandom.initRandom(0);
        serviceCaller = Mockito.mock(ServiceCaller.class);
        listener = Mockito.mock(TestCaseListener.class);
        Mockito.doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return null;
        }).when(listener).createAndExecuteTest(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.when(listener.shouldContinueExecution(Mockito.any(), Mockito.any())).thenReturn(true);
        Mockito.when(serviceCaller.call(Mockito.any())).thenReturn(CatsResponse.builder().responseCode(400).body("{}").build());
        FieldsIteratorExecutor executor = new FieldsIteratorExecutor(serviceCaller, listener,
                Mockito.mock(MatchArguments.class), Mockito.mock(FilesArguments.class));
        fuzzer = new RegexEngineAndPatternDifferentialFieldsFuzzer(executor);
    }

    @Test
    void shouldHaveDescriptionAndToString() {
        Assertions.assertThat(fuzzer.description()).contains("Regexp#to_s", "backreference");
        Assertions.assertThat(fuzzer).hasToString("RegexEngineAndPatternDifferentialFieldsFuzzer");
    }

    @ParameterizedTest
    @CsvSource({
            "regex,field,true",
            "regexp,field,true",
            ",filterPattern,false",
            ",search,true",
            ",username,false"
    })
    void shouldMatchPatternConsumerFields(String format, String fieldName, boolean expected) {
        Schema<String> schema = new Schema<String>().type("string").format(format);
        Assertions.assertThat(RegexEngineAndPatternDifferentialFieldsFuzzer.isPatternConsumer(schema, fieldName)).isEqualTo(expected);
    }

    @Test
    void shouldSendCompilerProbesForRegexConsumerField() {
        Schema<String> schema = new Schema<String>().type("string").format("regex");
        FuzzingData data = FuzzingData.builder()
                .path("/rules")
                .method(HttpMethod.POST)
                .reqSchema(new Schema<>())
                .requestContentTypes(List.of("application/json"))
                .payload("{\"customField\":\"[a-z]+\"}")
                .allFieldsByHttpMethod(Set.of("customField"))
                .requestPropertyTypes(Map.of("customField", schema))
                .build();

        fuzzer.fuzz(data);

        ArgumentCaptor<ServiceData> captor = ArgumentCaptor.forClass(ServiceData.class);
        Mockito.verify(serviceCaller, Mockito.times(3)).call(captor.capture());
        Assertions.assertThat(captor.getAllValues())
                .extracting(req -> JsonUtils.parseAsJsonElement(req.getPayload()).getAsJsonObject().get("customField").getAsString())
                .allSatisfy(val -> Assertions.assertThat(val).containsAnyOf("(?i", "\\1"));
    }

    @Test
    void shouldSendCaseFoldedAndMultilineProbesForPatternConstrainedField() {
        Schema<String> schema = new Schema<String>().type("string").pattern("^[a-zA-Z0-9]+$");
        FuzzingData data = FuzzingData.builder()
                .path("/users")
                .method(HttpMethod.POST)
                .reqSchema(new Schema<>())
                .requestContentTypes(List.of("application/json"))
                .payload("{\"code\":\"4290\"}")
                .allFieldsByHttpMethod(Set.of("code"))
                .requestPropertyTypes(Map.of("code", schema))
                .build();

        fuzzer.fuzz(data);

        Mockito.verify(listener, Mockito.times(2)).reportResult(
                Mockito.any(), Mockito.eq(data), Mockito.any(), Mockito.eq(ResponseCodeFamilyPredefined.FOURXX_TWOXX));
        Assertions.assertThat(RegexEngineAndPatternDifferentialFieldsFuzzer.buildCaseFoldedBackrefTwin("isKf"))
                .isEqualTo("\u0130\u017F\u212A\uFB00");
        Assertions.assertThat(RegexEngineAndPatternDifferentialFieldsFuzzer.buildCaseFoldedBackrefTwin("1234"))
                .isEqualTo("1234s\u017Fi\u0130k\u212A");
    }

    @Test
    void shouldSendFiveProbesWhenFieldIsBothPatternConsumerAndPatternConstrained() {
        Schema<String> schema = new Schema<String>().type("string").format("regex").pattern("^.*$");
        FuzzingData data = FuzzingData.builder()
                .path("/search")
                .method(HttpMethod.POST)
                .reqSchema(new Schema<>())
                .requestContentTypes(List.of("application/json"))
                .payload("{\"search\":\"\"}")
                .allFieldsByHttpMethod(Set.of("search"))
                .requestPropertyTypes(Map.of("search", schema))
                .build();

        fuzzer.fuzz(data);

        Mockito.verify(serviceCaller, Mockito.times(5)).call(Mockito.any());
    }

    @Test
    void shouldSkipUnconstrainedNonRegexAndEnumFields() {
        Schema<String> schema = new Schema<String>().type("string");
        Schema<String> enumFilter = new Schema<String>().type("string");
        enumFilter.setEnum(List.of("ALL", "ACTIVE"));
        FuzzingData data = FuzzingData.builder()
                .path("/users")
                .method(HttpMethod.POST)
                .reqSchema(new Schema<>())
                .requestContentTypes(List.of("application/json"))
                .payload("{\"firstName\":\"Alice\",\"filter\":\"ALL\"}")
                .allFieldsByHttpMethod(Set.of("firstName", "filter"))
                .requestPropertyTypes(Map.of("firstName", schema, "filter", enumFilter))
                .build();

        fuzzer.fuzz(data);

        Mockito.verifyNoInteractions(serviceCaller);
    }
}
