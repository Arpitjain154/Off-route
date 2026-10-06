package com.idr.navigator.engine;

import com.idr.navigator.cloud.TripPackage;
import java.io.*;
import java.nio.file.*;
import org.json.*;

/** Journal survives process death; recovery rolls back both file and metadata. */
public final class ModelTransaction {
    public static void stage(File source,File staged,String expectedHash)throws IOException {
        Files.copy(source.toPath(),staged.toPath(),StandardCopyOption.REPLACE_EXISTING);
        if(!expectedHash.equals(TripPackage.hash(staged)))throw new IOException("Staged model checksum mismatch");
    }

    public interface Metadata {
        JSONObject read(String name) throws Exception;
        void write(String name, JSONObject value) throws Exception;
    }

    private final File dir;
    private final Metadata metadata;

    public ModelTransaction(File dir, Metadata metadata) {
        this.dir = dir;
        this.metadata = metadata;
        dir.mkdirs();
    }

    public void recover() throws Exception {
        File journal = new File(dir, "install-journal.json");
        if (!journal.exists()) return;
        JSONObject j = TripPackage.json(journal);
        String n = j.getString("name");
        valid(n);
        File dest = new File(dir, n + ".tflite"),
            backup = new File(dir, n + ".previous");
        if (j.getBoolean("hadFile")) {
            if (
                !backup.isFile() || !TripPackage.hash(backup).equals(j.getString("oldSha256"))
            ) throw new IOException("Model backup unavailable; recovery required");
            File restore = new File(dir, n + ".restore");
            Files.copy(backup.toPath(), restore.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.move(
                restore.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        } else Files.deleteIfExists(dest.toPath());
        metadata.write(n, j.getJSONObject("oldMetadata"));
        Files.delete(journal.toPath());
    }

    public void install(String n, File staged, JSONObject next) throws Exception {
        valid(n);
        recover();
        File dest = new File(dir, n + ".tflite"),
            backup = new File(dir, n + ".previous"),
            journal = new File(dir, "install-journal.json");
        JSONObject old = metadata.read(n);
        if (dest.exists()) Files.copy(
            dest.toPath(),
            backup.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        );
        JSONObject j = new JSONObject()
            .put("name", n)
            .put("hadFile", dest.exists())
            .put("oldMetadata", old)
            .put("oldSha256", dest.exists() ? TripPackage.hash(dest) : "");
        TripPackage.save(journal, j);
        TripPackage.save(new File(dir, n + ".previous.json"), old);
        try {
            Files.move(
                staged.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
            metadata.write(n, next);
            Files.delete(journal.toPath());
        } catch (Exception e) {
            try {
                recover();
            } catch (Exception failure) {
                e.addSuppressed(failure);
            }
            throw e;
        }
    }

    private static void valid(String n) throws IOException {
        if (
            !java.util.Arrays.asList("speed", "motion", "gps-health").contains(n)
        ) throw new IOException("Unknown model");
    }
}
