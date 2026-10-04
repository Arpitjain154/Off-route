import com.idr.navigator.engine.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Standalone offline harness around the exact live engine; never part of the APK. */
public final class LaptopNavigationReplay {
    private static double number(String s) {
        return s.equalsIgnoreCase("nan") ? Double.NaN : Double.parseDouble(s);
    }
    public static void main(String[] args) throws Exception {
        LiveNavigation baseline = new LiveNavigation(), candidate = new LiveNavigation();
        LiveNavigation[] engines = {baseline, candidate};
        try (BufferedReader input = Files.newBufferedReader(Path.of(args[0]));
             PrintWriter output = new PrintWriter(Files.newBufferedWriter(Path.of(args[1])))) {
            input.readLine();
            output.println("t,hidden,baseline_error,candidate_error,baseline_available,candidate_available,candidate_ai");
            String line;
            while ((line = input.readLine()) != null) {
                String[] cols = line.split(",", -1);
                long t = Long.parseLong(cols[0]), fixTime = Long.parseLong(cols[12]);
                double[] r = Arrays.stream(cols).mapToDouble(LaptopNavigationReplay::number).toArray();
                boolean hidden = r[22] == 1, reference = r[21] == 1;
                for (int i = 0; i < 2; i++) {
                    LiveNavigation n = engines[i];
                    n.gravity(t, r[7], r[8], r[9]);
                    if (Double.isFinite(r[10])) n.compass(t, r[10], 15);
                    n.imu(new ImuSample(t,r[1],r[2],r[3],r[4],r[5],r[6]), r[11] == 1);
                    boolean accepted = false;
                    if (reference && !hidden) accepted = n.fix(new NavigationFusion.Fix(fixTime,
                        r[13],r[14],r[15],r[16],r[17],r[18],r[19],r[20]), t);
                    double prediction = i == 0 ? (r[25] == 1 ? r[23] : Double.NaN) : r[24];
                    if (Double.isFinite(r[24])) n.models(t, prediction, null, null,
                        !hidden && accepted ? r[17] : null, !hidden && accepted && r[16] <= 15 && r[18] <= 1);
                    n.tick(t);
                }
                if (reference) {
                    double[] errors = new double[2];
                    for (int i = 0; i < 2; i++) errors[i] = Math.hypot((engines[i].latitude()-r[13])*111320,
                        (engines[i].longitude()-r[14])*111320*Math.cos(Math.toRadians(r[13])));
                    output.printf(Locale.ROOT,"%d,%d,%.9f,%.9f,%d,%d,%.5f%n", t,hidden?1:0,
                        errors[0],errors[1],baseline.trackingAvailable(t)?1:0,candidate.trackingAvailable(t)?1:0,
                        candidate.fusion.aiActive(t)?1.0:0.0);
                }
            }
        }
    }
}
