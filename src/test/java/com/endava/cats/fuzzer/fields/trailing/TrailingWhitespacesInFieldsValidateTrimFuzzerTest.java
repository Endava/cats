package com.endava.cats.fuzzer.fields.trailing;

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
class TrailingWhitespacesInFieldsValidateTrimFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;
    private TrailingWhitespacesInFieldsValidateTrimFuzzer trailingWhitespacesInFieldsValidateTrimFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        trailingWhitespacesInFieldsValidateTrimFuzzer = new TrailingWhitespacesInFieldsValidateTrimFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
        Mockito.when(testCaseListener.isFieldNotADiscriminator(Mockito.anyString())).thenReturn(true);
        Mockito.when(testCaseListener.isFieldNotADiscriminator("pet#type")).thenReturn(false);
    }

    @Test
    void givenANewTrailingSpacesInFieldsValidateTrimFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheTrailingSpacesInFieldsValidateTrimFuzzer() {
        FuzzingStrategy fuzzingStrategy = trailingWhitespacesInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(null, null).get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.trail().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(" ");
        Assertions.assertThat(trailingWhitespacesInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(trailingWhitespacesInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(trailingWhitespacesInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamily.FOURXX);

        Assertions.assertThat(trailingWhitespacesInFieldsValidateTrimFuzzer.description()).isNotNull();
        Assertions.assertThat(trailingWhitespacesInFieldsValidateTrimFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        Assertions.assertThat(trailingWhitespacesInFieldsValidateTrimFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#type", null)).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        Assertions.assertThat(trailingWhitespacesInFieldsValidateTrimFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#number", null)).isTrue();
    }
}
