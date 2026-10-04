# OFFROUTE outage evaluation

Baseline source: `41670ae7`. Run: `C:\Users\surya\Documents\ChatGPT\offroute 3.0\.venv-ml\Scripts\python.exe ml/evaluate_outages.py --label followup/live-a`.

| Split | Subset | Seconds | Ablation | N | Median drift % | Mean drift % | Under 10% (%) | ATE m | Speed MAE m/s | Speed RMSE m/s |
|---|---|---:|---|---:|---:|---:|---:|---:|---:|---:|
| regression-S | all | 30 | ESKF + A (live source) | 12 | 59.760 | 430.353 | 0.000 | 74.158 | 3.949 | 4.627 |
| regression-S | all | 30 | ESKF baseline commit | 12 | 185.773 | 1496.887 | 0.000 | 173.805 | 7.034 | 8.487 |
| regression-S | all | 30 | last GNSS speed + constant heading | 12 | 73.279 | 618.179 | 0.000 | 93.890 | 4.180 | 4.739 |
| regression-S | all | 60 | ESKF + A (live source) | 12 | 47.971 | 61.195 | 0.000 | 133.266 | 4.741 | 5.573 |
| regression-S | all | 60 | ESKF baseline commit | 12 | 88.980 | 694.535 | 0.000 | 397.567 | 9.492 | 11.078 |
| regression-S | all | 60 | last GNSS speed + constant heading | 12 | 84.199 | 95.535 | 0.000 | 193.368 | 4.792 | 5.417 |
| regression-S | all | 120 | ESKF + A (live source) | 12 | 59.482 | 56.074 | 0.000 | 320.911 | 5.221 | 6.141 |
| regression-S | all | 120 | ESKF baseline commit | 12 | 73.870 | 1206.041 | 0.000 | 892.908 | 12.863 | 14.763 |
| regression-S | all | 120 | last GNSS speed + constant heading | 12 | 85.203 | 96.911 | 0.000 | 431.363 | 5.131 | 5.809 |
| validation-Vfa | all | 30 | ESKF + A (live source) | 4 | 18.737 | 22.281 | 25.000 | 66.893 | 3.315 | 3.926 |
| validation-Vfa | all | 30 | ESKF baseline commit | 4 | 66.192 | 62.839 | 0.000 | 173.351 | 6.547 | 7.664 |
| validation-Vfa | all | 30 | last GNSS speed + constant heading | 4 | 11.535 | 12.215 | 50.000 | 38.745 | 1.239 | 1.530 |
| validation-Vfa | all | 60 | ESKF + A (live source) | 4 | 21.365 | 27.684 | 25.000 | 160.176 | 4.071 | 4.770 |
| validation-Vfa | all | 60 | ESKF baseline commit | 4 | 80.684 | 77.097 | 0.000 | 463.944 | 10.120 | 11.272 |
| validation-Vfa | all | 60 | last GNSS speed + constant heading | 4 | 15.943 | 18.774 | 50.000 | 105.416 | 1.720 | 2.057 |
| validation-Vfa | all | 120 | ESKF + A (live source) | 4 | 19.252 | 30.422 | 50.000 | 395.290 | 4.498 | 5.230 |
| validation-Vfa | all | 120 | ESKF baseline commit | 4 | 73.884 | 77.768 | 0.000 | 1083.556 | 12.643 | 13.876 |
| validation-Vfa | all | 120 | last GNSS speed + constant heading | 4 | 15.485 | 23.681 | 25.000 | 317.705 | 1.999 | 2.390 |
| regression-S | >=40 km/h | 30 | ESKF + A (live source) | 3 | 39.252 | 43.087 | 0.000 | 100.250 | 5.483 | 6.449 |
| regression-S | >=40 km/h | 30 | ESKF baseline commit | 3 | 52.926 | 45.034 | 0.000 | 107.381 | 4.512 | 5.430 |
| regression-S | >=40 km/h | 30 | last GNSS speed + constant heading | 3 | 56.466 | 56.900 | 0.000 | 125.511 | 6.366 | 6.824 |
| regression-S | >=40 km/h | 60 | ESKF + A (live source) | 3 | 57.598 | 48.569 | 0.000 | 214.060 | 7.365 | 8.201 |
| regression-S | >=40 km/h | 60 | ESKF baseline commit | 3 | 56.739 | 57.963 | 0.000 | 243.058 | 5.198 | 6.291 |
| regression-S | >=40 km/h | 60 | last GNSS speed + constant heading | 3 | 65.269 | 62.728 | 0.000 | 267.583 | 7.119 | 7.532 |
| regression-S | >=40 km/h | 120 | ESKF + A (live source) | 1 | 72.788 | 72.788 | 0.000 | 571.907 | 6.545 | 7.142 |
| regression-S | >=40 km/h | 120 | ESKF baseline commit | 1 | 76.936 | 76.936 | 0.000 | 674.548 | 4.694 | 5.638 |
| regression-S | >=40 km/h | 120 | last GNSS speed + constant heading | 1 | 49.261 | 49.261 | 0.000 | 394.800 | 3.959 | 4.452 |
| validation-Vfa | >=40 km/h | 30 | ESKF + A (live source) | 4 | 18.737 | 22.281 | 25.000 | 66.893 | 3.315 | 3.926 |
| validation-Vfa | >=40 km/h | 30 | ESKF baseline commit | 4 | 66.192 | 62.839 | 0.000 | 173.351 | 6.547 | 7.664 |
| validation-Vfa | >=40 km/h | 30 | last GNSS speed + constant heading | 4 | 11.535 | 12.215 | 50.000 | 38.745 | 1.239 | 1.530 |
| validation-Vfa | >=40 km/h | 60 | ESKF + A (live source) | 4 | 21.365 | 27.684 | 25.000 | 160.176 | 4.071 | 4.770 |
| validation-Vfa | >=40 km/h | 60 | ESKF baseline commit | 4 | 80.684 | 77.097 | 0.000 | 463.944 | 10.120 | 11.272 |
| validation-Vfa | >=40 km/h | 60 | last GNSS speed + constant heading | 4 | 15.943 | 18.774 | 50.000 | 105.416 | 1.720 | 2.057 |
| validation-Vfa | >=40 km/h | 120 | ESKF + A (live source) | 4 | 19.252 | 30.422 | 50.000 | 395.290 | 4.498 | 5.230 |
| validation-Vfa | >=40 km/h | 120 | ESKF baseline commit | 4 | 73.884 | 77.768 | 0.000 | 1083.556 | 12.643 | 13.876 |
| validation-Vfa | >=40 km/h | 120 | last GNSS speed + constant heading | 4 | 15.485 | 23.681 | 25.000 | 317.705 | 1.999 | 2.390 |

Map matching: **not evaluated**. Bundled graph covers Madhya Pradesh; no Coventry graph is supplied.

Limitations: Vfa is validation; S* is a previously reused regression set, not a blind test. No training data is used here. First 600 seconds per local recording; independent outages start at 60 and 300 seconds, with 60 seconds of warmup. Phone/vehicle elapsed clocks are aligned as in the existing harness; vehicle GNSS is a noisy scoring reference, never estimator input. Paths are translated to zero at the first outage sample (relative ATE; no rotation or scale fitting). Speed errors use vehicle GNSS speed. ATE and speed metrics in the table are means of per-window metrics. The high-speed subset uses mean reference speed >=40 km/h. Near-stationary distance under 1 m is excluded. Frame calibration uses only pre-outage phone GNSS and raw accelerometer/gyro; constant GRAVITY columns are ignored. Planar replay suppresses unidentifiable transverse gyro components. Low-correlation mappings are flagged, not claimed calibrated. The 10 Hz recording is held at 100 Hz for the existing filter; output is 10 Hz. This is not a measured mobile performance result. AI uses the unchanged 300x6 -> 1 bundled model, with experimental variance 25 (m/s)^2 and trust 0.10; it is not validated or enabled live.

Live Stage A source evaluated with BOUNDED_SPEED enabled. Stage C and AI are absent from this ablation. Promotion uses aggregate median/mean drift, legacy replay and existing tests; individual regressions alone do not reject it. See the follow-up verification report for the complete promotion gate.