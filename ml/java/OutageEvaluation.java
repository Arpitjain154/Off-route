import com.idr.navigator.engine.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Evaluation-only: the reference columns are never passed to the estimator. */
public final class OutageEvaluation {
    public static void main(String[] args) throws Exception {
        NavigationFusion n=new NavigationFusion();
        boolean candidate=!args[2].equals("baseline"),ai=args[3].equals("true");
        if(candidate) {
            n.getClass().getField("boundedOutageSpeed").setBoolean(n,!args[2].equals("gyro"));
            try { n.getClass().getField("freezeOutageGyroBias").setBoolean(n,!args[2].equals("speed")); }
            catch(NoSuchFieldException absent) { /* Stage A snapshot. */ }
        }
        double start=Double.parseDouble(args[4]);
        double lastSeconds=Double.NaN;
        boolean mounted=false;
        try(BufferedReader input=Files.newBufferedReader(Path.of(args[0]));
            PrintWriter output=new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))) {
            input.readLine();String line;
            output.println("seconds,lat,lon,speed,heading,uncertainty,ai_used");
            while((line=input.readLine())!=null) {
                double[] r=Arrays.stream(line.split(",")).mapToDouble(x->x.equalsIgnoreCase("nan")?Double.NaN:Double.parseDouble(x)).toArray();
                long t=1_000_000_000L+Math.round(r[0]*1e9);
                // Zero-order hold preserves the existing 100 Hz filter/stop-detector contract;
                // the recording and output remain 10 Hz, with no invented sensor information.
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
                }
                // Explicit experimental observation, never model validation or promotion.
                boolean used=ai&&r[0]>=start&&Double.isFinite(r[15])&&n.aiSpeed(t,r[15],25,.10);
                if(r[0]>=start)output.printf(Locale.ROOT,"%.9f,%.12f,%.12f,%.9f,%.9f,%.9f,%d%n",
                    r[0],n.filter.latitude(),n.filter.longitude(),n.speed(t),n.filter.heading(),n.filter.uncertainty(),used?1:0);
            }
        }
    }
}
