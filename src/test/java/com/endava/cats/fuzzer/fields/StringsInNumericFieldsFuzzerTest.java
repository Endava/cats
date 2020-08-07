package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class StringsInNumericFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private StringsInNumericFieldsFuzzer stringsInNumericFieldsFuzzer;

    @BeforeEach
    void setup() {
        stringsInNumericFieldsFuzzer = new StringsInNumericFieldsFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    void shouldGetSchemasThatTheFuzzerWillApplyTo() {
        Assertions.assertThat(stringsInNumericFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo()).containsOnly(IntegerSchema.class, NumberSchema.class);
    }

    @Test
    void shouldGetTypeOfDataSentToTheService() {
        Assertions.assertThat(stringsInNumericFieldsFuzzer.typeOfDataSentToTheService()).isEqualTo("strings in numeric fields");
    }

    @Test
    void shouldReturnTrueForHasBoundaryDefined() {
        Assertions.assertThat(stringsInNumericFieldsFuzzer.hasBoundaryDefined(null)).isTrue();
    }

    @Test
    void shouldGetDescription() {
        Assertions.assertThat(stringsInNumericFieldsFuzzer.description()).isEqualTo("iterate through each Integer (int, long) and Number field (float, double) and send requests with alphanumeric characters in the targeted field");
    }

    @Test
    void shouldGenerateRandomString() {
        Assertions.assertThat(stringsInNumericFieldsFuzzer.getBoundaryValue(null)).isNotBlank();
    }
}
