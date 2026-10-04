# OFFROUTE outage evaluation

Baseline source: `41670ae7`. Run: `C:\Users\surya\Documents\ChatGPT\offroute 3.0\.venv-ml\Scripts\python.exe ml/evaluate_outages.py --label stage-a`.

| Split | Subset | Seconds | Ablation | N | Median drift % | Mean drift % | Under 10% (%) | ATE m | Speed MAE m/s | Speed RMSE m/s |
|---|---|---:|---|---:|---:|---:|---:|---:|---:|---:|
| regression-S | all | 30 | ESKF + stages | 12 | 81.947 | 432.868 | 0.000 | 89.008 | 3.954 | 4.691 |
| regression-S | all | 30 | ESKF baseline commit | 12 | 380.611 | 1448.576 | 0.000 | 245.500 | 9.598 | 11.264 |
| regression-S | all | 30 | last GNSS speed + constant heading | 12 | 73.279 | 618.179 | 0.000 | 93.890 | 4.180 | 4.739 |
| regression-S | all | 60 | ESKF + stages | 12 | 81.638 | 74.749 | 8.333 | 184.372 | 4.769 | 5.747 |
| regression-S | all | 60 | ESKF baseline commit | 12 | 189.811 | 335.877 | 0.000 | 667.026 | 15.046 | 17.254 |
| regression-S | all | 60 | last GNSS speed + constant heading | 12 | 84.199 | 95.535 | 0.000 | 193.368 | 4.792 | 5.417 |
| regression-S | all | 120 | ESKF + stages | 12 | 69.119 | 66.864 | 0.000 | 436.101 | 5.342 | 6.375 |
| regression-S | all | 120 | ESKF baseline commit | 12 | 405.449 | 573.768 | 0.000 | 1777.935 | 23.862 | 27.362 |
| regression-S | all | 120 | last GNSS speed + constant heading | 12 | 85.203 | 96.911 | 0.000 | 431.363 | 5.131 | 5.809 |
| validation-Vfa | all | 30 | ESKF + stages | 4 | 33.470 | 34.982 | 25.000 | 100.035 | 3.052 | 3.445 |
| validation-Vfa | all | 30 | ESKF baseline commit | 4 | 53.321 | 57.368 | 0.000 | 152.614 | 7.228 | 8.362 |
| validation-Vfa | all | 30 | last GNSS speed + constant heading | 4 | 11.535 | 12.215 | 50.000 | 38.745 | 1.239 | 1.530 |
| validation-Vfa | all | 60 | ESKF + stages | 4 | 51.778 | 53.878 | 0.000 | 290.486 | 2.717 | 3.133 |
| validation-Vfa | all | 60 | ESKF baseline commit | 4 | 73.041 | 76.463 | 0.000 | 465.899 | 9.893 | 11.176 |
| validation-Vfa | all | 60 | last GNSS speed + constant heading | 4 | 15.943 | 18.774 | 50.000 | 105.416 | 1.720 | 2.057 |
| validation-Vfa | all | 120 | ESKF + stages | 4 | 69.727 | 73.912 | 0.000 | 873.177 | 3.328 | 3.878 |
| validation-Vfa | all | 120 | ESKF baseline commit | 4 | 87.092 | 86.087 | 0.000 | 1143.940 | 12.043 | 13.312 |
| validation-Vfa | all | 120 | last GNSS speed + constant heading | 4 | 15.485 | 23.681 | 25.000 | 317.705 | 1.999 | 2.390 |
| regression-S | >=40 km/h | 30 | ESKF + stages | 3 | 85.836 | 67.203 | 0.000 | 137.147 | 7.020 | 7.603 |
| regression-S | >=40 km/h | 30 | ESKF baseline commit | 3 | 77.466 | 97.125 | 0.000 | 189.978 | 5.322 | 6.005 |
| regression-S | >=40 km/h | 30 | last GNSS speed + constant heading | 3 | 56.466 | 56.900 | 0.000 | 125.511 | 6.366 | 6.824 |
| regression-S | >=40 km/h | 60 | ESKF + stages | 3 | 92.401 | 87.754 | 0.000 | 338.213 | 7.569 | 8.364 |
| regression-S | >=40 km/h | 60 | ESKF baseline commit | 3 | 141.812 | 135.443 | 0.000 | 484.405 | 8.253 | 9.864 |
| regression-S | >=40 km/h | 60 | last GNSS speed + constant heading | 3 | 65.269 | 62.728 | 0.000 | 267.583 | 7.119 | 7.532 |
| regression-S | >=40 km/h | 120 | ESKF + stages | 1 | 140.981 | 140.981 | 0.000 | 1245.274 | 3.693 | 4.796 |
| regression-S | >=40 km/h | 120 | ESKF baseline commit | 1 | 154.051 | 154.051 | 0.000 | 1386.414 | 7.184 | 8.946 |
| regression-S | >=40 km/h | 120 | last GNSS speed + constant heading | 1 | 49.261 | 49.261 | 0.000 | 394.800 | 3.959 | 4.452 |
| validation-Vfa | >=40 km/h | 30 | ESKF + stages | 4 | 33.470 | 34.982 | 25.000 | 100.035 | 3.052 | 3.445 |
| validation-Vfa | >=40 km/h | 30 | ESKF baseline commit | 4 | 53.321 | 57.368 | 0.000 | 152.614 | 7.228 | 8.362 |
| validation-Vfa | >=40 km/h | 30 | last GNSS speed + constant heading | 4 | 11.535 | 12.215 | 50.000 | 38.745 | 1.239 | 1.530 |
| validation-Vfa | >=40 km/h | 60 | ESKF + stages | 4 | 51.778 | 53.878 | 0.000 | 290.486 | 2.717 | 3.133 |
| validation-Vfa | >=40 km/h | 60 | ESKF baseline commit | 4 | 73.041 | 76.463 | 0.000 | 465.899 | 9.893 | 11.176 |
| validation-Vfa | >=40 km/h | 60 | last GNSS speed + constant heading | 4 | 15.943 | 18.774 | 50.000 | 105.416 | 1.720 | 2.057 |
| validation-Vfa | >=40 km/h | 120 | ESKF + stages | 4 | 69.727 | 73.912 | 0.000 | 873.177 | 3.328 | 3.878 |
| validation-Vfa | >=40 km/h | 120 | ESKF baseline commit | 4 | 87.092 | 86.087 | 0.000 | 1143.940 | 12.043 | 13.312 |
| validation-Vfa | >=40 km/h | 120 | last GNSS speed + constant heading | 4 | 15.485 | 23.681 | 25.000 | 317.705 | 1.999 | 2.390 |

Map matching: **not evaluated**. Bundled graph covers Madhya Pradesh; no Coventry graph is supplied.

Limitations: Vfa is validation; S* is a previously reused regression set, not a blind test. No training data is used here. First 600 seconds per local recording; independent outages start at 60 and 300 seconds, with 60 seconds of warmup. Phone/vehicle elapsed clocks are aligned as in the existing harness; vehicle GNSS is a noisy scoring reference, never estimator input. Paths are translated to zero at the first outage sample (relative ATE; no rotation or scale fitting). Speed errors use vehicle GNSS speed. ATE and speed metrics in the table are means of per-window metrics. The high-speed subset uses mean reference speed >=40 km/h. Near-stationary distance under 1 m is excluded. Frame calibration uses only pre-outage phone GNSS and raw accelerometer/gyro; constant GRAVITY columns are ignored. Planar replay suppresses unidentifiable transverse gyro components. Low-correlation mappings are flagged, not claimed calibrated. The 10 Hz recording is held at 100 Hz for the existing filter; output is 10 Hz. This is not a measured mobile performance result. AI uses the unchanged 300x6 -> 1 bundled model, with experimental variance 25 (m/s)^2 and trust 0.10; it is not validated or enabled live.