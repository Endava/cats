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
class LeadingWhitespacesInFieldsTrimValidateFuzzerTest {
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private LeadingWhitespacesInFieldsTrimValidateFuzzer leadingWhitespacesInFieldsTrimValidateFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        leadingWhitespacesInFieldsTrimValidateFuzzer = new LeadingWhitespacesInFieldsTrimValidateFuzzer(serviceCaller, testCaseListener, filesArguments);
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);
        Mockito.when(testCaseListener.isFieldNotADiscriminator("pet#type")).thenReturn(false);
    }

    @Test
    void givenANewLeadingSpacesInFieldsTrimValidateFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLeadingSpacesInFieldsTrimValidateFuzzer() {
        FuzzingStrategy fuzzingStrategy = leadingWhitespacesInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(null, null).getFirst();
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.prefix().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(" ");
        Assertions.assertThat(leadingWhitespacesInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamilyPredefined.TWOXX);
        Assertions.assertThat(leadingWhitespacesInFieldsTrimValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingWhitespacesInFieldsTrimValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        Assertions.assertThat(leadingWhitespacesInFieldsTrimValidateFuzzer.isFuzzerWillingToFuzz(null, "pet#type")).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        Assertions.assertThat(leadingWhitespacesInFieldsTrimValidateFuzzer.isFuzzerWillingToFuzz(null, "pet#number")).isTrue();
    }
}
