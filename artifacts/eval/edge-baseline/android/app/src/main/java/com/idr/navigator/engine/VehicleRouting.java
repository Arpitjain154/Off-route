package com.idr.navigator.engine;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.conditional.DateRangeParser;
import com.graphhopper.routing.ev.*;
import com.graphhopper.routing.util.*;
import com.graphhopper.routing.util.parsers.*;
import com.graphhopper.util.PMap;
import java.util.Collections;
import java.util.Set;

/** One import/load policy for the bundled graph, route requests and matching. */
public final class VehicleRouting {
    public static final String GRAPH_DIRECTORY="mp-vehicle-v1";
    public static GraphHopper configure(GraphHopper graph) {
        graph.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(true),
            new Profile("motorcycle").setVehicle("motorcycle").setWeighting("fastest").setTurnCosts(true));
        graph.getCHPreparationHandler().setCHProfiles(Collections.emptyList());
        graph.setVehicleTagParserFactory((lookup,name,properties)->{
            VehicleTagParsers defaults=new DefaultVehicleTagParserFactory().createParsers(lookup,name,properties);
            if(!name.equals("car")&&!name.equals("motorcycle"))throw new IllegalArgumentException("Unsupported vehicle: "+name);
            return new VehicleTagParsers(new MotorAccess(lookup,properties,name).init(new DateRangeParser()),defaults.getSpeedParser(),defaults.getPriorityParser());
        });
        return graph;
    }
    public static String profile(String mode) {
        if(!"car".equals(mode)&&!"motorcycle".equals(mode))throw new IllegalArgumentException("Select car or motorcycle directions.");
        return mode;
    }
    public static final class MotorAccess extends CarAccessParser {
        private final String mode;
        private static final Set<String> HARD_BARRIERS=Set.of("steps","stile","turnstile","kissing_gate","cycle_barrier","motorcycle_barrier","bollard","block","fence","wall","jersey_barrier");
        public MotorAccess(EncodedValueLookup lookup,PMap properties,String mode) {
            super(lookup.getBooleanEncodedValue(VehicleAccess.key(mode)),lookup.getBooleanEncodedValue(Roundabout.KEY),properties,
                mode.equals("car")?TransportationMode.CAR:TransportationMode.MOTORCYCLE);
            this.mode=mode;
            // Unclassified tracks and roads are not evidence of motor-vehicle passability.
            trackTypeValues.clear();trackTypeValues.add("grade1");
        }
        private boolean explicitMotorAccess(com.graphhopper.reader.ReaderElement element) {
            String value=element.getFirstPriorityTag(mode.equals("car")?java.util.List.of("motorcar","motor_vehicle"):java.util.List.of("motorcycle","motor_vehicle"));
            return Set.of("yes","designated","permissive").contains(value);
        }
        @Override public WayAccess getAccess(ReaderWay way) {
            if(way.hasTag("highway",Set.of("footway","path","pedestrian","steps","cycleway","bridleway","corridor","construction","proposed")))return WayAccess.CAN_SKIP;
            if(way.hasTag("highway","road")&&!explicitMotorAccess(way))return WayAccess.CAN_SKIP;
            if(way.hasTag("highway","track")&&(!way.hasTag("tracktype","grade1")||!explicitMotorAccess(way)))return WayAccess.CAN_SKIP;
            if(way.hasTag("smoothness",Set.of("very_bad","horrible","very_horrible","impassable")))return WayAccess.CAN_SKIP;
            if(way.hasTag("access:conditional")||way.hasTag("motor_vehicle:conditional")||way.hasTag(mode+":conditional"))return WayAccess.CAN_SKIP;
            return super.getAccess(way);
        }
        @Override public boolean isBarrier(ReaderNode node) {
            if(node.hasTag("barrier",HARD_BARRIERS)||node.hasTag("locked","yes"))return true;
            if(node.hasTag("barrier",Set.of("gate","lift_gate","swing_gate","chain"))&&!explicitMotorAccess(node))return true;
            return super.isBarrier(node);
        }
    }
}
