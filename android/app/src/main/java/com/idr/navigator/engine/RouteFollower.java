package com.idr.navigator.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Optional outage-only motion along an active lon/lat route. The caller owns
 * GNSS trust and supplies an independent gyroscope heading, never this class's
 * route bearing. All timestamps use the same monotonic nanosecond clock.
 */
public final class RouteFollower {
    private static final double METRES_PER_DEGREE=111320.0;
    private static final double MAX_ANCHOR_DISTANCE=15;
    private static final long MAX_OUTAGE_NS=120_000_000_000L;
    private static final long TURN_WINDOW_NS=5_000_000_000L;

    public record Estimate(double latitude,double longitude,double bearingDegrees,
            double progressMeters,double distanceMeters,double alongUncertaintyMeters,
            double crossTrackUncertaintyMeters) {}
    public record Projection(double latitude,double longitude,double bearingDegrees,
            double progressMeters,double crossTrackMeters) {}
    private record Segment(double latitude,double longitude,double deltaLatitude,
            double deltaLongitude,double east,double north,double length,double start,
            double bearing,double turn) {}
    private record Turn(long time,double gyro,double route) {}

    private double[][] coordinates;
    private final List<Segment> segments=new ArrayList<>();
    private final ArrayDeque<Turn> turns=new ArrayDeque<>();
    private long anchorTime,lastTime,lastAnchorAttempt;
    private double routeLength,anchorProgress,progress,anchorAccuracy,anchorCross;
    private double previousGyro,gyroTurn;
    private boolean anchored,rejected,active;
    private Estimate latest;

    /** Changed route geometry requires a new anchor; identical refreshes are inert. Rows are lon,lat. */
    public boolean setRoute(double[][] coordinates) {
        if(sameRoute(coordinates))return !segments.isEmpty();
        clear();
        if(coordinates==null||coordinates.length<2)return false;
        for(double[] p:coordinates)
            if(p==null||p.length<2||!validCoordinate(p[1],p[0]))return false;
        this.coordinates=new double[coordinates.length][2];
        for(int i=0;i<coordinates.length;i++)this.coordinates[i]=new double[]{coordinates[i][0],coordinates[i][1]};
        double turn=0,previousBearing=0;
        for(int i=1;i<coordinates.length;i++) {
            double lat=coordinates[i-1][1],lon=coordinates[i-1][0];
            double dlat=coordinates[i][1]-lat,dlon=signedAngle(coordinates[i][0]-lon);
            double east=dlon*METRES_PER_DEGREE*Math.cos(Math.toRadians(lat+dlat/2));
            double north=dlat*METRES_PER_DEGREE,length=Math.hypot(east,north);
            if(length<.01)continue;
            double bearing=normalize(Math.toDegrees(Math.atan2(east,north)));
            if(!segments.isEmpty())turn+=signedAngle(bearing-previousBearing);
            segments.add(new Segment(lat,lon,dlat,dlon,east,north,length,routeLength,bearing,turn));
            routeLength+=length;previousBearing=bearing;
        }
        return !segments.isEmpty();
    }

    public void clear() {
        coordinates=null;segments.clear();turns.clear();routeLength=0;anchorTime=lastTime=lastAnchorAttempt=0;
        anchored=false;rejected=false;active=false;latest=null;
    }

    /**
     * Anchor only accepted trusted GNSS fixes. A newer off-route or invalid fix
     * invalidates the old anchor; a delayed fix cannot rewind current progress.
     */
    public boolean anchor(long timeNs,double latitude,double longitude,double accuracyMeters,
            double courseDeg,double gyroHeadingDeg) {
        if(timeNs<=0||timeNs<=lastAnchorAttempt)return false;
        lastAnchorAttempt=timeNs;anchored=false;active=false;latest=null;turns.clear();
        if(!Double.isFinite(accuracyMeters)||accuracyMeters<=0||accuracyMeters>20||
                !Double.isFinite(gyroHeadingDeg))return false;
        Projection projection=project(latitude,longitude,courseDeg);
        if(projection==null)return false;
        anchorTime=lastTime=timeNs;anchorProgress=progress=projection.progressMeters();
        anchorAccuracy=accuracyMeters;anchorCross=projection.crossTrackMeters();
        previousGyro=normalize(gyroHeadingDeg);gyroTurn=0;anchored=true;rejected=false;
        Segment startSegment=segmentAt(progress);
        double anchorTurn=startSegment.turn()+signedAngle(projection.bearingDegrees()-startSegment.bearing());
        turns.add(new Turn(timeNs,0,anchorTurn));
        return true;
    }

    public boolean eligible(){return anchored&&!rejected&&!segments.isEmpty();}
    public boolean active(){return active;}

    /**
     * Returns null to leave the ordinary navigation solution in control. Speed
     * must already be bounded by the caller; values outside (0,80] are rejected.
     * Stop, divergence, route end and timeout latch fallback until a new anchor.
     */
    public Estimate update(long nowNs,boolean outage,boolean stopped,double boundedSpeedMps,
            double gyroHeadingDeg) {
        if(!outage){active=false;latest=null;return null;}
        if(!eligible()||nowNs<lastTime)return null;
        if(stopped||nowNs-anchorTime>MAX_OUTAGE_NS||!Double.isFinite(boundedSpeedMps)||
                boundedSpeedMps<=0||boundedSpeedMps>80||!Double.isFinite(gyroHeadingDeg))
            return fallback();
        if(nowNs==lastTime)return latest;
        double nextProgress=progress+boundedSpeedMps*((nowNs-lastTime)*1e-9);
        if(!Double.isFinite(nextProgress)||nextProgress>routeLength+1e-6)return fallback();
        nextProgress=Math.min(routeLength,nextProgress);
        double nextGyro=gyroTurn+signedAngle(normalize(gyroHeadingDeg)-previousGyro);
        double nextRoute=segmentAt(nextProgress).turn();
        turns.addLast(new Turn(nowNs,nextGyro,nextRoute));
        Turn previous=windowStart(Math.max(anchorTime,nowNs-TURN_WINDOW_NS));
        // Compare signed changes, not magnitudes: an opposite turn is divergence.
        if(Math.abs((nextGyro-previous.gyro())-(nextRoute-previous.route()))>30+1e-9)
            return fallback();
        progress=nextProgress;gyroTurn=nextGyro;previousGyro=normalize(gyroHeadingDeg);lastTime=nowNs;
        Projection position=positionAt(progress);
        double travelled=progress-anchorProgress;
        // A route is a geometric prior, not a new accuracy observation. Preserve
        // GNSS uncertainty and include corridor/correspondence uncertainty.
        double along=anchorAccuracy+.15*travelled;
        double cross=Math.hypot(anchorAccuracy,Math.max(5,anchorCross))+.02*travelled;
        latest=new Estimate(position.latitude(),position.longitude(),position.bearingDegrees(),
                progress,travelled,along,cross);
        active=true;return latest;
    }

    private Estimate fallback(){rejected=true;active=false;latest=null;turns.clear();return null;}

    /**
     * Non-mutating projection for eligibility/recovery. First find the nearest
     * segment, then require forward-course agreement. A farther parallel road
     * cannot win merely because its bearing matches. Ties at a vertex use course.
     */
    public Projection project(double latitude,double longitude,double courseDeg) {
        if(!validCoordinate(latitude,longitude)||!Double.isFinite(courseDeg))return null;
        Segment best=null;double bestFraction=0,bestDistance=Double.POSITIVE_INFINITY;
        double bestAngle=Double.POSITIVE_INFINITY;
        for(Segment s:segments) {
            double east=signedAngle(longitude-s.longitude())*METRES_PER_DEGREE*
                    Math.cos(Math.toRadians(s.latitude()+s.deltaLatitude()/2));
            double north=(latitude-s.latitude())*METRES_PER_DEGREE;
            double fraction=Math.max(0,Math.min(1,(east*s.east()+north*s.north())/(s.length()*s.length())));
            double distance=Math.hypot(east-fraction*s.east(),north-fraction*s.north());
            double angle=Math.abs(signedAngle(courseDeg-s.bearing()));
            if(distance<bestDistance-1e-5||Math.abs(distance-bestDistance)<=1e-5&&angle<bestAngle) {
                best=s;bestFraction=fraction;bestDistance=distance;bestAngle=angle;
            }
        }
        if(best==null||bestDistance>MAX_ANCHOR_DISTANCE+1e-9||bestAngle>60)return null;
        return projection(best,bestFraction,bestDistance);
    }

    /** Position on the active polyline, suitable for blending recovery progress. */
    public Projection positionAt(double progressMeters) {
        if(segments.isEmpty()||!Double.isFinite(progressMeters)||progressMeters<0||
                progressMeters>routeLength+1e-6)return null;
        Segment s=segmentAt(Math.min(routeLength,progressMeters));
        return projection(s,Math.max(0,Math.min(1,(progressMeters-s.start())/s.length())),0);
    }

    private Projection projection(Segment s,double fraction,double crossTrack) {
        return new Projection(s.latitude()+fraction*s.deltaLatitude(),
                signedAngle(s.longitude()+fraction*s.deltaLongitude()),s.bearing(),
                s.start()+fraction*s.length(),crossTrack);
    }

    private Segment segmentAt(double distance) {
        int low=0,high=segments.size()-1;
        while(low<high) {
            int middle=(low+high+1)>>>1;
            if(segments.get(middle).start()<=distance)low=middle;else high=middle-1;
        }
        return segments.get(low);
    }

    private Turn windowStart(long target) {
        while(turns.size()>1) {
            Iterator<Turn> iterator=turns.iterator();iterator.next();Turn next=iterator.next();
            if(next.time()>target)break;
            turns.removeFirst();
        }
        Turn first=turns.getFirst();
        if(first.time()>=target||turns.size()==1)return first;
        Iterator<Turn> iterator=turns.iterator();iterator.next();Turn next=iterator.next();
        double fraction=(double)(target-first.time())/(next.time()-first.time());
        return new Turn(target,first.gyro()+fraction*(next.gyro()-first.gyro()),
                first.route()+fraction*(next.route()-first.route()));
    }

    private boolean sameRoute(double[][] next) {
        if(coordinates==null||next==null||coordinates.length!=next.length)return false;
        for(int i=0;i<next.length;i++) {
            if(next[i]==null||next[i].length<2||coordinates[i][0]!=next[i][0]||
                    coordinates[i][1]!=next[i][1])return false;
        }
        return true;
    }
    private static boolean validCoordinate(double latitude,double longitude) {
        return Double.isFinite(latitude)&&Double.isFinite(longitude)&&Math.abs(latitude)<=85&&
                Math.abs(longitude)<=180;
    }
    private static double normalize(double angle){return (angle%360+360)%360;}
    private static double signedAngle(double angle){return (normalize(angle)+180)%360-180;}
}
