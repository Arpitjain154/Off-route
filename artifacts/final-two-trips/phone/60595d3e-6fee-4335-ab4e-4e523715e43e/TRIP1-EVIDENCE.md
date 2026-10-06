# Trip 1 evidence — Offroute 1.5.6

Session: `60595d3e-6fee-4335-ab4e-4e523715e43e`, Samsung SM-E156B, 3 October 2026. Analysis is restricted to the saved Trip 1 phone files and Trip 1 laptop telemetry. No live device operations, app changes, training, cloud access, or Trip 2 inspection were performed.

## Capture quality

- 45,784 complete raw IMU records over 379.35 seconds: 120.69 Hz against the configured 200 Hz target. All acceleration and gyro values are finite.
- Raw sample gaps: median 8.00 ms, maximum 12.57 ms. All corresponding quality rows mark gyro fresh.
- Live navigation trace: 3,671 rows, 9.68 Hz, maximum trace gap 108.01 ms. Gravity sample age median 8.00 ms, maximum 36.01 ms.
- Mount alignment and speed availability remained true throughout. The 707 matching laptop pose packets show one unchanged engine generation and one location registration; no evidence of a service restart during capture.

## Location-off / reacquisition

Times below are relative to the first phone fusion trace row. The position comparison uses the **live navigation fields**, interpolated to the first returning GNSS fix timestamp. It does not compare the unrelated research-filter coordinates or use the already-smoothed recovery position as the inertial endpoint.

| INS interval | Duration | INS start → end speed | First returning GNSS speed | INS–GNSS position discrepancy | Residual along / across INS heading |
|---|---:|---:|---:|---:|---:|
| 127.54–163.31 s | 35.77 s | 5.96 → 0.21 m/s | 4.64 ± 2.30 m/s | 157.15 m | +155.95 / −19.40 m |
| 214.27–254.82 s | 40.55 s | 6.28 → 0.45 m/s | 5.66 ± 2.20 m/s | 114.68 m | +113.51 / −16.36 m |
| 302.36–317.03 s | 14.67 s | 7.03 → 5.95 m/s | 2.62 ± 2.30 m/s | 43.00 m | −42.49 / −6.60 m |

Positive along-track residual means the returning GPS coordinate lies ahead of the last inertial estimate. First returning GNSS reported horizontal accuracy 4.4, 3.9 and 4.4 m. These are reported uncertainties, not independently verified error bounds. Raw coordinate storage uses float32 (up to approximately 0.4 m longitude quantization here). GNSS fix timestamps precede the last pre-recovery INS sample by 68, 105 and 67 ms, respectively; interpolation accounts for that offset.

- No INS interval lost alignment or speed availability, and none asserted a confirmed stop. The first two speed estimates nearly collapsed despite returning GPS showing motion. The third overestimated endpoint progress.
- Heading differences at return were 0.75°, 1.35° and 10.88°. Return GNSS bearing uncertainties were 17.6°, 14.4° and 31.1°; the third bearing is weak evidence. The first two large position discrepancies are predominantly longitudinal.
- The recovery display offset relative to GPS falls below about 0.4 m by 8 seconds in all three cases, matching the current 8-second correction cap. At 2 seconds it still retains approximately 116, 83 and 32 m of offset. This is recovery smoothing, not another sensor outage.
- Just before return, reported navigation uncertainty was 76.87, 78.06 and 17.39 m, smaller than the corresponding GPS-return discrepancy. Treat current uncertainty as a process indicator, not a calibrated statistical guarantee.

**There is no continuous independent position reference while Location is off. These are reacquisition discrepancies; they do not establish true GPS-off trajectory accuracy, route distance, or the requirement's drift percentage.**

## Stops, calibration and AI

Two periods had GNSS speed ≤0.3 m/s with reported speed error ≤1 m/s for about 51 seconds near the beginning and 28 seconds at the end. Live navigation displayed at most 0.19 and 0.06 m/s there, but never asserted `nav_stationary`.

Their raw acceleration-norm standard deviations were 1.27 and 1.91 m/s², and gyro RMS was 0.243 and 0.457 rad/s. Those greatly exceed the current quiet-IMU gates. This explains why the detector could reject a quiet-stop classification; it does not identify whether the cause was vehicle vibration, phone handling, or another condition. Do not infer a stationary sensor calibration interval solely from GPS speed.

Acceleration bias ranged from −0.300 to +0.013 m/s². It remained fixed during the three outages at −0.0776, −0.0383 and −0.0584 m/s², respectively. Gyro bias stayed 0.001119 rad/s throughout. Mount-forward direction changed at healthy fixes, with the largest update 16.10° near initial movement; alignment stayed true. These are observations, not proof of a calibration defect.

The experimental speed model stayed excluded from navigation (`aiTrust=0`). In 451 good-GPS telemetry packet comparisons (horizontal accuracy ≤10 m, speed error ≤1 m/s), speed RMSE was 9.03 m/s and MAE 6.33 m/s. Among 150 packets with GNSS speed ≤0.3 m/s, the model output median was 0.029 m/s, 95th percentile 2.17 m/s and maximum 13.70 m/s. Packets contain repeated overlapping inference windows and are **not independent evaluation samples**. These values show unstable experimental output; they are not a model-validation score or grounds to deploy replacement weights.

Map status reported the Madhya Pradesh matcher active throughout; trust ranged 0–0.65. Trust alone does not establish a correct road association. The recorded data does not expose individual candidate-road residuals or every applied lateral correction, so it cannot attribute the cross-track component specifically to map matching.

## Evidence-led follow-up hypotheses

1. Prioritize longitudinal acceleration projection, gravity removal and bias stability in replay. First two outages substantially underpredict distance while heading at return remains consistent. A third shorter outage overpredicts distance, so a blanket speed multiplier or fixed minimum speed is not justified.
2. Examine vibration-tolerant stop evidence without removing the safeguards that distinguish constant-speed travel from rest. No stationary bias update was available in this recording despite low-GPS-speed portions.
3. Review whether navigation should retain such a large display correction for a full 8 seconds after a reliable GPS return. Any change must preserve explicit uncertainty and avoid manufacturing confidence.
4. Keep Trip 2 independent until the candidate change and evaluation criteria are fixed. Do not use these three endpoint discrepancies as continuous GPS-off labels.

Machine-readable calculations are in `trip1-evidence.json`; the analysis scripts are under `tmp/trip1-*.py`. Original recording files are unchanged.
