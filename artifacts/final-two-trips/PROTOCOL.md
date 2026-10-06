# Final two wired trips — preparation

Confirmed test phone: Samsung SM-E156B, wired serial RZCX71S8G8L, Offroute 1.5.6/code 15. Always pass this serial explicitly; ignore wireless entries. No APK, model, UI or fusion changes for these trips. Capture preference changed through existing Settings from 100 to 200 Hz target before recording. Actual rate will be measured, not assumed.

## Start / stop instructions for the user

Before each trip: phone fixed in its mount, USB cable connected to the laptop with enough slack, Offroute open, Location ON, local collection enabled. Plan the intended route while parked. A passenger operates Location controls during movement; otherwise pull over before touching the phone. Do not rotate or remove the phone when toggling Location. Return to Offroute immediately after changing the toggle.

1. Send **START 1**. Wait for confirmation that a new journey is recording and its byte count increases. Do not begin another journey while the first is active.
2. Remain stopped about 10 seconds with Location ON. Drive normally with GPS for at least 20–30 seconds to establish mount alignment; wait longer if the app is not ready for inertial tracking.
3. Trip 1: one Location OFF interval of about 15–20 seconds during ordinary steady travel, then Location ON. Continue for 20–30 seconds or until fresh fixes return. No need to drive faster or take a special manoeuvre.
4. Once parked, keep Location ON and remain still about 10 seconds, then send **STOP 1**. The assistant ends navigation through the existing control, verifies logging=false, copies the five per-session files, checks hashes and any capture-error marker, and confirms it is saved. Wait for that confirmation before trip 2.
5. Send **START 2** and repeat the same initial GPS/alignment phase. If the normal route permits, include a gentle turn or a brief stop/restart during the OFF interval. Otherwise use the same pattern as trip 1; do not force manoeuvres.
6. Restore Location ON before finishing. Once safely parked and still for about 10 seconds, send **STOP 2**. Wait for save confirmation before unplugging or closing Offroute.

If limited to one minute, prioritise the initial GPS/alignment period and a single short OFF/ON cycle. An unaligned outage is useful failure evidence but not a calibrated navigation accuracy test. Neither manual GPS-off trip provides independent ground truth during its outage; use GPS-on sections for labels and compare recovery discrepancies without calling them full trajectory error.

## What is captured

- Phone raw DRLOG: accelerometer XYZ, latest gyro XYZ and magnetometer XYZ, monotonic sample timestamps, accepted GPS coordinates/altitude/speed/accuracy and signal summaries. Gyro/magnetometer individual event timestamps and every raw GNSS measurement are not recorded by this installed app.
- Quality log: fix timestamp, speed uncertainty and gyro freshness on accepted IMU callbacks.
- Fusion trace: research fields plus live nav_* and ins_* positions/speed/heading, uncertainty, alignment, gravity, forward axis and acceleration/gyro biases. Use live fields for displayed navigation analysis. DRLOG coordinates are float precision; prefer live trace doubles for position comparisons.
- Laptop: timestamped full available navigation telemetry (AI output, map status/trust, tracking state, engine generation and recording progress), the Android Location mode sampled about every two seconds, device uptime for time alignment, and app-process-only logcat. Location-mode polling brackets toggle time; it is not an exact hardware-fix timestamp.
- Completed per-session summary, provenance and SHA-256 manifest, plus optional capture-error marker. No account credentials or unrelated app logs are intentionally collected.

## Operator commands for the assistant

At each START use a new output directory, e.g. `artifacts/final-two-trips/trip-1-live`:

`node scripts/field-trip-session.mjs RZCX71S8G8L artifacts/final-two-trips/trip-1-live 600`

This starts only laptop monitoring. Use the normal app route start action after inspecting its current controls and confirming the user's intended destination, then verify logging=true, a new active-log UUID and growing bytes. Do not call SensorPlugin.startLogging alone because that bypasses the UI's recording identity/lifecycle. Start the laptop monitor before the app recording where possible. The wrapper retries telemetry in segments and separately records app errors and Location state even while the WebView is temporarily backgrounded.

At STOP, inspect current controls and click Exit navigation for the active route. Confirm logging=false, active log cleared and the correct new journey saved. Write an empty STOP file inside that trip's laptop output directory to end laptop capture. Stop and phone-save are separate actions. If already ended by the user, do not create another recording. Copy by exact UUID:

`python scripts/collect-field-trip.py RZCX71S8G8L SESSION_UUID artifacts/final-two-trips/phone`

Check engine-last-error.txt if present in app files, without clearing it. Verify the stop-state and upload/collection status without forcing cloud uploads. Do not delete phone originals. Confirm all copied files' hashes against the device and analyze both sessions independently. Reserve trip 2 as held-out evaluation if fitting on trip 1; do not use the same trip to train and claim validation.

## Preflight evidence

Wired identity/version confirmed. Phone has roughly 3.3 GB free and was USB-powered at 29% battery. Initial monitor timed out while app was hidden; opening Offroute restored it. No phone recording was started during preparation. The small laptop-only capture checks are stored separately from the two actual trips.

Final full preflight passed: 23 of 23 samples had fresh IMU, fresh GPS and available experimental AI output; inference count advanced by 12. Sample-rate preference=200, local collection enabled, no active route/log, logging=false. Three existing journeys were listed at this check; do not assume only the two earlier analyzed journeys exist. App-only logcat and Location-state events were saved and the laptop capture exited successfully. Readiness is a point-in-time observation; verify again at each START.
