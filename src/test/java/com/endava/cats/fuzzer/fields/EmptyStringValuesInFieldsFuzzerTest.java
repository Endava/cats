package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingStrategy;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsParams;
import com.endava.cats.util.CatsUtil;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class EmptyStringValuesInFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private CatsParams catsParams;

    private EmptyStringValuesInFieldsFuzzer emptyStringValuesInFieldsFuzzer;

    @BeforeEach
    void setup() {
        emptyStringValuesInFieldsFuzzer = new EmptyStringValuesInFieldsFuzzer(serviceCaller, testCaseListener, catsUtil, catsParams);
    }

    @Test
    void givenANewEmptyStringFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheEmptyStringFuzzer() {
        Assertions.assertThat(emptyStringValuesInFieldsFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.FOURXX);
        Assertions.assertThat(emptyStringValuesInFieldsFuzzer.skipFor()).containsExactly(HttpMethod.GET, HttpMethod.DELETE);

        FuzzingStrategy fuzzingStrategy = emptyStringValuesInFieldsFuzzer.getFieldFuzzingStrategy(null, null);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isEmpty();
        Assertions.assertThat(emptyStringValuesInFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(emptyStringValuesInFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
