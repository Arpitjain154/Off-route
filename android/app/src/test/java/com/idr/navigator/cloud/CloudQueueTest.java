package com.idr.navigator.cloud;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;
import org.json.*;
import org.junit.Test;

public class CloudQueueTest {

    @Test
    public void persistentQueueDeduplicatesAndIsolatesAccounts() throws Exception {
        File dir = Files.createTempDirectory("queue").toFile();
        CloudQueueStore q = new CloudQueueStore(dir);
        q.enqueue("p", "alice", "12345678-1234-1234-1234-123456789abc", 7);
        q.enqueue("p", "alice", "12345678-1234-1234-1234-123456789abc", 7);
        assertEquals(1, new CloudQueueStore(dir).list("p", "alice").length());
        assertEquals(0, q.list("p", "bob").length());
        JSONObject item = q.list("p", "alice").getJSONObject(0);
        q.status(item, "uploading", "");
        q.progress(item, "part1", "abc");
        JSONObject restored = new CloudQueueStore(dir).list("p", "alice").getJSONObject(0);
        assertEquals("abc", restored.getJSONObject("progress").getString("part1"));
        assertEquals("uploading", restored.getString("state"));
    }

    @Test
    public void ownershipCannotBeChangedByRetry() throws Exception {
        CloudQueueStore q = new CloudQueueStore(Files.createTempDirectory("queue").toFile());
        String id = "12345678-1234-1234-1234-123456789abc";
        q.enqueue("p", "alice", id, 1);
        assertThrows(IOException.class, () -> q.enqueue("p", "bob", id, 2));
    }

    @Test
    public void publicKeyAndHttpsRequired() throws Exception {
        assertThrows(IOException.class, () ->
            new CloudConfig("http://example.com", "sb_publishable_x", "journeys", "model-releases")
        );
        assertThrows(IOException.class, () ->
            new CloudConfig(
                "https://example.supabase.co",
                "sb_secret_x",
                "journeys",
                "model-releases"
            )
        );
        assertThrows(IOException.class, () ->
            new CloudConfig("https://example.supabase.co", "anything", "../other", "models")
        );
    }

    @Test
    public void requestGuardRejectsStaleIdentityAndRevokedConsent() {
        assertTrue(CloudConfig.authorized("a", "a", 4, 4, true));
        assertFalse(CloudConfig.authorized("a", "b", 4, 4, true));
        assertFalse(CloudConfig.authorized("a", "a", 4, 5, true));
        assertFalse(CloudConfig.authorized("a", "a", 4, 4, false));
    }

    @Test
    public void reconciliationDoesNotEraseActionRequiredState() throws Exception {
        CloudQueueStore q = new CloudQueueStore(Files.createTempDirectory("queue").toFile());
        String id = "12345678-1234-1234-1234-123456789abc";
        JSONObject j = q.enqueue("p", "a", id, 1);
        q.status(j, "action-required", "Missing quality");
        q.authorizeCompleted("p", "a", id, 1);
        assertEquals("action-required", q.list("p", "a").getJSONObject(0).getString("state"));
    }
}
