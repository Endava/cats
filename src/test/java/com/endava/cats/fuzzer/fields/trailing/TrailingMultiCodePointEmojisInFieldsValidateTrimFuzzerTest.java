package com.endava.cats.fuzzer.fields.trailing;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class TrailingMultiCodePointEmojisInFieldsValidateTrimFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private TrailingMultiCodePointEmojisInFieldsValidateTrimFuzzer trailingMultiCodePointEmojisInFieldsValidateTrimFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        trailingMultiCodePointEmojisInFieldsValidateTrimFuzzer = new TrailingMultiCodePointEmojisInFieldsValidateTrimFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);
        Mockito.when(testCaseListener.isFieldNotADiscriminator("pet#type")).thenReturn(false);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        FuzzingStrategy fuzzingStrategy = trailingMultiCodePointEmojisInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(null, null).get(1);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.trail().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("\uD83D\uDC68\u200D\uD83C\uDFED️");
        Assertions.assertThat(trailingMultiCodePointEmojisInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(trailingMultiCodePointEmojisInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(trailingMultiCodePointEmojisInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);

        Assertions.assertThat(trailingMultiCodePointEmojisInFieldsValidateTrimFuzzer.description()).isNotNull();
        Assertions.assertThat(trailingMultiCodePointEmojisInFieldsValidateTrimFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        Assertions.assertThat(trailingMultiCodePointEmojisInFieldsValidateTrimFuzzer.isFuzzerWillingToFuzz(null, "pet#type")).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        Assertions.assertThat(trailingMultiCodePointEmojisInFieldsValidateTrimFuzzer.isFuzzerWillingToFuzz(null, "pet#number")).isTrue();
    }
}
