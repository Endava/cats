package com.endava.cats.fuzzer.fields.leading;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.strategy.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil();
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer leadingMultiCodePointEmojisInFieldsTrimValidateFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        leadingMultiCodePointEmojisInFieldsTrimValidateFuzzer = new LeadingMultiCodePointEmojisInFieldsTrimValidateFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);
        Mockito.when(testCaseListener.isFieldNotADiscriminator("pet#type")).thenReturn(false);
    }

    @Test
    void shouldProperlyOverrideMethods() {
        FuzzingStrategy fuzzingStrategy = leadingMultiCodePointEmojisInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(null, null).get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.prefix().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("\uD83D\uDC69\uD83C\uDFFE");
        Assertions.assertThat(leadingMultiCodePointEmojisInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(leadingMultiCodePointEmojisInFieldsTrimValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingMultiCodePointEmojisInFieldsTrimValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        Assertions.assertThat(leadingMultiCodePointEmojisInFieldsTrimValidateFuzzer.isFuzzerWillingToFuzz(null, "pet#type")).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        Assertions.assertThat(leadingMultiCodePointEmojisInFieldsTrimValidateFuzzer.isFuzzerWillingToFuzz(null, "pet#number")).isTrue();
    }
}
