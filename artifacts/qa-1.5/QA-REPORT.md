# IDR Navigator 1.5 QA — not approved for deployment

Updated 17 September 2026. The accepted 1.4 APK is preserved. A build labelled `1.5.0-qa` is a development candidate, not a claim of bug-free navigation.

## What changed

- GNSS loss can now use the aligned accelerometer/gyro/compass estimator to continue position and speed. Forward acceleration and braking are integrated in both directions. A qualified stop stays stopped. Missing or unaligned sensors remain explicitly unavailable; the application does not manufacture movement.
- The actual IO-VNBD motion, GPS-health and speed models feed the live pipeline. Motion/health predictions adjust process noise; speed correction requires demonstrated agreement across changing GNSS speeds. A constant or inaccurate model cannot earn trust merely from repeated observations at one speed. This is not equivalent to validated AI positioning.
- Sensor features are resampled to a fixed timestamp grid. Irregular device callbacks previously extended the intended three-second window or repeatedly reset it, preventing reliable inference.
- Cached zero GNSS speed could incorrectly train an accelerometer bias while the phone was accelerating. Bias learning now requires sustained quiet sensors. Remounting clears all calibration statistics. Invalid optional model/map output is rejected.
- The offline matcher consumes the live estimated position and applies bounded cross-track corrections. It does not invent along-road velocity or guarantee the selected road is correct.
- GPS recovery gradually reconciles position and speed. Healthy GNSS uses receiver speed/course, retaining the stationary filter rather than estimating turn speed from a straight chord.
- The map includes named roads and offline place labels using local text rendering and collision handling. External web-font loading was removed to avoid delaying offline startup.
- Route progress now provides the upcoming turn, remaining distance and an offline travel-time estimate. Traffic, lane guidance and Google Maps equivalence are not claimed.
- `edge/build/distributions/idr-edge-engine-1.5.0-qa.zip` packages the shared Java engine without Android classes. External hosts provide their sensor adapter, model runtime and road matcher.

## Test evidence

| Area | Result and limits |
|---|---|
| Native regression suite | Final rerun: 46 passed, 2 opt-in legacy dataset/graph-build tests skipped, zero failures. Covers alignment, invalid fixes and optional model/map output, stationary noise, bidirectional speed, turns, recovery, routing access restrictions, map matching, timestamp resampling, feature parity and 200 Hz input. The replay-report test checks finite output/report generation; its accuracy gate separately fails below. |
| Route progress and rerouting | Five JavaScript tests pass, including advancing ETA/distance, next turn, sustained distinct fixes, cooldown and uncertain sensor-estimate rejection. |
| Offline maps | Two geometry tests pass across all Bhopal zooms and six Madhya Pradesh cities. Map and POI labels visibly render in browser screenshots. Exact unknown road names cannot be supplied where OSM has no name. |
| Destination search | Browser test passes both offline autocomplete fields, car/motorcycle selection, swap, invalid input, close, Escape, themes, drive list and return to navigation without JavaScript errors. Android route confirmation, live marker, real offline rerouting, 90°/180° GPS course, turn speed and GPS recovery PASS in 60.006 seconds. The initial test mixed emulator hardware GPS with injected fixes; isolating the scenario inputs resolved that test conflict. See `NavigationBehaviorTest.log`. |
| Backend | All three API tests pass with local MongoDB: authentication, uploads, metadata, retry idempotency, filtering, model/report permissions and invalid-file rejection. The first attempt failed because MongoDB was not running; it was started and the suite rerun. |
| Android lifecycle and outage soak | PASS on Android 16 emulator, 390.439 seconds including setup: three outages, acceleration/braking, turns, degraded fixes, recovery, stationary stops, three background/foreground cycles, one location registration and actual TFLite inference. No engine restart or crash. See `InertialWorkflowTest.log` and `inertial-workflow.csv`. This is controlled input, not a physical accuracy test. |
| Android startup, tabs and recreation | PASS in 58.334 seconds: tab returns and three activity recreations retain one GPS watch; service stop/restart remains stable; statewide graph loads with 96 MB additional heap pressure; diagnostic export and original Java-keyword crash regression pass. The separate optional ten-minute GPS-only soak was skipped; the six-minute multi-outage soak above ran. See `EngineStartupTest.log`. |
| Physical Samsung validation | Phone is connected, but the new QA build has not been installed. No new physical walking/driving, tunnel or independent-reference accuracy pass is claimed. The earlier 1.4 stationary results are historical, not 1.5 results. |
| Specific Patanjali/RGPV obstruction | Exact gate/steps location remains unknown. Vehicle access/footpath/barrier fixture tests pass, but the specific physical passage is not certified. |
| Standalone edge distribution | Java library and dependency distribution build successfully. Native simulation exercises 200 Hz timestamps; external hardware accuracy is untested. |

## Accuracy gate: failed

`iovnbd/accuracy-gate.json` evaluates all 15 injected 30-second outages across three reused IO-VNBD Driver A recordings. **0/15 meet <10% relative endpoint drift.** Median drift is approximately 77.5%; the largest percentage occurs on a short reference trajectory. The complete numbers and all trajectories are retained, including failures:

- `iovnbd/drift-summary.csv`
- `iovnbd/outage-trajectories.png` and `.svg`
- `iovnbd/S-S1-positions.csv`, `S-S2-positions.csv`, `S-S3a-positions.csv`

The recordings are regression data previously evaluated, not a new blind test set. Phone GNSS fields repeat for approximately nine seconds in these files. Missing accuracy fields are assumed for replay. Gyro axis labels/source-frame mapping remain uncertain. Vehicle GNSS is the reference, not surveyed truth. No UK graph was loaded, so these figures test the unaided estimator rather than the complete map-constrained system. The uncertainty model is not calibrated against empirical error coverage.

The live speed model's trust gate did not enable speed corrections in these replay windows. Claiming the gate is active on every device, or that the model improves this benchmark, would be incorrect.

## Retraining outcome

A new speed candidate was trained for 16 epochs with a stationary-weighted objective. It was **not promoted**: validation MAE worsened from about 5.109 to 5.629 m/s, despite some stationary improvements. The existing shipped models remain unchanged. See `speed-candidate/report.json` and `promotion-decision.json` for metrics and the candidate checksum.

## Diagnostic from the connected phone

The latest saved error was a handled destination-routing rejection, not evidence of a new process crash:

```text
java.lang.IllegalArgumentException: This place is not close enough to an accessible vehicle road. Select its vehicle entrance or a nearby road.
    at com.idr.navigator.engine.OfflineDirections.route(OfflineDirections.java:45)
    at com.idr.navigator.plugins.DirectionsPlugin.lambda$route$0(DirectionsPlugin.java:25)
```

## Acceptance still required

Resolve the recorded sensor-frame/calibration and drift failures; demonstrate useful calibrated model corrections on held-out trajectories; measure physical stationary, walking/driving, turns, loss and recovery using an independent reference; verify the actual blocked RGPV passage. Until those pass, this is not the requested deployable, lane-accurate final build.

## QA package

`IDR-Navigator-1.5.0-qa.apk` is retained here for controlled testing, not recommended as the accepted release. SHA-256: `5e89aef7c26ac4b7a096536b2ccd3e4f5671fce95ccb436883714acf33725879`; size 221,174,279 bytes. Verified: signature, 16 KB native-library alignment, current UI, 39,285 statewide map tiles, eight graph files and all three model checksums. Only the emulator received this build; the phone was not upgraded.
