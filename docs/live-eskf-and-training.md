# Live ESKF and phone training recordings

## What the audit found

The app already ran a 15-error-state ESKF for IMU propagation, GPS corrections, accelerometer/gyro bias estimation, gravity alignment, zero-velocity updates and vehicle constraints. However, SensorCaptureService displayed LiveNavigation's separate GPS/planar inertial solution. The ESKF was effectively a parallel diagnostic/research path. The raw neural-network speed shown under Model status was another diagnostic and did not by itself drive all navigation.

DRLOG recordings already contained accelerometer, gyroscope, magnetometer, GPS position, GPS speed and accuracy. Quality sidecars already supplied GPS timing and speed uncertainty. The GPS coordinates in DRLOG were float32, and independently arriving sensor callbacks were combined at accelerometer timestamps.

## Updated navigation

LiveNavigation now owns the NavigationFusion ESKF used by the Android service, the external-sensor EdgeNavigator API and desktop Java replay. Once mounting and fresh sensors establish observability, the displayed position and speed come from that ESKF. Accepted GNSS, IMU propagation, gravity, zero-velocity/non-holonomic constraints, cross-track road observations and trusted AI speed measurements all act on the same state. Short display interpolation preserves continuity when GNSS returns. Before mounting is observable, the UI explicitly reports GNSS fallback/ESKF alignment; stale or invalid sensors cannot claim available inertial tracking.

The neural model still predicts forward speed from IMU windows. Its role is to provide an uncertain speed observation to the ESKF, which combines it with its physical prediction and other measurements. ESKF is not a neural-network training algorithm. The former standalone planar engine remains a mount, freshness and model-calibration helper, not the authoritative live outage pose.

AI corrections require a validated, non-placeholder model and live calibration against distinct, recent accepted GPS observations with speed uncertainty at most 1 m/s and horizontal accuracy at most 15 m. Calibration needs at least 15 observations, at least 3 m/s speed variation and the existing residual/slope checks. Duplicate model timestamps cannot count twice. An accepted measurement updates ESKF velocity/covariance; raw AI output and actual ESKF correction activity are displayed separately.

The bundled models remain marked `validated: false`. This change does not silently certify or activate their weights. They continue diagnostic inference; an approved model installed through the existing laptop/model-release workflow can supply corrections after calibration. Physical evaluation is still needed before claiming the problem description's drift or lane-accuracy targets.

## Updated recordings and laptop learning

New sessions retain DRLOG v1 and add:

- `.sensors.csv`: original timestamps, sensor type, accuracy status and values for accelerometer, gyro, magnetometer, gravity and rotation-vector callbacks, before navigation filtering/rate limiting.
- `.gnss.csv`: full-precision receiver coordinates, fix and callback timestamps, UTC time, altitude, speed, bearing, available uncertainties, mock status and navigation acceptance.
- `.training.json`: explicit schema, units, device axes and receiver-GNSS label provenance.

The local files are included in the existing checksummed Supabase upload package. Declared missing sidecars and capture/flush failures block completion. Download reconstructs and verifies the sidecars with the same immutable manifest protocol. Legacy recordings remain readable.

Laptop training resamples the independent accelerometer and gyro streams into the existing three-second, six-channel model contract, retaining the existing 10 Hz block averaging. Labels come exclusively from accepted, non-mock, recent, sufficiently accurate receiver speed. Missing streams, excessive sensor gaps, invalid values and incomplete declared files are rejected. ESKF, map-matched positions and AI speed are never training labels. Old recordings use their existing quality-gated DRLOG path.

Run `python ml/phone_learning.py <recordings-directory> --out <output-directory>` using the project's ML environment to build a dataset, or use the existing laptop training agent for download, candidate training and evaluation. Whole-session train/validation/test splits and model promotion checks remain in place. Recording more data does not automatically establish that a candidate is better.

Replay uses precise receiver coordinates and callback-causal observation timing for new sessions. Its current inertial replay still uses DRLOG's synchronized IMU, estimated gravity and magnetometer-derived compass; it does not yet replay the raw Android rotation-vector/gravity callbacks or map matching. Evaluation therefore remains an offline regression check, not proof of physical navigation accuracy.

Technical references: [ESKF quaternion formulation](https://arxiv.org/abs/1711.02508), [Supabase Storage uploads](https://supabase.com/docs/guides/storage/uploads/standard-uploads). The existing upload protocol and bucket permissions were retained; no database schema or access policies were changed.

## Verification and phone setup

Verified on 2026-10-04: 127 Android unit tests passed (two existing opt-in tests skipped), 43 Python tests passed, 12 final Python recording/integration tests passed after the replay activity change, 14 focused UI/state tests passed, and the production web build passed. Legacy backend tests timed out connecting to local MongoDB, and the browser transition check timed out loading the local development server. Physical drift accuracy remains unverified; the later physical recording/cloud check is documented below.

The phone is not required for source changes or APK compilation. For installation through the laptop and physical verification, connect it by USB, enable USB debugging, and accept the debugging authorization on the phone. Record a short session with actual sensor/GNSS reception, upload it, and verify the downloaded raw sidecars before collecting longer mounted driving sessions. Model promotion still requires the existing evaluation gates; collecting data alone does not activate unvalidated weights.

## Physical phone and cloud verification

Version 1.6.0-offroute (17) was installed in place on the connected Samsung SM-S711B. Preferences and existing journey history matched their pre-install fingerprints before the test. The app launched with one engine and one location registration. A 30-second physical test captured 3,744 events each for accelerometer, gyroscope, magnetometer, gravity and rotation vector, plus 30 accepted, non-mock GPS observations. Laptop ingestion accepted 17 three-second training windows and rejected 13 labels for quality. No model weights were trained or promoted during this check.

The app's existing opted-in Wi-Fi upload completed. The laptop downloaded all seven files through its saved user-scoped Supabase login and verified the manifest and chunk/file checksums. Six files, including raw sensor and GNSS CSVs, were byte-identical to the phone copies. The portable summary matched the phone summary with its device-local path intentionally excluded by the existing packaging format. Downloaded inputs produced the same 17 training windows.

Evidence: `artifacts/eskf-phone/physical-recording-validation.json` and `artifacts/eskf-phone/cloud-round-trip-validation.json`. The short test does not measure driving/GPS-outage accuracy. The final phone snapshot reported live GNSS tracking, confirmed stop and AI inference awaiting phone validation; it does not establish active ESKF mounting alignment or accepted AI corrections on this stationary test.
