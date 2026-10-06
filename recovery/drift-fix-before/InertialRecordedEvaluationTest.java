package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;
import java.nio.file.*;
import java.util.*;

/** Reused IO-VNBD recordings: numeric results, never a fabricated accuracy pass. */
public class InertialRecordedEvaluationTest {
    @Test public void evaluateThirtySecondOutagesAgainstVehicleReference() throws Exception {
        Path dir=Paths.get("../../artifacts/qa-1.5/iovnbd");
        org.junit.Assume.assumeTrue("Run ml/prepare_inertial_evaluation.py first",Files.exists(dir.resolve("manifest.json")));
        List<String> summary=new ArrayList<>();summary.add("recording,outage_start_s,distance_m,relative_endpoint_drift_m,drift_percent,aligned_fraction,ai_used_fraction");
        for(String file:new String[]{"S-S1.csv","S-S2.csv","S-S3a.csv"}) {
            List<double[]> rows=Files.readAllLines(dir.resolve(file)).stream().skip(1).map(line->Arrays.stream(line.split(",")).mapToDouble(s->s.equalsIgnoreCase("nan")?Double.NaN:Double.parseDouble(s)).toArray()).toList();
            var n=new InertialNavigation();double[] prior=null,start=null;double startLat=0,startLon=0,distance=0;int count=0,aligned=0,ai=0;boolean wasOutage=false;
            List<String> track=new ArrayList<>();track.add("seconds,reference_lat,reference_lon,estimated_lat,estimated_lon,speed_mps,uncertainty_m,outage,aligned,ai_weight");
            for(double[] row:rows) {
                long t=1_000_000_000L+(long)(row[0]*1e9);boolean outage=row[0]>=60&&row[0]%120>=60&&row[0]%120<90;
                double endLat=n.latitude(),endLon=n.longitude();
                n.gravity(t,row[14],row[15],row[16]);n.compass(t,row[17],15);
                boolean changed=prior==null||row[7]!=prior[7]||row[8]!=prior[8]||row[10]!=prior[10]||row[11]!=prior[11];
                if(!outage&&changed&&row[9]<=20) {
                    n.anchor(new NavigationFusion.Fix(t,row[7],row[8],0,Math.max(3,row[9]),row[10],.5,row[11],10),t,row[10],row[10]<.1,row[11]);
                }
                if(Double.isFinite(row[18]))n.models(t,row[18],new float[]{(float)row[19],(float)row[20],(float)row[21],(float)row[22],(float)row[23]},
                    new float[]{(float)row[24],(float)row[25],(float)row[26]},outage?null:row[10],!outage&&changed&&row[9]<=15);
                long dt=prior==null?100_000_000L:(long)((row[0]-prior[0])*1e9);
                for(long offset=Math.min(100_000_000L,dt)-10_000_000L;offset>=0;offset-=10_000_000L)
                    n.predict(new ImuSample(t-offset,row[1],row[2],row[3],row[4],row[5],row[6]),!outage,!outage&&row[10]<.1);
                if(outage&&!wasOutage){start=row;startLat=n.latitude();startLon=n.longitude();distance=0;count=aligned=ai=0;}
                if(outage){if(prior!=null)distance+=metres(row[12]-prior[12],row[13]-prior[13],row[12]);count++;if(n.available(t))aligned++;if(n.aiWeight(t)>0)ai++;}
                if(!outage&&wasOutage&&start!=null&&prior!=null) {
                    double error=metres((endLat-startLat)-(prior[12]-start[12]),(endLon-startLon)-(prior[13]-start[13]),prior[12]);
                    summary.add(String.format(Locale.ROOT,"%s,%.1f,%.3f,%.3f,%.3f,%.3f,%.3f",file,start[0],distance,error,100*error/Math.max(1,distance),(double)aligned/count,(double)ai/count));
                }
                assertTrue(Double.isFinite(n.latitude()+n.longitude()+n.speed()+n.uncertainty()));
                if(file.equals("S-S1.csv")&&row[0]>54&&row[0]<90&&Math.abs(row[0]%5)<.002) {
                    for(String field:new String[]{"forward","bias","acceleration"}) {
                        var f=InertialNavigation.class.getDeclaredField(field);f.setAccessible(true);Object value=f.get(n);
                        System.out.println(row[0]+" "+field+"="+(value instanceof double[]?Arrays.toString((double[])value):value));
                    }
                }
                track.add(String.format(Locale.ROOT,"%.3f,%.9f,%.9f,%.9f,%.9f,%.4f,%.3f,%b,%b,%.3f",row[0],row[12],row[13],n.latitude(),n.longitude(),n.speed(),n.uncertainty(),outage,n.available(t),n.aiWeight(t)));
                prior=row;wasOutage=outage;
            }
            Files.write(dir.resolve(file.replace(".csv","-positions.csv")),track);
        }
        Files.write(dir.resolve("drift-summary.csv"),summary);summary.forEach(System.out::println);
        assertTrue(summary.size()>1);
    }
    private static double metres(double lat,double lon,double originLat){return Math.hypot(lat*111320,lon*111320*Math.cos(Math.toRadians(originLat)));}
}
