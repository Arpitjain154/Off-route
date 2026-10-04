# IDR edge engine

This is the same timestamped live estimator used by the Android app, packaged without Android classes. Java 17 or newer is required. Put all `lib/*.jar` files on the host classpath.

Current source routes live navigation and calibrated AI speed observations through `NavigationFusion`'s ESKF. See [live ESKF and recording details](../docs/live-eskf-and-training.md). Rebuild any previously packaged edge JAR to use these source changes.

Call `EdgeNavigator` from one thread. All timestamps are monotonic nanoseconds. Accelerometer values include gravity and use m/s²; gyroscope values use radians/second. Gravity, acceleration and angular velocity must share the sensor coordinate frame. Compass heading is clockwise from true north in degrees. GNSS speed is m/s, coordinates are WGS84 degrees, and accuracy fields are metres, m/s and degrees respectively. Report missing GNSS measurements as `Double.NaN`, not zero.

Feed `gravity`, `compass`, `imu` and `gnss` observations in timestamp order. Call `gnssUnavailable` for an explicit provider outage and continue IMU updates. Read `position(now)` at the consumer's desired rate. The IMU path is exercised at 200 Hz in the native test suite; this does not claim a specific external IMU's calibrated accuracy or real-time scheduling guarantees.

The `models` method accepts predictions from a host's inference runtime. The Android app bundles TFLite inference; this Java distribution does not include a cross-platform TFLite native runtime. A host must implement the same model input contract described in `ml/README.md`. `roadMatch` accepts a host road match, applying bounded cross-track correction only. No road database is bundled in this Java distribution.

**Not accepted for deployment:** the current IO-VNBD replay fails the requested 10% drift benchmark. Read `artifacts/qa-1.5/iovnbd/accuracy-gate.json` and the trajectory plots. Model uncertainty is not a calibrated accuracy guarantee. Correct sensor frame metadata and device validation remain required; do not interpret continuous estimated output as lane-level accuracy.
