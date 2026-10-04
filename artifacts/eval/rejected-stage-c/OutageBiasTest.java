package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;

public class OutageBiasTest {
    @Test public void frozenGyroBiasRejectsUnobservableOutageCorrections() {
        Eskf f=new Eskf();f.initialize(23,77,0);f.bg[2]=.01;
        f.freezeGyroBias=true;
        assertTrue(f.update(Measurement.direct(12,new double[]{.02,.02,.02},f.bg,.001),1));
        assertArrayEquals(new double[]{0,0,.01},f.bg,0);
        f.freezeGyroBias=false;
        assertTrue(f.update(Measurement.direct(12,new double[]{.02,.02,.02},f.bg,.001),1));
        assertTrue(f.bg[0]>0);
    }
    @Test public void straightMotionAlreadyAllowsNonHolonomicObservation() {
        Eskf f=new Eskf();f.initialize(23,77,0);f.v[1]=10;
        PhysicsAdvisor advisor=new PhysicsAdvisor(f);advisor.aligned=true;
        for(int i=0;i<120;i++)advisor.observe(new ImuSample(1_000_000_000L+i*10_000_000L,0,0,9.80665,0,0,0));
        assertNotNull(advisor.provide(2_190_000_000L));assertTrue(advisor.trustScore()>0);
    }
}
