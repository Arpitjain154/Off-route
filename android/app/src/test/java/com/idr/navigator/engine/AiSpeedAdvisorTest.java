package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;

public class AiSpeedAdvisorTest {
    @Test public void futureInvalidAndDuplicatePredictionsCannotUpdateFilter() {
        Eskf f=new Eskf();f.initialize(23,77,0);f.v[1]=10;
        AiSpeedAdvisor a=new AiSpeedAdvisor(f);
        a.estimate(2_000_000_000L,12,1,false);
        assertNull(a.provide(1_000_000_000L));
        a.estimate(3_000_000_000L,12,Double.NaN,false);
        assertNull(a.provide(3_000_000_000L));
        a.estimate(4_000_000_000L,12,1,true);
        assertNull(a.provide(4_000_000_000L));
        a.estimate(5_000_000_000L,12,1,false);
        assertNotNull(a.provide(5_000_000_000L));
        assertNull(a.provide(5_000_000_000L));
        a.estimate(5_000_000_000L,14,1,false);
        assertNull(a.provide(5_000_000_000L));
    }
}
