package com.endava.cats.fuzzer.fields;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class RawJsonFieldTemplateTest {
    @Test
    void shouldKeepNestedAndArrayReplacementsExactAndPreserveCollidingUserStrings() {
        String payload = "{\"items\":[{\"amount\":1},{\"amount\":2}],\"keep\":\"__CATS_RAW_NUMBER__\"}";
        var template = RawJsonFieldTemplate.of(payload, "items[*]#amount");
        assertThat(template.replaceWith("1e+"))
                .isEqualTo("{\"items\":[{\"amount\":1e+},{\"amount\":1e+}],\"keep\":\"__CATS_RAW_NUMBER__\"}");
    }

    @Test
    void shouldPreserveEscapedUserStringsThatWouldCollideAfterJsonParsing() {
        var template = RawJsonFieldTemplate.of("{\"amount\":1,\"keep\":\"\\u005f_CATS_RAW_NUMBER__\"}", "amount");
        assertThat(template.replaceWith("-0.0")).isEqualTo("{\"amount\":-0.0,\"keep\":\"__CATS_RAW_NUMBER__\"}");
    }
}
