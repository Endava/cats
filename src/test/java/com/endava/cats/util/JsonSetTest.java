package com.endava.cats.util;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

@QuarkusTest
class JsonSetTest {

    @Test
    void shouldReturnSize() {
        JsonSet jsonSet = new JsonSet();
        Assertions.assertThat(jsonSet.size()).isZero();

        jsonSet.add("test");
        Assertions.assertThat(jsonSet.size()).isOne();
    }

    @ParameterizedTest
    @MethodSource("jsonSetTestDataProvider")
    void testJsonSet(String[] jsonStrings, int expectedSize) {
        JsonSet jsonSet = new JsonSet();
        for (String jsonString : jsonStrings) {
            jsonSet.add(jsonString);
        }
        Assertions.assertThat(jsonSet.size()).isEqualTo(expectedSize);
    }

    private static Stream<Object[]> jsonSetTestDataProvider() {
        return Stream.of(
                new Object[]{new String[]{}, 0},
                new Object[]{new String[]{"test"}, 1},
                new Object[]{new String[]{"{\"field\":\"value\"}", "{\"field\":\"value\"}"}, 1},
                new Object[]{new String[]{"{\"field\":\"value\"}", "{\"field\":\"otherValue\"}"}, 2},
                new Object[]{new String[]{"{\"field\":\"value\"}", "{\"field1\":\"otherValue\"}"}, 2}
        );
    }
}
