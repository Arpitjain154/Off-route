package com.idr.navigator.engine;

public record ImuSample(long t, double ax, double ay, double az, double gx, double gy, double gz) {}
