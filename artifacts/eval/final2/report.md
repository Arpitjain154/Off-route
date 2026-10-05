# OFFROUTE final outage evaluation

Baseline source: 41670ae7. Reproduce: python ml/evaluate_outages.py --label final2.
All candidate rows compile the live Android-independent engine sources. Stage C is OFF.

| Split | Subset | Seconds | Ablation | N | Median drift % | Mean drift % | Under 10% (%) | ATE m | Speed MAE m/s | Speed RMSE m/s |
|---|---|---:|---|---:|---:|---:|---:|---:|---:|---:|
| regression-S | all | 30 | ESKF + A (live source) | 12 | 59.760 | 430.353 | 0.000 | 74.158 | 3.949 | 4.627 |
| regression-S | all | 30 | ESKF + A + AI (live source) | 12 | 57.668 | 75.469 | 0.000 | 62.320 | 3.587 | 4.278 |
| regression-S | all | 30 | ESKF + A + B oracle (reference route; upper bound) | 12 | 62.096 | 426.496 | 0.000 | 68.575 | 3.949 | 4.627 |
| regression-S | all | 30 | ESKF baseline commit 41670ae7 | 12 | 185.773 | 1496.887 | 0.000 | 173.805 | 7.034 | 8.487 |
| regression-S | all | 30 | last GNSS speed + constant heading | 12 | 73.279 | 618.179 | 0.000 | 93.890 | 4.180 | 4.739 |
| regression-S | all | 60 | ESKF + A (live source) | 12 | 47.971 | 61.195 | 0.000 | 133.266 | 4.741 | 5.573 |
| regression-S | all | 60 | ESKF + A + AI (live source) | 12 | 35.022 | 58.500 | 0.000 | 112.159 | 4.155 | 4.957 |
| regression-S | all | 60 | ESKF + A + B oracle (reference route; upper bound) | 12 | 48.406 | 58.985 | 0.000 | 122.332 | 4.741 | 5.573 |
| regression-S | all | 60 | ESKF baseline commit 41670ae7 | 12 | 88.980 | 694.535 | 0.000 | 397.567 | 9.492 | 11.078 |
| regression-S | all | 60 | last GNSS speed + constant heading | 12 | 84.199 | 95.535 | 0.000 | 193.368 | 4.792 | 5.417 |
| regression-S | all | 120 | ESKF + A (live source) | 12 | 59.482 | 56.074 | 0.000 | 320.911 | 5.221 | 6.141 |
| regression-S | all | 120 | ESKF + A + AI (live source) | 12 | 32.486 | 51.790 | 8.333 | 283.074 | 4.235 | 5.131 |
| regression-S | all | 120 | ESKF + A + B oracle (reference route; upper bound) | 12 | 59.667 | 55.278 | 0.000 | 315.883 | 5.221 | 6.141 |
| regression-S | all | 120 | ESKF baseline commit 41670ae7 | 12 | 73.870 | 1206.041 | 0.000 | 892.908 | 12.863 | 14.763 |
| regression-S | all | 120 | last GNSS speed + constant heading | 12 | 85.203 | 96.911 | 0.000 | 431.363 | 5.131 | 5.809 |
| validation-Vfa | all | 30 | ESKF + A (live source) | 4 | 18.737 | 22.281 | 25.000 | 66.893 | 3.315 | 3.926 |
| validation-Vfa | all | 30 | ESKF + A + AI (live source) | 4 | 11.758 | 16.635 | 25.000 | 57.068 | 2.594 | 3.159 |
| validation-Vfa | all | 30 | ESKF + A + B oracle (reference route; upper bound) | 4 | 19.987 | 18.407 | 25.000 | 52.954 | 3.315 | 3.926 |
| validation-Vfa | all | 30 | ESKF baseline commit 41670ae7 | 4 | 66.192 | 62.839 | 0.000 | 173.351 | 6.547 | 7.664 |
| validation-Vfa | all | 30 | last GNSS speed + constant heading | 4 | 11.535 | 12.215 | 50.000 | 38.745 | 1.239 | 1.530 |
| validation-Vfa | all | 60 | ESKF + A (live source) | 4 | 21.365 | 27.684 | 25.000 | 160.176 | 4.071 | 4.770 |
| validation-Vfa | all | 60 | ESKF + A + AI (live source) | 4 | 19.131 | 27.498 | 25.000 | 140.876 | 3.295 | 3.902 |
| validation-Vfa | all | 60 | ESKF + A + B oracle (reference route; upper bound) | 4 | 17.582 | 27.552 | 0.000 | 164.524 | 4.071 | 4.770 |
| validation-Vfa | all | 60 | ESKF baseline commit 41670ae7 | 4 | 80.684 | 77.097 | 0.000 | 463.944 | 10.120 | 11.272 |
| validation-Vfa | all | 60 | last GNSS speed + constant heading | 4 | 15.943 | 18.774 | 50.000 | 105.416 | 1.720 | 2.057 |
| validation-Vfa | all | 120 | ESKF + A (live source) | 4 | 19.252 | 30.422 | 50.000 | 395.290 | 4.498 | 5.230 |
| validation-Vfa | all | 120 | ESKF + A + AI (live source) | 4 | 22.519 | 33.779 | 25.000 | 402.160 | 3.996 | 4.714 |
| validation-Vfa | all | 120 | ESKF + A + B oracle (reference route; upper bound) | 4 | 19.449 | 30.678 | 50.000 | 382.303 | 4.498 | 5.230 |
| validation-Vfa | all | 120 | ESKF baseline commit 41670ae7 | 4 | 73.884 | 77.768 | 0.000 | 1083.556 | 12.643 | 13.876 |
| validation-Vfa | all | 120 | last GNSS speed + constant heading | 4 | 15.485 | 23.681 | 25.000 | 317.705 | 1.999 | 2.390 |
| regression-S | >=40 km/h | 30 | ESKF + A (live source) | 3 | 39.252 | 43.087 | 0.000 | 100.250 | 5.483 | 6.449 |
| regression-S | >=40 km/h | 30 | ESKF + A + AI (live source) | 3 | 36.852 | 36.874 | 0.000 | 93.089 | 5.213 | 6.100 |
| regression-S | >=40 km/h | 30 | ESKF + A + B oracle (reference route; upper bound) | 3 | 34.636 | 34.353 | 0.000 | 81.952 | 5.483 | 6.449 |
| regression-S | >=40 km/h | 30 | ESKF baseline commit 41670ae7 | 3 | 52.926 | 45.034 | 0.000 | 107.381 | 4.512 | 5.430 |
| regression-S | >=40 km/h | 30 | last GNSS speed + constant heading | 3 | 56.466 | 56.900 | 0.000 | 125.511 | 6.366 | 6.824 |
| regression-S | >=40 km/h | 60 | ESKF + A (live source) | 3 | 57.598 | 48.569 | 0.000 | 214.060 | 7.365 | 8.201 |
| regression-S | >=40 km/h | 60 | ESKF + A + AI (live source) | 3 | 30.080 | 34.196 | 0.000 | 168.137 | 5.911 | 6.586 |
| regression-S | >=40 km/h | 60 | ESKF + A + B oracle (reference route; upper bound) | 3 | 58.469 | 49.343 | 0.000 | 188.762 | 7.365 | 8.201 |
| regression-S | >=40 km/h | 60 | ESKF baseline commit 41670ae7 | 3 | 56.739 | 57.963 | 0.000 | 243.058 | 5.198 | 6.291 |
| regression-S | >=40 km/h | 60 | last GNSS speed + constant heading | 3 | 65.269 | 62.728 | 0.000 | 267.583 | 7.119 | 7.532 |
| regression-S | >=40 km/h | 120 | ESKF + A (live source) | 1 | 72.788 | 72.788 | 0.000 | 571.907 | 6.545 | 7.142 |
| regression-S | >=40 km/h | 120 | ESKF + A + AI (live source) | 1 | 65.522 | 65.522 | 0.000 | 520.647 | 5.683 | 6.191 |
| regression-S | >=40 km/h | 120 | ESKF + A + B oracle (reference route; upper bound) | 1 | 73.326 | 73.326 | 0.000 | 563.282 | 6.545 | 7.142 |
| regression-S | >=40 km/h | 120 | ESKF baseline commit 41670ae7 | 1 | 76.936 | 76.936 | 0.000 | 674.548 | 4.694 | 5.638 |
| regression-S | >=40 km/h | 120 | last GNSS speed + constant heading | 1 | 49.261 | 49.261 | 0.000 | 394.800 | 3.959 | 4.452 |
| validation-Vfa | >=40 km/h | 30 | ESKF + A (live source) | 4 | 18.737 | 22.281 | 25.000 | 66.893 | 3.315 | 3.926 |
| validation-Vfa | >=40 km/h | 30 | ESKF + A + AI (live source) | 4 | 11.758 | 16.635 | 25.000 | 57.068 | 2.594 | 3.159 |
| validation-Vfa | >=40 km/h | 30 | ESKF + A + B oracle (reference route; upper bound) | 4 | 19.987 | 18.407 | 25.000 | 52.954 | 3.315 | 3.926 |
| validation-Vfa | >=40 km/h | 30 | ESKF baseline commit 41670ae7 | 4 | 66.192 | 62.839 | 0.000 | 173.351 | 6.547 | 7.664 |
| validation-Vfa | >=40 km/h | 30 | last GNSS speed + constant heading | 4 | 11.535 | 12.215 | 50.000 | 38.745 | 1.239 | 1.530 |
| validation-Vfa | >=40 km/h | 60 | ESKF + A (live source) | 4 | 21.365 | 27.684 | 25.000 | 160.176 | 4.071 | 4.770 |
| validation-Vfa | >=40 km/h | 60 | ESKF + A + AI (live source) | 4 | 19.131 | 27.498 | 25.000 | 140.876 | 3.295 | 3.902 |
| validation-Vfa | >=40 km/h | 60 | ESKF + A + B oracle (reference route; upper bound) | 4 | 17.582 | 27.552 | 0.000 | 164.524 | 4.071 | 4.770 |
| validation-Vfa | >=40 km/h | 60 | ESKF baseline commit 41670ae7 | 4 | 80.684 | 77.097 | 0.000 | 463.944 | 10.120 | 11.272 |
| validation-Vfa | >=40 km/h | 60 | last GNSS speed + constant heading | 4 | 15.943 | 18.774 | 50.000 | 105.416 | 1.720 | 2.057 |
| validation-Vfa | >=40 km/h | 120 | ESKF + A (live source) | 4 | 19.252 | 30.422 | 50.000 | 395.290 | 4.498 | 5.230 |
| validation-Vfa | >=40 km/h | 120 | ESKF + A + AI (live source) | 4 | 22.519 | 33.779 | 25.000 | 402.160 | 3.996 | 4.714 |
| validation-Vfa | >=40 km/h | 120 | ESKF + A + B oracle (reference route; upper bound) | 4 | 19.449 | 30.678 | 50.000 | 382.303 | 4.498 | 5.230 |
| validation-Vfa | >=40 km/h | 120 | ESKF baseline commit 41670ae7 | 4 | 73.884 | 77.768 | 0.000 | 1083.556 | 12.643 | 13.876 |
| validation-Vfa | >=40 km/h | 120 | last GNSS speed + constant heading | 4 | 15.485 | 23.681 | 25.000 | 317.705 | 1.999 | 2.390 |

AI promotion gate: FAIL. Strict improvement in both median and mean endpoint drift in all six all-speed split/duration groups. The evaluator does not change production flags.

Healthy recorded-trip parity: PASS (S-Vfa01, 6000 native samples; exact baseline/candidate LiveNavigation CSV bytes).

The A+B row uses the vehicle-reference path as an oracle route and advances RouteFollower with A estimated speed only. Reference speed and timed reference positions do not update the estimator. The route geometry still leaks the scoring path: this is a contaminated upper-bound ablation, not a real UK map-matching result or evidence of deployment accuracy. The bundled road graph covers Madhya Pradesh, not this UK recording.

Limitations: Vfa is validation; S* is a previously reused regression set, not a blind test. First 600 seconds per local recording; independent outages start at 60 and 300 seconds with 60 seconds of warmup. Phone/vehicle elapsed clocks follow the existing harness. Vehicle GNSS is a noisy scoring reference. Paths are translated to zero at the first outage sample (relative ATE, no rotation or scale fitting). Speed errors use vehicle GNSS speed. ATE and speed metrics are means of per-window metrics. The high-speed subset uses mean reference speed >=40 km/h. Near-stationary distance under 1 m is excluded. Frame calibration uses only pre-outage phone GNSS and raw accelerometer/gyro. Planar replay suppresses unidentifiable transverse gyro components; low-correlation mappings remain flagged. The outage accuracy harness preserves its historical 10 Hz sample hold at 100 Hz; output remains 10 Hz. The separate edge runner exercises native 10 Hz and resampled 200 Hz transport. Neither is measured mobile performance. AI uses the unchanged 300x6 -> 1 bundled model, variance 25 (m/s)^2 and trust 0.10. These offline results do not validate or retrain the model. See source-provenance.json for exact source/model hashes.