package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.args.FilesArguments;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class StringFormatTotallyWrongValuesFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private FilesArguments filesArguments;

    private StringFormatTotallyWrongValuesFuzzer stringFormatTotallyWrongValuesFuzzer;

    @BeforeEach
    void setup() {
        stringFormatTotallyWrongValuesFuzzer = new StringFormatTotallyWrongValuesFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenANewStringFormatTotallyWrongValuesFuzzerTest_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheStringFormatTotallyWrongValuesFuzzerTest() {
        NumberSchema nrSchema = new NumberSchema();
        nrSchema.setFormat("email");
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.getSchemasThatTheFuzzerWillApplyTo()).containsExactly(StringSchema.class, DateSchema.class, DateTimeSchema.class, PasswordSchema.class, UUIDSchema.class, EmailSchema.class);
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.getBoundaryValue(nrSchema)).isNotNull();
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.hasBoundaryDefined("test", FuzzingData.builder().build())).isTrue();
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.description()).isNotNull();
        Assertions.assertThat(stringFormatTotallyWrongValuesFuzzer.typeOfDataSentToTheService()).isNotNull();

    }
}
