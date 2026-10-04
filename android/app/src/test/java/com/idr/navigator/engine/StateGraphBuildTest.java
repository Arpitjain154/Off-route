package com.idr.navigator.engine;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.routing.util.EdgeFilter;
import java.io.File;
import java.util.Collections;
import org.junit.Test;

/** Explicit offline preparation step; enable with IDR_BUILD_STATE_GRAPH=1. */
public class StateGraphBuildTest {
    @Test public void buildMadhyaPradeshAndFindMajorCities() {
        assumeTrue("1".equals(System.getenv("IDR_BUILD_STATE_GRAPH")));
        GraphHopper hopper=new GraphHopper();
        try {
            hopper.setOSMFile(new File("src/main/assets/maps/madhya-pradesh.osm.pbf").getAbsolutePath());
            hopper.setGraphHopperLocation(new File("build/mp-vehicle-v1").getAbsolutePath());
            VehicleRouting.configure(hopper);
            hopper.importOrLoad();
            for(double[] city:new double[][]{{23.2599,77.4126},{22.7196,75.8577},{23.1815,79.9864},{26.2183,78.1828},{24.5362,81.3037},{23.1765,75.7885}}) {
                var snap=hopper.getLocationIndex().findClosest(city[0],city[1],EdgeFilter.ALL_EDGES);
                assertTrue("Road near city "+city[0],snap.isValid());
                assertTrue(snap.getQueryDistance()<3000);
            }
        } finally { hopper.close(); }
    }
}
