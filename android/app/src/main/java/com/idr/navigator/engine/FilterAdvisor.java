package com.idr.navigator.engine;

public interface FilterAdvisor {
    Measurement provide(long timestamp);
    double trustScore();
}
