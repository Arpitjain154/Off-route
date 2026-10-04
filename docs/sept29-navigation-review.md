# Navigation review — 29 September 2026

## Findings and corrections

Reviewed both supplied WhatsApp screen recordings and the corresponding phone trip logs. The user confirmed that the phone was held/moved and that vehicle speed was roughly steady during the longer GPS outage. The matching log showed estimated speed increasing from 40.6 to 44.8 km/h over a 45.1-second outage. Another recorded outage increased from 41.3 to 82.7 km/h in 32.7 seconds. These are app estimates, not independent vehicle-speed measurements.

- **Speed calibration:** the old small per-fix bias update used smoothed speed changes and could leave a positive acceleration offset that masked gentle braking. Calibration now compares accumulated forward acceleration with raw, accurate GNSS speed changes over at least four seconds of suitable driving. Gaps, poor fixes, turns and loss of alignment interrupt calibration. Speed still responds to signed acceleration; no automatic speed decay was added.
- **Phone handling:** alignment is checked against the last GPS-calibrated gravity direction. Comparing only consecutive gravity samples missed gradual tilt. Significant tilt now invalidates the estimate until GPS can establish alignment again. A true vehicle yaw turn does not by itself invalidate the mount.
- **Map continuity:** losing sensor alignment holds the last estimated position and retains its uncertainty, instead of returning to the old pre-outage GPS coordinate. Unavailable estimates are no longer submitted as fresh road-matching observations.
- **Previous AI integration review:** inference results could influence the live estimator while the model's device-validation flag was false. The existing validation gate now controls speed corrections and motion/health noise adjustments. Models may still run for inference, but unvalidated output does not control navigation.

Changed native files: `InertialNavigation.java`, `LiveNavigation.java`, `SensorCaptureService.java`. All three bundled `.tflite` hashes are unchanged. This work does not train or publish a new model or implement the laptop training loop.

## Verification and delivery

- Reproduced the bias problem with failing controlled tests before fixing it. Regression coverage includes gentle braking with a positive offset, both signs of offset during steady motion, actual acceleration followed by braking, gradual phone tilt, vehicle turns, and holding position after alignment loss.
- Full Android unit tests: **89 total, 87 passed, zero failures, two skipped**. The skipped tests are opt-in IO-VNBD research-filter replay and state map construction. Cloud authentication, queue, packaging and model transaction tests were included in the passing tests.
- Rechecked the previous signup fix in the isolated browser fixture: validation, preserved retry credentials, trimmed email, disabled buttons during a request and signup confirmation all passed. The local server needed restarting; the fixture's initial render allowance was increased from five to fifteen seconds for cold startup and request-failure diagnostics were added. No app UI change or real signup was performed.
- Debug APK built successfully and installed in place on the connected phone. The previous installed APK is backed up at `artifacts/sept29-navigation/installed-before-navigation.apk`. No uninstall or recording deletion was performed.
- After launch, the native bridge returned `logging=false`, `trackingAvailable=false`, and `AI inference only · awaiting phone validation`. This confirms the validation gate's status on the device, not driving accuracy. Existing drive files remained present; the recent AndroidRuntime error check was empty.
- Build evidence: `artifacts/sept29-navigation/build.log`. Video sheets, trace summaries, before/after model hashes and the failing-test evidence are in the same folder. Delivery APK: `artifacts/IDR-Navigator-debug.apk`.

## Limits and next measurement

The existing recorded-data replay produced mixed endpoint changes; it does not establish an overall accuracy improvement. The user's raw recordings omit the live Android gravity/rotation-vector stream, so they cannot exactly reproduce the live estimator. The videos also do not provide independent speed truth during GPS loss.

A mounted-phone drive with reference speed, braking, turns, stops and measured GPS outages is needed to quantify improvement. Moving a phone independently of the vehicle can still corrupt heading and acceleration, especially rotations around gravity that the tilt guard cannot distinguish from a vehicle turn. GPS-free speed remains an estimate with growing uncertainty. Reliable self-training later needs good reference labels, separate evaluation trips and promotion only after device validation.
