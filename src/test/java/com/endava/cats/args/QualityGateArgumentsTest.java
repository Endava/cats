package com.endava.cats.args;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

@QuarkusTest
class QualityGateArgumentsTest {

    private QualityGateArguments qualityGateArguments;

    @BeforeEach
    void setUp() {
        qualityGateArguments = new QualityGateArguments();
    }

    @Test
    void shouldFailOnErrorsByDefault() {
        // Default behavior: fail on any error
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 10)).isFalse();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 0)).isFalse();
    }

    @Test
    void shouldFailOnErrorsWhenExplicitlyConfigured() {
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "error");
        
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(5, 10)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 10)).isFalse();
    }

    @Test
    void shouldFailOnWarningsWhenConfigured() {
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "warn");
        
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 1)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 10)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isFalse();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 0)).isFalse();
    }

    @Test
    void shouldFailOnErrorsOrWarningsWhenBothConfigured() {
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "error,warn");
        
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 1)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 1)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 0)).isFalse();
    }

    @Test
    void shouldHandleCaseInsensitiveFailOn() {
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "ERROR,WARN");
        
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 1)).isTrue();
    }

    @Test
    void shouldHandleWhitespaceInFailOn() {
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", " error , warn ");
        
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 1)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "errors<5, 4, 0, false",
        "errors<5, 5, 0, true",
        "errors<5, 6, 0, true",
        "warns<10, 0, 9, false",
        "warns<10, 0, 10, true",
        "warns<10, 0, 11, true"
    })
    void shouldEvaluateLessThanQualityGates(String gate, int errors, int warnings, boolean shouldFail) {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", gate);
        
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(errors, warnings)).isEqualTo(shouldFail);
    }

    @ParameterizedTest
    @CsvSource({
        "errors>5, 6, 0, false",
        "errors>5, 5, 0, true",
        "errors>5, 4, 0, true",
        "warns>10, 0, 11, false",
        "warns>10, 0, 10, true",
        "warns>10, 0, 9, true"
    })
    void shouldEvaluateGreaterThanQualityGates(String gate, int errors, int warnings, boolean shouldFail) {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", gate);
        
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(errors, warnings)).isEqualTo(shouldFail);
    }

    @Test
    void shouldEvaluateMultipleQualityGates() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "errors<5,warns<20");
        
        // Both pass
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(4, 19)).isFalse();
        
        // Errors fail
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(5, 19)).isTrue();
        
        // Warnings fail
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(4, 20)).isTrue();
        
        // Both fail
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(5, 20)).isTrue();
    }

    @Test
    void shouldSupportWarningsAlias() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "warnings<10");
        
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 9)).isFalse();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 10)).isTrue();
    }

    @Test
    void shouldHandleWhitespaceInQualityGate() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", " errors < 5 , warns < 20 ");
        
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(4, 19)).isFalse();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(5, 19)).isTrue();
    }

    @Test
    void shouldHandleEmptyQualityGate() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "");
        
        // Should fall back to default behavior
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 10)).isFalse();
    }

    @Test
    void shouldHandleInvalidQualityGateFormat() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "errors=5");
        
        // Should not fail on invalid format (logs warning)
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(10, 0)).isFalse();
    }

    @Test
    void shouldHandleInvalidMetricName() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "invalid<5");
        
        // Should not fail on unknown metric (logs warning)
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(10, 0)).isFalse();
    }

    @Test
    void shouldHandleInvalidThresholdValue() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "errors<abc");
        
        // Should not fail on invalid threshold (logs warning)
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(10, 0)).isFalse();
    }

    @Test
    void shouldPrioritizeQualityGateOverFailOn() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "errors<10");
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "error");
        
        // Quality gate takes precedence
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(5, 0)).isFalse();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(10, 0)).isTrue();
    }

    @Test
    void shouldGetQualityGateDescription() {
        Assertions.assertThat(qualityGateArguments.getQualityGateDescription())
                .isEqualTo("Default: fail on any error");
        
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "error,warn");
        Assertions.assertThat(qualityGateArguments.getQualityGateDescription())
                .isEqualTo("Fail on: error,warn");
        
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "errors<5,warns<20");
        Assertions.assertThat(qualityGateArguments.getQualityGateDescription())
                .isEqualTo("Quality gate: errors<5,warns<20");
    }

    @Test
    void shouldHandleEmptyFailOn() {
        ReflectionTestUtils.setField(qualityGateArguments, "failOn", "");
        
        // Should fall back to default behavior
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 10)).isFalse();
    }

    @Test
    void shouldHandleEmptyConditionsInQualityGate() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "errors<5,,warns<20");
        
        // Should skip empty conditions
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(4, 19)).isFalse();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(5, 19)).isTrue();
    }

    @Test
    void shouldHandleZeroThresholds() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "errors<0");
        
        // Any error should fail
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(0, 0)).isTrue();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1, 0)).isTrue();
    }

    @Test
    void shouldHandleLargeNumbers() {
        ReflectionTestUtils.setField(qualityGateArguments, "qualityGate", "errors<1000000");
        
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(999999, 0)).isFalse();
        Assertions.assertThat(qualityGateArguments.shouldFailBuild(1000000, 0)).isTrue();
    }
}
