package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.generator.simple.PayloadGenerator;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class LeadingTabsInFieldsValidateTrimFuzzerTest {

    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private FilesArguments filesArguments;

    private LeadingTabsInFieldsValidateTrimFuzzer leadingTabsInFieldsValidateTrimFuzzer;

    @BeforeEach
    void setup() {
        leadingTabsInFieldsValidateTrimFuzzer = new LeadingTabsInFieldsValidateTrimFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenANewLeadingTabsInFieldsValidateTrimFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLeadingTabsInFieldsValidateTrimFuzzer() {
        FuzzingStrategy fuzzingStrategy = leadingTabsInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(null, null);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.prefix().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo("\t");
        Assertions.assertThat(leadingTabsInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(leadingTabsInFieldsValidateTrimFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingTabsInFieldsValidateTrimFuzzer.typeOfDataSentToTheService()).isNotNull();
    }


    @Test
    void shouldNotFuzzIfDiscriminatorField() {
        PayloadGenerator.GlobalData.getDiscriminators().add("pet#type");
        Assertions.assertThat(leadingTabsInFieldsValidateTrimFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#type", null)).isFalse();
    }

    @Test
    void shouldFuzzIfNotDiscriminatorField() {
        PayloadGenerator.GlobalData.getDiscriminators().add("pet#type");
        Assertions.assertThat(leadingTabsInFieldsValidateTrimFuzzer.isFuzzingPossibleSpecificToFuzzer(null, "pet#number", null)).isTrue();
    }
}

