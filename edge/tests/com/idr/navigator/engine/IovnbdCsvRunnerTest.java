package com.idr.navigator.engine;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Transport and causality tests; synthetic constant motion is not accuracy evidence. */
public class IovnbdCsvRunnerTest {
    private Path fixture(boolean mutateHidden) throws Exception {
        Path path=Files.createTempFile("iovnbd-runner-", ".csv");
        List<String> rows=new ArrayList<>();rows.add("GPS LATITUDE,GPS LONGITUDE,ALT,SPEED,ACCURACY,BEARING,SATELLITES,TIME SINCE START,DATE,AX,AY,AZ,GRAVX,GRAVY,GRAVZ,GX,GY,GZ,MX,MY,MZ,YAW,PITCH,ROLL");
        for(int i=0;i<=200;i++) {
            double seconds=i*.1,lat=23.3+seconds*10/111320,lon=77.35,speed=10;
            if(mutateHidden&&seconds>=5&&seconds<15){lat=-45;lon=-110;speed=70;}
            rows.add(String.format(Locale.ROOT,"%.12f,%.12f,0,%.3f,3,0,12,%d,date,0,0,9.80665,0,0,9.80665,0,0,0,0,0,0,0,0,0",lat,lon,speed,i*100));
        }
        Files.write(path,rows);return path;
    }
    private List<String[]> run(Path input,int rate) throws Exception {
        Path out=Files.createTempFile("iovnbd-trajectory-", ".csv");
        List<String> args=new ArrayList<>(List.of(input.toString(),out.toString(),"--gps-speed-unit","mps","--outage-start","5","--outage-duration","10"));
        if(rate>0)args.addAll(List.of("--resample-hz",Integer.toString(rate)));
        IovnbdCsvRunner.main(args.toArray(String[]::new));
        List<String[]> rows=Files.readAllLines(out).stream().skip(1).map(line->line.split(",")).toList();
        Files.delete(out);return rows;
    }
    @Test public void nativeTenHertzAndResampledTwoHundredHertzAreFiniteAndCausal() throws Exception {
        Path input=fixture(false);
        for(int rate:new int[]{0,200}) {
            var rows=run(input,rate);assertEquals(rate==0?201:4001,rows.size());
            double previous=-1;
            for(String[] row:rows) {
                double seconds=Double.parseDouble(row[0]);assertTrue(seconds>previous);previous=seconds;
                assertTrue(Double.parseDouble(row[10])<=seconds+1e-8);
                for(int i=1;i<=5;i++)assertTrue(Double.isFinite(Double.parseDouble(row[i])));
                if(seconds>=5&&seconds<15)assertEquals("false",row[8]);
            }
            String[] last=rows.get(rows.size()-1);
            assertEquals(20,Double.parseDouble(last[0]),1e-9);
            assertTrue("Continuous straight trace stays available",Boolean.parseBoolean(last[6]));
        }
        Files.delete(input);
    }
    @Test public void withheldCoordinatesAndSpeedCannotAlterEitherReplayRate() throws Exception {
        Path original=fixture(false),mutated=fixture(true);
        for(int rate:new int[]{0,200}) {
            var expected=run(original,rate);var actual=run(mutated,rate);
            assertEquals(expected.size(),actual.size());
            for(int i=0;i<expected.size();i++)assertArrayEquals("Hidden GNSS leaked at row "+i,expected.get(i),actual.get(i));
        }
        Files.delete(original);Files.delete(mutated);
    }
}
