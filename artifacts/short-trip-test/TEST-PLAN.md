# One-minute physical-phone test

Target confirmed by user: Samsung SM-E156B, USB serial RZCX71S8G8L. Installed app: Offroute 1.5.6, code 15. Authenticated wireless service: adb-RZCX71S8G8L-diZtVG._adb-tls-connect._tcp; device identity verified as RZCX71S8G8L. Its direct endpoint changed during preparation from 10.234.101.18:37613 to 10.234.101.18:46083. Prefer the discovered service, and rediscover before reconnecting. Do not start navigation/recording until the user says start.

USB readiness check passed: app visible, local collection enabled, dashboard mount setting, 100 Hz target, zero saved journeys, no active route/log; all ten samples had fresh GPS/IMU and available AI output, and inference count increased by four. Hardware reports accelerometer, gyroscope, gravity and rotation-vector sensors. A separate wireless readiness capture follows in this directory.

Wireless readiness passed after returning Offroute to the foreground: eight samples, eight fresh GPS/IMU samples, seven available experimental AI readings, inference count increased by four. No active recording or route. Evidence: 2026-10-03T12-18-19.652Z-telemetry.jsonl. Earlier preparation attempts recorded a changed wireless port and a background-page telemetry timeout; neither started a journey.

Preparation: verify installed version, precise location permission, fresh accelerometer/gyro, model inference updates, local collection enabled, no existing recording, and an authenticated wireless ADB connection. Use an explicit device serial for all commands. Never reset app data or reuse calibration from the previous SM-S711B phone.

Before departure, secure the phone in its mount, leave Offroute open, and wait outdoors for a fresh GPS fix. Select the intended destination while parked. Keep the phone and laptop on the same Wi-Fi/hotspot if live monitoring is desired.

Suggested one-minute route: approximately 10 seconds stationary, 35–40 seconds of normal driving including a straight section and gentle turn if the route permits, then 10–15 seconds stopped. A passenger operates the phone. Keep GPS enabled for this first short baseline, so measured speed can label the IMU data. No deliberate outage or remote setting changes while driving.

On START: confirm the destination and normal app navigation/collection flow; verify logging=true and bytes increase. Run scripts/field-trip-monitor.mjs with the wireless serial, this output directory, and up to 90 seconds. This monitor only reads telemetry at about 2 Hz; the phone records its normal raw sensor data and 10 Hz fusion trace separately. The monitor does not start or stop a journey. A monitor disconnect does not issue a stop request to the app.

On STOP, after the vehicle is parked: end navigation through the app, confirm the saved journey, and copy its raw DRLOG, quality CSV, fusion CSV, training metadata and summary from app-private storage. Verify hashes and inspect for sensor gaps, fresh-GNSS speed error, stop behavior, mount alignment, gravity/bias evolution and map correction jumps. Do not overwrite existing captures; telemetry filenames include their timestamp.

One minute can diagnose obvious faults and collect calibration evidence. It cannot validate a replacement model, prove the under-10% outage-drift target, or establish performance across mounts and vehicles. No model promotion or app change during the drive.
