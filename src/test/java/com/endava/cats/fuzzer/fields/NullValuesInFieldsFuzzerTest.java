package com.endava.cats.fuzzer.fields;

import com.endava.cats.fuzzer.http.ResponseCodeFamily;
import com.endava.cats.http.HttpMethod;
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
public class NullValuesInFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private NullValuesInFieldsFuzzer nullValuesInFieldsFuzzer;

    @BeforeEach
    public void setup() {
        nullValuesInFieldsFuzzer = new NullValuesInFieldsFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    public void givenANewNullValuesInFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheNullValuesInFieldsFuzzer() {
        Assertions.assertThat(nullValuesInFieldsFuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamily.TWOXX);
        Assertions.assertThat(nullValuesInFieldsFuzzer.skipFor()).containsExactly(HttpMethod.GET, HttpMethod.DELETE);

        FuzzingStrategy fuzzingStrategy = nullValuesInFieldsFuzzer.getFieldFuzzingStrategy(null, null);
        Assertions.assertThat(fuzzingStrategy.name()).isEqualTo(FuzzingStrategy.replace().name());
        Assertions.assertThat(fuzzingStrategy.getData()).isNull();
        Assertions.assertThat(nullValuesInFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(nullValuesInFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
