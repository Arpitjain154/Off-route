package com.idr.navigator.cloud;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Immutable, bounded-size objects; complete.json is the remote commit marker. */
public final class TripPackage {

    public static final int CHUNK_BYTES = 8 * 1024 * 1024;

    public static void identity(String id) throws IOException {
        try {
            if (!UUID.fromString(id).toString().equals(id)) throw new Exception();
        } catch (Exception e) {
            throw new IOException("Invalid session identity");
        }
    }

    public static JSONObject json(File f) throws IOException {
        try {
            if (f.length() > 1024 * 1024) throw new IOException("Metadata too large");
            return new JSONObject(
                new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
            );
        } catch (JSONException e) {
            throw new IOException("Invalid metadata", e);
        }
    }

    public static void save(File f, JSONObject value) throws IOException {
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(value.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        Files.move(
            tmp.toPath(),
            f.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        );
    }

    public static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) s.append(String.format(Locale.ROOT, "%02x", b));
        return s.toString();
    }

    public static String hash(File f) throws IOException {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new FileInputStream(f)) {
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) != -1) d.update(b, 0, n);
            }
            return hex(d.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public static JSONObject prepare(File dir, String id, File stage, int chunkBytes)
        throws IOException {
        identity(id);
        if (chunkBytes <= 0 || chunkBytes > CHUNK_BYTES) throw new IOException(
            "Invalid chunk size"
        );
        if (new File(dir, id + ".capture-error").exists()) throw new IOException(
            "Recording had a capture failure; originals retained"
        );
        try {
            for (String suffix : new String[] {
                ".drlog",
                ".quality.csv",
                ".training.json",
                ".summary.json",
            }) {
                File f = new File(dir, id + suffix);
                if (
                    !f.isFile() ||
                    f.length() == 0 ||
                    !f.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile())
                ) throw new IOException("Missing complete training file: " + suffix);
            }
            JSONObject p = json(new File(dir, id + ".training.json"));
            if (
                !"device".equals(p.optString("source")) ||
                p.optInt("schemaVersion") != 1 ||
                !id.equals(p.optString("sessionId"))
            ) throw new IOException("Recording provenance mismatch");
            if(p.has("rawSidecarsSchemaVersion")) {
                if(p.optInt("rawSidecarsSchemaVersion")!=1)throw new IOException("Unsupported raw sidecars schema");
                for(String suffix:new String[]{".sensors.csv",".gnss.csv"}) {
                    File f=new File(dir,id+suffix);
                    if(!f.isFile()||f.length()==0||!f.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile()))throw new IOException("Missing raw training sidecar: "+suffix);
                    try(BufferedReader reader=Files.newBufferedReader(f.toPath(),StandardCharsets.UTF_8)) {
                        String expected=suffix.equals(".sensors.csv")?com.idr.navigator.engine.RawTrainingWriter.SENSOR_HEADER:com.idr.navigator.engine.RawTrainingWriter.GNSS_HEADER;
                        if(!expected.equals(reader.readLine()))throw new IOException("Invalid raw training header: "+suffix);
                    }
                }
            }
            File raw = new File(dir, id + ".drlog");
            byte[] header = new byte[200];
            try (DataInputStream in = new DataInputStream(new FileInputStream(raw))) {
                in.readFully(header);
            }
            int rate = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getShort(6) & 65535;
            if (
                !"DRLOG".equals(new String(header, 0, 5, StandardCharsets.US_ASCII)) ||
                header[5] != 1 ||
                (rate != 100 && rate != 200) ||
                (raw.length() - 200) % 70 != 0 ||
                !id.equals(
                    new String(header, 136, 64, StandardCharsets.UTF_8).split("\u0000", 2)[0]
                )
            ) throw new IOException("Invalid DRLOG framing or identity");
            verifyQuality(raw, new File(dir, id + ".quality.csv"));
            stage.mkdirs();
            JSONObject summary = json(new File(dir, id + ".summary.json")),
                clean = new JSONObject();
            for (String key : new String[] {
                "durationSeconds",
                "distanceMeters",
                "phoneModel",
                "sizeBytes",
            })
                if (summary.has(key)) clean.put(key, summary.get(key));
            File portable = new File(stage, "summary-source.json");
            save(portable, clean);
            JSONArray files = new JSONArray();
            for (String suffix : new String[] {
                ".drlog",
                ".quality.csv",
                ".training.json",
                ".summary.json",
                ".fusion.csv",
                ".sensors.csv",
                ".gnss.csv",
            }) {
                File f = suffix.equals(".summary.json") ? portable : new File(dir, id + suffix);
                if (!f.exists()) continue;
                JSONArray parts = new JSONArray();
                int index = 0;
                try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
                    byte[] buffer = new byte[65536];
                    boolean end = false;
                    while (!end) {
                        String name =
                            id + suffix + String.format(Locale.ROOT, ".part%05d", index++);
                        File part = new File(stage, name);
                        long size = 0;
                        try (
                            OutputStream out = new BufferedOutputStream(new FileOutputStream(part))
                        ) {
                            while (size < chunkBytes) {
                                int n = in.read(
                                    buffer,
                                    0,
                                    (int) Math.min(buffer.length, chunkBytes - size)
                                );
                                if (n < 0) {
                                    end = true;
                                    break;
                                }
                                out.write(buffer, 0, n);
                                size += n;
                            }
                        }
                        if (size == 0) {
                            Files.delete(part.toPath());
                            break;
                        }
                        parts.put(
                            new JSONObject()
                                .put("objectName", name)
                                .put("sizeBytes", size)
                                .put("sha256", hash(part))
                        );
                    }
                }
                files.put(
                    new JSONObject()
                        .put("originalName", id + suffix)
                        .put("sizeBytes", f.length())
                        .put("sha256", hash(f))
                        .put("chunks", parts)
                );
            }
            Files.delete(portable.toPath());
            JSONObject manifest = new JSONObject()
                .put("schemaVersion", 1)
                .put("source", "device")
                .put("sessionId", id)
                .put("files", files);
            if(p.has("rawSidecarsSchemaVersion"))manifest.put("rawSidecarsSchemaVersion",1);
            save(new File(stage, "complete.json"), manifest);
            return manifest;
        } catch (JSONException e) {
            throw new IOException("Invalid training metadata", e);
        }
    }

    private static void verifyQuality(File raw, File quality) throws IOException {
        try (
            RandomAccessFile records = new RandomAccessFile(raw, "r");
            BufferedReader rows = new BufferedReader(
                new InputStreamReader(new FileInputStream(quality), StandardCharsets.UTF_8)
            )
        ) {
            if (
                !"t_ns,fix_t_ns,speed_accuracy_mps,gyro_fresh".equals(rows.readLine())
            ) throw new IOException("Invalid quality header");
            records.seek(200);
            byte[] record = new byte[70];
            String line = rows.readLine();
            long previous = 0;
            while (records.getFilePointer() < records.length()) {
                records.readFully(record);
                long time = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN).getLong();
                if (time <= previous) throw new IOException("Non-monotonic recording");
                previous = time;
                // Quality can include sensor events omitted by the DRLOG sample-rate limiter.
                while (line != null && qualityTime(line) < time) line = rows.readLine();
                if (line == null || qualityTime(line) != time) throw new IOException(
                    "Incomplete sensor quality; originals retained"
                );
                line = rows.readLine();
            }
        }
    }

    private static long qualityTime(String row) throws IOException {
        try {
            String[] c = row.split(",", -1);
            if (c.length != 4 || (!c[3].equals("0") && !c[3].equals("1"))) throw new Exception();
            Long.parseLong(c[1]);
            Double.parseDouble(c[2]);
            return Long.parseLong(c[0]);
        } catch (Exception e) {
            throw new IOException("Malformed quality row");
        }
    }
}
