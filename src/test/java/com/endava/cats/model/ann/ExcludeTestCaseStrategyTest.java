package com.endava.cats.model.ann;

import com.google.gson.FieldAttributes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExcludeTestCaseStrategyTest {

    private ExcludeTestCaseStrategy excludeTestCaseStrategy;

    @BeforeEach
    void setup() {
        excludeTestCaseStrategy = new ExcludeTestCaseStrategy();
    }

    @Test
    void shouldExcludeClass() {
        boolean actual = excludeTestCaseStrategy.shouldSkipClass(ExcludedClass.class);
        Assertions.assertThat(actual).isTrue();
    }

    @Test
    void shouldNotExcludeClass() {
        boolean actual = excludeTestCaseStrategy.shouldSkipClass(NotExcludedClass.class);
        Assertions.assertThat(actual).isFalse();
    }


    @Test
    void shouldExcludeField() throws Exception {
        FieldAttributes fa = new FieldAttributes(ExcludedClass.class.getField("excluded"));
        boolean actual = excludeTestCaseStrategy.shouldSkipField(fa);
        Assertions.assertThat(actual).isTrue();
    }

    @Test
    void shouldNotExcludeField() throws Exception {
        FieldAttributes fa = new FieldAttributes(NotExcludedClass.class.getField("notExcluded"));
        boolean actual = excludeTestCaseStrategy.shouldSkipField(fa);
        Assertions.assertThat(actual).isFalse();
    }


    @Exclude
    static class ExcludedClass {

        @Exclude
        public String excluded;
    }

    static class NotExcludedClass {
        public String notExcluded;
    }
}
