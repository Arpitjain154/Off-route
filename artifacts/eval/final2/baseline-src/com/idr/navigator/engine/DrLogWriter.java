package com.idr.navigator.engine;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** DRLOG v1: 200-byte header (three fixed 64-byte UTF-8 fields), then 70-byte LE records. */
public final class DrLogWriter implements AutoCloseable {

    private final BufferedOutputStream stream;
    public final File file;
    public final int sampleRate;
    private long bytes = 200, lastObserved, nextDue;
    public String error;

    public DrLogWriter(File file, int rate, String phone, String mount, String session)
        throws IOException {
        this.file = file;
        this.sampleRate = rate;
        stream = new BufferedOutputStream(new FileOutputStream(file), 65536);
        ByteBuffer h = ByteBuffer.allocate(200).order(ByteOrder.LITTLE_ENDIAN);
        h.put("DRLOG".getBytes(StandardCharsets.US_ASCII));
        h.put((byte) 1);
        h.putShort((short) rate);
        for (String s : new String[] { phone, mount, session }) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            h.put(b, 0, Math.min(63, b.length));
            h.position(h.position() + 64 - Math.min(63, b.length));
        }
        stream.write(h.array());
        stream.flush(); // Recovery identity must survive interruption before the first buffer fills.
    }

    public synchronized void write(
        long t,
        float[] acc,
        float[] gyro,
        float[] mag,
        float lat,
        float lon,
        float alt,
        float speed,
        float accuracy,
        int sat,
        float cn0,
        int flags
    ) {
        if (t <= 0 || t <= lastObserved) return;
        lastObserved = t;
        long period = 1_000_000_000L / sampleRate;
        // Keep the requested cadence instead of resetting it after each write:
        // 130 Hz callbacks otherwise become only 65 Hz at a 100 Hz target.
        // Choose real samples within half a period; never synthesize timestamps.
        if (nextDue != 0 && t < nextDue - period / 2) return;
        // A slower source or a delivery gap starts a new cadence, not a backlog.
        nextDue = nextDue == 0 || t > nextDue + period / 2 ? t + period : nextDue + period;
        ByteBuffer b = ByteBuffer.allocate(70).order(ByteOrder.LITTLE_ENDIAN);
        b.putLong(t);
        for (float[] a : new float[][] { acc, gyro, mag }) for (float x : a) b.putFloat(x);
        for (float x : new float[] { lat, lon, alt, speed, accuracy }) b.putFloat(x);
        b.put((byte) Math.min(255, sat));
        b.putFloat(cn0);
        b.put((byte) flags);
        try {
            stream.write(b.array());
            bytes += 70;
        } catch (IOException e) {
            error = e.getMessage();
        }
    }

    public synchronized long size() {
        return bytes;
    }

    public synchronized void close() throws IOException {
        IOException failure=null;
        try{stream.flush();}catch(IOException e){failure=e;}
        try{stream.close();}catch(IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}
        if(failure!=null)throw failure;
    }
}
