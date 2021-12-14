package com.endava.cats.fuzzer.fields.leading;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class LeadingControlCharsInFieldsValidateTrimFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private LeadingControlCharsInFieldsValidateTrimFuzzer leadingControlCharsInFieldsValidateTrimFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        leadingControlCharsInFieldsValidateTrimFuzzer = new LeadingControlCharsInFieldsValidateTrimFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);
        Mockito.when(testCaseListener.isFieldNotADiscriminator("pet#type")).thenReturn(false);
    }

    @Test
    void givenANewLeadingTabsInFieldsValidateTrimFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLeadingTabsInFieldsValidateTrimFuzzer() {
        FuzzingStrategy fuzzingStrategy = leadingControlCharsInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(null, null).get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.prefix().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("\r\n");
        Assertions.assertThat(leadingControlCharsInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(leadingControlCharsInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(leadingControlCharsInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);

        Assertions.assertThat(leadingControlCharsInFieldsValidateTrimFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingControlCharsInFieldsValidateTrimFuzzer.typeOfDataSentToTheService()).isNotNull();
    }


    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        Assertions.assertThat(leadingControlCharsInFieldsValidateTrimFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#type", null)).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        Assertions.assertThat(leadingControlCharsInFieldsValidateTrimFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#number", null)).isTrue();
    }
}

