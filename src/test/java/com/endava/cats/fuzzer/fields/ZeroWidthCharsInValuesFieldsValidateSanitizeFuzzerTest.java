package com.endava.cats.fuzzer.fields;

import com.endava.cats.http.ResponseCodeFamilyPredefined;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ZeroWidthCharsInValuesFieldsValidateSanitizeFuzzerTest {

    @Test
    void shouldReturn4XX() {
        ZeroWidthCharsInValuesFieldsValidateSanitizeFuzzer zwcf = new ZeroWidthCharsInValuesFieldsValidateSanitizeFuzzer(null, null, null);
        Assertions.assertThat(zwcf.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(zwcf.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(zwcf.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern()).isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
    }
}
