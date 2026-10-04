package com.idr.navigator.engine;

/** Raw diagnostic output only. Availability never grants permission to steer navigation. */
public final class AiSpeedReading {
    private long timestamp;
    private double value=Double.NaN;
    public void clear(){timestamp=0;value=Double.NaN;}
    public void update(long now,double speed){
        clear();
        if(now>0&&Double.isFinite(speed)&&speed>=0&&speed<=80){timestamp=now;value=speed;}
    }
    public boolean available(long now){return timestamp>0&&now>=timestamp&&now-timestamp<=2_000_000_000L&&Double.isFinite(value);}
    public double speed(long now){return available(now)?value:Double.NaN;}
}
