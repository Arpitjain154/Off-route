package com.idr.navigator.engine;

/** Outage-only experiments. Defaults require replay evidence before promotion. */
public final class OutageTuning {
    private OutageTuning() {}
    public static final boolean BOUNDED_SPEED = true;
    public static final double MAX_ACCELERATION_MPS2 = 2.0;
    public static final double MAX_SPEED_CHANGE_MPS = 5.0;
}
