package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@QuarkusTest
class HangulFillerFieldsSanitizeValidateFuzzerTest {
    private HangulFillerFieldsSanitizeValidateFuzzer hangulFillerFieldsSanitizeValidateFuzzer;

    @BeforeEach
    void setup() {
        ServiceCaller serviceCaller = Mockito.mock(ServiceCaller.class);
        TestCaseListener testCaseListener = Mockito.mock(TestCaseListener.class);
        FilesArguments filesArguments = Mockito.mock(FilesArguments.class);
        hangulFillerFieldsSanitizeValidateFuzzer = new HangulFillerFieldsSanitizeValidateFuzzer(serviceCaller, testCaseListener, filesArguments);
    }

    private FuzzingData mockFuzzingData() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("testField", new StringSchema());
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        return data;
    }

    @Test
    void shouldHaveAllMethodsOverridden() {
        Assertions.assertThat(hangulFillerFieldsSanitizeValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(hangulFillerFieldsSanitizeValidateFuzzer.typeOfDataSentToTheService()).isEqualTo("Hangul filler characters");
    }

    @Test
    void shouldGetHangulFillersAsPayload() {
        FuzzingData data = mockFuzzingData();
        List<FuzzingStrategy> strategies = hangulFillerFieldsSanitizeValidateFuzzer.getFieldFuzzingStrategy(data, "testField");
        Assertions.assertThat(strategies).hasSize(4);
    }

    @Test
    void shouldSkipFuzzingForSpecialChars() {
        FuzzingData data = mockFuzzingData();
        Assertions.assertThat(hangulFillerFieldsSanitizeValidateFuzzer.isFuzzerWillingToFuzz(data, "testField")).isFalse();
    }
}
