package com.idr.navigator;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.idr.navigator.engine.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Execute with a connected Android phone: gradlew connectedDebugAndroidTest. */
@RunWith(AndroidJUnit4.class)
public class NativePipelineTest {

    @Test
    public void allBundledModelsLoadAndInfer() throws Exception {
        try (
            TfliteModels models = new TfliteModels(
                InstrumentationRegistry.getInstrumentation().getTargetContext()
            )
        ) {
            FeatureBuffer features = new FeatureBuffer();
            for (int i = 0; i < 320; i++) features.add(
                new ImuSample(1_000_000_000L + i * 10_000_000L, 0, 0, 9.80665, 0, 0, 0)
            );
            assertTrue(features.ready());
            assertEquals(5, models.motion(features).length);
            assertEquals(3, models.health(new float[10]).length);
            assertTrue(Float.isFinite(models.speed(features)));
        }
    }
}
