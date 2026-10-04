package com.idr.navigator.engine;

import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Test;

public class LaptopTrialProvenanceTest {
    @Test public void ordinaryAndTrialRecordingsHaveDistinctProvenance() throws Exception {
        Path dir=Files.createTempDirectory("laptop-trial-test");
        String normal=UUID.randomUUID().toString(), trial=UUID.randomUUID().toString(), hash="a".repeat(64);
        try {
            try(TrainingQualityWriter writer=new TrainingQualityWriter(dir.toFile(),normal)) { writer.write(1,1,.2,true); }
            try(TrainingQualityWriter writer=new TrainingQualityWriter(dir.toFile(),trial,hash)) { writer.write(1,1,.2,true); }
            assertFalse(new JSONObject(new String(Files.readAllBytes(dir.resolve(normal+".training.json")),StandardCharsets.UTF_8)).has("candidateTrialSha256"));
            assertEquals(hash,new JSONObject(new String(Files.readAllBytes(dir.resolve(trial+".training.json")),StandardCharsets.UTF_8)).getString("candidateTrialSha256"));
            assertThrows(java.io.IOException.class,()->new TrainingQualityWriter(dir.toFile(),UUID.randomUUID().toString(),"bad-hash"));
        } finally {
            try(var files=Files.list(dir)) { for(Path p:files.toList())Files.delete(p); }
            Files.delete(dir);
        }
    }
}
