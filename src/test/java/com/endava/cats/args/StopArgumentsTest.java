package com.endava.cats.args;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

@QuarkusTest
class StopArgumentsTest {
    private StopArguments stopArguments;

    @BeforeEach
    void setup() {
        stopArguments = new StopArguments();
    }

    @ParameterizedTest
    @CsvSource({"2,0,false", "2,1,true", "2,3,false"})
    void shouldTestStopOnErrors(int errorNo, int threshold, boolean expected) {
        ReflectionTestUtils.setField(stopArguments, "stopAfterErrors", threshold);

        boolean result = stopArguments.shouldStop(errorNo, 0, 0);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"2,0,false", "2,1,true", "2,3,false"})
    void shouldTestStopOnTests(int testsNo, int threshold, boolean expected) {
        ReflectionTestUtils.setField(stopArguments, "stopAfterMutations", threshold);

        boolean result = stopArguments.shouldStop(0, testsNo, 0);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"2000,0,false", "2000,1,true", "2000,3,false"})
    void shouldTestStopOnTime(int millisToSubtract, int thresholdInSeconds, boolean expected) {
        ReflectionTestUtils.setField(stopArguments, "stopAfterTimeInSec", thresholdInSeconds);
        long startTime = System.currentTimeMillis() - millisToSubtract;
        boolean result = stopArguments.shouldStop(0, 0, startTime);
        Assertions.assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"stopAfterTimeInSec,0,false", "stopAfterMutations,0,false", "stopAfterErrors,0,false",
            "stopAfterTimeInSec,1,true", "stopAfterMutations,1,true", "stopAfterErrors,1,true"})
    void shouldTestStopConditionProvided(String field, int value, boolean expected) {
        ReflectionTestUtils.setField(stopArguments, field, value);

        boolean result = stopArguments.isAnyStopConditionProvided();
        Assertions.assertThat(result).isEqualTo(expected);
    }
}
