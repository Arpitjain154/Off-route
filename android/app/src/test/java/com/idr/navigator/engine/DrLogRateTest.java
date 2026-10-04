package com.idr.navigator.engine;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class DrLogRateTest {
    private static final long START = 1_000_000_000L;

    private static List<Long> record(int rate, long... timestamps) throws Exception {
        Path file = Files.createTempFile("idr-rate-", ".drlog");
        try {
            try (DrLogWriter log = new DrLogWriter(file.toFile(), rate, "phone", "mount", "session")) {
                for (long timestamp : timestamps) {
                    log.write(timestamp, new float[] {0, 0, 9.8f}, new float[3], new float[3],
                        23, 77, 500, 10, 3, 10, 35, 3);
                }
            }
            byte[] data = Files.readAllBytes(file);
            assertEquals(0, (data.length - 200) % 70);
            ByteBuffer bytes = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(rate, Short.toUnsignedInt(bytes.getShort(6)));
            List<Long> result = new ArrayList<>();
            for (int offset = 200; offset < data.length; offset += 70) result.add(bytes.getLong(offset));
            return result;
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test public void hundredHertzTargetDoesNotHalveFasterSensorStream() throws Exception {
        long[] input = new long[1300];
        for (int i = 0; i < input.length; i++) input[i] = START + Math.round(i * 1e9 / 130);
        List<Long> output = record(100, input);
        assertTrue("Expected approximately 1000 real samples, got " + output.size(),
            output.size() >= 999 && output.size() <= 1001);
        int cursor = 0;
        for (long timestamp : output) {
            while (input[cursor] < timestamp) cursor++;
            assertEquals("Recorded timestamps must come from the sensor", input[cursor], timestamp);
        }
    }

    @Test public void hundredHertzJitterDoesNotDropEveryOtherCallback() throws Exception {
        long[] input = new long[1000];
        for (int i = 0; i < input.length; i++) {
            input[i] = START + i * 10_000_000L - (i % 2 == 1 ? 100_000L : 0);
        }
        assertEquals(input.length, record(100, input).size());
    }

    @Test public void twoHundredHertzTargetKeepsSlowerSensorStream() throws Exception {
        long[] input = new long[1300];
        for (int i = 0; i < input.length; i++) input[i] = START + Math.round(i * 1e9 / 130);
        assertEquals(input.length, record(200, input).size());
    }

    @Test public void longGapDoesNotCauseCatchUpBurst() throws Exception {
        long gapEnd = START + 10_000_000_000L;
        List<Long> output = record(100, START, START + 10_000_000L, gapEnd,
            gapEnd + 1_000_000L, gapEnd + 2_000_000L, gapEnd + 10_000_000L);
        assertEquals(List.of(START, START + 10_000_000L, gapEnd, gapEnd + 10_000_000L), output);
    }

    @Test public void invalidAndReorderedSourceTimesAreIgnored() throws Exception {
        List<Long> output = record(100, -1, 0, START, START, START - 1,
            START + 4_000_000L, START + 3_000_000L, START + 10_000_000L);
        assertEquals(List.of(START, START + 10_000_000L), output);
    }
}
