package com.idr.navigator.engine;

import org.ejml.simple.SimpleMatrix;

public final class AiSpeedAdvisor implements FilterAdvisor {

    private final Eskf f;
    private double speed, trust,variance=9;
    private long time;
    private boolean pending;

    public AiSpeedAdvisor(Eskf f) {
        this.f = f;
    }

    public void estimate(long t, double speed, double confidence, boolean placeholder) {
        estimate(t,speed,confidence,placeholder,9);
    }
    public void estimate(long t,double speed,double confidence,boolean placeholder,double variance) {
        if(t<=time)return;
        this.time = t;
        this.speed = speed;
        this.variance=variance;
        this.trust = placeholder||!Double.isFinite(confidence)||confidence<=0||!Double.isFinite(variance)||variance<=0 ? 0 : Math.min(.35, confidence * .35);
        pending = true;
    }

    public Measurement provide(long t) {
        if (
            !pending ||
            t < time ||
            t - time > 2_000_000_000L ||
            trust == 0 ||
            !Double.isFinite(speed) ||
            speed < 0 ||
            speed > 80
        ) return null;
        pending = false;
        double mag = f.speed();
        if (mag < .5) return null;
        SimpleMatrix h = new SimpleMatrix(1, 15);
        h.set(0, 3, f.v[0] / mag);
        h.set(0, 4, f.v[1] / mag);
        return new Measurement(
            new SimpleMatrix(1, 1, true, new double[] { speed - mag }),
            h,
            SimpleMatrix.identity(1).scale(Math.max(1,variance))
        );
    }

    public double trustScore() {
        return trust;
    }
}
