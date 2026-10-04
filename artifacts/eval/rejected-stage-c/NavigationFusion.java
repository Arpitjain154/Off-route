package com.idr.navigator.engine;

/** Android-independent orchestration shared by live capture and recorded-drive regression replay. */
public final class NavigationFusion {
    public final Eskf filter=new Eskf();
    public final GpsAdvisor gps=new GpsAdvisor(filter);
    public final PhysicsAdvisor physics=new PhysicsAdvisor(filter);
    private final double[] gravity={0,0,9.80665};
    private final Butterworth[] clean={new Butterworth(),new Butterworth(),new Butterworth()};
    private long start, lastImu, lastPhysics, lastFix, previousFix;
    private long gravityTime, consumedGravity;
    private boolean tiltReady;
    private double[] vehicleForward;
    private final AiSpeedAdvisor ai=new AiSpeedAdvisor(filter);
    private long lastAiUpdate;
    private Fix latest, recovery, courseAnchor;
    private int rejected;
    private double[] stopPosition;
    public boolean freezeOutageGyroBias=OutageTuning.FREEZE_GYRO_BIAS;
    public String diagnostic="Waiting for sensors and GPS";

    public void mount(double[] gravity,double[] forward,double heading,double speed) {
        vehicleForward=forward.clone();filter.align(gravity,heading,vehicleForward);
        tiltReady=true;physics.aligned=true;
        double h=Math.toRadians(heading);filter.v[0]=Math.sin(h)*speed;filter.v[1]=Math.cos(h)*speed;
    }
    public void invalidateMount(){physics.aligned=false;vehicleForward=null;lastAiUpdate=0;}
    public boolean calibratedMount(){return vehicleForward!=null&&physics.aligned;}
    public boolean mountMatches(double[] forward){return calibratedMount()&&vehicleForward[0]*forward[0]+vehicleForward[1]*forward[1]+vehicleForward[2]*forward[2]>.94;}
    public boolean aiSpeed(long now,double speed,double variance,double trust) {
        ai.estimate(now,speed,trust/.35,false,variance);
        boolean accepted=physics.aligned&&!physics.isStopped(now)&&filter.update(ai.provide(now),ai.trustScore());
        if(accepted)lastAiUpdate=now;
        return accepted;
    }
    public boolean aiActive(long now){return lastAiUpdate>0&&now>=lastAiUpdate&&now-lastAiUpdate<=2_000_000_000L;}

    public void gravity(long t,double x,double y,double z) {
        double norm=Math.sqrt(x*x+y*y+z*z);
        if(t<=gravityTime||!Double.isFinite(norm)||norm<8||norm>11)return;
        if(gravityTime>0&&t-gravityTime<500_000_000L&&physics.aligned) {
            double oldNorm=Math.sqrt(gravity[0]*gravity[0]+gravity[1]*gravity[1]+gravity[2]*gravity[2]);
            double cosine=(x*gravity[0]+y*gravity[1]+z*gravity[2])/(norm*oldNorm);
            if(cosine<Math.cos(Math.toRadians(20))) { physics.aligned=false;stopPosition=null; }
        }
        gravityTime=t;gravity[0]=x;gravity[1]=y;gravity[2]=z;
    }

    public record Fix(long time,double lat,double lon,double alt,double accuracy,
                      double speed,double speedError,double bearing,double bearingError) {
        public boolean speedValid() { return Double.isFinite(speed)&&speed>=0&&speed<=80&&
            Double.isFinite(speedError)&&speedError>=0&&speedError<=1; }
        public boolean courseValid() { return speedValid()&&speed>3&&Double.isFinite(bearing)&&
            Double.isFinite(bearingError)&&bearingError>=0&&bearingError<=20; }
    }

    public void imu(ImuSample raw, boolean gyroFresh) {
        long t=raw.t();
        if (t<=lastImu || !Double.isFinite(raw.ax()+raw.ay()+raw.az()+raw.gx()+raw.gy()+raw.gz())) return;
        double dt=lastImu==0?.01:(t-lastImu)*1e-9;
        if (dt<.004) return;
        lastImu=t;
        if (start==0) { start=t;if(gravityTime==0){gravity[0]=raw.ax();gravity[1]=raw.ay();gravity[2]=raw.az();} }
        double alpha=1-Math.exp(-Math.min(dt,.1)/1.5);
        double norm=Math.sqrt(raw.ax()*raw.ax()+raw.ay()*raw.ay()+raw.az()*raw.az());
        if (t-gravityTime>500_000_000L && (!physics.aligned || physics.isStopped(t))) {
            if (Math.abs(norm-9.80665)<.5) {
                gravity[0]+=alpha*(raw.ax()-gravity[0]);gravity[1]+=alpha*(raw.ay()-gravity[1]);gravity[2]+=alpha*(raw.az()-gravity[2]);
            }
        }
        if (!tiltReady && t-start>=1_000_000_000L) { filter.align(gravity,0);tiltReady=true; }
        ImuSample sample=new ImuSample(t,clean[0].apply(raw.ax(),dt),clean[1].apply(raw.ay(),dt),
            clean[2].apply(raw.az(),dt),raw.gx(),raw.gy(),raw.gz());
        physics.observe(sample);
        filter.freezeGyroBias=freezeOutageGyroBias&&filter.initialized&&t-lastFix>2_000_000_000L;
        // A missing gyro is not a zero angular rate. GPS still updates, but unaided INS is unavailable.
        if (tiltReady && gyroFresh && (physics.aligned || physics.isStopped(t))) filter.predict(sample);
        if (t-lastPhysics>=100_000_000L && gyroFresh) {
            lastPhysics=t;
            if(gravityTime>consumedGravity && Math.abs(t-gravityTime)<300_000_000L) {
                consumedGravity=gravityTime;
                double linear=Math.sqrt(Math.pow(raw.ax()-gravity[0],2)+Math.pow(raw.ay()-gravity[1],2)+Math.pow(raw.az()-gravity[2],2));
                if(linear<2)filter.update(filter.gravity(gravity,.35+linear*.3),.6);
            }
            filter.update(physics.provide(t),physics.trustScore());
            if (physics.isStopped(t)) {
                if (stopPosition==null) stopPosition=new double[]{filter.p[0],filter.p[1]};
                filter.update(Measurement.direct(0,stopPosition,filter.p,.25),.8);
                // During a confirmed stop, measured angular rate directly observes gyro bias.
                filter.update(Measurement.direct(12,new double[]{raw.gx(),raw.gy(),raw.gz()},filter.bg,.000025),.8);
            } else stopPosition=null;
        }
        diagnostic=!gyroFresh?"Gyroscope missing or stale · GPS only":!filter.initialized?"Waiting for accurate GPS":
            !tiltReady?"Aligning phone tilt":!physics.aligned?"Drive straight to establish heading":
            t-lastFix>2_000_000_000L?"Inertial tracking · GPS unavailable":"GNSS + INS tracking";
    }

    public boolean fix(Fix f,long now) {
        if (f.time()<=previousFix || f.time()>now || now-f.time()>2_000_000_000L ||
            !Double.isFinite(f.lat()+f.lon()+f.accuracy()) || Math.abs(f.lat())>90 || Math.abs(f.lon())>180 ||
            f.accuracy()<=0 || f.accuracy()>50 || (!filter.initialized&&f.accuracy()>25)) return false;
        previousFix=f.time();
        // Recover velocity observability on devices that omit Doppler speed accuracy.
        // Use an accepted anchor over several seconds, propagating both position errors.
        if(!f.speedValid() && courseAnchor!=null) {
            double seconds=(f.time()-courseAnchor.time())*1e-9;
            double north=(f.lat()-courseAnchor.lat())*111320,east=(f.lon()-courseAnchor.lon())*111320*Math.cos(Math.toRadians(f.lat()));
            double distance=Math.hypot(north,east),error=Math.hypot(f.accuracy(),courseAnchor.accuracy());
            if(seconds>=3&&seconds<=12&&distance>Math.max(10,3*error)&&error/seconds<=1&&distance/seconds<60)
                f=new Fix(f.time(),f.lat(),f.lon(),f.alt(),f.accuracy(),distance/seconds,Math.max(.5,error/seconds),
                    (Math.toDegrees(Math.atan2(east,north))+360)%360,Math.toDegrees(Math.atan2(error,distance)));
        }
        // Some Android providers omit bearing uncertainty. Derive a conservative course only
        // after a displacement large enough to dominate the two position error circles.
        if (!f.courseValid() && latest!=null && f.speedValid() && f.speed()>3) {
            double north=(f.lat()-latest.lat())*111320;
            double east=(f.lon()-latest.lon())*111320*Math.cos(Math.toRadians(f.lat()));
            double distance=Math.hypot(north,east),error=Math.hypot(f.accuracy(),latest.accuracy());
            double seconds=(f.time()-latest.time())*1e-9;
            if(seconds>=.5&&seconds<=5&&distance>Math.max(10,3*error))
                f=new Fix(f.time(),f.lat(),f.lon(),f.alt(),f.accuracy(),f.speed(),f.speedError(),
                    (Math.toDegrees(Math.atan2(east,north))+360)%360,Math.toDegrees(Math.atan2(error,distance)));
        }
        boolean first=!filter.initialized;
        if (first) filter.initialize(f.lat(),f.lon(),Double.isFinite(f.alt())?f.alt():0);
        double observationLat=f.lat(),observationLon=f.lon(),observationAccuracy=f.accuracy();
        double age=(now-f.time())*1e-9;
        if(!first&&f.courseValid()&&age>0) {
            double course=Math.toRadians(f.bearing());
            observationLat+=Math.toDegrees(Math.cos(course)*f.speed()*age/6378137);
            observationLon+=Math.toDegrees(Math.sin(course)*f.speed()*age/(6378137*Math.cos(Math.toRadians(f.lat()))));
            observationAccuracy=Math.hypot(f.accuracy(),age*(f.speedError()+f.speed()*Math.toRadians(f.bearingError())));
        }
        gps.fix(f.time(),observationLat,observationLon,Double.isFinite(f.alt())?f.alt():filter.originAlt,observationAccuracy);
        Measurement position=gps.provide(now);
        boolean accepted=first || filter.update(position,gps.trustScore());
        if (!accepted && f.accuracy()<=15) {
            double distance=recovery==null?Double.POSITIVE_INFINITY:Math.hypot((f.lat()-recovery.lat())*111320,
                (f.lon()-recovery.lon())*111320*Math.cos(Math.toRadians(f.lat())));
            rejected=distance<30+(f.speedValid()?f.speed():0)*(f.time()-(recovery==null?f.time():recovery.time()))*1e-9?rejected+1:1;
            recovery=f;
            if (rejected>=3) {
                double innovation=Math.hypot(position.residual().get(0),position.residual().get(1));
                filter.allowGpsRecovery(Math.max(f.accuracy(),innovation/5));accepted=filter.update(position,gps.trustScore());
                if(accepted) physics.aligned=false;
            }
        }
        if (!accepted) return false;
        rejected=0;recovery=null;latest=f;lastFix=f.time();
        filter.freezeGyroBias=false;
        if(courseAnchor==null||f.time()-courseAnchor.time()>=10_000_000_000L)courseAnchor=f;
        physics.gpsPosition(f.time(),f.lat(),f.lon(),f.accuracy());
        physics.gpsSpeed(f.time(),f.speedValid()?f.speed():Double.NaN,f.speedError());
        if(physics.isStopped(now)) {
            filter.update(Measurement.direct(3,new double[]{0,0,0},filter.v,.0025),1);
            return true;
        }
        if (tiltReady && f.courseValid()) {
            if (!physics.aligned) {
                filter.align(gravity,f.bearing(),vehicleForward);physics.aligned=true;
                double h=Math.toRadians(f.bearing());filter.v[0]=Math.sin(h)*f.speed();filter.v[1]=Math.cos(h)*f.speed();
            } else filter.update(filter.course(f.bearing(),Math.max(5,f.bearingError())),.75);
            double h=Math.toRadians(f.bearing());
            // Course uncertainty contributes cross-track velocity error; do not treat it as exact.
            double variance=Math.max(.09,f.speedError()*f.speedError()+Math.pow(f.speed()*Math.toRadians(Math.max(5,f.bearingError())),2));
            filter.update(Measurement.direct(3,new double[]{Math.sin(h)*f.speed(),Math.cos(h)*f.speed()},filter.v,variance),.85);
        } else if(f.speedValid() && filter.speed()>.5) {
            double magnitude=filter.speed();
            org.ejml.simple.SimpleMatrix h=new org.ejml.simple.SimpleMatrix(1,15);
            h.set(0,3,filter.v[0]/magnitude);h.set(0,4,filter.v[1]/magnitude);
            filter.update(new Measurement(new org.ejml.simple.SimpleMatrix(1,1,true,new double[]{f.speed()-magnitude}),h,
                org.ejml.simple.SimpleMatrix.identity(1).scale(Math.max(.09,f.speedError()*f.speedError()))),.8);
        }
        return true;
    }
    public void providerDisabled() { lastFix=0;latest=null;courseAnchor=null; }
    public long lastFix() { return lastFix; }
    public boolean aligned() { return physics.aligned; }
    public boolean imuFresh(long now) { return lastImu>0 && now>=lastImu && now-lastImu<500_000_000L; }
    public double speed(long now) {
        if (!filter.initialized || physics.isStopped(now)) return 0;
        return filter.speed(); // UI, position integration and distance all use the same ESKF state.
    }
}
