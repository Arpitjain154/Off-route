package com.idr.navigator.cloud;

import static org.junit.Assert.*;

import java.io.*;
import org.junit.Test;

public class CloudRequestGateTest {

    @Test
    public void revokeCancelsActiveAndPreventsLaterDispatch() throws Exception {
        CloudRequestGate g = new CloudRequestGate();
        boolean[] valid = { true },
            cancelled = { false };
        int[] starts = { 0 };
        AutoCloseable lease = g.begin(
            () -> {
                if (!valid[0]) throw new IOException("revoked");
            },
            () -> cancelled[0] = true,
            () -> starts[0]++
        );
        g.invalidate(() -> valid[0] = false);
        assertTrue(cancelled[0]);
        assertThrows(IOException.class, () ->
            g.begin(
                () -> {
                    if (!valid[0]) throw new IOException("revoked");
                },
                () -> {},
                () -> starts[0]++
            )
        );
        assertEquals(1, starts[0]);
        lease.close();
    }
}
