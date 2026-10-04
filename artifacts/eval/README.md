# OFFROUTE staged evaluation

The authoritative results are in `final/`. Earlier `stage-a/` results are preserved as historical evidence but **superseded**: the first fixture calibration gate rejected sparse phone GNSS intervals. The final fixture accepts causal intervals of 1–20 seconds, learns only from pre-outage phone GNSS, and explicitly flags insufficient calibration.

Run from the workspace root:

```powershell
.\.venv-ml\Scripts\python.exe ml/evaluate_outages.py --label final
```

The script verifies local source-data hashes, extracts baseline Java from commit `41670ae7`, compiles baseline and experimental snapshots separately, invokes the unchanged bundled speed TFLite model, and writes per-window metrics, aggregate CSV, Markdown, and a trajectory PNG. No network, training, model replacement, or graph construction is performed. `experimental-src/` is never packaged in the APK or edge JAR. `--no-ai` is available for a faster non-AI replay.

The 30/60/120-second results share deterministic prefixes of one 120-second propagation per start and ablation. Starts are 60 and 300 seconds, with the first 600 seconds of all six local S* and two Vfa recordings. Prefixes overlap and are not independent statistical samples. The plotted examples are the first eligible window in each split, not selected for favorable performance. Full trajectories and all windows are in CSV.

The GRAVITY columns are ignored. Acceleration defines an estimated up direction, pre-outage speed changes estimate forward, and pre-outage GNSS course changes select the vertical gyro channel. These are planar replay assumptions, not changes to live phone logic. Unreliable calibration remains visible in `axis-calibration.json`; there is no perfect-heading or perfect-speed input.

All A–C live changes were reverted. A and C worsened individual outage windows; B's new classes were incompatible with the edge Gradle source allowlist. The isolated baseline edge build already fails on excluded `AiSpeedAdvisor`; configuration was left untouched. Android baseline and restored builds pass. No benchmark, on-phone latency, or mobile throughput success is claimed.

Tests are logged beside this file. The existing Python integration suite trains a temporary synthetic test fixture as part of its pre-existing converter test; it does not retrain, replace, or validate the shipped models.
