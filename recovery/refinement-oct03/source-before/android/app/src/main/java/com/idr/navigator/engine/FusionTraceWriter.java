package com.idr.navigator.engine;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** A separate 10 Hz fused trace preserves DRLOG v1 and makes field failures reproducible. */
public final class FusionTraceWriter implements AutoCloseable {
    private final BufferedWriter out;
    private long last;
    public FusionTraceWriter(File file) throws IOException {
        out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8),32768);
        out.write("t_ns,lat,lon,speed_mps,heading_deg,uncertainty_m,gps_age_s,gps_accuracy_m,stationary,heading_ready,ba_x,ba_y,ba_z,bg_x,bg_y,bg_z,nav_lat,nav_lon,nav_speed_mps,speed_available,position_fresh,nav_heading_deg,heading_source,gps_fix_ns,gps_speed_mps,gps_speed_error_mps,gps_bearing_deg,gps_bearing_error_deg\n");
    }
    public void write(long t,NavigationFusion n,LiveNavigation live) throws IOException {
        if(t-last<100_000_000L)return;last=t;Eskf f=n.filter;
        out.write(String.format(Locale.ROOT,"%d,%.9f,%.9f,%.5f,%.3f,%.3f,%.3f,%.3f,%b,%b,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
            t,f.latitude(),f.longitude(),n.speed(t),f.heading(),f.uncertainty(),n.lastFix()==0?-1:(t-n.lastFix())*1e-9,n.gps.accuracy(),
            n.physics.isStopped(t),n.aligned(),f.ba[0],f.ba[1],f.ba[2],f.bg[0],f.bg[1],f.bg[2]));
        var gps=live.observation();
        out.write(String.format(Locale.ROOT,",%.9f,%.9f,%.5f,%b,%b,%.3f,%s,%d,%.5f,%.5f,%.3f,%.3f%n",
            live.latitude(),live.longitude(),live.speedAvailable(t)?live.speed(t):Double.NaN,live.speedAvailable(t),live.fresh(t),live.heading(),live.headingSource(t),live.fixTime(),
            gps==null?Double.NaN:gps.speed(),gps==null?Double.NaN:gps.speedError(),gps==null?Double.NaN:gps.bearing(),gps==null?Double.NaN:gps.bearingError()));
    }
    public void close() throws IOException { out.close(); }
}
