package com.idr.navigator.engine;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.*;
import org.junit.Test;

public class EskfTest {

    @Test
    public void gravityAlignmentWorksForUprightAndTiltedMounts() {
        for (double[] g : new double[][] {
            { 0, 9.80665, 0 },
            { 3, 4, Math.sqrt(9.80665 * 9.80665 - 25) },
        }) {
            Eskf f = new Eskf();
            f.initialize(23.24, 77.4, 0);
            f.align(g, 73);
            double[] world = Eskf.rotate(f.q, g);
            assertEquals(0, world[0], 1e-8);
            assertEquals(0, world[1], 1e-8);
            assertEquals(9.80665, world[2], 1e-8);
            assertEquals(73, f.heading(), 1e-8);
        }
    }

    @Test
    public void straightLineAndGpsBlackout() {
        Eskf f = new Eskf();
        f.initialize(23.24, 77.4, 500);
        f.v[1] = 10;
        for (int i = 0; i <= 6000; i++) {
            long t = 1_000_000_000L + i * 10_000_000L;
            f.predict(new ImuSample(t, 0, 0, 9.80665, 0, 0, 0));
            if (i % 100 == 0 && i < 2000) f.update(
                Measurement.direct(0, new double[] { 0, i * .1, 0 }, f.p, 4),
                .9
            );
        }
        assertEquals(600, f.p[1], 1);
        assertEquals(0, f.p[0], .01);
        assertEquals(10, f.speed(), .01);
        for (int i = 0; i < 15; i++) assertTrue(
            Double.isFinite(f.covarianceDiagonal(i)) && f.covarianceDiagonal(i) >= 0
        );
    }

    @Test
    public void rejectionAndZeroVelocity() {
        Eskf f = new Eskf();
        f.initialize(23.24, 77.4, 0);
        assertFalse(f.update(Measurement.direct(0, new double[] { 100000, 100000, 0 }, f.p, 1), 1));
        f.v[0] = .2;
        for (int i = 0; i < 20; i++) f.update(
            Measurement.direct(3, new double[] { 0, 0, 0 }, f.v, .01),
            .9
        );
        assertEquals(0, f.speed(), .01);
    }

    @Test
    public void binaryContract() throws Exception {
        File file = File.createTempFile("idr-test", ".drlog");
        try {
            DrLogWriter w = new DrLogWriter(
                file,
                100,
                "Test phone",
                "dashboard",
                "00000000-0000-0000-0000-000000000000"
            );
            w.write(
                1_000_000_000L,
                new float[] { 1, 2, 3 },
                new float[] { 4, 5, 6 },
                new float[] { 7, 8, 9 },
                23.24f,
                77.4f,
                500,
                10,
                3,
                17,
                35,
                3
            );
            w.close();
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            assertEquals(270, bytes.length);
            ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(100, b.getShort(6));
            assertEquals(1_000_000_000L, b.getLong(200));
            assertEquals(23.24f, b.getFloat(244), .00001);
            assertEquals(17, b.get(264));
            assertEquals(3, b.get(269));
        } finally {
            file.delete();
        }
    }
}
