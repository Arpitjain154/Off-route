package com.idr.navigator.engine;
import static org.junit.Assert.*;
import com.graphhopper.*;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.util.PMap;
import java.nio.file.*;
import org.junit.Test;

public class VehicleAccessTest {
    @Test public void gateShortcutIsReplacedByLongerVehicleRoadForBothModes() throws Exception {
        Path root=Files.createTempDirectory("idr-road-test");Path osm=root.resolve("roads.osm");
        Files.write(osm,"""
            <osm version="0.6">
              <node id="1" lat="23.3" lon="77.35"/>
              <node id="2" lat="23.3" lon="77.351"><tag k="barrier" v="gate"/></node>
              <node id="3" lat="23.3" lon="77.352"/>
              <node id="4" lat="23.301" lon="77.35"/>
              <node id="5" lat="23.301" lon="77.352"/>
              <way id="10"><nd ref="1"/><nd ref="2"/><nd ref="3"/><tag k="highway" v="residential"/></way>
              <way id="11"><nd ref="1"/><nd ref="4"/><nd ref="5"/><nd ref="3"/><tag k="highway" v="residential"/></way>
            </osm>
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        GraphHopper old=new GraphHopper(),fixed=new GraphHopper();
        try {
            old.init(new GraphHopperConfig().putObject("graph.location",root.resolve("old").toString()).putObject("import.osm.ignored_highways","").putObject("prepare.min_network_size",0));
            old.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest"));
            old.setOSMFile(osm.toString()).setGraphHopperLocation(root.resolve("old").toString());old.importOrLoad();
            double shortcut=old.route(new GHRequest(23.3,77.35,23.3,77.352).setProfile("car")).getBest().getDistance();
            fixed.init(new GraphHopperConfig().putObject("graph.location",root.resolve("fixed").toString()).putObject("import.osm.ignored_highways","").putObject("prepare.min_network_size",0));VehicleRouting.configure(fixed);
            fixed.setOSMFile(osm.toString()).setGraphHopperLocation(root.resolve("fixed").toString());fixed.importOrLoad();
            for(String mode:new String[]{"car","motorcycle"}) {
                var result=fixed.route(new GHRequest(23.3,77.35,23.3,77.352).setProfile(mode));
                assertFalse(result.getErrors().toString(),result.hasErrors());
                assertTrue(result.getBest().getDistance()>shortcut+150);
                var parser=new VehicleRouting.MotorAccess(fixed.getEncodingManager(),new PMap(),mode);
                parser.init(new DateRangeParser());
                for(String highway:new String[]{"footway","pedestrian","steps","path","cycleway","road","track"}) {
                    ReaderWay way=new ReaderWay(100);way.setTag("highway",highway);way.setTag("motor_vehicle","yes");
                    if(highway.equals("road"))way.removeTag("motor_vehicle");
                    assertTrue(mode+" "+highway,parser.getAccess(way).canSkip());
                }
                for(String barrier:new String[]{"gate","bollard","stile","cycle_barrier","fence","block"}) {
                    ReaderNode node=new ReaderNode(100,23.3,77.35);node.setTag("barrier",barrier);
                    assertTrue(mode+" "+barrier,parser.isBarrier(node));
                }
            }
            System.out.println("Unspecified gate: baseline shortcut="+shortcut+"m; vehicle routes use the longer open road.");
        } finally {old.close();fixed.close();}
    }
}
