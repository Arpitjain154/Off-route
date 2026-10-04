package com.idr.navigator.engine;

public final class GpsAdvisor implements FilterAdvisor {

    private final Eskf filter;
    private double lat,
        lon,
        alt,
        accuracy = 100,
        trust;
    private boolean pending;
    private long time;
    public int satellites;
    public double cn0;

    public GpsAdvisor(Eskf filter) {
        this.filter = filter;
    }

    public void satellites(int count, double mean) {
        satellites = count;
        cn0 = mean;
    }

    public void fix(long t, double lat, double lon, double alt, double accuracy) {
        this.time = t;
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
        this.accuracy = accuracy;
        pending = true;
        // Raw GNSS measurements are optional on Android devices. Their absence
        // must not discard an otherwise accurate LocationManager fix.
        double signal = satellites > 0 && cn0 > 0
            ? Math.max(.5, Math.min(1, satellites / 10.0) * Math.min(1, cn0 / 30.0)) : .8;
        trust = signal * Math.min(1, 15 / Math.max(1, accuracy));
    }

    public Measurement provide(long t) {
        if (!pending || t - time > 2_000_000_000L) return null;
        pending = false;
        return Measurement.direct(
            0,
            java.util.Arrays.copyOf(filter.enu(lat, lon, alt), 2),
            java.util.Arrays.copyOf(filter.p, 2),
            Math.max(4, accuracy * accuracy)
        );
    }

    public double trustScore() {
        return trust;
    }

    public long lastFix() {
        return time;
    }

    public double accuracy() {
        return accuracy;
    }
}
