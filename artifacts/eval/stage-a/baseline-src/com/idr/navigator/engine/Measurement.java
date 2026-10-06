package com.idr.navigator.engine;

import org.ejml.simple.SimpleMatrix;

/** Error-state residual, Jacobian and measurement covariance in the ENU frame. */
public record Measurement(SimpleMatrix residual, SimpleMatrix h, SimpleMatrix covariance) {
    public static Measurement direct(
        int offset,
        double[] observed,
        double[] predicted,
        double variance
    ) {
        int n = observed.length;
        SimpleMatrix h = new SimpleMatrix(n, 15),
            r = new SimpleMatrix(n, 1);
        for (int i = 0; i < n; i++) {
            h.set(i, offset + i, 1);
            r.set(i, observed[i] - predicted[i]);
        }
        return new Measurement(r, h, SimpleMatrix.identity(n).scale(variance));
    }
}
