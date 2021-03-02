package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.util.CatsUtil;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class TrailingSpacesInFieldsValidateTrimFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private FilesArguments filesArguments;

    private TrailingSpacesInFieldsValidateTrimFuzzer trailingSpacesInFieldsValidateTrimFuzzer;

    @BeforeEach
    void setup() {
        trailingSpacesInFieldsValidateTrimFuzzer = new TrailingSpacesInFieldsValidateTrimFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenANewTrailingSpacesInFieldsValidateTrimFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheTrailingSpacesInFieldsValidateTrimFuzzer() {
        FuzzingStrategy fuzzingStrategy = trailingSpacesInFieldsValidateTrimFuzzer.getFieldFuzzingStrategy(null, null);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.trail().name());

        Assertions.assertThat(fuzzingStrategy.getData()).isEqualTo(" ");
        Assertions.assertThat(trailingSpacesInFieldsValidateTrimFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(trailingSpacesInFieldsValidateTrimFuzzer.description()).isNotNull();
        Assertions.assertThat(trailingSpacesInFieldsValidateTrimFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
