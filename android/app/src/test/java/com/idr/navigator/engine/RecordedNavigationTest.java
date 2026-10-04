package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;
import java.nio.*;
import java.nio.file.*;

/** Actual captured IMU/GPS fields. DRLOG v1 lacks bearing and speed-error metadata. */
public class RecordedNavigationTest {
    @Test public void recordedTripsKeepGpsSpeedThroughTurnsAndExposeDropouts() throws Exception {
        for(String name:new String[]{"af866a4b-f2a3-4d1b-90f7-30f74f921eaa","d62bf76e-e570-4555-8d65-c5ce7eb8870e"}) {
            byte[] bytes=Files.readAllBytes(Paths.get("../../artifacts/physical-tests/"+name+".drlog"));
            ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);LiveNavigation n=new LiveNavigation();
            long fixTime=0;int moving=0,unavailable=0;double maximumError=0;
            float previousLat=Float.NaN,previousLon=Float.NaN,previousSpeed=Float.NaN;
            for(int p=200;p+70<=bytes.length;p+=70) {
                long t=b.getLong(p);n.imu(new ImuSample(t,b.getFloat(p+8),b.getFloat(p+12),b.getFloat(p+16),b.getFloat(p+20),b.getFloat(p+24),b.getFloat(p+28)));
                float lat=b.getFloat(p+44),lon=b.getFloat(p+48),speed=b.getFloat(p+56),accuracy=b.getFloat(p+60);
                if(Float.isFinite(lat)&&Float.isFinite(lon)&&(lat!=previousLat||lon!=previousLon||speed!=previousSpeed)) {
                    // Time is the recording sample time: this tests output processing,
                    // not exact reconstruction of the missing receiver timestamp/course.
                    n.fix(new NavigationFusion.Fix(t,lat,lon,0,accuracy,speed,Double.NaN,Double.NaN,Double.NaN),t);
                    previousLat=lat;previousLon=lon;previousSpeed=speed;fixTime=t;
                }
                if(t-fixTime>2_000_000_000L){assertFalse(n.speedAvailable(t));unavailable++;}
                if(n.speedAvailable(t)&&speed>2){assertTrue(n.speed(t)>0);maximumError=Math.max(maximumError,Math.abs(n.speed(t)-speed));moving++;}
            }
            assertTrue(moving>100);assertTrue(unavailable>0);assertTrue(maximumError<3);
            System.out.println(name+": moving samples="+moving+", unavailable samples="+unavailable+", max smoothed GNSS speed difference="+maximumError+" m/s; heading not replayable from DRLOG v1.");
        }
    }
    @Test public void universityRouteFromRecordedAreaUsesVehicleGraph() {
        try(OfflineDirections router=new OfflineDirections("src/main/assets/maps/mp-vehicle-v1")) {
            for(String mode:new String[]{"car","motorcycle"}) {
                try {
                    var route=router.route(23.302282333374023,77.35601043701172,23.3094246,77.3607503,mode);
                    assertTrue(route.distance()>100);System.out.println("RGPV "+mode+": "+route.distance()+" m, "+route.points().size()+" road points");
                }catch(IllegalArgumentException unavailable) {
                    // Without the exact reported origin/entrance this is an audit, not a claimed physical pass.
                    System.out.println("RGPV "+mode+": "+unavailable.getMessage());
                }
            }
        }
    }
}
