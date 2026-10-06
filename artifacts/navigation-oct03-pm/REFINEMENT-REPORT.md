# Offroute 1.5.6 — vehicle navigation refinement

3 October 2026. Scope: the supplied afternoon screen recording, problemDescription.md, the new phone journey, and previously downloaded local/cloud recordings. The existing interface and production model weights are preserved. This is a bounded reliability update; the full positioning benchmark is not achieved.

## Findings and changes

- **Vehicle speed/NHC:** removed pedestrian cadence correction from the vehicle estimator. Repeated vertical road bumps previously could be interpreted as walking steps and overwrite vehicle speed. Lateral/vertical acceleration remains excluded from forward velocity. Quiet sensors plus a low integrated speed no longer establish a stop by themselves; the independent GNSS/IMU stationary detector still can.
- **Map matching:** invalidate the previous match when the current match fails or leaves map coverage. Calculate east/west error using the current latitude. Reject corrections while stopped, after sensor freshness expires, or when the match predates a newer GNSS anchor. Android supplies the asynchronous result's delivery time. Valid corrections remain lateral, bounded to two metres per accepted match, and cannot manufacture a forward-speed measurement.
- **Training alignment:** a speed label's IMU window now ends at the GNSS fix timestamp rather than its later arrival timestamp. This avoids including motion that occurred after the measured label.
- **Diagnostics:** add the live estimator's stationary/alignment state, uncertainty, gravity, forward axis, filtered acceleration and biases to the existing 10 Hz fusion trace. Earlier fields included a separate research filter's state, which was insufficient to reproduce the displayed estimate. Existing columns and raw recording format are preserved.

## What the recording showed

The supplied 206-second video shows GNSS speed followed by falling inertial speed, recovery corrections, and a temporary guidance pause. The five outages in the latest recorded journey lasted approximately 10–37 seconds; several estimates decayed toward zero. Neither a screen recording nor a held GNSS fix provides true speed during an outage. Consequently, these observations establish symptoms, not an accuracy measurement for that drive.

## AI speed experiment — not installed

A research-only model uses rotation-invariant IMU statistics. It was fitted on 717 quality fixes from three older training journeys, with whole-journey cross-validation confined to training data. The newly supplied journey was held out from fitting and selection.

| Evaluation | Existing model RMSE | Candidate RMSE |
|---|---:|---:|
| New journey, 88 quality GNSS labels | 20.49 m/s | 3.27 m/s |
| Previously used test set, diagnostic only | 12.94 m/s | 2.90 m/s |

These are errors against accepted GNSS speed labels, not measurements of GPS-denied position accuracy. The candidate incorrectly indicated motion on approximately 99% of stopped observations in the reused test set, versus 32% for the existing model. Independent validation also lacks enough quality labels. The candidate was therefore **not promoted, installed, or uploaded**; AI speed remains explicitly unvalidated and excluded from navigation fusion. TensorFlow Lite export agrees with the reference calculation within 0.000011 m/s on the new journey.

Artifacts: `speed-candidate/report.json`, `speed-candidate/speed-research-candidate.tflite`. Research entry point: `ml/vehicle_speed_candidate.py`. Production validation gates were not relaxed.

## Verification and requirements

- Android suite: 107 tests, 105 passed, two skipped, zero failures/errors. Includes regressions for bump-induced walking speed, false stops, stopped/stale/pre-anchor map corrections, valid bounded correction, NHC force exclusion and new trace fields.
- Python: 12 tests passed, including delayed-GNSS label alignment and rotation-invariant feature checks.
- Independent code review identified the asynchronous freshness gap; it was fixed and re-reviewed with no material remaining findings.
- Recorded before/after comparison: only **1 of 15** IO-VNBD outage cases met the under-10% drift threshold, both before and after this change. Median error was 69.52% before and 70.59% after. Thirteen cases were unchanged; the changed cases differed by approximately 0.03 m and 0.24 m. This does **not** demonstrate an overall accuracy improvement. The comparison isolates inertial prediction and does not replay phone map matching or establish real-road performance.

| Problem requirement | Status supported by this work |
|---|---|
| Vehicle alignment and vibration handling | Existing engine retained; vehicle cadence/stop errors corrected. Broad mounting and road-condition validation remains needed. |
| IMU-only AI speed | Research candidate improved moving-speed error but failed stop validation; no replacement deployed. |
| Offline map matching and NHC | Specific stale-correction and vehicle-constraint bugs covered by regression tests. Lane-level accuracy not established. |
| Seamless GNSS/INS recovery | Existing freshness and uncertainty safeguards retained. This patch does not establish millisecond transitions under all device conditions. |
| Under 10% outage drift | **Not met** by the recorded benchmark. |
| Smartphone 10 Hz / edge around 200 Hz | Existing architecture retained; no new sustained device-rate or external-IMU qualification claimed. |

## Use for the next field check

1. Enable precise location and leave Offroute open. Start outdoors with a clear sky view.
2. Fix the phone firmly in its vehicle mount before driving. Obtain fresh GNSS fixes and drive straight briefly so the engine can establish mount direction. Avoid handling or rotating the phone during the test.
3. Keep trip collection enabled. Use the usual route and mark the approximate times of signal loss, stopping, turning and signal recovery. A passenger should operate the phone.
4. Export diagnostics after the drive. The added live-state columns make it possible to distinguish bias/alignment failure, actual sensor interruption and a poor road correction.

“Guidance paused” means the app cannot currently support a reliable position; it is not evidence that you used the controls incorrectly. “AI speed estimate” is experimental. Its presence is not proof of accuracy or permission to use it in navigation.

## Installed package and live verification

The normal Gradle build completed successfully (26 minutes 25 seconds, mostly APK assembly). A packaging fallback was prepared during the delay but was not installed. The installed artifact is the normal Gradle output: `artifacts/Offroute-1.5.6.apk`, version code 15, version name `1.5.6-offroute`, 221,113,367 bytes.

SHA-256: `b7ff63fdb3e5700aad77fed7b26c96f7a1af5936ef3bf1e4284d62258ce8493e`.

- APK signature and 16 KB native-library alignment checks passed; signer matches 1.5.5.
- All 39,482 bundled asset entries match 1.5.5, including UI, maps and production models.
- Installed successfully as an in-place update on the connected Samsung SM-S711B. Android confirms version code 15.
- Six live samples confirmed fresh IMU/GNSS tracking and updating experimental AI speed. Validation remains false and AI fusion trust remains zero. This stationary/desk check does not validate moving-vehicle speed or outage accuracy.
- Five journeys remain listed. All 25 previously saved journey files match their pre-update byte counts and SHA-256 hashes.
- Supabase remains connected; the app reports three uploaded queue entries. No approved model release is available. Legacy backend-address/workspace-key settings remain absent; local collection and Supabase settings remain present.

Evidence: `package-verification.json`, `signature-after.txt`, `phone-after.json`, `recording-preservation.json`, `phone-sensors.png`, `phone-settings.png`.

Recovery: `artifacts/Offroute-1.5.5.apk` is retained. Pre-change source files are under `recovery/navigation-oct03-pm/source-before/`; `source-changes.patch` records this refinement.
