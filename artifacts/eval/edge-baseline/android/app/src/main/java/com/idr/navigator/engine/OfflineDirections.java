package com.idr.navigator.engine;

import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.util.PointList;
import java.util.Collections;

/** Separate read-only road router. Never reads or writes the tracking filter. */
public final class OfflineDirections implements AutoCloseable {
    private final GraphHopper graph = new GraphHopper();
    public record Step(String text,int sign,double lat,double lon,double distance,double duration) {}
    public record Result(PointList points, double distance, long time,java.util.List<Step> steps) {}

    public OfflineDirections(String directory) {
        graph.init(new GraphHopperConfig().putObject("graph.location", directory)
            .putObject("graph.dataaccess.default_type", "MMAP_RO")
            .putObject("import.osm.ignored_highways", "")
            .putObject("routing.max_visited_nodes", 500000));
        VehicleRouting.configure(graph);
        try { if (!graph.load()) throw new IllegalStateException("Offline road graph could not be opened"); }
        catch (RuntimeException | LinkageError error) { graph.close(); throw error; }
    }

    public static void validate(double lat, double lon) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < 21.0706 || lat > 26.8696 || lon < 74.0293 || lon > 82.8127)
            throw new IllegalArgumentException("Choose both points inside the Madhya Pradesh offline map.");
    }

    public Result route(double fromLat, double fromLon, double toLat, double toLon) {
        return route(fromLat,fromLon,toLat,toLon,"car");
    }
    public Result route(double fromLat, double fromLon, double toLat, double toLon,String mode) {
        validate(fromLat, fromLon); validate(toLat, toLon);
        if (Math.hypot(fromLat-toLat, fromLon-toLon) < 0.00005)
            throw new IllegalArgumentException("Choose a destination different from your starting point.");
        var response = graph.route(new GHRequest(fromLat, fromLon, toLat, toLon).setProfile(VehicleRouting.profile(mode))
            .setLocale(java.util.Locale.ENGLISH).putHint("instructions", true).putHint("ch.disable", true).putHint("way_point_max_distance", 5));
        if (response.hasErrors()) throw new IllegalArgumentException("No driving route found in the offline map. Choose a nearby road or another place.");
        var path = response.getBest();
        var snapped=path.getWaypoints();
        for(int i=0;i<2;i++) {
            double lat=i==0?fromLat:toLat,lon=i==0?fromLon:toLon;
            double distance=com.graphhopper.util.DistanceCalcEarth.DIST_EARTH.calcDist(lat,lon,snapped.getLat(i),snapped.getLon(i));
            if(distance>80)throw new IllegalArgumentException("This place is not close enough to an accessible vehicle road. Select its vehicle entrance or a nearby road.");
        }
        if (path.getPoints().size() < 2 || path.getDistance() < 1)
            throw new IllegalArgumentException("These points connect to the same road location. Choose a different destination.");
        var steps=new java.util.ArrayList<Step>();
        var translation=graph.getTranslationMap().getWithFallBack(java.util.Locale.ENGLISH);
        for(var instruction:path.getInstructions()) {
            var points=instruction.getPoints();if(points.isEmpty())continue;
            steps.add(new Step(instruction.getTurnDescription(translation),instruction.getSign(),points.getLat(0),points.getLon(0),instruction.getDistance(),instruction.getTime()/1000.0));
        }
        return new Result(path.getPoints(), path.getDistance(), path.getTime(),steps);
    }
    public void close() { graph.close(); }
}
