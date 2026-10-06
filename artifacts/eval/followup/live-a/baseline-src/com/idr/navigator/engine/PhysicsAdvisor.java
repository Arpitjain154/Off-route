package com.idr.navigator.engine;

import org.ejml.simple.SimpleMatrix;

public final class PhysicsAdvisor implements FilterAdvisor {

    private final Eskf f;
    private final double[] norms = new double[100];
    private int cursor, count;
    private double gyroNorm, trust;
    private boolean stopped;
    private final StationaryDetector stationary = new StationaryDetector();
    public boolean aligned = false;

    public PhysicsAdvisor(Eskf f) {
        this.f = f;
    }

    public void observe(ImuSample s) {
        stationary.observe(s);
        norms[cursor++ % 100] = Math.sqrt(s.ax() * s.ax() + s.ay() * s.ay() + s.az() * s.az());
        count = Math.min(100, count + 1);
        gyroNorm = Math.sqrt(s.gx() * s.gx() + s.gy() * s.gy() + s.gz() * s.gz());
    }

    public Measurement provide(long timestamp) {
        stopped = stationary.isStopped(timestamp);
        if (stopped) {
            trust = 1;
            return Measurement.direct(3, new double[] { 0, 0, 0 }, f.v, .0025);
        }
        if (count < 100 || !aligned) {
            trust = 0;
            return null;
        }
        double mean = 0;
        for (double n : norms) mean += n / 100;
        double variance = 0;
        for (double n : norms) variance += ((n - mean) * (n - mean)) / 100;
        // Low IMU variance alone cannot distinguish a smooth moving car from a stop.
        if (f.speed() < 1 || gyroNorm > .08 || variance > 1) {
            trust = 0;
            return null;
        }
        trust = .55;
        return f.nonHolonomic(.5,2);
    }

    public double trustScore() {
        return trust;
    }

    public boolean isStopped() {
        return stopped;
    }

    public boolean isStopped(long now) { return stationary.isStopped(now); }
    public void gpsSpeed(long t, double speed, double error) { stationary.gps(t,speed,error); }
    public void gpsPosition(long t,double lat,double lon,double accuracy){stationary.position(t,lat,lon,accuracy);}
}
