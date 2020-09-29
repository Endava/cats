package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.model.FuzzingData;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.StringSchema;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;

@ExtendWith(SpringExtension.class)
class InvalidValuesInEnumsFieldsFuzzerTest {

    @Mock
    private ServiceCaller serviceCaller;
    @Mock
    private TestCaseListener testCaseListener;
    @Mock
    private CatsUtil catsUtil;
    private InvalidValuesInEnumsFieldsFuzzer invalidValuesInEnumsFieldsFuzzer;

    @BeforeEach
    void setup() {
        invalidValuesInEnumsFieldsFuzzer = new InvalidValuesInEnumsFieldsFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    void shouldNotHaveBoundaryDefined() {
        StringSchema stringSchema = new StringSchema();
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(StringSchema.class))).isTrue();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getBoundaryValue(stringSchema)).isEmpty();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.hasBoundaryDefined("test", data)).isFalse();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }

    @Test
    void shouldHaveBoundaryDefined() {
        StringSchema stringSchema = new StringSchema();
        stringSchema.setEnum(Collections.singletonList("TEST"));
        FuzzingData data = FuzzingData.builder().requestPropertyTypes(Collections.singletonMap("test", stringSchema)).build();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(StringSchema.class))).isTrue();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.getBoundaryValue(stringSchema)).hasSizeLessThanOrEqualTo(4);
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.hasBoundaryDefined("test", data)).isTrue();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(invalidValuesInEnumsFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
