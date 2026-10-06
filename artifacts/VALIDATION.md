# IDR Navigator — validation

Built locally on 2026-09-13 (Asia/Kolkata).

## Main-app 1.3 field-test update

See [MAIN-APP-STATUS.md](MAIN-APP-STATUS.md) for current checks and release blockers. The updated APK is `IDR-Navigator-1.3-field-test.apk`; the old `IDR-Navigator-debug.apk` remains the judge prototype. Version 1.3 passes 23 native tests (one optional map rebuild skipped), three API integration tests, TypeScript/production UI build, APK signature/content checks, and native/ZIP 16 KB alignment checks. Device instrumentation compiled only. Real-recording replay executes but fails navigation-accuracy expectations; it is explicitly not an accuracy pass.

## Judge prototype 1.2 update

- Final APK built successfully: version 1.2 (code 3), 213,964,921 bytes. APK Signature Scheme v2 verification passed. `scripts/verify-judge-apk.py` confirmed all 39,285 statewide tiles, seven graph files, three model checksums, bundled MapLibre worker, and the latest UI. Exact APK SHA-256 is in `apk-verification.json`.
- Final TypeScript check and both offline map regression tests passed.
- Madhya Pradesh: 39,285 offline tiles (114.9 MB uncompressed tile files), 554,852 display features, and a prebuilt GraphHopper road graph. Six-city road coverage verified at every zoom 5–13; graph lookup passed for the same six cities.
- Eleven native tests passed, including four GPS/stationary regressions and the explicit statewide graph preparation test. Stationary detection works before heading alignment, rejects a smooth moving car, requires valid speed evidence, and releases when motion begins during a GPS dropout.
- IO-VNBD export regressions passed; stationary versus moving speed diagnostics are in `ml/export-regression.json`. Learned fusion corrections remain disabled.
- Read `JUDGES-DEMO.md` for a two-minute demo and `GPS-AND-MAP-UPDATE.md` for the changes and phone checks.

## Earlier 1.1 verification (retained for context)

- React/TypeScript production build: passed.
- Java Android debug APK compilation: passed.
- Java tests: six passed — straight-line GPS blackout, measurement outlier/ZUPT, exact DRLOG binary layout, portrait/tilted mount gravity alignment, Bhopal HMM matching with 25 m synthetic drift on a real OSM road, and Java/Python feature parity on a real held-out IO-VNBD window.
- API integration suites: two passed, covering auth, uploads, streamed downloads, metadata filtering, idempotent retry, invalid input cleanup, reports, model publishing, semantic version selection, and model file download against a real isolated MongoDB database.
- npm audit: zero known advisories after dependency updates.
- Browser QA at a 390×844 viewport: onboarding, test drive, start/stop recording, metadata entry, upload, persisted history, drive detail/report pending, model version discovery, and dark/light preferences checked.
- A simulated 3:19 test drive is retained in the local workspace and clearly marked Test/simulation. It is not a real phone recording.
- Bhopal offline vector tiles and GraphHopper graph are packaged locally; no map service account required.
- Three models trained on 144 real IO-VNBD synchronized CSVs are packaged and registered as 1.0.0. Their shapes, checksums and Keras/TFLite prediction parity passed. See `ml/MODEL-CARD.md` and `ml/training-report.json` for held-out results and limitations. `placeholder=false`, `validated=false`: inference runs, learned tracking corrections remain disabled. Hardware inference instrumentation test APK compiled.
- Fixed both map failures: geojson-vt 5/vt-pbf 3 geometry incompatibility and MapLibre 6 worker relocation by Vite. Regenerated 160 tiles using geojson-vt 4, added geometry regression coverage, and explicitly bundled the worker. Streets, buildings and lakes visually confirmed in development and production previews.
- Updated Android APK version 1.1 (versionCode 2), incorporating the trained models and map fixes.

Not executed: physical-device or emulator instrumentation, native sensor sampling, screen-off endurance, native model OTA installation, real-road drift/accuracy evaluation, hosted deployment, Play Store release signing.

The database/API/preview are local development processes. Use README.md for restarting them and connecting a phone. A debug APK is installable but is not a signed Play Store release.
