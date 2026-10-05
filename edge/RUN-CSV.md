# CSV edge demo

Build once from the repository root: `android\gradlew.bat -p edge build installDist --offline` (Java 17+). Then run this one command from the repository root:

```powershell
java -cp "edge/build/install/idr-edge-engine/lib/*" com.idr.navigator.engine.IovnbdCsvRunner "data/iovnbd/Vf (Driver E)/V-Vfa01/S-Vfa01.csv" "artifacts/eval/final2/edge-native-trajectory.csv" --gps-speed-unit mps --outage-start 60 --outage-duration 120
```

The default consumes the phone CSV at its native timestamps (~10 Hz). It writes WGS84 position, speed, heading, uncertainty, availability, estimated state, GNSS-use status and source timestamp. `--resample-hz 200` causally holds the previous sensor sample at 200 Hz; this demonstrates API/rate support, not 200 Hz information or hardware timing certification.

`./scripts/test-edge-csv.ps1` runs the 39 original JAR tests and two CSV tests: native/resampled finite output and invariance to hidden GNSS coordinate/speed changes. The generated 20-second trace has 201 native rows and 4001 at 200 Hz.

The corpus speed header is misleading: this corpus was audited as m/s; other inputs must select `mps` or `kmh` explicitly. Constant recorded GRAVITY columns are ignored. The raw transport demo estimates initial tilt from the first accelerometer sample and passes raw gyro; it does not solve the recording-specific phone mount. Do not interpret this raw output as accuracy evidence. Use `python ml/evaluate_outages.py --label final2` for the separately calibrated, pre-outage-only accuracy fixture. External IMU applications should call EdgeNavigator with actual SI-frame gravity/compass/IMU measurements and monotonic nanoseconds.
