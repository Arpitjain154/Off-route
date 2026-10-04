# Java native layer

Current navigation/recording architecture and model-validation limits are documented in [Live ESKF and phone training recordings](../docs/live-eskf-and-training.md). LiveNavigation owns the service's ESKF; AI diagnostic speed is distinct from accepted ESKF corrections. New recordings include precise `.sensors.csv` and `.gnss.csv` sidecars in the existing cloud package.

See the root README for building. API 26+, JDK 21, SDK 36, Gradle 8.14.3. All app-owned native code is Java. EJML implements matrix operations; GraphHopper supplies HMM matching; the official TensorFlow Lite dependency includes its normal platform runtime binaries.

`SensorCaptureService` owns a single HandlerThread for sensors, ESKF prediction and measurement updates. A single-thread worker loads the OSM-derived graph and matches a bounded observation window. The UI polls immutable fused pose snapshots at 10 Hz; raw high-frequency samples never enter JavaScript. The service starts only from a visible activity with location permission and holds a persistent notification and partial wake lock until stopped.

The ESKF uses ENU position and velocity, a phone-to-world quaternion, accelerometer bias, and gyro bias. Its 15D error covariance propagates position/velocity/attitude/bias coupling. Updates use innovation gating, Joseph covariance form and attitude reset Jacobian. GPS, gravity, ZUPT/NHC, HMM cross-track corrections and trusted learned speed submit residual/Jacobian/covariance measurements. Live mounting calibration uses fresh gravity, compass and a moving GPS course, including sideways mounts. A changed mounting direction must be calibrated again before inertial tracking is considered available.

The bundled IO-VNBD motion model has no phone-knock training labels, so that output is fixed at zero. All learned corrections also require a separate `validated` flag, false in the bundled models. ZUPT requires quiet IMU and independent GNSS stop evidence (reliable low speed, or noisy low speed supported by compact position history), since a smoothly moving car can also have low IMU variance. A confirmed stop can persist through GPS loss until motion resumes. NHC is enabled only after heading/mount alignment. These constraints cannot guarantee bounded drift during arbitrary outages.

The HMM sigma is 30 m. It operates independently of ML and rejects matches outside the packaged central-Bhopal bounds. It supplies a soft position measurement (not an independent navigation state). A prebuilt graph from the passing JVM Bhopal test is bundled, avoiding an OSM XML import on first phone launch. It is copied into private storage before loading. After changing the extract, regenerate the graph using the HMM test with a fresh build/test-map-graph directory and copy its files to assets/maps/graph-v7.

`ModelPlugin.downloadModel` validates and installs a file staged by Axios + Capacitor Filesystem; despite the name, Java does not make an internet request. SHA-256 and tensor shapes are checked, and candidate files are moved atomically in the private models directory. Model metadata/version/placeholder/validated flags are persisted. Bundled 1.0.0 IO-VNBD models migrate the original constant 0.0.0 files on engine start. Later OTA versions are preserved. FeatureBuffer block averages raw IMU to the source's 10 Hz representation; the separate physics filter keeps its existing preprocessing.

## Hardware validation still required

1. Install debug APK, request precise location and notification permissions, start engine outdoors and wait for a fix. Check that all three sensor types exist on the target phone.
2. Mount phone securely and drive forward to establish heading. Check heading convention, units and measured sensor frequency against actual timestamps.
3. Record a short drive, turn off the screen for several minutes, reopen and inspect the foreground notification and log continuity. Check OEM battery restrictions explicitly.
4. Test loss of GPS in a controlled route, then reacquisition. Compare against a separately measured ground-truth trajectory; do not infer road accuracy from the synthetic tests.
5. Verify `TfliteModels` loads all three shapes and executes; publish an updated placeholder and confirm version and checksum installation from Settings. Invalid files must leave the old model intact.
6. Upload a recording using a reachable backend, verify returned metadata, download and parse it with `ml/read_drlog.py`.
7. Exercise permission denial/revocation, missing sensors, no initial fix, no network, full storage and OS process termination. Android can terminate foreground services; no application can promise uninterrupted capture under all OS policies.

The local tests cover a 60-second straight-line trip with a 40-second GPS blackout, outlier rejection, zero-velocity correction, binary byte offsets, and HMM matching of real OSM geometry with injected 25-meter drift. They do not replace physical-device testing.
