package com.endava.cats.fuzzer.fields.base;

/**
 * Computes online simple linear regression over {@code (requestedDelay, observedDelay)} pairs using
 * Welford's running-variance formulation, mirroring OWASP ZAP's {@code OnlineSimpleLinearRegression}.
 */
final class OnlineLinearRegression {
    private int count;
    private double sumX;
    private double sumY;
    private double varianceXN;
    private double varianceYN;
    private double covarianceN;
    private double slope = 1.0;
    private double rSquared = 1.0;

    void addPoint(double x, double y) {
        if (count == 0) {
            count = 1;
            sumX = x;
            sumY = y;
            return;
        }
        double oldDeltaX = x - sumX / count;
        double oldDeltaY = y - sumY / count;
        count++;
        sumX += x;
        sumY += y;
        double newDeltaX = x - sumX / count;
        double newDeltaY = y - sumY / count;
        varianceXN += newDeltaX * oldDeltaX;
        varianceYN += newDeltaY * oldDeltaY;
        covarianceN += newDeltaX * oldDeltaY;
        updateRegression();
    }

    private void updateRegression() {
        if (varianceXN == 0.0) {
            slope = 0.0;
            rSquared = 0.0;
            return;
        }
        slope = covarianceN / varianceXN;
        if (varianceYN == 0.0) {
            rSquared = 1.0;
            return;
        }
        double r = slope * Math.sqrt(varianceXN / varianceYN);
        rSquared = Double.isNaN(r) ? 0.0 : r * r;
    }

    double slope() {
        return slope;
    }

    double rSquared() {
        return rSquared;
    }

    boolean withinConfidence(double maxCorrelationError, double expectedSlope, double maxSlopeError) {
        return rSquared > 1.0 - maxCorrelationError && Math.abs(expectedSlope - slope) < maxSlopeError;
    }
}
