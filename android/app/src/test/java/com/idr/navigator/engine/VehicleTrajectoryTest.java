package com.idr.navigator.engine;
import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Random;

public class VehicleTrajectoryTest {
    @Test public void noisyTurningVehicleWithThirtySecondBlackoutAndGpsReturn() {
        NavigationFusion n=new NavigationFusion();Random noise=new Random(729);
        double east=0,north=0,heading=0,speed=12,maxBlind=0;long origin=1_000_000_000L;
        for(int i=0;i<=12000;i++) {
            double seconds=i*.01;
            double omega=seconds>=40&&seconds<60?.04:0;
            heading+=omega*.01;
            if(i>0){east+=speed*Math.sin(heading)*.01;north+=speed*Math.cos(heading)*.01;}
            long t=origin+i*10_000_000L;
            n.gravity(t,0,0,9.80665);
            // Phone Y points forward, X right. Constant-radius turn accelerates along body X.
            n.imu(new ImuSample(t,speed*omega+.03+noise.nextGaussian()*.03,
                -.02+noise.nextGaussian()*.03,9.80665+noise.nextGaussian()*.03,
                noise.nextGaussian()*.0005,noise.nextGaussian()*.0005,-omega+.002),true);
            boolean blackout=seconds>=35&&seconds<65;
            if(i%100==0&&!blackout) {
                double lat=23.24+Math.toDegrees(north/6378137),lon=77.4+Math.toDegrees(east/(6378137*Math.cos(Math.toRadians(23.24))));
                n.fix(new NavigationFusion.Fix(t,lat,lon,0,3,speed,.2,Math.toDegrees(heading),5),t);
            }
            if(blackout)maxBlind=Math.max(maxBlind,Math.hypot(n.filter.p[0]-east,n.filter.p[1]-north));
        }
        double recovered=Math.hypot(n.filter.p[0]-east,n.filter.p[1]-north);
        System.out.println("Noisy turning vehicle: blackout maximum error="+maxBlind+" m, recovered error="+recovered+" m");
        assertTrue("Thirty-second synthetic blackout error "+maxBlind,maxBlind<30);
        assertTrue("Recovery error "+recovered,recovered<10);
    }
}
