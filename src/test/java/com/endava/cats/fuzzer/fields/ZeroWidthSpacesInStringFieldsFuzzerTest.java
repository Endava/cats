package com.endava.cats.fuzzer.fields;

import com.endava.cats.args.FilesArguments;
import com.endava.cats.http.HttpMethod;
import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class ZeroWidthSpacesInStringFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    @Mock
    private FilesArguments filesArguments;

    private ZeroWidthSpacesInStringFieldsFuzzer zeroWidthSpacesInStringFieldsFuzzer;

    @BeforeEach
    void setup() {
        zeroWidthSpacesInStringFieldsFuzzer = new ZeroWidthSpacesInStringFieldsFuzzer(serviceCaller, testCaseListener, catsUtil, filesArguments);
    }

    @Test
    void givenANewStringFieldsRightBoundaryFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreNotReturningNull() {
        Assertions.assertThat(zeroWidthSpacesInStringFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
        Assertions.assertThat(zeroWidthSpacesInStringFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(zeroWidthSpacesInStringFieldsFuzzer.skipFor()).containsOnly(HttpMethod.GET, HttpMethod.DELETE);
        Assertions.assertThat(zeroWidthSpacesInStringFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo()).containsOnly(StringSchema.class);
    }

    @ParameterizedTest
    @CsvSource({"2,2", "3,3", "2,3", "3,5", "1,1"})
    void shouldAddZeroLengthSpace(int min, int max) {
        StringSchema stringSchema = new StringSchema();
        stringSchema.setMinLength(min);
        stringSchema.setMaxLength(max);
        String generatedValue = zeroWidthSpacesInStringFieldsFuzzer.getBoundaryValue(stringSchema);
        Assertions.assertThat(generatedValue).contains(ZeroWidthSpacesInStringFieldsFuzzer.ZERO_WIDTH_SPACE).hasSizeBetween(min, max);
    }
}
