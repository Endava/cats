package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
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
public class LeadingSpacesInFieldsValidateTrimFuzzerTest {

    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private LeadingSpacesInFieldsValidateTrimFuzzer leadingSpacesInFieldsValidateTrimFuzzer;

    @BeforeEach
    public void setup() {
        leadingSpacesInFieldsValidateTrimFuzzer = new LeadingSpacesInFieldsValidateTrimFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    public void givenANewLeadingSpacesInFieldsValidateTrimFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLeadingSpacesInFieldsValidateTrimFuzzer() {
        FuzzingStrategy fuzzingStrategy = leadingSpacesInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(null, null);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.prefix().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(" ");
        Assertions.assertThat(leadingSpacesInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(leadingSpacesInFieldsValidateTrimFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingSpacesInFieldsValidateTrimFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
