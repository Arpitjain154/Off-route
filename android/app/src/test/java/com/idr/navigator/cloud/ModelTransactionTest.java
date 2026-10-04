package com.idr.navigator.cloud;

import static org.junit.Assert.*;

import com.idr.navigator.engine.ModelTransaction;
import java.io.*;
import java.nio.file.*;
import org.json.*;
import org.junit.Test;

public class ModelTransactionTest {

    @Test public void changedDownloadCannotBecomeInstallerCandidate() throws Exception {
        File dir=Files.createTempDirectory("stage").toFile(),source=new File(dir,"download"),stage=new File(dir,"owned");
        Files.write(source.toPath(),new byte[]{1});
        String expected=com.idr.navigator.cloud.TripPackage.hash(source);
        Files.write(source.toPath(),new byte[]{2});
        assertThrows(IOException.class,()->ModelTransaction.stage(source,stage,expected));
    }

    @Test
    public void failedMetadataCommitRestoresBinaryAndMetadata() throws Exception {
        File dir = Files.createTempDirectory("models").toFile();
        Files.write(new File(dir, "speed.tflite").toPath(), new byte[] { 1 });
        File staged = new File(dir, "new");
        Files.write(staged.toPath(), new byte[] { 2 });
        JSONObject[] meta = { new JSONObject().put("version", "1.0.0") };
        ModelTransaction tx = new ModelTransaction(
            dir,
            new ModelTransaction.Metadata() {
                public JSONObject read(String n) {
                    return meta[0];
                }

                public void write(String n, JSONObject v) throws Exception {
                    if ("2.0.0".equals(v.getString("version"))) throw new IOException("disk full");
                    meta[0] = v;
                }
            }
        );
        assertThrows(IOException.class, () ->
            tx.install("speed", staged, new JSONObject().put("version", "2.0.0"))
        );
        assertArrayEquals(
            new byte[] { 1 },
            Files.readAllBytes(new File(dir, "speed.tflite").toPath())
        );
        assertEquals("1.0.0", meta[0].getString("version"));
        assertFalse(new File(dir, "install-journal.json").exists());
    }
}
