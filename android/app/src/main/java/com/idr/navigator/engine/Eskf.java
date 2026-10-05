package com.idr.navigator.engine;

import java.util.Arrays;
import org.ejml.simple.SimpleMatrix;

/** One 15-dimensional error-state filter: [dp,dv,dtheta,dba,dbg]. Nominal attitude maps phone to ENU. */
public final class Eskf {

    public final double[] p = new double[3],
        v = new double[3],
        ba = new double[3],
        bg = new double[3];
    public double[] q = { 1, 0, 0, 0 };
    private double[] forwardPhone = { 0, 1, 0 }, upPhone = { 0, 0, 1 };
    private SimpleMatrix covariance = SimpleMatrix.identity(15).scale(.1);
    private long last;
    public boolean initialized = false;
    public double originLat, originLon, originAlt;
    private static final double EARTH = 6378137.0;
    private boolean boundedOutage;
    private double outageAnchor;
    private double[] outageBias;

    public void outageSpeed(boolean active,double trustedSpeed) {
        if(active&&!boundedOutage) {
            outageAnchor=Math.max(0,Math.min(80,trustedSpeed));outageBias=ba.clone();
            double h=Math.toRadians(heading());v[0]=Math.sin(h)*outageAnchor;v[1]=Math.cos(h)*outageAnchor;
        }
        boundedOutage=active;
    }

    public Eskf() {
        for (int i = 0; i < 3; i++) {
            covariance.set(i, i, 25);
            covariance.set(i + 3, i + 3, 9);
            covariance.set(i + 9, i + 9, .04);
            covariance.set(i + 12, i + 12, .001);
        }
    }

    public void initialize(double lat, double lon, double alt) {
        originLat = lat;
        originLon = lon;
        originAlt = alt;
        Arrays.fill(p, 0);
        Arrays.fill(v, 0);
        initialized = true;
    }

    public void allowGpsRecovery(double accuracy) {
        // Retain the nominal state; allow a fresh, repeatedly consistent GPS
        // observation to correct accumulated DR drift through the same filter.
        // A badly diverged attitude/bias estimate invalidates its old cross-correlations.
        // Reset the error prior, retaining the same nominal position and the same ESKF.
        covariance=new SimpleMatrix(15,15);
        for(int i=0;i<3;i++) {
            covariance.set(i,i,Math.max(100,accuracy*accuracy*25));
            covariance.set(i+3,i+3,100);
            covariance.set(i+6,i+6,1);
            covariance.set(i+9,i+9,.04);
            covariance.set(i+12,i+12,.001);
        }
        Arrays.fill(ba,0);Arrays.fill(bg,0);
    }

    public double[] enu(double lat, double lon, double alt) {
        return new double[] {
            Math.toRadians(lon - originLon) * EARTH * Math.cos(Math.toRadians(originLat)),
            Math.toRadians(lat - originLat) * EARTH,
            alt - originAlt,
        };
    }

    public double latitude() {
        return originLat + Math.toDegrees(p[1] / EARTH);
    }

    public double longitude() {
        return originLon + Math.toDegrees(p[0] / (EARTH * Math.cos(Math.toRadians(originLat))));
    }

    public double speed() {
        return Math.hypot(v[0], v[1]);
    }

    public double heading() {
        double[] forward = rotate(q, forwardPhone);
        return (Math.toDegrees(Math.atan2(forward[0], forward[1])) + 360) % 360;
    }

    public double uncertainty() {
        return Math.sqrt(Math.max(0, covariance.get(0, 0) + covariance.get(1, 1)));
    }

    public void predict(ImuSample s) {
        if (!Double.isFinite(s.ax()+s.ay()+s.az()+s.gx()+s.gy()+s.gz())) return;
        if (last == 0) {
            last = s.t();
            return;
        }
        double dt = (s.t() - last) * 1e-9;
        if (dt <= 0) return; // A delayed sensor event must not move the integration clock backwards.
        last = s.t();
        if (!initialized) return;
        if (dt > .25) {
            // Missing IMU data cannot be reconstructed. Preserve position and expose uncertainty.
            for (int i=0;i<3;i++) {
                covariance.set(i,i,covariance.get(i,i)+25*dt*dt);
                covariance.set(i+3,i+3,covariance.get(i+3,i+3)+4*dt);
            }
            return;
        }
        double[] a = { s.ax() - ba[0], s.ay() - ba[1], s.az() - ba[2] },
            w = { s.gx() - bg[0], s.gy() - bg[1], s.gz() - bg[2] };
        double[] midpoint = normalize(multiply(q, delta(new double[] { w[0]*dt*.5,w[1]*dt*.5,w[2]*dt*.5 })));
        q = normalize(multiply(q, delta(new double[] { w[0] * dt, w[1] * dt, w[2] * dt })));
        double[] world = rotate(midpoint, a);
        world[2] -= 9.80665;
        if(boundedOutage) {
            // Freeze the pre-loss acceleration bias for this bounded speed integrator.
            double[] corrected=rotate(midpoint,new double[]{s.ax()-outageBias[0],s.ay()-outageBias[1],s.az()-outageBias[2]});
            double h=Math.toRadians(heading()),east=Math.sin(h),north=Math.cos(h);
            double acceleration=Math.max(-OutageTuning.MAX_ACCELERATION_MPS2,Math.min(OutageTuning.MAX_ACCELERATION_MPS2,corrected[0]*east+corrected[1]*north));
            double next=Math.max(0,Math.min(80,Math.max(outageAnchor-OutageTuning.MAX_SPEED_CHANGE_MPS,
                Math.min(outageAnchor+OutageTuning.MAX_SPEED_CHANGE_MPS,speed()+acceleration*dt))));
            // Position and velocity share the same bounded propagation, including turns.
            world[0]=(next*east-v[0])/dt;world[1]=(next*north-v[1])/dt;
        }
        for (int i = 0; i < 3; i++) {
            p[i] += v[i] * dt + .5 * world[i] * dt * dt;
            v[i] += world[i] * dt;
        }
        SimpleMatrix f = SimpleMatrix.identity(15),
            r = rotation(midpoint);
        for (int i = 0; i < 3; i++) f.set(i, i + 3, dt);
        f.insertIntoThis(3, 6, r.mult(skew(a)).scale(-dt));
        f.insertIntoThis(3, 9, r.scale(-dt));
        f.insertIntoThis(6, 6, SimpleMatrix.identity(3).minus(skew(w).scale(dt)));
        f.insertIntoThis(6, 12, SimpleMatrix.identity(3).scale(-dt));
        SimpleMatrix noise = new SimpleMatrix(15, 15);
        for (int i = 0; i < 3; i++) {
            noise.set(i, i, 1e-6 * dt);
            noise.set(i + 3, i + 3, .16 * dt);
            noise.set(i + 6, i + 6, .0004 * dt);
            noise.set(i + 9, i + 9, 1e-5 * dt);
            noise.set(i + 12, i + 12, 1e-7 * dt);
        }
        covariance = f.mult(covariance).mult(f.transpose()).plus(noise);
    }

    public boolean update(Measurement m, double trust) {
        if (!initialized || m == null || !Double.isFinite(trust) || trust < .02) return false;
        for (SimpleMatrix value : new SimpleMatrix[]{m.h(),m.residual(),m.covariance()})
            for (int i=0;i<value.getNumElements();i++) if (!Double.isFinite(value.get(i))) return false;
        SimpleMatrix h = m.h(),
            r = m.covariance().divide(Math.max(.02, Math.min(1, trust))),
            s = h.mult(covariance).mult(h.transpose()).plus(r);
        SimpleMatrix inverse;
        try { inverse=s.invert(); } catch (RuntimeException singular) { return false; }
        double nis = m.residual().transpose().mult(inverse).mult(m.residual()).get(0);
        if (!Double.isFinite(nis) || nis > 36 * m.residual().numRows()) return false; // Reject grossly inconsistent advisors, not ordinary drift.
        SimpleMatrix gain = covariance.mult(h.transpose()).mult(inverse),
            dx = gain.mult(m.residual());
        // Sparse/poor GNSS must not turn position innovations into unbounded sensor biases.
        // Scale the corresponding gain rows and use that same gain in the Joseph covariance update.
        for(int i=9;i<15;i++) {
            double current=i<12?ba[i-9]:bg[i-12],limit=i<12?1:.05;
            double change=dx.get(i),target=Math.max(-limit,Math.min(limit,current+change));
            double scale=Math.abs(change)<1e-12?1:Math.max(0,Math.min(1,(target-current)/change));
            for(int j=0;j<gain.numCols();j++)gain.set(i,j,gain.get(i,j)*scale);
        }
        double angle=Math.sqrt(dx.get(6)*dx.get(6)+dx.get(7)*dx.get(7)+dx.get(8)*dx.get(8));
        if(angle>.25)for(int i=6;i<9;i++)for(int j=0;j<gain.numCols();j++)gain.set(i,j,gain.get(i,j)*.25/angle);
        dx=gain.mult(m.residual());
        for (int i = 0; i < 3; i++) {
            p[i] += dx.get(i);
            v[i] += dx.get(i + 3);
            ba[i] += dx.get(i + 9);
            bg[i] += dx.get(i + 12);
        }
        double[] dtheta = { dx.get(6), dx.get(7), dx.get(8) };
        q = normalize(multiply(q, delta(dtheta)));
        // Joseph form preserves PSD; reset Jacobian accounts for attitude error injection.
        SimpleMatrix a = SimpleMatrix.identity(15).minus(gain.mult(h));
        covariance = a
            .mult(covariance)
            .mult(a.transpose())
            .plus(gain.mult(r).mult(gain.transpose()));
        SimpleMatrix reset = SimpleMatrix.identity(15);
        reset.insertIntoThis(6, 6, SimpleMatrix.identity(3).minus(skew(dtheta).scale(.5)));
        covariance = reset.mult(covariance).mult(reset.transpose());
        covariance = covariance.plus(covariance.transpose()).scale(.5);
        return true;
    }

    public void align(double[] gravity, double headingDegrees) {
        align(gravity,headingDegrees,null);
    }

    /** Calibrated vehicle forward direction in phone axes, including sideways mounts. */
    public void align(double[] gravity, double headingDegrees, double[] vehicleForward) {
        double n = Math.sqrt(
            gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2]
        );
        if (n < 1) return;
        double gx = gravity[0] / n,
            gy = gravity[1] / n,
            gz = gravity[2] / n;
        upPhone = new double[]{gx,gy,gz};
        double roll = Math.atan2(gy, gz),
            pitch = Math.atan2(-gx, Math.hypot(gy, gz));
        double[] tilt = multiply(axis(1, pitch), axis(0, roll));
        // A portrait windshield mount has phone-Y almost vertical. Choose a horizontal reference.
        double[] candidate = vehicleForward!=null ? vehicleForward :
            Math.abs(gy) > .85 ? new double[] { 0, 0, -1 } : new double[] { 0, 1, 0 };
        double dot = candidate[0] * gx + candidate[1] * gy + candidate[2] * gz;
        forwardPhone = new double[] {
            candidate[0] - dot * gx,
            candidate[1] - dot * gy,
            candidate[2] - dot * gz,
        };
        double length = Math.sqrt(
            forwardPhone[0] * forwardPhone[0] +
                forwardPhone[1] * forwardPhone[1] +
                forwardPhone[2] * forwardPhone[2]
        );
        for (int i = 0; i < 3; i++) forwardPhone[i] /= length;
        double[] f = rotate(tilt, forwardPhone);
        double yaw = Math.atan2(f[0], f[1]) - Math.toRadians(headingDegrees);
        q = normalize(multiply(axis(2, yaw), tilt));
        for (int i = 6; i < 9; i++) covariance.set(i, i, .08);
    }

    /** Vehicle-frame lateral/normal velocity, including right-error attitude sensitivity.
     * A slope rotates both vehicle axes; it must not impose zero world-vertical speed. */
    public Measurement nonHolonomic(double lateralSigma,double normalSigma) {
        double[] right={forwardPhone[1]*upPhone[2]-forwardPhone[2]*upPhone[1],
            forwardPhone[2]*upPhone[0]-forwardPhone[0]*upPhone[2],
            forwardPhone[0]*upPhone[1]-forwardPhone[1]*upPhone[0]};
        double[] bodyVelocity=rotate(new double[]{q[0],-q[1],-q[2],-q[3]},v);
        SimpleMatrix h=new SimpleMatrix(2,15),residual=new SimpleMatrix(2,1);
        double[][] axes={right,upPhone};
        for(int row=0;row<2;row++) {
            double[] world=rotate(q,axes[row]);
            SimpleMatrix derivative=new SimpleMatrix(1,3,true,axes[row]).mult(skew(bodyVelocity));
            for(int i=0;i<3;i++) {h.set(row,3+i,world[i]);h.set(row,6+i,derivative.get(i));residual.set(row,residual.get(row)-world[i]*v[i]);}
        }
        SimpleMatrix noise=new SimpleMatrix(2,2);noise.set(0,0,lateralSigma*lateralSigma);noise.set(1,1,normalSigma*normalSigma);
        return new Measurement(residual,h,noise);
    }

    /** Course is a scalar attitude observation, with a Jacobian for right quaternion errors. */
    public Measurement course(double degrees, double sigmaDegrees) {
        double nominal=heading();
        SimpleMatrix h=new SimpleMatrix(1,15);
        for (int i=0;i<3;i++) {
            double[] perturb=new double[3]; perturb[i]=1e-6;
            double[] forward=rotate(multiply(q,delta(perturb)),forwardPhone);
            double changed=Math.toDegrees(Math.atan2(forward[0],forward[1]));
            h.set(0,6+i,Math.toRadians(wrapDegrees(changed-nominal))/1e-6);
        }
        return new Measurement(new SimpleMatrix(1,1,true,new double[]{Math.toRadians(wrapDegrees(degrees-nominal))}),h,
            SimpleMatrix.identity(1).scale(Math.pow(Math.toRadians(sigmaDegrees),2)));
    }

    public static double wrapDegrees(double degrees) { return ((degrees+180)%360+360)%360-180; }

    /** Gravity constrains roll/pitch, never absolute yaw. Right-error body-frame Jacobian. */
    public Measurement gravity(double[] measured, double sigma) {
        double norm=Math.sqrt(measured[0]*measured[0]+measured[1]*measured[1]+measured[2]*measured[2]);
        if(!Double.isFinite(norm)||norm<8||norm>11)return null;
        double[] predicted=rotate(new double[]{q[0],-q[1],-q[2],-q[3]},new double[]{0,0,9.80665});
        SimpleMatrix h=new SimpleMatrix(3,15);h.insertIntoThis(0,6,skew(predicted));
        return new Measurement(new SimpleMatrix(3,1,true,new double[]{measured[0]*9.80665/norm-predicted[0],
            measured[1]*9.80665/norm-predicted[1],measured[2]*9.80665/norm-predicted[2]}),h,SimpleMatrix.identity(3).scale(sigma*sigma));
    }

    private static double[] axis(int i, double a) {
        double[] q = { Math.cos(a / 2), 0, 0, 0 };
        q[i + 1] = Math.sin(a / 2);
        return q;
    }

    public static double[] delta(double[] a) {
        double n = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        double k = n < 1e-9 ? .5 : Math.sin(n / 2) / n;
        return new double[] { Math.cos(n / 2), a[0] * k, a[1] * k, a[2] * k };
    }

    public static double[] multiply(double[] a, double[] b) {
        return new double[] {
            a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
            a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
            a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
            a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0],
        };
    }

    public static double[] normalize(double[] q) {
        double n = 0;
        for (double x : q) n += x * x;
        n = Math.sqrt(n);
        for (int i = 0; i < 4; i++) q[i] /= n;
        return q;
    }

    public static double[] rotate(double[] q, double[] v) {
        SimpleMatrix x = rotation(q).mult(new SimpleMatrix(3, 1, true, v));
        return new double[] { x.get(0), x.get(1), x.get(2) };
    }

    public static SimpleMatrix rotation(double[] q) {
        double w = q[0],
            x = q[1],
            y = q[2],
            z = q[3];
        return new SimpleMatrix(new double[][] {
            { 1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y) },
            { 2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x) },
            { 2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y) },
        });
    }

    public static SimpleMatrix skew(double[] a) {
        return new SimpleMatrix(new double[][] {
            { 0, -a[2], a[1] },
            { a[2], 0, -a[0] },
            { -a[1], a[0], 0 },
        });
    }

    public double covarianceDiagonal(int i) {
        return covariance.get(i, i);
    }
}
