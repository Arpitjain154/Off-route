package com.idr.navigator.engine;

/** Navigation observations with explicit availability, separate from experimental INS estimates. */
public final class LiveNavigation {
    private final StationaryDetector stationary=new StationaryDetector();
    private NavigationFusion.Fix fix, candidate, courseAnchor;
    private int candidates;
    private double speed=Double.NaN,heading;
    private double compass,compassError=180;
    private long compassTime,headingTime;
    private String headingSource="Unavailable";
    private boolean headingKnown;
    private boolean holdingStop;
    private double stopLat,stopLon;
    private long lastImu;
    private boolean gyroscopeFresh;
    public final InertialNavigation inertial=new InertialNavigation();
    private long clock,recoveryStart;private double recoveryLat,recoveryLon,recoverySeconds,recoverySpeed;

    public void imu(ImuSample sample) { imu(sample,true); }
    public void imu(ImuSample sample,boolean gyroFresh) {
        stationary.observe(sample);lastImu=sample.t();clock=sample.t();gyroscopeFresh=gyroFresh;
        if(!stationary.isStopped(sample.t()))holdingStop=false;
        if(gyroFresh)inertial.predict(sample,healthy(sample.t()),stationary.isStopped(sample.t()));
    }
    public void gravity(long t,double x,double y,double z){inertial.gravity(t,x,y,z);}
    public void tick(long now){clock=now;}
    public boolean healthy(long now){return fresh(now)&&fix.accuracy()<=20;}
    public boolean estimated(long now){return !healthy(now)&&inertial.available(now);}
    public boolean trackingAvailable(long now){return fresh(now)||estimated(now);}
    public String speedSource(long now){return estimated(now)?"Sensor-fused (INS)"+(inertial.aiWeight(now)>0?" + AI":""):speedAvailable(now)?stopped(now)?"Confirmed stop":"GNSS speed":"Awaiting sensor alignment";}
    public String status(long now){return estimated(now)?inertial.diagnostic:!ready()?"Waiting for GPS":!fresh(now)?inertial.diagnostic:recoveryFactor()>0.01?"Reconciling sensor estimate with GPS":"Live GNSS tracking";}
    public double uncertainty(long now){return estimated(now)||!fresh(now)&&inertial.hasPosition()?inertial.uncertainty():Math.hypot(accuracy(),recoveryFactor()*Math.hypot(recoveryLat*111320,recoveryLon*111320*Math.cos(Math.toRadians(latitude()))));}
    private double recoveryFactor(){return recoveryStart>0&&clock>=recoveryStart?Math.max(0,1-(clock-recoveryStart)*1e-9/recoverySeconds):0;}
    public void compass(long time,double degrees,double error) {
        if(time<=compassTime||!Double.isFinite(degrees+error)||error<0||error>30)return;
        compassTime=time;compass=wrap(degrees);compassError=error;
        inertial.compass(time,degrees,error);
        // A compass describes phone facing, not necessarily vehicle travel. Never
        // overwrite a reliable moving-vehicle course with the phone's mount angle.
        if(fix==null||!fresh(time)||!Double.isFinite(speed)||speed<1.5)updateHeading(time,compass,"Phone compass",.22);
    }
    private void updateHeading(long time,double degrees,String source,double weight) {
        heading=headingKnown?wrap(heading+Eskf.wrapDegrees(degrees-heading)*weight):wrap(degrees);
        headingKnown=true;headingTime=time;headingSource=source;
    }
    private static double wrap(double d){return (d%360+360)%360;}
    private static double distance(NavigationFusion.Fix a,NavigationFusion.Fix b) {
        return Math.hypot((a.lat()-b.lat())*111320,(a.lon()-b.lon())*111320*Math.cos(Math.toRadians(b.lat())));
    }
    public boolean fix(NavigationFusion.Fix next,long now) {
        if(next.time()<=0||next.time()>now||now-next.time()>2_000_000_000L||
            !Double.isFinite(next.lat()+next.lon()+next.accuracy())||Math.abs(next.lat())>85||Math.abs(next.lon())>180||
            next.accuracy()<=0||next.accuracy()>50||(fix!=null&&next.time()<=fix.time()))return false;
        if(fix!=null) {
            double dt=(next.time()-fix.time())*1e-9;
            double allowance=Math.max(15,3*Math.hypot(next.accuracy(),fix.accuracy()))+80*dt;
            if(distance(next,fix)>allowance) {
                candidates=candidate!=null&&distance(candidate,next)<Math.max(20,3*next.accuracy())?candidates+1:1;candidate=next;
                if(candidates<3)return false;
            }
        }
        boolean recovering=estimated(now)&&next.accuracy()<=20;
        double priorLat=latitude(),priorLon=longitude(),priorSpeed=speed(now);clock=now;
        candidates=0;candidate=null;
        double dt=fix==null?1:Math.min(5,(next.time()-fix.time())*1e-9);
        boolean reacquired=fix==null||next.time()-fix.time()>2_000_000_000L;
        fix=next;disabled=false;
        double error=Double.isFinite(next.speedError())?next.speedError():2;
        boolean speedValid=Double.isFinite(next.speed())&&next.speed()>=0&&next.speed()<=80&&error>=0&&error<=3;
        stationary.position(next.time(),next.lat(),next.lon(),next.accuracy());
        stationary.gps(next.time(),speedValid?next.speed():Double.NaN,error);
        // Android GNSS speed can be Doppler-derived. Do not replace it with a
        // long chord through a turn or the magnitude of a constrained INS vector.
        if(speedValid) speed=reacquired||!Double.isFinite(speed)?next.speed():speed+(next.speed()-speed)*(1-Math.exp(-dt/.45));
        else speed=Double.NaN;
        if(stationary.isStopped(now)) {
            speed=0;
            if(!holdingStop){stopLat=next.lat();stopLon=next.lon();holdingStop=true;}
        } else holdingStop=false;
        boolean course=speedValid&&next.speed()>=1&&Double.isFinite(next.bearing())&&
            Double.isFinite(next.bearingError())&&next.bearingError()>=0&&next.bearingError()<=30;
        if(course) {
            double target=next.bearing();
            // At walking speeds an accurate compass can stabilize a noisy GPS course.
            // Blend only when the two agree; an arbitrary phone mount is not a travel bearing.
            if(next.speed()<3&&now-compassTime<1_000_000_000L&&compassError<=20&&Math.abs(Eskf.wrapDegrees(compass-target))<35)
                target+=Eskf.wrapDegrees(compass-target)*Math.max(0,(3-next.speed())/6);
            updateHeading(now,target,"GPS course",reacquired?1:1-Math.exp(-dt/.35));
        } else if(courseAnchor!=null) {
            double seconds=(next.time()-courseAnchor.time())*1e-9,d=distance(next,courseAnchor);
            if(seconds>=1&&seconds<=4&&d>Math.max(5,2*Math.hypot(next.accuracy(),courseAnchor.accuracy()))) {
                double north=(next.lat()-courseAnchor.lat()),east=(next.lon()-courseAnchor.lon())*Math.cos(Math.toRadians(next.lat()));
                updateHeading(now,Math.toDegrees(Math.atan2(east,north)),"GPS displacement",.85);
                courseAnchor=next;
            }
        }
        if(courseAnchor==null||next.time()-courseAnchor.time()>4_000_000_000L)courseAnchor=next;
        if(next.accuracy()<=20&&speedValid) {
            NavigationFusion.Fix anchor=holdingStop?new NavigationFusion.Fix(next.time(),stopLat,stopLon,next.alt(),next.accuracy(),next.speed(),next.speedError(),next.bearing(),next.bearingError()):next;
            inertial.anchor(anchor,now,speed,stationary.isStopped(now),heading);
            if(recovering){recoveryLat=priorLat-next.lat();recoveryLon=priorLon-next.lon();recoverySpeed=priorSpeed-speed;recoveryStart=now;
                recoverySeconds=Math.max(1,Math.min(8,Math.hypot(recoveryLat*111320,recoveryLon*111320*Math.cos(Math.toRadians(next.lat())))/5));}
        }
        return true;
    }
    private boolean disabled;
    public void providerDisabled(){disabled=true;speed=Double.NaN;courseAnchor=null;}
    public void providerEnabled(){disabled=false;}
    public boolean fresh(long now){return !disabled&&fix!=null&&now>=fix.time()&&now-fix.time()<=2_000_000_000L;}
    public boolean ready(){return fix!=null;}
    public long fixTime(){return fix==null?0:fix.time();}
    public NavigationFusion.Fix observation(){return fix;}
    // Losing alignment makes the estimate unavailable, not a reason to rewind the
    // map to the GPS coordinate from before the outage. Keep the last known pose.
    private boolean retainInertialPosition(){return estimated(clock)||!fresh(clock)&&inertial.hasPosition();}
    public double latitude(){return retainInertialPosition()?inertial.latitude():fix==null?23.2418:(holdingStop?stopLat:fix.lat())+recoveryLat*recoveryFactor();}
    public double longitude(){return retainInertialPosition()?inertial.longitude():fix==null?77.4029:(holdingStop?stopLon:fix.lon())+recoveryLon*recoveryFactor();}
    public double accuracy(){return fix==null?0:fix.accuracy();}
    public boolean stopped(long now){return estimated(now)?inertial.stopped():fresh(now)&&stationary.isStopped(now);}
    public boolean speedAvailable(long now){return estimated(now)||fresh(now)&&(stopped(now)||Double.isFinite(speed));}
    public double speed(long now){return estimated(now)?inertial.speed():speedAvailable(now)?stopped(now)?0:Math.max(0,speed+recoverySpeed*(recoveryStart>0?Math.max(0,1-(now-recoveryStart)*1e-9/2):0)):0;}
    public double heading(){return estimated(clock)?inertial.heading():heading;}
    public boolean headingReady(long now){return estimated(now)||headingKnown&&now>=headingTime&&now-headingTime<5_000_000_000L;}
    public String headingSource(long now){return estimated(now)?"Gyroscope + calibrated compass":headingReady(now)?headingSource:"Unavailable";}
    public boolean imuFresh(long now){return gyroscopeFresh&&lastImu>0&&now>=lastImu&&now-lastImu<500_000_000L;}
}
