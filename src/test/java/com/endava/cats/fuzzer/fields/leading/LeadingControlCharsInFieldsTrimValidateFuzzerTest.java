package com.endava.cats.fuzzer.fields.leading;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.generator.simple.PayloadGenerator;
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
class LeadingControlCharsInFieldsTrimValidateFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);
    private ServiceCaller serviceCaller;
    private TestCaseListener testCaseListener;
    private FilesArguments filesArguments;

    private LeadingControlCharsInFieldsTrimValidateFuzzer leadingControlCharsInFieldsTrimValidateFuzzer;

    @BeforeEach
    void setup() {
        serviceCaller = Mockito.mock(ServiceCaller.class);
        testCaseListener = Mockito.mock(TestCaseListener.class);
        filesArguments = Mockito.mock(FilesArguments.class);
        leadingControlCharsInFieldsTrimValidateFuzzer = new LeadingControlCharsInFieldsTrimValidateFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenANewLeadingTabsInFieldsTrimValidateFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLeadingTabsInFieldsTrimValidateFuzzer() {
        FuzzingStrategy fuzzingStrategy = leadingControlCharsInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(null, null).get(0);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.prefix().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("\r\n");
        Assertions.assertThat(leadingControlCharsInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(leadingControlCharsInFieldsTrimValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingControlCharsInFieldsTrimValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        PayloadGenerator.GlobalData.getDiscriminators().add("pet#type");
        Assertions.assertThat(leadingControlCharsInFieldsTrimValidateFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#type", null)).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        PayloadGenerator.GlobalData.getDiscriminators().add("pet#type");
        Assertions.assertThat(leadingControlCharsInFieldsTrimValidateFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#number", null)).isTrue();
    }
}

