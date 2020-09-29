package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class StringFormatAlmostValidValuesFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private StringFormatAlmostValidValuesFuzzer stringFormatAlmostValidValuesFuzzer;

    @BeforeEach
    void setup() {
        stringFormatAlmostValidValuesFuzzer = new StringFormatAlmostValidValuesFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    void givenANewStringFormatAlmostValidValuesFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFormatAlmostValidValuesFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        nrSchema.setFormat("email");
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.getSchemasThatTheFuzzerWillApplyTo()).containsExactly(StringSchema.class, DateSchema.class, DateTimeSchema.class, PasswordSchema.class, UUIDSchema.class, EmailSchema.class, ByteArraySchema.class);
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.getBoundaryValue(nrSchema)).isNotNull();
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.description()).isNotNull();
        Assertions.assertThat(stringFormatAlmostValidValuesFuzzer.typeOfDataSentToTheService()).isNotNull();

    }
}
