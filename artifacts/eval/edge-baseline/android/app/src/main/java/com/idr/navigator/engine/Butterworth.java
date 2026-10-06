
package com.idr.navigator.engine;

/** Second-order low-pass, bilinear transform. Coefficients adapt to the actual sample period. */
public final class Butterworth {

    private double x1, x2, y1, y2;
    private boolean initialized;

    public double apply(double x, double dt) {
        if (!initialized) {
            initialized = true;
            x1 = x2 = y1 = y2 = x;
            return x;
        }
        if (Math.abs(x - x1) > 40) x = x1;
        double k = Math.tan(Math.PI * 10 * Math.min(.02, Math.max(.001, dt))),
            norm = 1 / (1 + Math.sqrt(2) * k + k * k),
            b0 = k * k * norm,
            b1 = 2 * b0,
            b2 = b0,
            a1 = 2 * (k * k - 1) * norm,
            a2 = (1 - Math.sqrt(2) * k + k * k) * norm;
        double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        x2 = x1;
        x1 = x;
        y2 = y1;
        y1 = y;
        return y;
    }
}
