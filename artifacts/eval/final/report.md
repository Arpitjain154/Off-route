# OFFROUTE outage evaluation

Baseline source: `41670ae7`. Run: `C:\Users\surya\Documents\ChatGPT\offroute 3.0\.venv-ml\Scripts\python.exe ml/evaluate_outages.py --label final`.

| Split | Subset | Seconds | Ablation | N | Median drift % | Mean drift % | Under 10% (%) | ATE m | Speed MAE m/s | Speed RMSE m/s |
|---|---|---:|---|---:|---:|---:|---:|---:|---:|---:|
| regression-S | all | 30 | ESKF + A (rejected) | 12 | 59.760 | 430.353 | 0.000 | 74.158 | 3.949 | 4.627 |
| regression-S | all | 30 | ESKF + A+C (experimental; B not evaluated) | 12 | 56.745 | 425.540 | 0.000 | 72.792 | 3.957 | 4.638 |
| regression-S | all | 30 | ESKF + A+C (experimental; B not evaluated) + AI | 12 | 51.770 | 73.827 | 0.000 | 61.240 | 3.588 | 4.283 |
| regression-S | all | 30 | ESKF + C only | 12 | 177.859 | 1441.214 | 0.000 | 174.914 | 6.954 | 8.355 |
| regression-S | all | 30 | ESKF baseline commit | 12 | 185.773 | 1496.887 | 0.000 | 173.805 | 7.034 | 8.487 |
| regression-S | all | 30 | last GNSS speed + constant heading | 12 | 73.279 | 618.179 | 0.000 | 93.890 | 4.180 | 4.739 |
| regression-S | all | 60 | ESKF + A (rejected) | 12 | 47.971 | 61.195 | 0.000 | 133.266 | 4.741 | 5.573 |
| regression-S | all | 60 | ESKF + A+C (experimental; B not evaluated) | 12 | 41.874 | 60.089 | 0.000 | 132.064 | 4.732 | 5.563 |
| regression-S | all | 60 | ESKF + A+C (experimental; B not evaluated) + AI | 12 | 35.589 | 57.042 | 8.333 | 110.742 | 4.141 | 4.944 |
| regression-S | all | 60 | ESKF + C only | 12 | 82.254 | 657.892 | 0.000 | 398.773 | 9.223 | 10.762 |
| regression-S | all | 60 | ESKF baseline commit | 12 | 88.980 | 694.535 | 0.000 | 397.567 | 9.492 | 11.078 |
| regression-S | all | 60 | last GNSS speed + constant heading | 12 | 84.199 | 95.535 | 0.000 | 193.368 | 4.792 | 5.417 |
| regression-S | all | 120 | ESKF + A (rejected) | 12 | 59.482 | 56.074 | 0.000 | 320.911 | 5.221 | 6.141 |
| regression-S | all | 120 | ESKF + A+C (experimental; B not evaluated) | 12 | 59.538 | 58.427 | 0.000 | 323.189 | 5.209 | 6.128 |
| regression-S | all | 120 | ESKF + A+C (experimental; B not evaluated) + AI | 12 | 38.706 | 52.851 | 0.000 | 278.711 | 4.210 | 5.106 |
| regression-S | all | 120 | ESKF + C only | 12 | 96.566 | 1103.681 | 0.000 | 908.168 | 12.704 | 14.705 |
| regression-S | all | 120 | ESKF baseline commit | 12 | 73.870 | 1206.041 | 0.000 | 892.908 | 12.863 | 14.763 |
| regression-S | all | 120 | last GNSS speed + constant heading | 12 | 85.203 | 96.911 | 0.000 | 431.363 | 5.131 | 5.809 |
| validation-Vfa | all | 30 | ESKF + A (rejected) | 4 | 18.737 | 22.281 | 25.000 | 66.893 | 3.315 | 3.926 |
| validation-Vfa | all | 30 | ESKF + A+C (experimental; B not evaluated) | 4 | 22.845 | 20.672 | 25.000 | 63.106 | 3.358 | 3.958 |
| validation-Vfa | all | 30 | ESKF + A+C (experimental; B not evaluated) + AI | 4 | 16.792 | 18.467 | 0.000 | 57.403 | 2.630 | 3.179 |
| validation-Vfa | all | 30 | ESKF + C only | 4 | 59.280 | 60.975 | 0.000 | 166.930 | 5.949 | 6.972 |
| validation-Vfa | all | 30 | ESKF baseline commit | 4 | 66.192 | 62.839 | 0.000 | 173.351 | 6.547 | 7.664 |
| validation-Vfa | all | 30 | last GNSS speed + constant heading | 4 | 11.535 | 12.215 | 50.000 | 38.745 | 1.239 | 1.530 |
| validation-Vfa | all | 60 | ESKF + A (rejected) | 4 | 21.365 | 27.684 | 25.000 | 160.176 | 4.071 | 4.770 |
| validation-Vfa | all | 60 | ESKF + A+C (experimental; B not evaluated) | 4 | 22.899 | 25.295 | 0.000 | 156.763 | 4.082 | 4.763 |
| validation-Vfa | all | 60 | ESKF + A+C (experimental; B not evaluated) + AI | 4 | 19.595 | 20.351 | 0.000 | 135.739 | 3.269 | 3.849 |
| validation-Vfa | all | 60 | ESKF + C only | 4 | 84.615 | 79.543 | 0.000 | 472.453 | 9.250 | 10.445 |
| validation-Vfa | all | 60 | ESKF baseline commit | 4 | 80.684 | 77.097 | 0.000 | 463.944 | 10.120 | 11.272 |
| validation-Vfa | all | 60 | last GNSS speed + constant heading | 4 | 15.943 | 18.774 | 50.000 | 105.416 | 1.720 | 2.057 |
| validation-Vfa | all | 120 | ESKF + A (rejected) | 4 | 19.252 | 30.422 | 50.000 | 395.290 | 4.498 | 5.230 |
| validation-Vfa | all | 120 | ESKF + A+C (experimental; B not evaluated) | 4 | 38.822 | 37.909 | 0.000 | 479.550 | 4.511 | 5.228 |
| validation-Vfa | all | 120 | ESKF + A+C (experimental; B not evaluated) + AI | 4 | 33.084 | 33.191 | 0.000 | 425.384 | 3.977 | 4.681 |
| validation-Vfa | all | 120 | ESKF + C only | 4 | 75.946 | 78.537 | 0.000 | 1095.485 | 11.063 | 12.637 |
| validation-Vfa | all | 120 | ESKF baseline commit | 4 | 73.884 | 77.768 | 0.000 | 1083.556 | 12.643 | 13.876 |
| validation-Vfa | all | 120 | last GNSS speed + constant heading | 4 | 15.485 | 23.681 | 25.000 | 317.705 | 1.999 | 2.390 |
| regression-S | >=40 km/h | 30 | ESKF + A (rejected) | 3 | 39.252 | 43.087 | 0.000 | 100.250 | 5.483 | 6.449 |
| regression-S | >=40 km/h | 30 | ESKF + A+C (experimental; B not evaluated) | 3 | 41.777 | 41.225 | 0.000 | 95.467 | 5.494 | 6.476 |
| regression-S | >=40 km/h | 30 | ESKF + A+C (experimental; B not evaluated) + AI | 3 | 36.532 | 35.949 | 0.000 | 89.619 | 5.210 | 6.112 |
| regression-S | >=40 km/h | 30 | ESKF + C only | 3 | 63.230 | 51.627 | 0.000 | 118.931 | 4.232 | 5.141 |
| regression-S | >=40 km/h | 30 | ESKF baseline commit | 3 | 52.926 | 45.034 | 0.000 | 107.381 | 4.512 | 5.430 |
| regression-S | >=40 km/h | 30 | last GNSS speed + constant heading | 3 | 56.466 | 56.900 | 0.000 | 125.511 | 6.366 | 6.824 |
| regression-S | >=40 km/h | 60 | ESKF + A (rejected) | 3 | 57.598 | 48.569 | 0.000 | 214.060 | 7.365 | 8.201 |
| regression-S | >=40 km/h | 60 | ESKF + A+C (experimental; B not evaluated) | 3 | 46.365 | 46.491 | 0.000 | 211.002 | 7.367 | 8.221 |
| regression-S | >=40 km/h | 60 | ESKF + A+C (experimental; B not evaluated) + AI | 3 | 34.529 | 34.357 | 0.000 | 163.589 | 5.894 | 6.583 |
| regression-S | >=40 km/h | 60 | ESKF + C only | 3 | 65.413 | 62.399 | 0.000 | 265.328 | 4.948 | 5.964 |
| regression-S | >=40 km/h | 60 | ESKF baseline commit | 3 | 56.739 | 57.963 | 0.000 | 243.058 | 5.198 | 6.291 |
| regression-S | >=40 km/h | 60 | last GNSS speed + constant heading | 3 | 65.269 | 62.728 | 0.000 | 267.583 | 7.119 | 7.532 |
| regression-S | >=40 km/h | 120 | ESKF + A (rejected) | 1 | 72.788 | 72.788 | 0.000 | 571.907 | 6.545 | 7.142 |
| regression-S | >=40 km/h | 120 | ESKF + A+C (experimental; B not evaluated) | 1 | 55.750 | 55.750 | 0.000 | 446.695 | 6.613 | 7.224 |
| regression-S | >=40 km/h | 120 | ESKF + A+C (experimental; B not evaluated) + AI | 1 | 35.011 | 35.011 | 0.000 | 319.304 | 5.724 | 6.238 |
| regression-S | >=40 km/h | 120 | ESKF + C only | 1 | 67.131 | 67.131 | 0.000 | 668.957 | 3.644 | 4.537 |
| regression-S | >=40 km/h | 120 | ESKF baseline commit | 1 | 76.936 | 76.936 | 0.000 | 674.548 | 4.694 | 5.638 |
| regression-S | >=40 km/h | 120 | last GNSS speed + constant heading | 1 | 49.261 | 49.261 | 0.000 | 394.800 | 3.959 | 4.452 |
| validation-Vfa | >=40 km/h | 30 | ESKF + A (rejected) | 4 | 18.737 | 22.281 | 25.000 | 66.893 | 3.315 | 3.926 |
| validation-Vfa | >=40 km/h | 30 | ESKF + A+C (experimental; B not evaluated) | 4 | 22.845 | 20.672 | 25.000 | 63.106 | 3.358 | 3.958 |
| validation-Vfa | >=40 km/h | 30 | ESKF + A+C (experimental; B not evaluated) + AI | 4 | 16.792 | 18.467 | 0.000 | 57.403 | 2.630 | 3.179 |
| validation-Vfa | >=40 km/h | 30 | ESKF + C only | 4 | 59.280 | 60.975 | 0.000 | 166.930 | 5.949 | 6.972 |
| validation-Vfa | >=40 km/h | 30 | ESKF baseline commit | 4 | 66.192 | 62.839 | 0.000 | 173.351 | 6.547 | 7.664 |
| validation-Vfa | >=40 km/h | 30 | last GNSS speed + constant heading | 4 | 11.535 | 12.215 | 50.000 | 38.745 | 1.239 | 1.530 |
| validation-Vfa | >=40 km/h | 60 | ESKF + A (rejected) | 4 | 21.365 | 27.684 | 25.000 | 160.176 | 4.071 | 4.770 |
| validation-Vfa | >=40 km/h | 60 | ESKF + A+C (experimental; B not evaluated) | 4 | 22.899 | 25.295 | 0.000 | 156.763 | 4.082 | 4.763 |
| validation-Vfa | >=40 km/h | 60 | ESKF + A+C (experimental; B not evaluated) + AI | 4 | 19.595 | 20.351 | 0.000 | 135.739 | 3.269 | 3.849 |
| validation-Vfa | >=40 km/h | 60 | ESKF + C only | 4 | 84.615 | 79.543 | 0.000 | 472.453 | 9.250 | 10.445 |
| validation-Vfa | >=40 km/h | 60 | ESKF baseline commit | 4 | 80.684 | 77.097 | 0.000 | 463.944 | 10.120 | 11.272 |
| validation-Vfa | >=40 km/h | 60 | last GNSS speed + constant heading | 4 | 15.943 | 18.774 | 50.000 | 105.416 | 1.720 | 2.057 |
| validation-Vfa | >=40 km/h | 120 | ESKF + A (rejected) | 4 | 19.252 | 30.422 | 50.000 | 395.290 | 4.498 | 5.230 |
| validation-Vfa | >=40 km/h | 120 | ESKF + A+C (experimental; B not evaluated) | 4 | 38.822 | 37.909 | 0.000 | 479.550 | 4.511 | 5.228 |
| validation-Vfa | >=40 km/h | 120 | ESKF + A+C (experimental; B not evaluated) + AI | 4 | 33.084 | 33.191 | 0.000 | 425.384 | 3.977 | 4.681 |
| validation-Vfa | >=40 km/h | 120 | ESKF + C only | 4 | 75.946 | 78.537 | 0.000 | 1095.485 | 11.063 | 12.637 |
| validation-Vfa | >=40 km/h | 120 | ESKF baseline commit | 4 | 73.884 | 77.768 | 0.000 | 1083.556 | 12.643 | 13.876 |
| validation-Vfa | >=40 km/h | 120 | last GNSS speed + constant heading | 4 | 15.485 | 23.681 | 25.000 | 317.705 | 1.999 | 2.390 |

Map matching: **not evaluated**. Bundled graph covers Madhya Pradesh; no Coventry graph is supplied.

Limitations: Vfa is validation; S* is a previously reused regression set, not a blind test. No training data is used here. First 600 seconds per local recording; independent outages start at 60 and 300 seconds, with 60 seconds of warmup. Phone/vehicle elapsed clocks are aligned as in the existing harness; vehicle GNSS is a noisy scoring reference, never estimator input. Paths are translated to zero at the first outage sample (relative ATE; no rotation or scale fitting). Speed errors use vehicle GNSS speed. ATE and speed metrics in the table are means of per-window metrics. The high-speed subset uses mean reference speed >=40 km/h. Near-stationary distance under 1 m is excluded. Frame calibration uses only pre-outage phone GNSS and raw accelerometer/gyro; constant GRAVITY columns are ignored. Planar replay suppresses unidentifiable transverse gyro components. Low-correlation mappings are flagged, not claimed calibrated. The 10 Hz recording is held at 100 Hz for the existing filter; output is 10 Hz. This is not a measured mobile performance result. AI uses the unchanged 300x6 -> 1 bundled model, with experimental variance 25 (m/s)^2 and trust 0.10; it is not validated or enabled live.

Disposition: A and C are evaluation-only rejected snapshots; no live engine change is retained. Stage B is not included in numeric ablations because no UK graph is available; its attempted live wiring was reverted after an edge source-list incompatibility. Earlier stage-a results used an overly restrictive calibration interval gate and are superseded by this final run. Neither the aggregate gains nor experimental AI establish the <10% benchmark. Optional latency work was skipped.
