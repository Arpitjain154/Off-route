package com.idr.navigator.cloud;

import static org.junit.Assert.*;

import java.io.*;
import org.json.*;
import org.junit.Test;

public class ModelReleaseTest {

    private JSONObject release() throws Exception {
        return new JSONObject()
            .put("schemaVersion", 1)
            .put("name", "speed")
            .put("version", "1.2.0")
            .put("approved", true)
            .put("validated", true)
            .put("minAppVersionCode", 12)
            .put("featureContract", "idr-iovnbd-v1")
            .put("dtype", "float32")
            .put("inputShape", new JSONArray("[1,300,6]"))
            .put("outputShape", new JSONArray("[1,1]"))
            .put("path", "speed/1.2.0/speed.tflite")
            .put("sizeBytes", 31000)
            .put("sha256", new String(new char[64]).replace('\0', 'a'));
    }

    @Test
    public void acceptsCompatibleApprovedRelease() throws Exception {
        assertEquals("speed", ModelRelease.parse(release(), "1.1.0", 12).name);
    }

    @Test
    public void rejectsUnapprovedDowngradeWrongShapeAndOrigin() throws Exception {
        assertThrows(IOException.class, () ->
            ModelRelease.parse(release().put("approved", false), "1.1.0", 12)
        );
        assertThrows(IOException.class, () -> ModelRelease.parse(release(), "1.3.0", 12));
        assertThrows(IOException.class, () ->
            ModelRelease.parse(release().put("inputShape", new JSONArray("[1,42]")), "1.1.0", 12)
        );
        assertThrows(IOException.class, () ->
            ModelRelease.parse(release().put("path", "https://evil/model"), "1.1.0", 12)
        );
        assertThrows(IOException.class, () ->
            ModelRelease.parse(release().put("featureContract", "other"), "1.1.0", 12)
        );
    }
}
