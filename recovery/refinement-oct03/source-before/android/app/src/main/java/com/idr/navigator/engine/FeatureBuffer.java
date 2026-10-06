package com.idr.navigator.engine;

/** Fixed 100 Hz feature timebase regardless of the logging sample rate. Tensor contract in ml/README.md. */
public final class FeatureBuffer {

    private final float[][] window = new float[300][6];
    private int cursor, count;
    private long last,nextSample;
    private float[] previous;

    public void add(ImuSample s) {
        if (!Double.isFinite(s.ax()+s.ay()+s.az()+s.gx()+s.gy()+s.gz())) return;
        if (s.t()<=last) return;
        float[] current = new float[] {
            (float) s.ax(),
            (float) s.ay(),
            (float) s.az(),
            (float) s.gx(),
            (float) s.gy(),
            (float) s.gz(),
        };
        // SensorManager timing varies by device. Resample timestamps, rather than
        // assuming 300 callbacks always span three seconds. Never bridge a long gap.
        if(last==0||s.t()-last>100_000_000L){count=0;cursor=0;append(current);nextSample=s.t()+10_000_000L;}
        else while(nextSample<=s.t()) {
            double fraction=(double)(nextSample-last)/(s.t()-last);float[] value=new float[6];
            for(int axis=0;axis<6;axis++)value[axis]=(float)(previous[axis]+fraction*(current[axis]-previous[axis]));
            append(value);nextSample+=10_000_000L;
        }
        previous=current;last=s.t();
    }
    private void append(float[] value){window[cursor]=value;cursor=(cursor+1)%300;count=Math.min(300,count+1);}

    public boolean ready() {
        return count == 300;
    }

    public float[][][] imu() {
        float[][][] out = new float[1][300][6];
        // IO-VNBD is sampled at 10 Hz. Average each 100 ms block and repeat it
        // to retain the tensor contract without claiming unavailable high-frequency data.
        for (int start = 0; start < 300; start += 10) {
            float[] mean = new float[6];
            for (int j = 0; j < 10; j++) for (int axis = 0; axis < 6; axis++)
                mean[axis] += window[(cursor + start + j) % 300][axis] / 10f;
            for (int j = 0; j < 10; j++) out[0][start + j] = mean.clone();
        }
        return out;
    }

    public float[][] motion() {
        float[][] samples = imu()[0];
        float[][] result = new float[1][42];
        for (int axis = 0; axis < 6; axis++) {
            double sum = 0,
                sq = 0,
                min = Double.MAX_VALUE,
                max = -Double.MAX_VALUE;
            for (float[] s : samples) {
                double v = s[axis];
                sum += v;
                sq += v * v;
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            double mean = sum / 300,
                variance = Math.max(0, sq / 300 - mean * mean),
                rms = Math.sqrt(sq / 300);
            int crossing = 0;
            for (int i = 1; i < 300; i++) if (
                (samples[i][axis] - mean) * (samples[i - 1][axis] - mean) < 0
            ) crossing++;
            // Parseval: sum of DFT energy / N equals sum of squared samples. Store mean spectral energy.
            int partner = axis < 3 ? axis + 3 : axis - 3;
            double pmean = 0,
                pvar = 0,
                cov = 0;
            for (float[] s : samples) pmean += s[partner] / 300.0;
            for (float[] s : samples) {
                pvar += ((s[partner] - pmean) * (s[partner] - pmean)) / 300;
                cov += ((s[axis] - mean) * (s[partner] - pmean)) / 300;
            }
            float[] values = {
                (float) mean,
                (float) Math.sqrt(variance),
                (float) (max - min),
                (float) rms,
                crossing / 299f,
                (float) (sq / 300),
                (float) (cov / Math.sqrt(Math.max(1e-12, variance * pvar))),
            };
            System.arraycopy(values, 0, result[0], axis * 7, 7);
        }
        return result;
    }
}
