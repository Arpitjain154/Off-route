package com.idr.navigator.engine;

/** A stop needs recent low-speed GNSS plus quiet IMU, never low variance alone. */
public final class StationaryDetector {
    private final double[] norms = new double[150], rates = new double[150];
    private final long[] times = new long[150];
    private int cursor, count;
    private long gpsTime, quietSince;
    private double gpsSpeed = Double.NaN, gpsError = Double.NaN;
    private boolean stopped;
    private long lastSample;
    private double stopAx, stopAy, stopAz;
    private long anchorTime,positionTime;
    private double anchorLat,anchorLon;
    private boolean compactPosition;

    public void position(long t,double lat,double lon,double accuracy) {
        if(t<=positionTime||!Double.isFinite(lat+lon+accuracy)||accuracy>10)return;
        positionTime=t;
        double distance=Math.hypot((lat-anchorLat)*111320,(lon-anchorLon)*111320*Math.cos(Math.toRadians(lat)));
        if(anchorTime==0||distance>Math.max(3,Math.min(5,accuracy))) {
            anchorTime=t;anchorLat=lat;anchorLon=lon;compactPosition=false;
        } else compactPosition=t-anchorTime>=8_000_000_000L;
    }

    public void gps(long t, double speed, double error) {
        gpsTime=t; gpsSpeed=speed; gpsError=error;
        if (speed > 1.1 || !Double.isFinite(speed)) { stopped=false; quietSince=0; }
    }
    public void observe(ImuSample s) {
        if (s.t()-lastSample<9_000_000L) return;
        lastSample=s.t();
        if (stopped && Math.sqrt(Math.pow(s.ax()-stopAx,2)+Math.pow(s.ay()-stopAy,2)+Math.pow(s.az()-stopAz,2))>.35) {
            stopped=false;quietSince=0;
        }
        int i=cursor++ % norms.length;
        norms[i]=Math.sqrt(s.ax()*s.ax()+s.ay()*s.ay()+s.az()*s.az());
        rates[i]=Math.sqrt(s.gx()*s.gx()+s.gy()*s.gy()+s.gz()*s.gz());
        times[i]=s.t(); count=Math.min(norms.length,count+1);
        double sum=0,sq=0,rotation=0; int n=0;long oldest=s.t();
        for (int j=0;j<count;j++) if (s.t()-times[j]<=1_000_000_000L) {
            sum+=norms[j];sq+=norms[j]*norms[j];rotation+=rates[j]*rates[j];n++;oldest=Math.min(oldest,times[j]);
        }
        boolean quiet=n>20 && s.t()-oldest>=700_000_000L &&
            sq/n-(sum/n)*(sum/n)<.025 && Math.abs(sum/n-9.80665)<.3 && Math.sqrt(rotation/n)<.035;
        if (!quiet) { quietSince=0;stopped=false;return; }
        if (quietSince==0)quietSince=s.t();
        boolean recent=gpsTime>0 && s.t()>=gpsTime && s.t()-gpsTime<2_500_000_000L;
        boolean low=Double.isFinite(gpsSpeed) && gpsSpeed>=0 &&
            ((gpsSpeed<=.5&&Double.isFinite(gpsError)&&gpsError<=.5)||
             (gpsSpeed<=1.0&&compactPosition&&s.t()-positionTime<2_000_000_000L));
        if (recent && !low) { stopped=false;quietSince=0; }
        else if (recent && low && s.t()-quietSince>=1_500_000_000L && !stopped) {
            stopped=true;stopAx=s.ax();stopAy=s.ay();stopAz=s.az();
        }
        // An already confirmed stop can persist through a dropout while the IMU stays quiet.
    }
    public boolean isStopped(long now) {
        long last=count==0?0:times[(cursor-1)%norms.length];
        return stopped && now>=last && now-last<500_000_000L;
    }
}
