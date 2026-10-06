# Offroute 1.5.4 — repair and verification

Date: 3 October 2026. Source remains in the user's `offroute 3.0` workspace.

## What was established

The supplied RAR contains the same navigation source and React UI as the workspace. The connected Samsung SM-S711B was running package `com.idr.navigator`, version 1.5.3-stationary (12). Its APK was copied to `recovery/offroute-20261003/installed-original.apk`; SHA-256 is B7F485ED0726659D0778805C229D165D63499787CAE5A09D1EBA5584C7DE8CDE. This matches the previous `artifacts/IDR-Navigator-debug.apk`. Changed source files and phone preferences were backed up separately.

The screenshot means guidance has no position it considers usable. The route is retained. Active accelerometer data alone does not establish a usable inertial pose: the engine also needs a fresh gyro, gravity, a prior GNSS position/speed, and mounting alignment. The phone's saved diagnostic included “Mount direction uncertain · calibrate with GPS.” During the desk inspection, real GPS locked, the IMU was active and the Madhya Pradesh offline HMM loaded. There is no recording of the supplied screenshot's exact event, so its precise trigger cannot be established retrospectively.

## Repairs

1. Independent Android sensor streams can be delivered out of timestamp order. The old gravity freshness guard rejected a gravity event even 5 ms newer than the current accelerometer event, preventing inertial prediction/takeover. A bounded 20 ms lead is now allowed. Gravity older than 500 ms, large clock mismatches, missing samples and lost mounting alignment remain unavailable. Tests demonstrate continuous 50 m prediction after timeout-based GNSS loss with the reordered stream; this is a controlled synthetic test, not an accuracy benchmark.
2. The “IMU ready” flag previously depended only on recent accelerometer events. It now also requires gyroscope freshness, so a missing gyro is not represented as a ready IMU.
3. Visible app branding is **Offroute**, including launcher/activity, page title, onboarding text and tracking notification. Version is 1.5.4-offroute (13). Application ID remains unchanged so this can update the existing installation and retain its data.

No layouts, CSS, map behavior, navigation controls, cloud configuration or model-trust thresholds were changed. The pre-edit build matched the installed JavaScript and CSS byte-for-byte. Comparing the renamed build proves that all bundle differences are branding text and generated asset filename references. The CSS and map worker are unchanged. Source changes are recorded in `source-changes.diff`.

## Verification

- Regression tests before repair: 3 tests, 2 failed as expected (reordered gravity and stale gyro readiness).
- Full Android suite after repair: 93 tests, 91 passed, 0 failures/errors, 2 skipped. Optional `IovnbdReplayTest` and `StateGraphBuildTest` were skipped by their existing opt-in gates. `InertialRecordedEvaluationTest` did run.
- Frontend state/presentation regression tests: 25 passed.
- TypeScript build check: passed.
- Production web bundle: built successfully. Existing large-bundle warning remains.
- API suite: 3 passed after starting its local MongoDB dependency. Initial attempt failed because the database was absent; the failed attempt is preserved as `api-tests-initial-no-db.log`.
- Independent focused code review: no actionable introduced defects found.
- APK: built and signature verified; application label Offroute, version 1.5.4-offroute (13), package com.idr.navigator. SHA-256 CF589378C8683411E6811E9FD582BE58D35848FFA2FC021C17BA073AE73A99BD. 39,474 map/model/style payload entries match the original byte-for-byte. Packaging took 14 minutes because of the offline asset set.
- Installed in place successfully on the connected Samsung. Existing application ID and signing identity are retained. See phone-after.json for post-install observations. An initial inspection overlapped the package replacement and timed out; the check was rerun after installation completed.

## Problem-statement coverage and remaining work

| Requirement | Present implementation and actual limitation |
| --- | --- |
| Automatic mounting alignment | Gravity plus good moving GNSS course and compass; large mount changes invalidate alignment. Unknown heading cannot be recovered from gravity alone. |
| Noise rejection and AI speed | Signal conditioning, shock rejection, stop detection, IO-VNBD TFLite inference and bias calibration exist. Unvalidated models remain inference-only; they were not promoted or retrained in this repair. |
| Offline map matching and constraints | Existing Madhya Pradesh graph, HMM matching and kinematic constraints retained. Map constraints cannot determine forward speed on their own. |
| GNSS/INS handoff | Sensor timing defect repaired; native output supports inertial tracking once aligned. Missing sensors, moved mount and excessive uncertainty can still pause guidance. |
| Smooth interface, 10 Hz phone output | Existing native publication and UI retained. Controlled pipeline tests pass; a full moving-vehicle test remains necessary. |
| External IMU / edge engine | Existing Android-independent `EdgeNavigator` accepts timestamped SI observations. Existing 200 Hz synthetic regression passes. External sensor hardware was not tested. |
| IO-VNBD preliminary results | Existing models and held-out-recording evaluation retained. Fresh suite replay has only 1/15 outages below 10% drift, median 69.520%. It does **not** meet the requested drift benchmark. |

This is a focused repair, not completion of the research accuracy target. Lane-level accuracy, less than 10% outage drift and arbitrary phone handling are not established. Hiding the pause or accepting uncertain positions would not solve those requirements.

## The two separate cloud connections

**Supabase connection:** project URL, publishable key and signed-in user session. Native `SupabaseClient` sends the publishable key as `apikey` and the user's session token as `Authorization: Bearer ...`. It supports authentication, consented journey uploads to the configured `journeys` bucket, metadata and model-release workflow.

**Collection & legacy server:** Backend API address points to the older Express/MongoDB API serving `/api/drives`, `/api/reports`, and `/api/models`. Workspace API key is sent as `X-API-Key` and must match that server's configured device/workspace credential. It is not a Supabase publishable key, user password, or service-role key. This app also separates privileged publisher credentials on the legacy server.

If you are using only Supabase, these legacy fields may remain empty. They are optional for live GPS/IMU navigation and offline maps. The “Test saved connection” / “Check legacy server models” actions need a configured legacy server and otherwise can fail without implying a Supabase problem. Keep the two sets of credentials separate.

## Next field test

Keep the phone firmly mounted. Start with good outdoor GPS and drive straight long enough to establish alignment and bias; then test a measured short outage, steady travel, braking, a turn, a stop, and GPS recovery. Use independent reference speed/distance and a passenger to operate the test controls. Record the existing local trip/trace. A stationary desk test cannot validate this benchmark. Do not use live-road operation as the first test of extended GPS-denied guidance.

The original APK is retained locally for recovery. An Android downgrade may require special handling; do not uninstall the app just to restore it, because uninstalling removes app data.

Post-install check passed: saved preferences/history retained; five native samples were finite; one GPS registration; real healthy GPS and fresh IMU observed; Madhya Pradesh HMM active; recording stayed off; recent crash-log scan found no matching fatal exception. This was a stationary device smoke test, not a driving accuracy test.
