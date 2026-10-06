# Main-app 1.3 development status

**September 15 navigation update — 1.4.0:** vehicle routing now uses rebuilt car/motorcycle profiles with conservative barriers and turn restrictions. Live navigation follows accepted GPS positions, reroutes after sustained deviation, uses qualified course/compass heading, suppresses confirmed stationary jitter and shows unavailable speed during GPS loss. Public navigation no longer exposes experimental INS position/speed during outages. Five-minute real-GPS stationary/lifecycle/outage testing passed on the connected Samsung, and controlled Android movement/rerouting/turn tests passed. The specific Patanjali obstruction and physical moving-heading accuracy remain unverified. See [navigation fixes, evidence and limitations](NAVIGATION-FIXES.md). Historical fusion descriptions below describe earlier releases.

**September 15 destination-search update:** version 1.3.3 is available as `IDR-Navigator-1.3.3-destination-search.apk` and installed on the connected Samsung. Live Navigation now has a destination search bar, editable current-GPS origin, two-field offline autocomplete, swap, cancel and local road-route plotting. Final phone tests pass, including routing from live GPS, three tab cycles and a background return with one GPS listener; real keyboard selection was checked. The existing GPS/fusion flow and other screen sections are preserved. See [destination search implementation and verification](DESTINATION-SEARCH.md). This UI/routing update does not change the model or moving-navigation accuracy limitations below.

**September 15 update:** patch 1.3.2 is available as `IDR-Navigator-1.3.2-auto-gps-fix.apk`. It automatically starts GPS and fixes the Samsung GPS-lock crash caused by GraphHopper calling an unavailable Java compiler class. Seven Android 16 emulator tests passed, including ten minutes of tracking, one GPS registration across lifecycle changes, and bundled-model inference. Automatic precise-location prompting and process relaunch were checked separately. A Samsung SM-S711B on Android 16 also passed ten uninterrupted minutes on real GPS, including nine tab cycles, three background returns, active road matching and one registration throughout; automatic GPS startup after process relaunch passed. The phone was stationary, so physical vehicle-heading and driving accuracy remain unvalidated. See [GPS-lock fix, actual stack trace and verification](GPS-LOCK-FIX.md). The navigation-accuracy/model limitations below still apply.

**September 14 update:** startup patch 1.3.1 fixed a reproduced statewide-graph memory crash and passed four Android 16 emulator tests. See [startup crash fix](STARTUP-CRASH-FIX.md) for historical validation; the 1.3 details below are also historical.

This is a field-test build with substantial implementation and evaluation improvements. It is not yet a validated GNSS/INS navigation release. The earlier 1.2 judge prototype is preserved as `IDR-Navigator-debug.apk`; the updated build is `IDR-Navigator-1.3-field-test.apk`.

## Fusion changes

Android capture and offline replay now share `NavigationFusion.java`, which owns the same single ESKF and its GNSS/physics advisors. Position, displayed speed and drive distance use that filter. GNSS velocity and uncertainty, continuous course corrections, gravity-based tilt observations, stop constraints and bounded bias corrections update this one engine.

Rejected, stale, out-of-order and non-finite samples cannot masquerade as fresh accepted GNSS. Missing gyro data is surfaced and cannot silently become an invented zero angular rate. Reliable accepted position changes can establish velocity/course when the device omits speed-accuracy metadata. Delayed GNSS positions are propagated to the current epoch only when measured velocity/course support it. Three consistent accurate fixes can recover a grossly diverged error prior. A large abrupt gravity-direction change invalidates the mount heading for reacquisition.

Map matching remains independent of ML with sigma 30 m. Worker results apply their correction relative to the observation's original pose, avoiding an old absolute endpoint pulling the car backward. Position/gyro-bias stop constraints and gravity updates are driven by the native sensor timeline, not UI polling. Model feature windows reset when samples are missing.

## Actual training

Reused the verified 144 synchronized IO-VNBD CSVs. Training/validation remain recording-disjoint; the previously evaluated Driver A set is described as a regression set, not a fresh blind test. No labels were invented for potholes or phone movement.

| Metric | Previous model | Refined model |
|---|---:|---:|
| Speed validation MAE, m/s | 7.098 | 5.109 |
| Motion validation balanced accuracy | 55.35% | 61.29% |
| GPS proxy validation balanced accuracy | 33.19% | 32.13% |
| Speed Driver A regression MAE, m/s | 4.088 | 3.722 |
| Motion Driver A regression balanced accuracy | 63.81% | 68.30% |

Speed and motion were bundled as 1.1.0 based on validation improvements. GPS-health remains 1.0.0 because the candidate regressed. The native tensor contracts and TFLite numerical parity were verified. Learned corrections remain disabled: speed still has substantial stationary outliers, and the GNSS classifier's receiver-agreement proxy is not an adequate blackout detector. Full results, hashes and promotion decisions are under `artifacts/ml/refinement/`.

## Validation and limits

Final 1.3 debug APK: 220,570,105 bytes, versionCode 4; SHA-256 `5c31b19541b89f636764377978fdebd1bc14ed633a0df1d52f12a1584d1f93f3`. APK signature verification passed. Both 64-bit TFLite native libraries have 16,384-byte ELF load alignment; APK ZIP alignment verification passed. All 39,285 statewide tiles, seven graph files, three model hashes and current UI were verified inside the APK. Native suite: 23 passed, one optional statewide graph rebuild skipped. Three API integration tests passed. TypeScript/production UI build passed. The on-device instrumentation APK compiled but was not executed.

The noisy synthetic turning-vehicle test includes accelerometer/gyro noise, biases, a 30-second GNSS outage and GNSS recovery. It is a mathematical regression, not evidence of real tunnel accuracy. Its JUnit output records actual error values. Native tests also cover stop behaviour, invalid timestamps, heading wraparound, gravity tilt correction, missing speed accuracy, stale gyro, recovery, feature parity and model-version ordering.

The real-recording replay runs three ten-minute phone traces against vehicle GNSS used only as an evaluation reference. Results are in `fusion-replay.csv`. **Errors remain unacceptable, including hundreds of metres and larger excursions. Passing the replay test means finite states/covariances and successful execution, not an accuracy pass.** These recordings repeat coordinate tuples at roughly nine-second intervals and show a phone-speed unit mismatch: coordinate-derived speeds support m/s although the CSV header says km/h. These inferences and input hashes are recorded in `data/fusion-replay/manifest.json`. Gyro column/frame interpretation still needs confirmation; the replay is not certified as an exact Android sensor reproduction. Ten-Hz data is sample-held for the high-rate filter, adding no new information.

API integration tests pass against real local MongoDB, including uploads, retries, reports, registry and separate publishing credentials. No Android device is connected; on-device sensor capture, diagnostic sharing, background endurance, large-file upload memory use and TFLite execution still need phone testing. No real drift report is fabricated without reference data.

## Inbuilt and deployable components

The APK includes statewide tiles, the road graph, native fusion, trained TFLite files and local drive recording. The phone does not need a laptop for offline tracking. Uploads, remote model distribution, shared drive history and report storage need the Express/MongoDB backend. The self-hosted package is under `deploy/`, including Caddy HTTPS, MongoDB and persistent volumes. No host/domain was supplied, and Docker is unavailable locally, so public deployment and container startup have not been verified.

`scripts/build-release.ps1` supports APK/AAB signing with a user-owned keystore through environment variables and an HTTPS API origin. No production signing identity has been invented. Field-test debug signing is not a Play Store release.

The runtime dependency is upgraded from TensorFlow Lite 2.16.1 to Google's maintained LiteRT 1.4.2, preserving the Java `org.tensorflow.lite.Interpreter` API and offline `.tflite` models. The previous native libraries had 4 KB ELF load alignment. `scripts/check-native-pages.py` checks every 64-bit native load segment in the resulting APK; physical 16 KB-device execution is a separate pending check. See [Google's API-compatible migration guide](https://developers.google.com/edge/litert/migration).

## Phone verification needed

Install the 1.3 field-test build over the prototype. Enable precise location, mount the phone rigidly, start outdoors and wait for a current fix. Drive straight to establish heading. Record a short stationary-to-moving-to-stationary drive. After stopping the recording, use **Settings → Export last drive diagnostics**. The Android share sheet exports the DRLOG and the 10 Hz fused trace together; nothing is sent automatically. Provide the exported ZIP, phone model, mount position, approximate failure time and observed behaviour to diagnose the remaining fusion issue. Use a passenger/operator to collect notes during driving.

Required before a production navigation claim: device calibration and reliable real-drive replay, a reference trajectory for measured drift, annotated motion/blackout data, background/endurance testing, deployment verification, and release signing.

## Reproduce

```powershell
.\.venv-ml\Scripts\python.exe ml\refine_iovnbd.py
.\.venv-ml\Scripts\python.exe ml\compare_candidates.py
.\.venv-ml\Scripts\python.exe ml\prepare_fusion_replay.py
$env:IDR_REPLAY='1'
.\android\gradlew.bat -p android testDebugUnitTest
npm test
```

Source references: [IO-VNBD repository](https://github.com/onyekpeu/IO-VNBD), [Android Location measurement timestamps and uncertainty](https://developer.android.com/reference/android/location/Location), and [Solà's ESKF quaternion derivation](https://arxiv.org/abs/1711.02508). These describe the source data and mathematical/API contracts; they do not validate this implementation.
