package com.idr.navigator.engine;

import static org.junit.Assert.*;
import java.io.*;
import org.junit.Test;

public class FeatureParityTest {
    @Test public void irregularCallbacksStillRepresentThreeSecondsAndGapsReset() {
        FeatureBuffer buffer=new FeatureBuffer();long start=1_000_000_000L;
        for(int i=0;i<=200;i++)buffer.add(new ImuSample(start+i*15_000_000L,i*.015,0,9.80665,0,0,0));
        assertTrue(buffer.ready());
        assertEquals(2.955,buffer.imu()[0][299][0],.001);
        buffer.add(new ImuSample(start+4_000_000_000L,4,0,9.80665,0,0,0));assertFalse(buffer.ready());
    }
    @Test public void nativeFeaturesMatchPythonOnHeldOutIovnbdWindow() throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("/iovnbd-feature-fixture.txt")))) {
            String[] expected = reader.readLine().split(",");
            FeatureBuffer buffer = new FeatureBuffer();
            for (int i=0; i<300; i++) {
                String[] row = reader.readLine().split(",");
                double[] x = new double[6];
                for (int j=0; j<6; j++) x[j]=Double.parseDouble(row[j]);
                buffer.add(new ImuSample(1_000_000_000L+i*10_000_000L,x[0],x[1],x[2],x[3],x[4],x[5]));
            }
            assertTrue(buffer.ready());
            float[] actual = buffer.motion()[0];
            for (int i=0; i<42; i++) {
                double value = Double.parseDouble(expected[i]);
                assertEquals("feature " + i, value, actual[i], Math.max(.0002,Math.abs(value)*.0002));
            }
        }
    }
}
