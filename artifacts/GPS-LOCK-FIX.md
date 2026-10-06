# Live Navigation 1.3.2 — automatic startup and GPS-lock crash

## Actual phone error

Captured from the connected Samsung F15 (`SM-E156B`) on Android 16 before it disconnected. Repeated fatal exceptions include 2026-09-15 01:17:45:

```text
FATAL EXCEPTION: pool-10-thread-1
Process: com.idr.navigator
java.lang.NoClassDefFoundError: Failed resolution of: Ljavax/lang/model/SourceVersion;
    at com.graphhopper.routing.ev.IntEncodedValueImpl.isValidEncodedValue(IntEncodedValueImpl.java:237)
    at com.graphhopper.routing.ev.IntEncodedValueImpl.<init>(IntEncodedValueImpl.java:74)
    at com.graphhopper.util.EdgeIteratorState.<clinit>(EdgeIteratorState.java:43)
    ...
    at com.graphhopper.matching.MapMatching.match(MapMatching.java:205)
    at com.idr.navigator.engine.MapMatchAdvisor.observe(MapMatchAdvisor.java:74)
    at com.idr.navigator.engine.SensorCaptureService$2.lambda$run$0(SensorCaptureService.java:383)
Caused by: java.lang.ClassNotFoundException: Didn't find class "javax.lang.model.SourceVersion"
```

Full IDR-only crash blocks: `gps-lock-samsung-crash.txt`. Android exit history: `gps-lock-samsung-exits.txt`. The 29.25-second supplied recording was inspected at acquisition, GPS-healthy/heading-pending, and return-to-launcher frames.

The callback begins collecting road observations after the first GPS fix. On its fourth observation, GraphHopper starts matching and initializes an edge-encoding class that calls the unavailable Java compiler API. The road worker caught RuntimeException but not this LinkageError, so the process terminated. This is distinct from the startup-memory problem fixed in 1.3.1 and is not a null-heading diagnosis.

## Fix

- A narrow Android build transform redirects GraphHopper 7's single `SourceVersion.isKeyword(CharSequence)` call to an Android-compatible reserved-keyword check. The transform fails the build if the expected call count changes. Java keyword validation and the existing statewide graph format are preserved.
- Road-worker failures now record a stack trace and disable the optional map advisor without killing GNSS/INS tracking.
- Native startup runs automatically on app launch, return from background, and tab changes. A shared pending start promise coalesces permission/start requests; Android reuses the existing service. No manual Start engine button or ready-for-next-drive prompt remains in the native UI.
- Location permission is requested automatically when missing. Denial is shown inline with a link to Android permissions; returning after a grant retries startup.
- Coordinate and heading validation guards the map boundary. Missing heading keeps a north-up map while alignment is pending. Camera updates are bounded to 10 Hz, with map exceptions logged to the local engine diagnostic report.
- GPS acquisition, first lock and heading-established transitions are logged along with service generation and registration count for duplicate-listener checks.

## Validation

Native JVM suite: 23 passed, two opt-in tests skipped. Map-boundary tests: three passed, covering missing/null/non-finite heading, invalid coordinates and heading wrapping.

Android 16 emulator, x86_64:

- The road-encoding regression fails against 1.3.1 with the same `NoClassDefFoundError` and passes against 1.3.2. Evidence: `gps-lock-before-instrumentation.txt` and `gps-lock-after-instrumentation.txt`.
- With location permission revoked, launching the app automatically opens Android's precise-location prompt. Accepting "While using the app" starts the foreground tracking service without another tap. Evidence: `gps-lock-auto-permission.xml` and `gps-lock-permission-launch-log.txt`.
- Ten-minute continuous tracking passed (`Time: 606.56`, `OK (1 test)`). It covered acquisition, GPS lock with missing heading, heading establishment, active movement and a stationary phase. Road matching remained active and the original service retained exactly one location registration during repeated Settings/Navigate and background/foreground cycles. Evidence: `gps-lock-soak-emulator.txt`, `gps-lock-soak-logcat.txt`, and `gps-lock-tracking-emulator.png`.
- All three tabs (My drives, Settings, Navigate) were selected and verified across three cycles, with three Android activity recreations. Each retained the same service and one location registration. The ten-minute run separately completed 18 tab/background cycles.
- Five additional Android tests passed (`Time: 33.271`): tab/recreation lifecycle, diagnostics export, statewide graph under 96 MB additional heap pressure, three service start/stop cycles, and all bundled TFLite models loading and executing. Evidence: `gps-lock-lifecycle-emulator.txt`. In total, seven Android tests passed across the three runs.
- Two force-stop/relaunch cycles created distinct app processes and automatically started acquisition with one location registration, with no engine button tap. Evidence: `gps-lock-process-relaunch.json`.
- The test injects locations into the real native service while the real WebView, map, road matcher and sensors run. It does not simulate a real satellite receiver or certify location accuracy.

APK: `IDR-Navigator-1.3.2-auto-gps-fix.apk`, versionCode 6, 218,732,456 bytes. SHA-256: `3c37edc20adcf21dcc5929ae171c3c6099493c1eadd3a4ba4ef979605007e29a`. All 39,285 statewide tiles, seven graph files, three model hashes and current UI are verified inside the APK. Signature and 16 KB ZIP/native-library alignment checks pass. This APK uses the existing debug signing identity and can update compatible previous field-test builds; it is not a signed Play Store release.

Install this APK over the previous build without clearing app data. Open Live Navigation and accept precise location if prompted. GPS acquisition starts automatically; heading remains pending until the accepted location/motion observations support it. If a new problem occurs, use Settings to export engine diagnostics.

## Physical Samsung verification — September 15

The authorized USB phone identifies as `SM-S711B` on Android 16, a different Samsung from the F15 that supplied the original crash trace. Version 1.3.2 was installed over the existing app without clearing data. Opening the app automatically acquired real GPS at 03:11:01; the map displayed GPS Healthy with heading pending and zero displayed speed.

**An uninterrupted 600.137-second real-GPS run passed from 03:17:33 to 03:27:33 IST.** All 39 sampled snapshots reported GPS Healthy, active Madhya Pradesh road matching, service generation 1 and one GPS registration. The app retained PID 12227 throughout nine full My drives/Settings/Navigate cycles and three home-screen/background returns. No injected locations were used. GPS fix age remained below one second at sample times. Receiver-reported accuracy ranged from 3.5 to 9.5 metres; this is the receiver estimate, not measured position error. Speed was zero in 37 of 39 samples; the two nonzero samples were at most 0.00595 m/s (about 0.021 km/h), which still displays as 0 km/h. No engine or fatal errors appeared in the monitored process log, and exit history contains no app exit during this run.

After the run, a deliberate force-stop/relaunch created PID 17309; GPS acquisition automatically restarted with one registration and reached GPS lock again within seconds. The app was left open. Evidence: `gps-lock-physical-monitor.json`, `gps-lock-physical-monitor.log`, `gps-lock-physical-live-logcat.txt`, `gps-lock-physical-exits-after.txt`, `gps-lock-physical-relaunch.txt`, and `gps-lock-physical-final.png`. The repeatable read-only-sensor monitor is `scripts/monitor-android-gps.mjs`.

The phone remained stationary, so vehicle heading appropriately remained pending. Physical moving-vehicle heading and navigation accuracy are not validated by this test; the full heading-established transition was tested separately using controlled Android emulator inputs.

The first monitoring attempt lost its USB connection after 93 seconds; Android later recorded a force-stop at 03:16:22 with description `stop com.idr.navigator due to set debug app`. This is retained in `gps-lock-physical-exits.txt` and `gps-lock-physical-monitor-interrupted.json`, and is not counted as a completed stability test. The old saved `engine-last-error.txt` on the phone dates to 01:25 and contains the original missing-class crash; it was not a new 1.3.2 failure and was preserved.

Upstream source: [GraphHopper 7 IntEncodedValueImpl](https://github.com/graphhopper/graphhopper/blob/7.0/core/src/main/java/com/graphhopper/routing/ev/IntEncodedValueImpl.java).

