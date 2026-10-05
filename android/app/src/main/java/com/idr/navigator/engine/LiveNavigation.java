package com.idr.navigator.engine;

/** Live ESKF navigation, with independent GNSS fallback and explicit sensor availability. */
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
    private boolean predictionValid;
    private final RouteFollower route=new RouteFollower();
    private RouteFollower.Estimate routeEstimate;
    private double routeGyroHeading;
    public boolean routeFollow=OutageTuning.ROUTE_FOLLOW;
    public void setActiveRoute(double[][] coordinates) {
        route.setRoute(coordinates);
        blendRouteProgress=Double.NaN;
        if(coordinates==null||coordinates.length<2){routeEstimate=null;blendRouteProgress=Double.NaN;}
        if(fix!=null&&healthy(clock))anchorRoute();
    }
    private void anchorRoute() {
        route.anchor(fix.time(),fix.lat(),fix.lon(),fix.accuracy(),heading,routeGyroHeading);
    }
    public boolean followingRoute(){return routeEstimate!=null&&!healthy(clock)&&fused(clock);}
    public double alongTrackUncertainty(long now){return followingRoute()?Math.max(routeEstimate.alongUncertaintyMeters(),fusion.filter.directionalUncertainty(routeEstimate.bearingDegrees())):uncertainty(now);}
    public double crossTrackUncertainty(long now){return followingRoute()?routeEstimate.crossTrackUncertaintyMeters():uncertainty(now);}
    private void updateRoute(long now) {
        if(!healthy(now)&&!fused(now)){route.invalidateAnchor();routeEstimate=null;return;}
        routeEstimate=routeFollow&&fused(now)?route.update(now,!healthy(now),stopped(now),fusion.speed(now),routeGyroHeading):null;
        if(routeEstimate!=null) {
            // Carry the route-constrained pose into the same filter so fallback does not
            // jump to a separately diverged trajectory. Preserve the full covariance.
            double[] position=fusion.filter.enu(routeEstimate.latitude(),routeEstimate.longitude(),fusion.filter.originAlt);
            fusion.filter.p[0]=position[0];fusion.filter.p[1]=position[1];
        }
    }
    public final InertialNavigation inertial=new InertialNavigation();
    public final NavigationFusion fusion=new NavigationFusion();
    private long lastModel,lastRoad,lastCalibrationFix;
    private boolean fusedPose;
    private long clock,recoveryStart;private double recoveryLat,recoveryLon,recoverySeconds,recoverySpeed;
    private double recoveryGpsLat,recoveryGpsLon,recoveryGpsSpeed;
    private boolean blendingRecovery,largeRecovery,finishingRecovery;
    private long blendTime;
    private double blendLat,blendLon,blendRouteProgress=Double.NaN,retainedRecoveryError;
    private double targetLat(){return retainInertialPosition()?fusion.filter.latitude():fix==null?23.2418:holdingStop?stopLat:fix.lat();}
    private double targetLon(){return retainInertialPosition()?fusion.filter.longitude():fix==null?77.4029:holdingStop?stopLon:fix.lon();}
    private void abandonRecovery() {
        if(!blendingRecovery)return;
        retainedRecoveryError=Math.max(retainedRecoveryError,Math.hypot((targetLat()-blendLat)*111320,(targetLon()-blendLon)*111320*Math.cos(Math.toRadians(blendLat))));
        if(fix!=null)retainedRecoveryError=Math.max(retainedRecoveryError,Math.hypot((fix.lat()-blendLat)*111320,(fix.lon()-blendLon)*111320*Math.cos(Math.toRadians(blendLat))));
        if(fusion.filter.initialized){double[] p=fusion.filter.enu(blendLat,blendLon,fusion.filter.originAlt);fusion.filter.p[0]=p[0];fusion.filter.p[1]=p[1];}
        blendingRecovery=false;route.invalidateAnchor();routeEstimate=null;
    }
    private void advanceRecovery(long now) {
        if(!blendingRecovery||now<=blendTime)return;
        if(!healthy(now)){abandonRecovery();return;}
        double dt=(now-blendTime)*1e-9;blendTime=now;
        double targetLat=finishingRecovery?targetLat():fix!=null?fix.lat():targetLat(),targetLon=finishingRecovery?targetLon():fix!=null?fix.lon():targetLon();
        double north=(targetLat-blendLat)*111320,east=(targetLon-blendLon)*111320*Math.cos(Math.toRadians(blendLat));
        double distance=Math.hypot(east,north);
        double step=Math.min(5,dt*(Math.max(0,Double.isFinite(speed)?speed:0)+10));
        RouteFollower.Projection from=Double.isFinite(blendRouteProgress)?route.positionAt(blendRouteProgress):route.project(blendLat,blendLon,heading),to=route.project(targetLat,targetLon,heading);
        if(!finishingRecovery&&from!=null&&to!=null&&Math.abs(to.progressMeters()-from.progressMeters())<=distance+50) {
            double delta=to.progressMeters()-from.progressMeters();
            RouteFollower.Projection next=route.positionAt(from.progressMeters()+Math.copySign(Math.min(Math.abs(delta),step),delta));
            if(next!=null){blendLat=next.latitude();blendLon=next.longitude();blendRouteProgress=next.progressMeters();}
            // Keep the route interpolation for this recovery even when its remaining offset is small.
            // Once along-route progress catches up, reconcile the small lateral/filter
            // residual at the same bounded rate; real GNSS is rarely exactly on a polyline.
            if(Math.abs(delta)<=Math.max(2,step))finishingRecovery=true;
        } else if(distance<=step){
            blendLat=targetLat;blendLon=targetLon;
            if(finishingRecovery)blendingRecovery=false;else finishingRecovery=true;
        }
        else {blendLat+=(targetLat-blendLat)*step/distance;blendLon+=(targetLon-blendLon)*step/distance;}
    }

    public void imu(ImuSample sample) { imu(sample,true); }
    public void imu(ImuSample sample,boolean gyroFresh) {
        if(sample.t()<=lastImu)return;
        if(blendingRecovery&&!healthy(sample.t()))abandonRecovery();
        predictionValid=Double.isFinite(sample.ax()+sample.ay()+sample.az()+sample.gx()+sample.gy()+sample.gz())&&
            (lastImu==0||sample.t()-lastImu<=250_000_000L);
        if(!Double.isFinite(sample.ax()+sample.ay()+sample.az()+sample.gx()+sample.gy()+sample.gz())){gyroscopeFresh=false;if(!healthy(sample.t())){route.invalidateAnchor();routeEstimate=null;}return;}
        double routeDt=lastImu==0?0:(sample.t()-lastImu)*1e-9;
        if(!healthy(sample.t())&&gyroFresh&&routeDt>0&&routeDt<=.25) {
            var d=inertial.diagnostics();double g=Math.sqrt(d.gravityX()*d.gravityX()+d.gravityY()*d.gravityY()+d.gravityZ()*d.gravityZ());
            if(g>8)routeGyroHeading=wrap(routeGyroHeading-Math.toDegrees((sample.gx()*d.gravityX()+sample.gy()*d.gravityY()+sample.gz()*d.gravityZ())/g-d.angularBias())*routeDt);
        }
        stationary.observe(sample);lastImu=sample.t();clock=sample.t();gyroscopeFresh=gyroFresh;
        if(!stationary.isStopped(sample.t()))holdingStop=false;
        if(gyroFresh)inertial.predict(sample,healthy(sample.t()),stationary.isStopped(sample.t()));
        if(!inertial.aligned())fusion.invalidateMount();
        long gravityAge=sample.t()-inertial.diagnostics().gravityTime();
        fusion.imu(sample,gyroFresh&&gravityAge>=-20_000_000L&&gravityAge<500_000_000L);
        if(!inertial.aligned())fusion.invalidateMount();
        if(fused(sample.t()))fusedPose=true;
        if(healthy(sample.t()))routeGyroHeading=inertial.heading();
        updateRoute(sample.t());
        advanceRecovery(sample.t());
    }
    public void gravity(long t,double x,double y,double z){inertial.gravity(t,x,y,z);fusion.gravity(t,x,y,z);if(!inertial.aligned())fusion.invalidateMount();}
    public boolean fused(long now){
        boolean sensors=inertial.available(now);
        if(OutageTuning.OUTAGE_CONTINUITY&&!healthy(now)) {
            // Availability follows the live ESKF sensor/mount contract, never its growing covariance
            // or the independent planar estimator. There is deliberately no outage-age timeout.
            long gravityAge=now-inertial.diagnostics().gravityTime();
            sensors=predictionValid&&inertial.aligned()&&gravityAge>=-20_000_000L&&gravityAge<500_000_000L;
        }
        return fusion.filter.initialized&&fusion.aligned()&&sensors&&imuFresh(now);
    }
    public void models(long now,double prediction,float[] motion,float[] health,Double gpsSpeed,boolean goodGps) {
        if(now<=lastModel)return;lastModel=now;
        boolean label=goodGps&&healthy(now)&&imuFresh(now)&&fix.accuracy()<=15&&fix.speedValid()&&
            now-fix.time()<=1_000_000_000L&&fix.time()>lastCalibrationFix;
        inertial.models(now,prediction,motion,health,label?fix.speed():null,label);
        if(label)lastCalibrationFix=fix.time();
        if(fused(now)&&!stopped(now))fusion.aiSpeed(now,inertial.calibratedModelSpeed(now),
            Math.max(1,inertial.modelRmse()*inertial.modelRmse()),inertial.aiWeight(now));
    }
    public void road(long now,long t,double sourceLat,double sourceLon,double matchedLat,double matchedLon,double roadHeading,double trust) {
        if(!estimated(now)||stopped(now)||t<=lastRoad||t<=fixTime()||t>now||now-t>3_000_000_000L||
            !Double.isFinite(sourceLat+sourceLon+matchedLat+matchedLon+roadHeading+trust)||trust<.3||trust>1)return;
        lastRoad=t;double h=Math.toRadians(roadHeading),east=(matchedLon-sourceLon)*111320*Math.cos(Math.toRadians(latitude())),north=(matchedLat-sourceLat)*111320;
        double angle=Math.abs(Eskf.wrapDegrees(roadHeading-heading()));angle=Math.min(angle,180-angle);
        if(angle>35||Math.hypot(east,north)>Math.min(35,Math.max(10,uncertainty(now)*2)))return;
        org.ejml.simple.SimpleMatrix jacobian=new org.ejml.simple.SimpleMatrix(1,15);
        jacobian.set(0,0,Math.cos(h));jacobian.set(0,1,-Math.sin(h));
        fusion.filter.update(new Measurement(new org.ejml.simple.SimpleMatrix(1,1,true,new double[]{east*Math.cos(h)-north*Math.sin(h)}),
            jacobian,org.ejml.simple.SimpleMatrix.identity(1).scale(25)),trust);
    }
    public void tick(long now){clock=now;advanceRecovery(now);if(!healthy(now)&&!fused(now)){route.invalidateAnchor();routeEstimate=null;}}
    public boolean healthy(long now){return fresh(now)&&fix.accuracy()<=20;}
    public boolean estimated(long now){return !healthy(now)&&fused(now);}
    public boolean trackingAvailable(long now){return fresh(now)||estimated(now);}
    public String speedSource(long now){return fused(now)?"ESKF · "+(healthy(now)?"GNSS + IMU":"IMU")+(fusion.aiActive(now)?" + AI":""):speedAvailable(now)?stopped(now)?"Confirmed stop":"GNSS speed · aligning ESKF":"Awaiting sensor alignment";}
    public String status(long now){return blendingRecovery||recoveryFactor()>0?"Reconciling ESKF estimate with GPS":fused(now)?healthy(now)?"ESKF · GNSS + INS fusion":"ESKF · inertial tracking":!ready()?"Waiting for GPS":!fresh(now)?inertial.diagnostic:"Live GNSS tracking";}
    public double uncertainty(long now){double base=fused(now)||!fresh(now)&&fusedPose?fusion.filter.uncertainty():accuracy();double offset=blendingRecovery?Math.hypot((targetLat()-blendLat)*111320,(targetLon()-blendLon)*111320*Math.cos(Math.toRadians(blendLat))):recoveryFactor()*Math.hypot(recoveryGpsLat*111320,recoveryGpsLon*111320*Math.cos(Math.toRadians(latitude())));
        if(blendingRecovery&&fix!=null)offset=Math.max(offset,Math.hypot((fix.lat()-blendLat)*111320,(fix.lon()-blendLon)*111320*Math.cos(Math.toRadians(blendLat))));
        return Math.hypot(Math.hypot(base,offset),retainedRecoveryError);}
    private double recoveryFactor(){if(OutageTuning.SMOOTH_RECOVERY&&largeRecovery)return 0;return recoveryStart>0&&clock>=recoveryStart?Math.max(0,1-(clock-recoveryStart)*1e-9/recoverySeconds):0;}
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
        boolean poorDuringOutage=estimated(now)&&next.accuracy()>20;
        RouteFollower.Estimate priorRoute=followingRoute()?routeEstimate:null;
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
                // A large INS error must not keep guidance far behind an accepted
                // GPS fix for eight seconds. Keep the first frame continuous,
                // then reconcile position within the same two seconds as speed.
                recoverySeconds=Math.max(1,Math.min(2,Math.hypot(recoveryLat*111320,recoveryLon*111320*Math.cos(Math.toRadians(next.lat())))/5));}
        }
        if(!poorDuringOutage)fusion.fix(next,now);
        if(inertial.aligned()) {
            var d=inertial.diagnostics();double[] forward={d.forwardX(),d.forwardY(),d.forwardZ()};
            if(!fusion.mountMatches(forward))fusion.mount(new double[]{d.gravityX(),d.gravityY(),d.gravityZ()},
                forward,heading,Double.isFinite(speed)?speed:0);
        }
        if(fused(now))fusedPose=true;
        if(recovering) {
            recoveryGpsLat=priorLat-next.lat();recoveryGpsLon=priorLon-next.lon();
            recoveryLat=priorLat-fusion.filter.latitude();recoveryLon=priorLon-fusion.filter.longitude();
            recoveryGpsSpeed=priorSpeed-speed;recoverySpeed=priorSpeed-fusion.speed(now);
        }
        if(healthy(now))retainedRecoveryError=0;
        if(recovering&&OutageTuning.SMOOTH_RECOVERY){
            largeRecovery=Math.hypot((next.lat()-priorLat)*111320,(next.lon()-priorLon)*111320*Math.cos(Math.toRadians(priorLat)))>10;
            blendingRecovery=largeRecovery;finishingRecovery=false;blendLat=priorLat;blendLon=priorLon;blendTime=now;
            blendRouteProgress=priorRoute==null?Double.NaN:priorRoute.progressMeters();
        }
        if(healthy(now)){routeGyroHeading=inertial.heading();anchorRoute();routeEstimate=null;}
        return true;
    }
    private boolean disabled;
    public void providerDisabled(){
        abandonRecovery();
        disabled=true;speed=Double.NaN;courseAnchor=null;fusion.providerDisabled();}
    public void providerEnabled(){disabled=false;}
    public boolean fresh(long now){return !disabled&&fix!=null&&now>=fix.time()&&now-fix.time()<=2_000_000_000L;}
    public boolean ready(){return fix!=null;}
    public long fixTime(){return fix==null?0:fix.time();}
    public NavigationFusion.Fix observation(){return fix;}
    // Losing alignment makes the estimate unavailable, not a reason to rewind the
    // map to the GPS coordinate from before the outage. Keep the last known pose.
    private boolean retainInertialPosition(){return fused(clock)||!fresh(clock)&&fusedPose;}
    public double latitude(){if(blendingRecovery)return blendLat;return retainInertialPosition()?fusion.filter.latitude()+recoveryLat*recoveryFactor():fix==null?23.2418:(holdingStop?stopLat:fix.lat())+recoveryGpsLat*recoveryFactor();}
    public double longitude(){if(blendingRecovery)return blendLon;return retainInertialPosition()?fusion.filter.longitude()+recoveryLon*recoveryFactor():fix==null?77.4029:(holdingStop?stopLon:fix.lon())+recoveryGpsLon*recoveryFactor();}
    public double accuracy(){return fix==null?0:fix.accuracy();}
    public boolean stopped(long now){return fused(now)?fusion.physics.isStopped(now):fresh(now)&&stationary.isStopped(now);}
    public boolean speedAvailable(long now){return fused(now)||fresh(now)&&(stopped(now)||Double.isFinite(speed));}
    public double speed(long now){double factor=recoveryStart>0?Math.max(0,1-(now-recoveryStart)*1e-9/2):0;return stopped(now)?0:fused(now)?Math.max(0,fusion.speed(now)+recoverySpeed*factor):speedAvailable(now)?Math.max(0,speed+recoveryGpsSpeed*factor):0;}
    public double heading(){return followingRoute()?routeEstimate.bearingDegrees():fused(clock)?fusion.filter.heading():heading;}
    public boolean headingReady(long now){return fused(now)||headingKnown&&now>=headingTime&&now-headingTime<5_000_000_000L;}
    public String headingSource(long now){return fused(now)?"ESKF attitude":headingReady(now)?headingSource:"Unavailable";}
    public boolean imuFresh(long now){return gyroscopeFresh&&lastImu>0&&now>=lastImu&&now-lastImu<500_000_000L;}
}
