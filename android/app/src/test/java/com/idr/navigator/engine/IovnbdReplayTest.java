package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Assume;
import java.nio.file.*;
import java.util.*;

/** Opt-in real-recording evaluation. Accuracy numbers are reported, never invented pass thresholds. */
public class IovnbdReplayTest {
    @Test public void replayRecordedPhoneDataWithThirtySecondOutages() throws Exception {
        Assume.assumeTrue("Set IDR_REPLAY=1 to evaluate IO-VNBD fixtures", "1".equals(System.getenv("IDR_REPLAY")));
        Path root=Paths.get("../..").toAbsolutePath().normalize();
        Path input=root.resolve("data/fusion-replay"), output=root.resolve("artifacts/fusion-replay.csv");
        List<String> report=new ArrayList<>();report.add("recording,mode,samples,rmse_m,p95_m,outage_p95_m,max_m,accepted_gps");
        try(var files=Files.list(input)) {
            for(Path path:files.filter(p->p.toString().endsWith(".csv")).sorted().toList()) {
                List<double[]> rows=Files.readAllLines(path).stream().skip(1).map(line->Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray()).toList();
                for(boolean outage:new boolean[]{false,true}) {
                    NavigationFusion n=new NavigationFusion();List<Double> errors=new ArrayList<>(),blind=new ArrayList<>();
                    long last=0;int accepted=0;double[] previous=null;
                    for(double[] r:rows) {
                        long t=1_000_000_000L+(long)(r[0]*1e9);
                        n.gravity(t,r[14],r[15],r[16]);
                        int steps=last==0?1:(int)Math.min(10,Math.max(1,(t-last)/10_000_000L));
                        for(int j=steps-1;j>=0;j--) n.imu(new ImuSample(t-j*10_000_000L,r[1],r[2],r[3],r[4],r[5],r[6]),true);
                        last=t;
                        boolean hidden=outage&&r[0]>=60&&r[0]%120>=60&&r[0]%120<90;
                        if(previous==null || r[7]!=previous[7] || r[8]!=previous[8] || r[10]!=previous[10] || r[11]!=previous[11]) {
                            previous=r;
                            if(!hidden&&n.fix(new NavigationFusion.Fix(t,r[7],r[8],0,Math.max(3,r[9]),r[10],.5,r[11],10),t))accepted++;
                        }
                        if(n.filter.initialized && r[0]>30) {
                            double e=Math.hypot((n.filter.latitude()-r[12])*111320,(n.filter.longitude()-r[13])*111320*Math.cos(Math.toRadians(r[12])));
                            assertTrue("Non-finite estimate "+path,Double.isFinite(e));errors.add(e);if(hidden)blind.add(e);
                            for(int i=0;i<15;i++)assertTrue(n.filter.covarianceDiagonal(i)>=0&&Double.isFinite(n.filter.covarianceDiagonal(i)));
                        }
                    }
                    assertFalse(errors.isEmpty());Collections.sort(errors);Collections.sort(blind);
                    double rmse=Math.sqrt(errors.stream().mapToDouble(e->e*e).average().orElseThrow());
                    report.add(String.format(Locale.ROOT,"%s,%s,%d,%.3f,%.3f,%.3f,%.3f,%d",path.getFileName(),outage?"injected_outages":"gps_available",errors.size(),rmse,
                        errors.get((int)(errors.size()*.95)),blind.isEmpty()?0:blind.get((int)(blind.size()*.95)),errors.get(errors.size()-1),accepted));
                }
            }
        }
        Files.write(output,report);assertEquals(7,report.size());
    }
}
