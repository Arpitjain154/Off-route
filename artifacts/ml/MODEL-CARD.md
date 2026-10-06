# IO-VNBD models 1.0.0

Trained locally on 2026-09-13 using TensorFlow 2.20.0, seed 42. These learned models replace the constant-output placeholders. They are packaged in Android and published to the local MongoDB-backed model registry.

## Data and reproducibility

Source: [onyekpeu/IO-VNBD](https://github.com/onyekpeu/IO-VNBD/tree/118939602e3422d47b8ab0807b623751c3ac135b), revision `118939602e3422d47b8ab0807b623751c3ac135b`. Downloaded all 144 CSV files (72 paired recordings, 426.4 MB) in the synchronized categorized subset. Each file was verified against its Git LFS SHA-256 and size. Full filenames and hashes are in `data-audit.json`.

The authors manually synchronized these paired files. We anchor their first samples and join subsequent elapsed timestamps within 61 ms. This handles the smartphone local-clock versus vehicle UTC offset, including summer recordings. Windows crossing gaps, nonfinite measurements, or invalid reference readings are excluded. Residual source synchronization and receiver errors remain limitations.

Three-second windows, one-second hop. Training: **67,294** windows; validation: **7,893** (entire Vf family, Driver E); test: **30,539** (all Driver A/S recordings). No recording or overlapping window crosses the partitions. Validation selects the epoch; test data is excluded from fitting and normalization.

The 10 Hz IMU is repeated to the fixed 300×6 interface. Android averages each ten incoming 100 Hz samples and repeats each average, matching this representation. Repetition adds no signal information. Accelerometer values include gravity; gyroscope values use the source's three recorded channels. Speed uses acceleration and angular-rate magnitudes to reduce orientation sensitivity. Motion remains sensitive to device/mount differences.

## Held-out results

| Model | Result | Interpretation |
|---|---|---|
| Speed | MAE **4.09 m/s** (14.72 km/h), RMSE 5.71 m/s | Better than training-median baseline MAE 5.98 m/s; insufficient for precise navigation |
| Motion | Balanced accuracy **63.81%** | Weak CAN labels: normal driving, idling, strong braking |
| GPS health | Balanced accuracy **28.23%** | Failed to generalize; below a three-class chance baseline |

Motion labels derive from indicated CAN speed and acceleration/brake position. Pothole and phone-knock annotations are absent; those two output probabilities are fixed at zero. The five-output interface is preserved, but this is not a trained five-class detector.

GPS labels are receiver-separation proxies (≤10 m, ≤35 m, >35 m), not surveyed ground truth or labeled blackout events. The dataset lacks C/N0 and reliable fix-age features; their four input channels are masked inside the model. The proxy classifier must not replace GPS-health rules.

All three exports use TFLite built-in operations. Input/output tensor shapes and 100 held-out Keras-versus-TFLite predictions were checked; maximum absolute discrepancy was below 0.000015. See `training-report.json` for confusion matrices, model hashes, sizes, epochs and exact metrics. Compressed learned weights are retained alongside the TFLite files.

## App behavior and next validation

The models run inference on Android. Metadata sets `placeholder=false`, `validated=false`. Learned fusion corrections and classifier-triggered realignment remain disabled; physical and GPS rules continue to drive navigation. Installing these models does not claim improved real-world drift. Native execution and OTA must still be tested on a connected Android device.

Before enabling corrections, collect independently labeled phone drives, including representative Bhopal mounts and controlled signal gaps. Evaluate end-to-end position drift and speed against an independent reference. Improve the GPS classifier and add event labels for the two unsupported motion classes. Browser Test drive remains a labeled simulation and does not run native TFLite inference.

Reproduce with `ml/download_iovnbd.py`, then `ml/train_iovnbd.py`; pinned Python dependencies are in `ml/requirements-lock.txt`. No Python training code is included in the APK.

## Stationary export regression

`ml/test_trained_models.py` checks exported model hashes, finite outputs, unsupported motion classes fixed to zero, and invariance to unavailable GNSS fields. On 1,236 held-out stationary windows, the learned speed model had MAE 1.36 m/s and a 95th percentile prediction of 8.23 m/s. This reinforces keeping the learned speed correction disabled. Android stationary speed is determined by GNSS plus IMU stop detection, not this model. Exact results are in `export-regression.json`.
