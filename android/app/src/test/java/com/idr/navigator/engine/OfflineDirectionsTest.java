package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.File;

public class OfflineDirectionsTest {
    @Test public void routesOnBundledRoadsInBothDirections() {
        try (var router=new OfflineDirections(new File("src/main/assets/maps/mp-vehicle-v1").getAbsolutePath())) {
            for (boolean reverse:new boolean[]{false,true}) {
                var r=reverse?router.route(22.7203616,75.8681996,23.2584857,77.401989):router.route(23.2584857,77.401989,22.7203616,75.8681996);
                assertTrue("Must follow actual roads",r.points().size()>20);
                assertTrue(r.distance()>150000 && r.distance()<300000);
                assertTrue(r.time()>0);
                for(int i=0;i<r.points().size();i++) assertTrue(Double.isFinite(r.points().getLat(i))&&Double.isFinite(r.points().getLon(i)));
            }
        }
    }
    @Test public void rejectsInvalidAndOutsidePoints() {
        for(double[] point:new double[][]{{Double.NaN,77},{23,Double.POSITIVE_INFINITY},{0,0},{28.6,77.2}}) {
            try {OfflineDirections.validate(point[0],point[1]);fail("Invalid endpoint accepted");} catch(IllegalArgumentException expected){}
        }
    }
}
