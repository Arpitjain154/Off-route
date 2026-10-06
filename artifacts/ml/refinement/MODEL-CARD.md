# Refined IO-VNBD research models

These models were trained offline using the same 144 checksum-verified synchronized CSV files as the original model set. No on-phone training occurs. Driver A is a previously examined regression set; do not call the new scores a fresh blind-test result.

The speed candidate adds rotation-invariant acceleration/gyro temporal features, dilated convolutions and extra weight for stationary examples. The motion candidate uses regularization and dropout with the same 42-feature input. GPS-health was retrained with a smaller regularized network but did not improve validation performance.

| Model | Validation result | Driver A regression result | Package decision |
|---|---|---|---|
| Speed | MAE 5.109 m/s, previously 7.098 | MAE 3.722 m/s, previously 4.088 | Bundle 1.1.0; corrections disabled |
| Motion | Balanced accuracy 61.29%, previously 55.35% | 68.30%, previously 63.81% | Bundle 1.1.0; corrections disabled |
| GNSS agreement proxy | Balanced accuracy 32.13%, previously 33.19% | 29.56% | Reject candidate; retain 1.0.0 |

All exports preserve the original input/output tensor contracts and pass Keras/TFLite parity checks. Exact hashes, sizes and matrices are in `report.json`; like-for-like validation comparisons are in `validation-comparison.json`.

Limitations remain material: 10 Hz training data cannot supply 100 Hz detail; motion uses weak CAN supervision for normal/idling/braking; pothole and phone-knocked outputs are fixed at zero. Receiver separation is only a GNSS agreement proxy, not a true outage or absolute-error label. C/N0 and fix-age inputs remain masked. Android gyro-frame correspondence needs hardware confirmation. The candidate speed model's stationary P95 is 15.90 m/s on validation and 7.27 m/s on Driver A, which prevents enabling it as a navigation correction. Successful numerical export is not field validation.

Promotion means bundled research inference only: `placeholder=false`, `validated=false`. Physical phone data with reference trajectories and labelled events is required before enabling learned corrections.
