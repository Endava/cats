package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
class AbugidasInStringFieldsSanitizeValidateFuzzerTest {

    private final CatsUtil catsUtil = new CatsUtil(null);
    private AbugidasInStringFieldsSanitizeValidateFuzzer abugidasCharsInStringFieldsSanitizeValidateFuzzer;
    private FilesArguments filesArguments;

    @BeforeEach
    void setup() {
        ServiceCaller serviceCaller = Mockito.mock(ServiceCaller.class);
        TestCaseListener testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        abugidasCharsInStringFieldsSanitizeValidateFuzzer = new AbugidasInStringFieldsSanitizeValidateFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);
        Mockito.when(testCaseListener.isFieldNotADiscriminator("pet#type")).thenReturn(false);
    }

    @Test
    void shouldGetReplaceFuzzingStrategy() {
        FuzzingData data = mockFuzzingData();
        FuzzingStrategy fuzzingStrategy = abugidasCharsInStringFieldsSanitizeValidateFuzzer.getFieldFuzzingStrategy(data, "field").get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData().toString()).contains("జ్ఞ\u200Cా");
    }

    @Test
    void shouldReturnDescription() {
        Assertions.assertThat(abugidasCharsInStringFieldsSanitizeValidateFuzzer.description()).isNotBlank();
    }

    @Test
    void shouldReturn2xxWhenFuzzValueNotMatchingPattern() {
        Assertions.assertThat(abugidasCharsInStringFieldsSanitizeValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
    }

    @Test
    void shouldReturnTypeOfDataToSendToServices() {
        Assertions.assertThat(abugidasCharsInStringFieldsSanitizeValidateFuzzer.typeOfDataSentToTheService()).isNotBlank();
    }


    @NotNull
    private FuzzingData mockFuzzingData() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> reqTypes = new HashMap<>();
        StringSchema petAge = new StringSchema();
        petAge.setEnum(List.of("1", "2"));
        reqTypes.put("field", new StringSchema());
        reqTypes.put("pet#number", new StringSchema());
        reqTypes.put("pet#age", petAge);
        reqTypes.put("pet#size", new IntegerSchema());
        Mockito.when(data.getPath()).thenReturn("/test");
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        return data;
    }

    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        Assertions.assertThat(abugidasCharsInStringFieldsSanitizeValidateFuzzer.isFuzzerWillingToFuzz(mockFuzzingData(), "pet#type")).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        Assertions.assertThat(abugidasCharsInStringFieldsSanitizeValidateFuzzer.isFuzzerWillingToFuzz(mockFuzzingData(), "pet#number")).isTrue();
    }

    @Test
    void shouldNotFuzzIfRefDataField() {
        Map<String, String> refData = Map.of("field", "test");
        Mockito.when(filesArguments.getRefData("/test")).thenReturn(refData);
        Assertions.assertThat(abugidasCharsInStringFieldsSanitizeValidateFuzzer.isFuzzerWillingToFuzz(mockFuzzingData(), "field")).isFalse();
    }

    @Test
    void shouldNotFuzzWhenEnum() {
        Map<String, String> refData = Map.of("field", "test");
        Mockito.when(filesArguments.getRefData("/test")).thenReturn(refData);
        Assertions.assertThat(abugidasCharsInStringFieldsSanitizeValidateFuzzer.isFuzzerWillingToFuzz(mockFuzzingData(), "pet#age")).isFalse();
    }

    @Test
    void shouldNotFuzzWhenNotStringSchema() {
        Assertions.assertThat(abugidasCharsInStringFieldsSanitizeValidateFuzzer.getFieldFuzzingStrategy(mockFuzzingData(), "pet#size").get(0).isSkip()).isTrue();

    }
}
