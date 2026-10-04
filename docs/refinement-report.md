# OffRoute refinement verification — 28 September 2026

## Identity and recovery

This is the existing React 18 / TypeScript / Vite / Capacitor 6 application, package `com.idr.navigator`, with its Java navigation engine and Express/MongoDB API. The original project was found at `C:\Users\surya\Documents\ChatGPT\offroute 3.0`.

The phone initially ran 1.4.0 (8). The recovered source was already a newer 1.5.0-qa (9), not the exact installed release. This refinement is 1.5.1-refinement (10). The source's prior accuracy shortfall is retained explicitly below.

Recoverable originals:
- `recovery/installed-IDR-1.4.0.apk`: exact original installed package; SHA-256 `009b6853c57d6059ab2ac0346dc080ec6a5a8601986ceff0fa035a3e4fe432d1`.
- `recovery/phone-data-pre-update.tar`: app-private files, preferences and WebView data saved before update, 168,952,320 bytes. This is a live snapshot, not a guaranteed transactional database backup.
- `recovery/phone-local-storage-before.json`: preferences saved separately immediately before update. No journey list was stored in the original WebView snapshot.
- Original source/assets: `C:\Users\surya\.codex\.chatgpt-projects\g-p-6aa5938fc2f48191bcebfbb1e379eaac\recovery\source-baseline`.

No uninstall or app-storage clearing is part of this work. If rollback is necessary, reinstall the saved original APK in place with the matching debug signature and downgrade flag. Retain the current data copy first; do not clear storage.

## Implemented UI and behavior

The six supplied images, both full-duration sampled recordings, the UI brief and problem description were reviewed before changes. The original map remains interactive, with offline vector tiles, gestures, route rendering, a marker and a geographic uncertainty area. The screenshot's device frame, editor outlines and desktop background were excluded.

Implemented floating search/status, expandable position sheet, route planner, navigation banner and trip/confidence summary, journey list/details, Sensors and the settings dialog. Settings remains accessible from the top settings/profile control while Sensors remains in the bottom menu. Existing settings, unit/theme/mount controls, optional upload, report chart, connection checks, model updates, permissions and diagnostics remain accessible. Car and motorcycle are the real supported modes; Walk/Cycle are disabled with an explanation. Voice/audio controls were not fabricated because the source has no corresponding function. Example profile names, journey records and measurements were not copied.

The promotional 'A new journey awaits' and manual recording panel were removed. A live device navigation session starts local data collection; exiting navigation saves the journey. Sensors/Settings can pause it. Page changes do not restart the sensor service. Recording transitions are serialized; interrupted logs can be recovered without overwriting raw files. Failed recovery pauses collection and preserves metadata while navigation remains usable. Recovered partial tails receive a separate complete-record upload copy, preserving the original.

## Navigation and AI corrections

- ESKF non-holonomic measurements now constrain velocity in calibrated vehicle axes, including the matching right-error attitude Jacobian. Uphill travel is not treated as zero world-vertical velocity. High vibration/rotation suppresses the constraint.
- Live map corrections use the road's normal direction, reject future/stale results and remain bounded. They do not invent along-road speed.
- A stationary cold start cannot establish unknown mounting yaw. Moving GNSS course, recent gravity and compass observations are required. Confirmed stationary position is held and released on motion.
- GPS recovery includes the displayed transition offset in its uncertainty. Precision-dependent guidance and rerouting use that uncertainty. Inertial-only divergence cannot trigger rerouting; several distinct reliable GPS fixes must confirm it. Stale reroute messages expire.
- AI speed calibration uses distinct recent GNSS fixes with reported speed accuracy. Training provenance and sensor-quality sidecars are recorded alongside compatible DRLOG data. The desktop pipeline rejects legacy/simulated/incomplete/poor-quality labels, holds out entire journeys, fits a candidate, compares it with the existing model, and leaves deployment gated. See `docs/adaptive-learning.md`.

Existing model files and manifests were checksum-compared with the source backup and remain unchanged. No personal candidate was trained. USB ingestion of the current phone data rejected two short verification recordings and one legacy recording without quality metadata, yielding zero eligible windows. The pipeline correctly reports insufficient training data; at least five independent eligible sessions and sufficient distinct quality fixes in each data split are required. No data or models were automatically uploaded.

## Verification evidence

- 56 native JVM tests passed; 2 optional tests skipped. Includes ESKF Jacobian checks, slopes, stale/future map correction, stationary jitter, GNSS recovery, interrupted recording and existing route/filter tests.
- 21 frontend logic tests passed; 2 offline-map coverage tests passed.
- 8 learning-data/model-evaluation contract tests passed using clearly synthetic fixtures.
- 3 existing API integration tests passed against an isolated local test database: authentication, uploads, metadata, idempotency, reports and model registry.
- Browser interaction/responsive tests cover 360/390/412-pixel widths and short layouts, planner edit/swap/modes, invalid input, sheet, settings and tabs. A separate route fixture tests transition into active navigation and exit. Fixture screenshots are not physical sensor evidence.
- TypeScript and web production build passed. The existing large-bundle warning remains.
- The shared Java edge engine also compiled and produced its distributable ZIP successfully. It has no separate edge test suite; estimator tests are counted in the native suite above.
- Android build and signature verification passed. Installed 1.5.1-refinement (10) in place, with 39,445 offline vector tiles and unchanged bundled model bytes. The final APK matches the original installed signing certificate.
- Physical Samsung SM-S711B: dashboard/sheet, planner, Journeys, Sensors, Settings, native offline Bhopal-Indore route, current-GPS live route, automatic local recording and Exit/save all passed. Three tab cycles and a background/foreground return retained one engine generation and one location registration. No page errors occurred. Original preference fields and backend configuration matched the saved pre-update snapshot. Temporary theme changes for screenshots were restored.
- Final phone screenshots also verified street-level camera recentering and readable system-bar icons. Short stationary verification recordings are labelled in their notes; they are not driving accuracy tests.
- Evidence: `artifacts/refinement/apk-verification.json`, `phone-results.json`, `frontend-tests.log`, `native-test-summary.json`, `browser-results.json` and `api-tests.log`.

Deliverables: `artifacts/OffRoute-1.5.1-refinement.apk`, `artifacts/OffRoute-1.5.1-refinement-source.zip`, and the actual-phone screenshot gallery at `artifacts/refinement/screens.html` (overview image: `artifacts/refinement/screens-overview.png`).

## Accuracy limits and physical tests still required

The recorded IO-VNBD 30-second-outage evaluation produced **0 of 15 outages below 10% drift**, with **77.522% median drift**. The replay excludes road matching; it is not a claim about the complete phone system, and the new regression tests do not establish physical accuracy. The requested drift benchmark is not achieved by this refinement. No lane-level or tunnel-accuracy claim is justified.

Controlled real drives still need independent reference measurements: stationary/launch/braking, corners and slopes, 50 m and 1 km outages, GPS recovery, potholes, phone remounting, motorcycle lean, long sessions/battery use and background interruption. Record safely with a mounted phone and a passenger operating controls. Evaluate those recordings before changing model weights.

## Explanation

The changes preserve the working app and improve its presentation, recording reliability and several specific estimator errors. Recording is now internal to navigation and produces quality-labelled local training material instead of asking the driver to run a separate manual recording flow. Learning has two levels: bounded on-device calibration against reliable GPS, and desktop candidate training from eligible journeys. The app continues using its current bundled models until a replacement demonstrates improvement and passes phone validation. This prevents untested self-training from silently degrading navigation.
