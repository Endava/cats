package com.endava.cats.fuzzer.fields.base;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@QuarkusTest
class OnlineLinearRegressionTest {
    @Test
    void shouldRetainInitialEstimatesUntilASecondPointArrives() {
        OnlineLinearRegression regression = new OnlineLinearRegression();
        assertThat(regression.slope()).isEqualTo(1.0);
        assertThat(regression.rSquared()).isEqualTo(1.0);

        regression.addPoint(5, 12);

        assertThat(regression.slope()).isEqualTo(1.0);
        assertThat(regression.rSquared()).isEqualTo(1.0);
    }

    @Test
    void shouldHandleConstantInputAndRecoverWhenInputVaries() {
        OnlineLinearRegression regression = new OnlineLinearRegression();
        regression.addPoint(1, 2);
        regression.addPoint(1, 4);
        assertThat(regression.slope()).isZero();
        assertThat(regression.rSquared()).isZero();

        regression.addPoint(2, 6);

        assertThat(regression.slope()).isCloseTo(3.0, within(1e-12));
        assertThat(regression.rSquared()).isCloseTo(0.75, within(1e-12));
    }

    @Test
    void shouldTreatConstantObservationsAsAHorizontalPerfectFit() {
        OnlineLinearRegression regression = new OnlineLinearRegression();
        regression.addPoint(1, 7);
        regression.addPoint(2, 7);

        assertThat(regression.slope()).isZero();
        assertThat(regression.rSquared()).isEqualTo(1.0);
    }

    @Test
    void shouldRejectNonFiniteFitsAsUncorrelated() {
        OnlineLinearRegression regression = new OnlineLinearRegression();
        regression.addPoint(1, 1);
        regression.addPoint(Double.NaN, 2);

        assertThat(regression.slope()).isNaN();
        assertThat(regression.rSquared()).isZero();
        assertThat(regression.withinConfidence(0.1, 1, 0.1)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0.1, 2, 0.1, true", "0, 2, 0.1, false", "0.1, 3, 1, false", "0.1, 3, 1.01, true"})
    void shouldRequireBothCorrelationAndSlopeStrictlyWithinTolerance(double correlationError, double expectedSlope,
                                                                     double slopeError, boolean expected) {
        OnlineLinearRegression regression = new OnlineLinearRegression();
        regression.addPoint(1, 3);
        regression.addPoint(2, 5);
        regression.addPoint(3, 7);

        assertThat(regression.slope()).isEqualTo(2.0);
        assertThat(regression.rSquared()).isEqualTo(1.0);
        assertThat(regression.withinConfidence(correlationError, expectedSlope, slopeError)).isEqualTo(expected);
    }
}
