import com.idr.navigator.engine.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.*;

/** Accuracy replay. Only the explicitly named oracle mode may inspect reference geometry. */
public final class OutageEvaluation {
    private static List<double[]> read(Path path) throws IOException {
        try(var lines=Files.lines(path)) {
            return lines.skip(1).map(line->Arrays.stream(line.split(",")).mapToDouble(
                x->x.equalsIgnoreCase("nan")?Double.NaN:Double.parseDouble(x)).toArray()).toList();
        }
    }
    private static void healthy(List<double[]> rows,PrintWriter output) {
        LiveNavigation live=new LiveNavigation();
        output.println("seconds,lat,lon,speed,heading,uncertainty,available,estimated");
        for(double[] r:rows) {
            long t=1_000_000_000L+Math.round(r[0]*1e9);
            live.gravity(t,0,0,9.80665);
            live.imu(new ImuSample(t,r[1],r[2],r[3],r[4],r[5],r[6]),true);
            // Healthy transport parity: each recorded tuple is delivered at its native row time.
            live.fix(new NavigationFusion.Fix(t,r[7],r[8],0,Math.max(3,r[9]),r[10],.5,r[11],10),t);
            live.tick(t);
            output.printf(Locale.ROOT,"%.9f,%.12f,%.12f,%.9f,%.9f,%.9f,%b,%b%n",r[0],
                live.latitude(),live.longitude(),live.speed(t),live.heading(),live.uncertainty(t),
                live.trackingAvailable(t),live.estimated(t));
        }
    }
    public static void main(String[] args) throws Exception {
        List<double[]> rows=read(Path.of(args[0]));
        try(PrintWriter output=new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))) {
            if(args[2].equals("healthy")){healthy(rows,output);return;}
            NavigationFusion n=new NavigationFusion();
            boolean candidate=!args[2].equals("baseline"),ai=args[3].equals("true");
            if(candidate)n.getClass().getField("boundedOutageSpeed").setBoolean(n,true);
            // Old snapshots may expose C; it is explicitly disabled in every final candidate.
            try{n.getClass().getField("freezeOutageGyroBias").setBoolean(n,false);}
            catch(NoSuchFieldException absent){/* C is absent from live source. */}
            Object route=null;Method anchor=null,update=null;
            if(args[2].equals("oracle")) {
                Class<?> type=Class.forName("com.idr.navigator.engine.RouteFollower");
                route=type.getConstructor().newInstance();
                // Contaminated oracle geometry only: no timed reference location or reference speed.
                double[][] geometry=rows.stream().map(r->new double[]{r[13],r[12]}).toArray(double[][]::new);
                type.getMethod("setRoute",double[][].class).invoke(route,(Object)geometry);
                anchor=type.getMethod("anchor",long.class,double.class,double.class,double.class,double.class,double.class);
                update=type.getMethod("update",long.class,boolean.class,boolean.class,double.class,double.class);
            }
            double start=Double.parseDouble(args[4]),lastSeconds=Double.NaN;
            boolean mounted=false;
            output.println("seconds,lat,lon,speed,heading,uncertainty,ai_used,route_used");
            for(double[] r:rows) {
                long t=1_000_000_000L+Math.round(r[0]*1e9);
                // Preserve historical accuracy-harness sampling for comparable baseline numbers.
                int count=Double.isFinite(lastSeconds)?Math.max(1,Math.min(10,(int)Math.round((r[0]-lastSeconds)*100))):1;
                for(int j=count-1;j>=0;j--) {
                    long stamp=t-j*10_000_000L;
                    n.gravity(stamp,0,0,9.80665);
                    n.imu(new ImuSample(stamp,r[1],r[2],r[3],r[4],r[5],r[6]),true);
                }
                lastSeconds=r[0];
                if(r[0]<start&&r[16]==1) {
                    boolean accepted=n.fix(new NavigationFusion.Fix(t,r[7],r[8],0,Math.max(3,r[9]),r[10],.5,r[11],10),t);
                    if(accepted&&!mounted&&r[10]>3) {
                        n.mount(new double[]{0,0,9.80665},new double[]{0,1,0},r[11],r[10]);mounted=true;
                    }
                    if(accepted&&route!=null)anchor.invoke(route,t,r[7],r[8],Math.max(3,r[9]),r[11],n.filter.heading());
                }
                boolean used=ai&&r[0]>=start&&Double.isFinite(r[15])&&n.aiSpeed(t,r[15],25,.10);
                double lat=n.filter.latitude(),lon=n.filter.longitude(),heading=n.filter.heading(),uncertainty=n.filter.uncertainty();
                boolean routeUsed=false;
                if(route!=null&&r[0]>=start) {
                    Object estimate=update.invoke(route,t,true,n.physics.isStopped(t),n.speed(t),heading);
                    if(estimate!=null) {
                        Class<?> type=estimate.getClass();routeUsed=true;
                        lat=(double)type.getMethod("latitude").invoke(estimate);lon=(double)type.getMethod("longitude").invoke(estimate);
                        heading=(double)type.getMethod("bearingDegrees").invoke(estimate);
                        uncertainty=Math.max(uncertainty,(double)type.getMethod("alongUncertaintyMeters").invoke(estimate));
                    }
                }
                if(r[0]>=start)output.printf(Locale.ROOT,"%.9f,%.12f,%.12f,%.9f,%.9f,%.9f,%d,%d%n",
                    r[0],lat,lon,n.speed(t),heading,uncertainty,used?1:0,routeUsed?1:0);
            }
        }
    }
}
