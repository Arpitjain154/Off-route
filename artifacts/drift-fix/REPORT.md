# OffRoute 1.5.3 stationary drift refinement

## Scope and recovery
This updates the existing React/Capacitor Android application, package `com.idr.navigator`. The live planar INS path is refined; the experimental ESKF is not the active position source and was not changed in this patch. Prior UI, routes, offline maps and model assets are retained.

The previous install is `artifacts/OffRoute-1.5.2-navigation.apk`. Source files before this change and a fresh phone local-storage snapshot are in `recovery/drift-fix-before/`. The broader source recovery ZIP is `artifacts/OffRoute-1.5.1-refinement-source.zip`. `stationary-fix.patch` records the core changes.

## What the video establishes
The user confirmed that the phone was kept still. The video shows approximately 18 m of coordinate displacement during the first blackout. The displayed +/-312.5 m is estimated uncertainty, not measured travel or measured position error. Raw IMU for that exact dashboard episode was not recorded, so the exact sensor sequence cannot be reconstructed from the video.

## Fixes
- Honor a GPS-and-IMU-confirmed stop even with a small constant gravity-removal residual. Previously the INS could discard that stop and integrate residual acceleration as movement.
- While a stop remains confirmed, hold position and zero velocity; keep the last GNSS uncertainty plus a small residual process allowance instead of accumulating moving-velocity error. This uncertainty model is not yet empirically calibrated.
- Detect sustained small changes from the confirmed-stop sensor baseline so gentle launches release the stop. Quiet constant-speed vehicle motion is not automatically treated as a stop.
- Reject predictions with stale gravity or missing sample intervals instead of integrating unsupported motion.

## Verification
Native JUnit: 63 tests, zero failures/errors, two skipped (optional phone-log replay and full-region graph rebuild). All five focused stationary/outage regression tests passed. The controlled 120-second simulation with 0.28 m/s^2 residual changed from roughly 2,010 m false displacement and 33.55 m/s to zero displacement and zero speed; final modeled uncertainty was 3.35 m. These are synthetic regression results, not physical accuracy measurements.

The recorded vehicle replay fixture was corrected to deliver gravity with each simulated IMU timestamp, rather than deliver a future gravity sample first. Current replay: 1/15 outages below 10% endpoint drift, median 71.908%. This is a reused 10 Hz dataset replay with held samples; the changed fixture means it is not a controlled before/after accuracy comparison. The project accuracy target remains unmet.

## Desktop AI experiment
`ml/train_stationary_candidate.py` fitted a new stationary classifier on 65,085 existing IO-VNBD training windows. Validation: 7 false stops among 7,668 moving windows. Reused Driver A check: 59 false stops among 25,374 moving windows (0.233%), and stationary recall only 13.51%. It was not deployed: falsely declaring a moving vehicle stopped would damage navigation. No new walking or bike data has been used. Details: `desktop-model/report.json`.

## Physical verification
Build completed successfully. APK signature matches 1.5.2; all prior public UI/map assets and model bytes compare equal, including 39,445 offline vector tiles. APK: `artifacts/OffRoute-1.5.3-stationary.apk`, SHA-256 `9366bdca6b929376fd4f1f3d736a6771b6f2392f43b8395923a94868e4f26707`.

Installation was attempted but ADB reported device RZCX80NLSNN not found; a follow-up device inventory was empty. Consequently 1.5.3 is NOT confirmed installed and physical stationary validation remains pending. No phone Location setting was changed during this verification attempt. The pre-update snapshot succeeded before the phone disconnected.

Reconnect/unlock the phone with USB debugging allowed. Install the verified APK in place with `adb install -r` (do not uninstall or clear data), then run `node scripts/verify-stationary-phone.mjs` while the phone stays still. The script compares saved journeys/preferences, records native poses, and restores the original Location setting in a finally block. A fresh GPS-confirmed stop and aligned INS are needed for a meaningful stationary-outage pass; an unaligned cold start does not validate the fix.

Walking and bike trials require the user's readiness and must be requested in that order after the stationary check. Further work involves movement estimation and validation, not only training a model.
