package com.endava.cats.fuzzer.fields.within;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

@QuarkusTest
class WithinControlCharsInStringFieldsValidateSanitizeFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil();
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private WithinControlCharsInStringFieldsValidateSanitizeFuzzer controlCharsWithinFieldsValidateSanitizeFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        controlCharsWithinFieldsValidateSanitizeFuzzer = new WithinControlCharsInStringFieldsValidateSanitizeFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);
        Mockito.when(testCaseListener.isFieldNotADiscriminator("pet#type")).thenReturn(false);
    }

    @Test
    void shouldProperlyOverrideSuperClassMethods() {
        FuzzingData data = Mockito.mock(FuzzingData.class);
        Map<String, Schema> reqTypes = new HashMap<>();
        reqTypes.put("field", new StringSchema());
        Mockito.when(data.getRequestPropertyTypes()).thenReturn(reqTypes);
        FuzzingStrategy fuzzingStrategy = controlCharsWithinFieldsValidateSanitizeFuzzer.getFieldFuzzingStrategy(data, "field").get(1);

        Assertions.assertThat(fuzzingStrategy.getData().toString()).contains("\u0000");
        Assertions.assertThat(controlCharsWithinFieldsValidateSanitizeFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(controlCharsWithinFieldsValidateSanitizeFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(controlCharsWithinFieldsValidateSanitizeFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);

        Assertions.assertThat(controlCharsWithinFieldsValidateSanitizeFuzzer.description()).isNotNull();
        Assertions.assertThat(controlCharsWithinFieldsValidateSanitizeFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(controlCharsWithinFieldsValidateSanitizeFuzzer.concreteFuzzStrategy().name()).isEqualTo(FuzzingStrategy.replace().name());
    }

    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        Assertions.assertThat(controlCharsWithinFieldsValidateSanitizeFuzzer.isFuzzerWillingToFuzz(null, "pet#type")).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        Assertions.assertThat(controlCharsWithinFieldsValidateSanitizeFuzzer.isFuzzerWillingToFuzz(null, "pet#number")).isTrue();
    }
}
