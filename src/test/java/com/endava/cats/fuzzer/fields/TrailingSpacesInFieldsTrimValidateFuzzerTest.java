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
public class TrailingSpacesInFieldsTrimValidateFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private TrailingSpacesInFieldsTrimValidateFuzzer trailingSpacesInFieldsTrimValidateFuzzer;

    @BeforeEach
    public void setup() {
        trailingSpacesInFieldsTrimValidateFuzzer = new TrailingSpacesInFieldsTrimValidateFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    public void givenANewTrailingSpacesInFieldsTrimValidateFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheTrailingSpacesInFieldsTrimValidateFuzzer() {
        FuzzingStrategy fuzzingStrategy = trailingSpacesInFieldsTrimValidateFuzzer.getFieldFuzzingStrategy(null, null);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.trail().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(" ");
        Assertions.assertThat(trailingSpacesInFieldsTrimValidateFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(trailingSpacesInFieldsTrimValidateFuzzer.description()).isNotNull();
        Assertions.assertThat(trailingSpacesInFieldsTrimValidateFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
