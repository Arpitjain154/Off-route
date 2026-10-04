# Live ESKF and physical training recordings

Goal: connect the app's learned speed corrections to its live ESKF and preserve training-quality physical sensor/GNSS recordings through local storage, Supabase packaging and laptop ingestion.

User authorization: implement both changes autonomously. Work in the existing checkout; preserve recordings and deployed model validation gates.

Design: LiveNavigation owns the same NavigationFusion/ESKF used by the service, edge API and laptop replay. Its existing mount calibration and sensor freshness guards gate ESKF availability. IMU propagation, GPS, NHC/ZUPT, accepted map corrections and calibrated AI speed measurements update that state. Raw AI diagnostics remain distinct from the fused navigation speed. The legacy planar estimator remains only as a calibration helper, never the authoritative outage pose.

Recording design: preserve DRLOG v1 compatibility, add precise original-event sensor and GNSS sidecars with explicit units/provenance, package and verify them with existing immutable uploads, and prefer independent GNSS speed labels on the laptop. Never train against the AI or ESKF output.

- [x] Reproduce the live filter bypass with a navigation regression; wire a single live ESKF and test GPS/IMU/AI, invalid predictions, freshness and mounting.
- [x] Add recording sidecars, lifecycle handling, upload/download compatibility and laptop label quality tests (delegated independently).
- [x] Run Android, Python and web checks; build the APK; review the integrated changes and document actual validation limits.

Review focus: stale/future or duplicate AI samples; remounted/missing sensors; GPS recovery; sidecar loss/write failure; mock or poor GPS labels; legacy recording compatibility.

Verified: 129 Android tests (127 passed, 2 existing skips: IO-VNBD opt-in replay and statewide graph generation); 43 Python tests plus the model export checks; 12 final Python integration/recording tests after replay changed to count accepted ESKF measurements; 14 UI/state unit tests; production web build. All reviewer findings were reproduced and fixed: stationary GPS position evidence, consistent stop expiry through outage, availability without Doppler/course, and yaw remount alignment. The recording flush-failure test exposed an unfinished close path and now passes with all streams closed even when flushing fails.

Limits: legacy server tests cannot connect to local MongoDB (ETIMEDOUT), including a same-process test-database attempt. Supabase package/checksum/download tests are local fixture tests; live cloud upload and physical phone/outage accuracy are not claimed. Bundled model validation flags remain false.

APK verified: `assembleDebug` succeeded; package `com.idr.navigator`, version `1.6.0-offroute` (17); APK Signature Scheme v2 verification passed. SHA-256: `9CDDC2661F7261492F7304ECA86B478291B3C863474DEA137AD198A39283E520`. Build log: `artifacts/eskf-apk-build-final.log`. The browser transition check could not load the local dev server (timeout); focused UI/state tests and production build passed.
