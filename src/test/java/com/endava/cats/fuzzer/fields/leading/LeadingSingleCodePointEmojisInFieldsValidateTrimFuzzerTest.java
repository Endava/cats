package com.endava.cats.fuzzer.fields.leading;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.ResponseCodeFamilyPredefined;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.strategy.FuzzingStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class LeadingSingleCodePointEmojisInFieldsValidateTrimFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private LeadingSingleCodePointEmojisInFieldsValidateTrimFuzzer leadingSingleCodePointEmojisInFieldsValidateTrimFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        leadingSingleCodePointEmojisInFieldsValidateTrimFuzzer = new LeadingSingleCodePointEmojisInFieldsValidateTrimFuzzer(serviceCaller, testCaseListener, filesArguments);
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);
        Mockito.when(testCaseListener.isFieldNotADiscriminator("pet#type")).thenReturn(false);
    }

    @Test
    void givenANewLeadingTabsInFieldsValidateTrimFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLeadingTabsInFieldsValidateTrimFuzzer() {
        FuzzingStrategy fuzzingStrategy = leadingSingleCodePointEmojisInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(null, null).getFirst();
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.prefix().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("\uD83E\uDD76");
        Assertions.assertThat(leadingSingleCodePointEmojisInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(leadingSingleCodePointEmojisInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(leadingSingleCodePointEmojisInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);

        Assertions.assertThat(leadingSingleCodePointEmojisInFieldsValidateTrimFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingSingleCodePointEmojisInFieldsValidateTrimFuzzer.typeOfDataSentToTheService()).isNotNull();
    }


    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        Assertions.assertThat(leadingSingleCodePointEmojisInFieldsValidateTrimFuzzer.isFuzzerWillingToFuzz(null, "pet#type")).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        Assertions.assertThat(leadingSingleCodePointEmojisInFieldsValidateTrimFuzzer.isFuzzerWillingToFuzz(null, "pet#number")).isTrue();
    }
}
