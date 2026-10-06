# Offroute repair — 3 October 2026

Scope: fix demonstrated navigation defects; preserve the installed UI; rename the visible app Offroute; build and check the connected Samsung. User requests finishing implementation before field tests. No cloud deployment or model promotion.

Evidence: supplied RAR source matches current LiveNavigation, InertialNavigation, SensorCaptureService and UI. Installed APK SHA-256 equals artifacts/IDR-Navigator-debug.apk. Rebuilt pre-edit JS and CSS hashes equal installed assets. Recovery APK and changed-file baselines saved under recovery/offroute-20261003.

- [x] Read problem statement, screenshots, archive, installed version and native status.
- [x] Reproduce independent gravity/accelerometer timestamp ordering and stale gyro readiness using SensorTimingTest.
- [x] Repair bounded timestamp ordering and IMU readiness; retain stale sensor, remount and high uncertainty safeguards.
- [x] Rename labels only; rebuild and compare UI CSS, models and map payloads with original APK.
- [x] Run Android suite, frontend state/presentation tests, API tests, TypeScript and build.
- [x] Review fixes, install in place, verify retained app data and native service health.
- [x] Write requirements and verification report, API explanation, and short field-test protocol.

Limits: no evidence can establish arbitrary unmounted-phone tracking or the requested <10% drift from a desk test. Existing recorded evaluations have failed the accuracy target. Avoid replacing the working app with a new research estimator or enabling unvalidated AI corrections.
