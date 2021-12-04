package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class StringsInNumericFieldsFuzzerTest {

    private StringsInNumericFieldsFuzzer stringsInNumericFieldsFuzzer;

    @BeforeEach
    void setup() {
        stringsInNumericFieldsFuzzer = new StringsInNumericFieldsFuzzer(Mockito.mock(ServiceCaller.class), Mockito.mock(TestCaseListener.class), Mockito.mock(CatsUtil.class), Mockito.mock(FilesArguments.class));
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
        Assertions.assertThat(stringsInNumericFieldsFuzzer.hasBoundaryDefined(null, FuzzingData.builder().build())).isTrue();
    }

    @Test
    void shouldGetDescription() {
        Assertions.assertThat(stringsInNumericFieldsFuzzer.description()).isEqualTo("iterate through each Integer (int, long) and Number field (float, double) and send requests having the `fuzz` string value in the targeted field");
    }

    @Test
    void shouldGenerateRandomString() {
        Assertions.assertThat(stringsInNumericFieldsFuzzer.getBoundaryValue(null)).isNotBlank();
    }
}
