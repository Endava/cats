package com.endava.cats.fuzzer.headers.trailing;

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
class TrailingControlCharsInHeadersFuzzerTest {
    private final CatsUtil catsUtil = new CatsUtil(null);

    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    private TrailingControlCharsInHeadersFuzzer trailingControlCharsInHeadersFuzzer;

    @BeforeEach
    void setup() {
        trailingControlCharsInHeadersFuzzer = new TrailingControlCharsInHeadersFuzzer(catsUtil, serviceCaller, testCaseListener);
    }

    @Test
    void givenANewTrailingSpacesInHeadersFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheTrailingSpacesInHeadersFuzzer() {
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.description()).isNotNull();
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.fuzzStrategy().get(0).name()).isEqualTo(FuzzingStrategy.trail().name());
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.fuzzStrategy().get(1).getData()).isEqualTo("\u0000");
        Assertions.assertThat(trailingControlCharsInHeadersFuzzer.matchResponseSchema()).isFalse();
    }
}
