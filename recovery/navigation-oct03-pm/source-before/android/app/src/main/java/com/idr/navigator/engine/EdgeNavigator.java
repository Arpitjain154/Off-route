package com.idr.navigator.engine;

/** Device-independent embedding API. Call on one thread, with sensor-frame SI data and monotonic ns.
 * Gravity and compass must refer to the same axes as acceleration/gyro. No Android classes. */
public final class EdgeNavigator {
    private final LiveNavigation live=new LiveNavigation();
    public record Pose(double latitude,double longitude,double speed,double heading,double uncertainty,
                       boolean estimated,boolean available,String speedSource) {}
    public void gravity(long time,double x,double y,double z){live.gravity(time,x,y,z);}
    public void compass(long time,double trueNorthDegrees,double uncertaintyDegrees){live.compass(time,trueNorthDegrees,uncertaintyDegrees);}
    public void imu(ImuSample sample,boolean gyroscopeFresh){live.imu(sample,gyroscopeFresh);}
    public boolean gnss(NavigationFusion.Fix fix,long now){return live.fix(fix,now);}
    public void gnssUnavailable(){live.providerDisabled();}
    public void roadMatch(long time,double sourceLatitude,double sourceLongitude,double matchedLatitude,double matchedLongitude,double heading,double trust){live.inertial.road(time,sourceLatitude,sourceLongitude,matchedLatitude,matchedLongitude,heading,trust);}
    public void models(long time,double predictedSpeed,float[] motion,float[] health,Double referenceSpeed,boolean reliableGnss){live.inertial.models(time,predictedSpeed,motion,health,referenceSpeed,reliableGnss);}
    public Pose position(long now){live.tick(now);return new Pose(live.latitude(),live.longitude(),live.speed(now),live.heading(),live.uncertainty(now),live.estimated(now),live.trackingAvailable(now),live.speedSource(now));}
}
