# Follow-up: Steps 1 and 2

Step 1 was attempted and reverted: enabling Stage A fails the existing sidewaysMountUsesVehicleDirectionForEskfAcceleration test. All Stage A aggregate metrics reproduced exactly (maximum difference 0.0); legacy median drift remained 70.585%. Individual-window regressions were not the rejection reason. Checkpoint: ac3c1d42.

After rollback: Android 128 passed / 1 skipped with replay enabled; Python 46 passed; debug APK build succeeded. APK: android/app/build/outputs/apk/debug/app-debug.apk. Version unchanged. Stage C remains absent and bundled AI speed remains unvalidated and gated off.

Step 2 adds only AiSpeedAdvisor.java to the edge/build.gradle source allowlist. Edge build succeeded. Its Gradle test task reports NO-SOURCE; six compatible existing engine test classes were compiled against the built JAR and all 39 JUnit tests passed. See step-2-edge.log and step-2-jar-tests.log.

JAR: edge/build/libs/idr-edge-engine-1.5.0-qa.jar. Step 3 has not started. Android production source is unchanged from baseline.
