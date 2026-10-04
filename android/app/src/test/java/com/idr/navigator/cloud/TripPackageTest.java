package com.idr.navigator.cloud;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import org.json.*;
import org.junit.Test;

public class TripPackageTest {

    private final String id = "12345678-1234-1234-1234-123456789abc";

    private static void write(Path p, String s) throws IOException {
        Files.write(p, s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private File fixture() throws Exception {
        File dir = Files.createTempDirectory("trip-test").toFile();
        byte[] raw = new byte[340];
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        b.put("DRLOG".getBytes());
        b.put((byte) 1);
        b.putShort((short) 100);
        b.position(136);
        b.put(id.getBytes());
        b.putLong(200, 1);
        b.putLong(270, 2);
        Files.write(new File(dir, id + ".drlog").toPath(), raw);
        write(
            new File(dir, id + ".training.json").toPath(),
            "{\"schemaVersion\":1,\"source\":\"device\",\"sessionId\":\"" + id + "\"}"
        );
        write(
            new File(dir, id + ".quality.csv").toPath(),
            "t_ns,fix_t_ns,speed_accuracy_mps,gyro_fresh\n1,1,0.5,1\n2,1,0.5,1\n"
        );
        write(
            new File(dir, id + ".summary.json").toPath(),
            "{\"path\":\"file:///private/secret\",\"durationSeconds\":3,\"distanceMeters\":2}"
        );
        return dir;
    }

    @Test
    public void reassemblesExactlyAndRemovesPrivatePaths() throws Exception {
        File dir = fixture(),
            out = new File(dir, "stage");
        JSONObject m = TripPackage.prepare(dir, id, out, 100);
        assertEquals(1, m.getInt("schemaVersion"));
        assertEquals(4, m.getJSONArray("files").length());
        JSONObject file = m.getJSONArray("files").getJSONObject(0);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JSONArray parts = file.getJSONArray("chunks");
        assertTrue(parts.length() > 1);
        for (int i = 0; i < parts.length(); i++) bytes.write(
            Files.readAllBytes(
                new File(out, parts.getJSONObject(i).getString("objectName")).toPath()
            )
        );
        assertArrayEquals(
            Files.readAllBytes(new File(dir, id + ".drlog").toPath()),
            bytes.toByteArray()
        );
        for (File f : out.listFiles())
            assertFalse(new String(Files.readAllBytes(f.toPath())).contains("file:///private"));
    }

    @Test
    public void missingQualityRejected() throws Exception {
        File dir = fixture();
        Files.delete(new File(dir, id + ".quality.csv").toPath());
        assertThrows(IOException.class, () ->
            TripPackage.prepare(dir, id, new File(dir, "stage"), 100)
        );
    }

    @Test
    public void simulationRejected() throws Exception {
        File dir = fixture();
        write(new File(dir, id + ".training.json").toPath(), "{\"source\":\"simulation\"}");
        assertThrows(IOException.class, () ->
            TripPackage.prepare(dir, id, new File(dir, "stage"), 100)
        );
    }

    @Test
    public void traversalRejected() throws Exception {
        File dir = fixture();
        assertThrows(IOException.class, () ->
            TripPackage.prepare(dir, "../bad", new File(dir, "stage"), 100)
        );
    }

    @Test
    public void truncatedRecordingRejected() throws Exception {
        File dir = fixture();
        Files.write(new File(dir, id + ".drlog").toPath(), new byte[203]);
        assertThrows(IOException.class, () ->
            TripPackage.prepare(dir, id, new File(dir, "stage"), 100)
        );
    }

    @Test
    public void incompleteQualityRejected() throws Exception {
        File dir = fixture();
        write(
            new File(dir, id + ".quality.csv").toPath(),
            "t_ns,fix_t_ns,speed_accuracy_mps,gyro_fresh\n1,1,0.5,1\n"
        );
        assertThrows(IOException.class, () ->
            TripPackage.prepare(dir, id, new File(dir, "stage"), 100)
        );
    }

    @Test
    public void declaredRawSidecarsAreRequiredAndPackaged() throws Exception {
        File dir=fixture();
        JSONObject p=TripPackage.json(new File(dir,id+".training.json"));
        p.put("rawSidecarsSchemaVersion",1);TripPackage.save(new File(dir,id+".training.json"),p);
        assertThrows(IOException.class,()->TripPackage.prepare(dir,id,new File(dir,"stage"),100));
        write(new File(dir,id+".sensors.csv").toPath(),"t_ns,sensor_type,accuracy,value_count,v0,v1,v2,v3,v4\n1,4,3,3,1,2,3,,\n");
        write(new File(dir,id+".gnss.csv").toPath(),"fix_t_ns,arrival_t_ns,utc_ms,latitude_deg,longitude_deg,altitude_m,horizontal_accuracy_m,vertical_accuracy_m,speed_mps,speed_accuracy_mps,bearing_deg,bearing_accuracy_deg,mock,accepted\n1,2,3,23.1234567890123,77.1234567890123,500,4,6,12,.3,45,3,0,1\n");
        JSONObject manifest=TripPackage.prepare(dir,id,new File(dir,"stage"),100);
        assertEquals(6,manifest.getJSONArray("files").length());
    }

    @Test
    public void persistedCaptureFailureRejected() throws Exception {
        File dir = fixture();
        write(new File(dir, id + ".capture-error").toPath(), "quality writer failed");
        assertThrows(IOException.class, () ->
            TripPackage.prepare(dir, id, new File(dir, "stage"), 100)
        );
    }
}
