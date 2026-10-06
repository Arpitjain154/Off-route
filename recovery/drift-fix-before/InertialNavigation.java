package com.idr.navigator.engine;

/** Android-independent, timestamped planar strapdown engine. SI units, monotonic nanoseconds.
 * GNSS observes speed/mount yaw/bias; unaided prediction uses measured acceleration and yaw rate.
 * Covariance is a process-error estimate, not an empirically calibrated accuracy guarantee. */
public final class InertialNavigation {
    private final double[] gravity={0,0,9.80665},forward={0,1,0};
    private long gravityTime,compassTime,last,anchorTime,quietSince,modelTime,lastGoodModel,mapTime;
    private double phoneCompass,compassError=180,mountYaw,lat,lon,speed,heading,acceleration,bias,gyroBias;
    private double positionVariance=25,speedVariance=.25,yawVariance=.01,accumulatedAcceleration,accumulatedTime;
    private double modelOffset,modelError=100,modelPrediction,modelWeight,motionNoise=1,healthNoise=1;
    private double minCalibrationSpeed=80,maxCalibrationSpeed;
    private double calibrationMeanGps,calibrationMeanPrediction,calibrationGpsVariance,calibrationCovariance;
    private int calibrationSamples;
    private boolean anchored,aligned,stopped,unaided;
    private double lastGpsSpeed;
    private long stepTime,previousStep;private int steps;private double stepPeriod=.6,stepLength=.7;
    private boolean rising;private double priorVertical;private long previousGpsStepCount;
    public String diagnostic="Waiting for GPS and mounting alignment";

    public void gravity(long t,double x,double y,double z) {
        double norm=Math.sqrt(x*x+y*y+z*z);
        if(t<=gravityTime||!Double.isFinite(norm)||norm<8||norm>11)return;
        double old=Math.sqrt(dot(gravity,gravity));
        if(aligned&&gravityTime>0&&t-gravityTime<500_000_000L&&dot(gravity,new double[]{x,y,z})/(norm*old)<.94) {
            aligned=false;clearCalibration();
            diagnostic="Phone moved · mounting alignment needed";
        }
        gravityTime=t;gravity[0]=x;gravity[1]=y;gravity[2]=z;
    }
    public void compass(long t,double degrees,double error) {
        if(t<=compassTime||!Double.isFinite(degrees+error)||error<0||error>30)return;
        phoneCompass=wrap(degrees);compassError=error;compassTime=t;
        if(unaided&&aligned&&error<=15) {
            double residual=delta(phoneCompass+mountYaw-heading);
            // Magnetic disturbances must not turn the vehicle onto another street.
            if(Math.abs(residual)<25)heading=wrap(heading+residual*.015);
        }
    }
    public void anchor(NavigationFusion.Fix fix,long now,double measuredSpeed,boolean confirmedStop,double currentHeading) {
        if(!Double.isFinite(measuredSpeed)||measuredSpeed<0)return;
        double dt=anchorTime==0?0:(fix.time()-anchorTime)*1e-9;
        if(dt>0&&dt<2&&accumulatedTime>.25&&fix.accuracy()<=15&&aligned&&!unaided) {
            double measured=(measuredSpeed-lastGpsSpeed)/dt;
            if(Math.abs(measured)<5)bias=clamp(bias+.04*(accumulatedAcceleration/accumulatedTime-measured-bias),-.7,.7);
        }
        if(dt>0&&steps-previousGpsStepCount>=3&&measuredSpeed>.5&&measuredSpeed<2.5)
            stepLength=clamp(measuredSpeed*dt/(steps-previousGpsStepCount),.4,1.1);
        previousGpsStepCount=steps;accumulatedAcceleration=0;accumulatedTime=0;
        lat=fix.lat();lon=fix.lon();speed=measuredSpeed;lastGpsSpeed=measuredSpeed;
        heading=wrap(currentHeading);anchorTime=fix.time();anchored=true;stopped=confirmedStop;unaided=false;
        positionVariance=Math.max(4,fix.accuracy()*fix.accuracy());
        speedVariance=Math.max(.04,Double.isFinite(fix.speedError())?fix.speedError()*fix.speedError():1);
        yawVariance=Math.pow(Math.toRadians(Double.isFinite(fix.bearingError())?Math.max(3,fix.bearingError()):20),2);
        if(now>=compassTime&&now-compassTime<1_000_000_000L&&compassError<=30&&
            gravityTime>0&&now>=gravityTime&&now-gravityTime<1_000_000_000L&&
            measuredSpeed>=1&&Double.isFinite(fix.bearing())&&fix.bearingError()>=0&&fix.bearingError()<=25) {
            mountYaw=delta(fix.bearing()-phoneCompass);
            double norm=Math.sqrt(dot(gravity,gravity));
            double[] up={gravity[0]/norm,gravity[1]/norm,gravity[2]/norm};
            double[] base=Math.abs(up[1])>.85?new double[]{0,0,-1}:new double[]{0,1,0};
            double vertical=dot(base,up);for(int i=0;i<3;i++)base[i]-=vertical*up[i];
            norm=Math.sqrt(dot(base,base));for(int i=0;i<3;i++)base[i]/=norm;
            double[] right=cross(base,up);double yaw=Math.toRadians(mountYaw);
            for(int i=0;i<3;i++)forward[i]=base[i]*Math.cos(yaw)+right[i]*Math.sin(yaw);
            aligned=true;
        }
        if(confirmedStop){speed=0;quietSince=now;}
        diagnostic=aligned?"Mount calibrated · GNSS aiding":"GNSS tracking · establishing mount direction";
    }
    public void predict(ImuSample sample,boolean aiding,boolean confirmedStop) {
        long t=sample.t();if(t<=last||!Double.isFinite(sample.ax()+sample.ay()+sample.az()+sample.gx()+sample.gy()+sample.gz()))return;
        double dt=last==0?0:(t-last)*1e-9;last=t;
        if(!anchored||dt<=0)return;
        unaided=!aiding;
        if(dt>.25){positionVariance+=Math.pow(Math.max(1,speed)*dt,2);diagnostic="Sensor samples missing · high uncertainty";return;}
        double gnorm=Math.sqrt(dot(gravity,gravity));double[] up={gravity[0]/gnorm,gravity[1]/gnorm,gravity[2]/gnorm};
        double[] a={sample.ax()-gravity[0],sample.ay()-gravity[1],sample.az()-gravity[2]};
        double[] w={sample.gx(),sample.gy(),sample.gz()};
        double angular=dot(w,up),linear=dot(a,forward),vertical=dot(a,up);
        boolean shock=Math.sqrt(dot(a,a))>12||Math.sqrt(dot(w,w))>2;
        boolean quiet=Math.sqrt(dot(a,a))<.22&&Math.sqrt(dot(w,w))<.035;
        if(quiet){if(quietSince==0)quietSince=t;}else quietSince=0;
        // A cached zero GNSS speed is not a stationary calibration interval.
        // Learn bias only from sustained quiet sensors, never braking/launch acceleration.
        if(confirmedStop&&aiding&&quiet&&quietSince>0&&t-quietSince>1_500_000_000L){
            bias=clamp(bias+.01*(linear-bias),-.5,.5);gyroBias=clamp(gyroBias+.01*(angular-gyroBias),-.03,.03);stopped=true;speed=0;
        }
        if(stopped&&(!quiet||Math.abs(linear-bias)>.25))stopped=false;
        if(quiet&&speed<.12&&quietSince>0&&t-quietSince>2_000_000_000L)stopped=true;
        // Step cadence is used only following pedestrian-speed GNSS, never for a moving car.
        if(!shock&&lastGpsSpeed<2.5&&vertical>.8&&priorVertical<=.8&&t-stepTime>300_000_000L){
            previousStep=stepTime;stepTime=t;
            if(previousStep>0&&t-previousStep<1_100_000_000L){steps++;stepPeriod=.6*stepPeriod+.4*(t-previousStep)*1e-9;}else steps=1;
        }
        priorVertical=vertical;
        if(!shock){accumulatedAcceleration+=linear*dt;accumulatedTime+=dt;}
        // Predict between healthy fixes too, so timeout-based handoff includes
        // the distance travelled since the last receiver observation.
        positionVariance+=dt*(.15*healthNoise+2*Math.sqrt(positionVariance*speedVariance));
        speedVariance+=dt*(.04*motionNoise+(shock?.8:0));yawVariance+=dt*(.00003+(shock?.003:0));
        if(!aligned){diagnostic="Mount direction uncertain · calibrate with GPS";return;}
        heading=wrap(heading-Math.toDegrees(angular-gyroBias)*dt);
        double oldSpeed=speed;
        double target=shock?0:clamp(linear-bias,-8,5);
        acceleration+=(target-acceleration)*(1-Math.exp(-dt/.18));
        if(stopped){speed=0;acceleration=0;speedVariance=Math.min(speedVariance,.04);}
        else if(steps>=3&&lastGpsSpeed<2.5){speed=t-stepTime<1_800_000_000L?stepLength/Math.max(.3,stepPeriod):0;}
        else {
            speed=clamp(speed+acceleration*dt,0,80);
            if(modelWeight>0&&t-modelTime<2_000_000_000L&&t-lastGoodModel<60_000_000_000L) {
                double residual=modelPrediction-modelOffset-speed;
                if(Math.abs(residual)<Math.max(3,3*Math.sqrt(modelError)))speed+=clamp(residual,-2,2)*modelWeight*dt/8;
            }
        }
        double distance=(oldSpeed+speed)*.5*dt,h=Math.toRadians(heading);
        lat+=distance*Math.cos(h)/111320;lon+=distance*Math.sin(h)/(111320*Math.cos(Math.toRadians(lat)));
        positionVariance+=distance*distance*yawVariance;
        diagnostic=!unaided?"Mount calibrated · GNSS aiding":stopped?"Sensor tracking · confirmed stop":steps>=3&&lastGpsSpeed<2.5?"Step-based inertial tracking":
            modelWeight>0?"Sensor tracking · calibrated AI speed correction":"Sensor tracking · inertial estimate";
    }
    public void models(long now,double prediction,float[] motion,float[] health,Double gpsSpeed,boolean goodGps) {
        if(motion!=null&&motion.length>=4&&Float.isFinite(motion[3]))motionNoise=1+2*clamp(motion[3],0,1);
        if(health!=null&&health.length==3&&Float.isFinite(health[1]+health[2]))healthNoise=1+clamp(health[1]+health[2],0,1);
        if(!Double.isFinite(prediction)||prediction<0||prediction>80){modelWeight=0;return;}
        modelPrediction=prediction;modelTime=now;
        if(goodGps&&gpsSpeed!=null&&Double.isFinite(gpsSpeed)&&gpsSpeed>2) {
            double error=prediction-gpsSpeed;
            if(calibrationSamples==0){modelOffset=error;modelError=0;}
            double residual=error-modelOffset;modelOffset+=.06*residual;modelError=.94*modelError+.06*residual*residual;
            calibrationSamples++;minCalibrationSpeed=Math.min(minCalibrationSpeed,gpsSpeed);maxCalibrationSpeed=Math.max(maxCalibrationSpeed,gpsSpeed);
            double dx=gpsSpeed-calibrationMeanGps,dy=prediction-calibrationMeanPrediction;
            calibrationMeanGps+=dx/calibrationSamples;calibrationMeanPrediction+=dy/calibrationSamples;
            calibrationGpsVariance+=dx*(gpsSpeed-calibrationMeanGps);calibrationCovariance+=dx*(prediction-calibrationMeanPrediction);
            double slope=calibrationGpsVariance>0?calibrationCovariance/calibrationGpsVariance:0;
            modelWeight=calibrationSamples>=15&&maxCalibrationSpeed-minCalibrationSpeed>=3&&modelError<2.25&&slope>.5&&slope<1.5?.25:0;
            if(modelWeight>0)lastGoodModel=now;
        }
    }
    private void clearCalibration() {
        modelWeight=0;calibrationSamples=0;lastGoodModel=0;modelOffset=0;modelError=100;
        minCalibrationSpeed=80;maxCalibrationSpeed=0;
        calibrationMeanGps=0;calibrationMeanPrediction=0;calibrationGpsVariance=0;calibrationCovariance=0;
        bias=0;gyroBias=0;acceleration=0;accumulatedAcceleration=0;accumulatedTime=0;
    }
    public void road(long t,double sourceLat,double sourceLon,double matchedLat,double matchedLon,double roadHeading,double trust) {
        if(!Double.isFinite(sourceLat+sourceLon+matchedLat+matchedLon+roadHeading+trust))return;
        if(!unaided||!aligned||t<=mapTime||t>last||last-t>3_000_000_000L||trust<.3||trust>1)return;
        mapTime=t;
        double north=(matchedLat-sourceLat)*111320,east=(matchedLon-sourceLon)*111320*Math.cos(Math.toRadians(lat));
        double angle=Math.abs(delta(roadHeading-heading));angle=Math.min(angle,180-angle);
        if(angle>35||Math.hypot(north,east)>Math.min(35,Math.max(10,uncertainty()*2)))return;
        double h=Math.toRadians(roadHeading),cross=east*Math.cos(h)-north*Math.sin(h);
        // Apply only a bounded cross-track correction; a road does not measure along-track speed.
        double correction=clamp(cross*trust,-2,2);
        lon+=correction*Math.cos(h)/(111320*Math.cos(Math.toRadians(lat)));lat-=correction*Math.sin(h)/111320;
    }
    public boolean available(long now){return anchored&&aligned&&last>0&&now>=last&&now-last<500_000_000L;}
    public boolean aligned(){return aligned;}public boolean stopped(){return stopped;}
    public double latitude(){return lat;}public double longitude(){return lon;}public double speed(){return speed;}
    public double heading(){return heading;}public double uncertainty(){return Math.sqrt(positionVariance);}
    public double aiWeight(long now){return now-lastGoodModel<60_000_000_000L?modelWeight:0;}
    public double modelRmse(){return Math.sqrt(modelError);}public int calibrationSamples(){return calibrationSamples;}
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static double clamp(double x,double a,double b){return Math.max(a,Math.min(b,x));}
    private static double wrap(double x){return (x%360+360)%360;}private static double delta(double x){return (x%360+540)%360-180;}
}
