# OFFROUTE outage experiments — 5 October 2026

**Outcome: no live engine change retained; the <10% drift benchmark remains unmet.**

Baseline checkpoint: `41670ae7`. Stage A checkpoint: `6ba018c8`; Stage B attempt/rollback: `0930231d` / `5e379329`; Stage C rejection: `1b20a1f7`. UI, cloud/server, models, dependencies, configuration, version, map assets and existing documents are unchanged.

Retained files: `ml/evaluate_outages.py`, `ml/java/OutageEvaluation.java`, `ml/test_outage_evaluation.py`, this file, and `artifacts/eval/` evidence/experimental snapshots. Temporary engine edits to Eskf, NavigationFusion, LiveNavigation, MapMatchAdvisor and SensorCaptureService were restored. New constraint classes and their tests were moved to evaluation-only snapshots.

Flags: no new production flags remain. Experimental `BOUNDED_SPEED`, `FREEZE_GYRO_BIAS`, and rejected road `ROAD_HEADING` defaults are **false**. The evaluation harness explicitly enables the selected ablation. AI is evaluated using the unchanged 300×6 → 1 model, variance 25 (m/s)² and trust 0.10; no model is promoted or marked validated.

| Median endpoint drift % | Vfa 30 / 60 / 120 s | S* 30 / 60 / 120 s |
|---|---|---|
| Baseline ESKF | 66.19 / 80.68 / 73.88 | 185.77 / 88.98 / 73.87 |
| A only (rejected) | 18.74 / 21.36 / 19.25 | 59.76 / 47.97 / 59.48 |
| A+C experimental | 22.85 / 22.90 / 38.82 | 56.75 / 41.87 / 59.54 |
| A+C + experimental AI | 16.79 / 19.60 / 33.08 | 51.77 / 35.59 / 38.71 |

Numbers above come from `artifacts/eval/final/summary.csv`. A regressed 9/48 windows; C-only regressed 25/48; AI regressed 18/48 versus A+C. Consequently A/C were reverted. B was reverted after its new helper classes failed the edge source allowlist; full route advancement remains unimplemented. No Coventry graph is available, so map matching is **not evaluated**. E was skipped.

Verification: baseline Android 127 passed / 2 skipped; enabling recorded replay gives 128 passed / 1 skipped. Original Python 43 passed; final Python 46 passed including three new fixture-causality tests. The Android test/build command and forced replay were rerun after rollback; see `artifacts/eval/final-restored-android.log`. Legacy 15-window replay is unchanged: 70.585% median, 1/15 under 10%. Baseline edge build already fails on omitted `AiSpeedAdvisor`; forbidden Gradle configuration was not changed.

Evidence: `artifacts/eval/final/report.md`, `summary.csv`, `windows.csv`, `trajectories.csv`, `axis-calibration.json`, `decisions.json`, and `reference-vs-estimated.png`. Reproduce with `.\.venv-ml\Scripts\python.exe ml/evaluate_outages.py --label final`. Vfa is validation; S* is reused regression, not blind test. Sparse pre-outage phone GNSS learns replay axes; unreliable mappings are flagged. Earlier Stage A numbers are superseded. No mobile 10 Hz throughput, detection-latency, or tunnel accuracy success is claimed.
