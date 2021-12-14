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
class LeadingWhitespacesInFieldsValidateTrimFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private LeadingWhitespacesInFieldsValidateTrimFuzzer leadingWhitespacesInFieldsValidateTrimFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        leadingWhitespacesInFieldsValidateTrimFuzzer = new LeadingWhitespacesInFieldsValidateTrimFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);
        Mockito.when(testCaseListener.isFieldNotADiscriminator("pet#type")).thenReturn(false);
    }

    @Test
    void givenANewLeadingSpacesInFieldsValidateTrimFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLeadingSpacesInFieldsValidateTrimFuzzer() {
        FuzzingStrategy fuzzingStrategy = leadingWhitespacesInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(null, null).get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.prefix().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(" ");
        Assertions.assertThat(leadingWhitespacesInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(leadingWhitespacesInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(leadingWhitespacesInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);

        Assertions.assertThat(leadingWhitespacesInFieldsValidateTrimFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingWhitespacesInFieldsValidateTrimFuzzer.typeOfDataSentToTheService()).isNotNull();
    }


    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        Assertions.assertThat(leadingWhitespacesInFieldsValidateTrimFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#type", null)).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        Assertions.assertThat(leadingWhitespacesInFieldsValidateTrimFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#number", null)).isTrue();
    }
}
