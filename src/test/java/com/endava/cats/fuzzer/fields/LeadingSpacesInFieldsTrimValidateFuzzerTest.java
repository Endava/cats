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
public class LeadingSpacesInFieldsTrimValidateFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private LeadingSpacesInFieldsTrimValidateFuzzer leadingSpacesInFieldsTrimValidateFuzzer;

    @BeforeEach
    public void setup() {
        leadingSpacesInFieldsTrimValidateFuzzer = new LeadingSpacesInFieldsTrimValidateFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    public void givenANewLeadingSpacesInFieldsTrimValidateFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheLeadingSpacesInFieldsTrimValidateFuzzer() {
        FuzzingStrategy fuzzingStrategy = leadingSpacesInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(null, null);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.prefix().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(" ");
        Assertions.assertThat(leadingSpacesInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(leadingSpacesInFieldsTrimValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(leadingSpacesInFieldsTrimValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
