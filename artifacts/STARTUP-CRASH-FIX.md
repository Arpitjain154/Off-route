# Android 1.3.1 startup crash fix

Reported device: Samsung F15 5G, Android 16. The supplied screenshots show Android terminating IDR Navigator after Start engine; they do not contain a stack trace from the phone.

## Reproduction

The original 1.3 APK started and restarted normally in an Android 16 x86_64 emulator. A second test reserved 96 MiB of Java heap to represent memory used by other app components, with a 192 MiB process heap limit. Starting the same APK then killed the process with `OutOfMemoryError` in `RAMIntDataAccess.loadExisting`, while GraphHopper loaded the statewide location index.

The complete failing result is in `startup-memory-before.txt`, with the Android fatal stack in `startup-memory-before-crash.txt`. This reproduces a concrete startup defect; confirmation that it is the Samsung's exact failure still requires that device's report or a successful update test.

## Changes

- Open prebuilt road graphs through read-only memory-mapped files instead of copying roughly 100 MB of graph data into Java heap. The Madhya Pradesh map and road graph remain included.
- Wait for native engine initialization before reporting startup success. Run the foreground-service launch while the activity is visible, and return startup failures to the app.
- Guard sensor/GPS initialization and callbacks, make raw GNSS measurements optional through AndroidX compatibility APIs, and clean up partially initialized services and model interpreters.
- Stop UI polling after engine failure so the user can retry instead of receiving repeated errors.
- Add local startup/error evidence and Android process-exit history. **Settings → Export engine diagnostics** works before any drive has been recorded and includes the latest completed drive when available. It opens the user's share sheet; it does not send anything automatically.

## Verification

Native JVM regression suite: 22 passed, two optional tests skipped (dataset replay and statewide graph rebuild). TypeScript and production web build passed.

Android 16 emulator: **four instrumentation tests passed** in 25.777 seconds. These cover the exact memory-pressure case that killed the original APK, three start/stop cycles, all bundled models loading and inferring, and diagnostics export without a drive. Results: `startup-after-instrumentation.txt`. The crash buffer after testing is empty. The actual UI Start engine button was also tapped after onboarding; it changed to Stop engine and the process remained alive (`startup-fixed-android16.png`). These are emulator results, not physical Samsung tests.

Deliverable: `IDR-Navigator-1.3.1-startup-fix.apk`, versionCode 5, 218,748,489 bytes. SHA-256: `a2c5cdb65610190d16b011d33d58795dd159cbf7a83f5cf0780413afa0bd9452`. Android APK v2 signature verified; 16 KB ZIP/native alignment checks passed. All 39,285 map tiles, seven graph files, three model hashes and current UI were verified inside the APK. It installs over the prior debug-signed version without uninstalling.

Install this APK as an update on the Samsung F15 5G. If the engine still closes, reopen the app and use **Settings → Export engine diagnostics**, then provide that ZIP. It includes Android process-exit evidence even when no drive could be recorded.

No model weights, fusion accuracy claims, backend configuration or stored drives were changed by this patch. This remains a field-test navigation build; the previously documented real-drive drift limitations still apply.
