package com.endava.cats.fuzzer.fields;

import com.endava.cats.http.ResponseCodeFamilyPredefined;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EmojisInStringFieldsValidateSanitizeFuzzerTest {

    @Test
    void shouldReturn4XX() {
        EmojisInStringFieldsValidateSanitizeFuzzer fuzzer =
                new EmojisInStringFieldsValidateSanitizeFuzzer(null, null, null);
        Assertions.assertThat(fuzzer.getExpectedHttpCodeWhenRequiredFieldsAreFuzzed())
                .isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(fuzzer.getExpectedHttpCodeWhenOptionalFieldsAreFuzzed())
                .isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
        Assertions.assertThat(fuzzer.getExpectedHttpCodeWhenFuzzedValueNotMatchesPattern())
                .isEqualTo(ResponseCodeFamilyPredefined.FOURXX);
    }
}
