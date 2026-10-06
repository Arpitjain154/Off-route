package com.idr.navigator.engine;

import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.Profile;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.util.PMap;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;
import java.util.*;

/** HMM map advisor. Runs on its own worker; never calls ML and never owns another filter. */
public final class MapMatchAdvisor implements FilterAdvisor, AutoCloseable {

    private final Eskf filter;
    private GraphHopper hopper;
    private MapMatching matcher;
    private final List<Observation> track = new ArrayList<>();
    public record Match(long time,double lat,double lon,double sourceLat,double sourceLon,double heading,double trust) {}
    private volatile Match matched;
    private volatile double trust;
    private volatile long matchedTime;
    private long consumed;
    public volatile String status = "Loading offline graph";
    private boolean statewide;
    private String region = "Bhopal";
    private boolean disabled;

    public MapMatchAdvisor(Eskf filter) {
        this.filter = filter;
    }

    public void load(String osm, String cache) {
        statewide = osm.contains("madhya-pradesh");
        region = statewide ? "Madhya Pradesh" : "Bhopal";
        hopper = new GraphHopper();
        boolean prebuilt = new java.io.File(cache, "properties").isFile();
        if (prebuilt) {
            // RAM_STORE copies the entire MP graph (~100 MB) into Android's bounded Java heap.
            // Read-only mapped files let the OS page the bundled graph without that startup spike.
            hopper.init(new GraphHopperConfig().putObject("graph.location", cache)
                .putObject("graph.dataaccess.default_type", "MMAP_RO")
                .putObject("import.osm.ignored_highways", ""));
        }
        hopper.setOSMFile(osm);
        hopper.setGraphHopperLocation(cache);
        VehicleRouting.configure(hopper);
        if (prebuilt) {
            if (!hopper.load()) throw new IllegalStateException("The bundled road graph could not be opened");
        } else hopper.importOrLoad();
        matcher = MapMatching.fromGraphHopper(
            hopper,
            new PMap().putObject("profile", "car").putObject("ch.disable", true)
        );
        matcher.setMeasurementErrorSigma(30.0);
        status = region + " HMM ready";
    }

    public void observe(long timestamp, double lat, double lon) {
        if (disabled || matcher == null || !Double.isFinite(lat) || !Double.isFinite(lon)) { matched=null;trust=0;return; }
        if (statewide ? lat<21.0706 || lat>26.8696 || lon<74.0293 || lon>82.8127 :
            lat < 23.22 || lat > 23.27 || lon < 77.38 || lon > 77.43) {
            trust = 0; matched = null;
            track.clear();
            status = "Outside offline " + region + " area";
            return;
        }
        track.add(new Observation(new GHPoint(lat, lon)));
        if (track.size() > 24) track.remove(0);
        if (track.size() < 4) return;
        try {
            MatchResult result = matcher.match(new ArrayList<>(track));
            PointList points = result.getMergedPath().calcPoints();
            if (points.size() < 2) {
                trust = 0; matched = null;
                return;
            }
            int last = points.size() - 1;
            double mlat = points.getLat(last),
                mlon = points.getLon(last);
            double error = Math.hypot((lat - mlat) * 111320, (lon - mlon) * 111320 * Math.cos(Math.toRadians(lat)));
            if (error > 120) {
                trust = 0; matched = null;
                return;
            }
            trust = Math.min(.65, Math.exp((-error * error) / (2 * 30 * 30)) * .65);
            double heading=Math.toDegrees(Math.atan2((mlon-points.getLon(last-1))*Math.cos(Math.toRadians(mlat)),mlat-points.getLat(last-1)));
            matched = new Match(timestamp,mlat,mlon,lat,lon,heading,trust);
            matchedTime = timestamp;
            status = region + " HMM active";
        } catch (RuntimeException ex) {
            trust = 0; matched = null;
            status = "No consistent road match";
        }
    }

    public Measurement provide(long now) {
        Match m = matched;
        if (m == null || m.time() == consumed || now < m.time() || now - m.time() > 3_000_000_000L || trust==0) return null;
        consumed = m.time();
        double[] enu = filter.enu(m.lat(), m.lon(), filter.originAlt);
        double[] source=filter.enu(m.sourceLat(),m.sourceLon(),filter.originAlt);
        // HMM runs asynchronously. Apply its position correction to the current pose;
        // an old absolute endpoint would pull a moving vehicle backwards when the worker returns.
        return Measurement.direct(
            0,
            new double[] { filter.p[0]+enu[0]-source[0], filter.p[1]+enu[1]-source[1] },
            new double[] { filter.p[0], filter.p[1] },
            225
        );
    }

    public double trustScore() {
        return trust;
    }
    public Match latest(){return matched;}

    public void disable(Throwable error) {
        disabled = true;
        trust = 0; matched = null; track.clear();
        status = "Road matching unavailable: " + error.getClass().getSimpleName();
        try { close(); } catch (RuntimeException | LinkageError ignored) {}
    }

    public void close() {
        matcher = null;
        matched = null;
        trust = 0; matched = null;
        track.clear();
        GraphHopper previous = hopper;
        hopper = null;
        if (previous != null) previous.close();
    }
}
