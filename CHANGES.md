# OFFROUTE final pass

ON: `BOUNDED_SPEED`, `OUTAGE_CONTINUITY`, `ROUTE_FOLLOW`, `EARLY_LOSS`, `SMOOTH_RECOVERY` (`OutageTuning`); `OUTAGE_DIRECTIONAL_GUIDANCE` and `RECOVERY_REROUTE` (frontend). `AI_SPEED` is OFF; Stage C remains absent/off. Models remain unchanged/unvalidated; GPS-health has no navigation vote.

Valid aligned sensors support ten-minute moving outages with growing uncertainty. Route aid requires a trusted fix within 15 m; fallback occurs at 120 s, confirmed stop, invalid sensors, or signed gyro/route divergence >30 degrees over 5 s. Guidance checks maneuver distance and road corridor instead of one radial cutoff. Total confidence stays honest. Recovery corrects at bounded speed (maximum 5 m/update), finishes with ordinary GPS residuals, and handles repeated loss. After outage, rerouting needs >40 m deviation for >10 s. Satellite collapse uses 300 ms confirmation; diagnostics measure callbacks, not physical signal-loss latency. No <100 ms claim.

Endpoint drift (%), baseline -> live A:

| Split, outage | Median | Mean | A under 10% |
|---|---:|---:|---:|
| Vfa, 30s | 66.19 -> 18.74 | 62.84 -> 22.28 | 25% |
| Vfa, 60s | 80.68 -> 21.36 | 77.10 -> 27.68 | 25% |
| Vfa, 120s | 73.88 -> 19.25 | 77.77 -> 30.42 | 50% |
| S*, 30s | 185.77 -> 59.76 | 1496.89 -> 430.35 | 0% |
| S*, 60s | 88.98 -> 47.97 | 694.54 -> 61.19 | 0% |
| S*, 120s | 73.87 -> 59.48 | 1206.04 -> 56.07 | 0% |

Legacy: median 70.585%, mean 140.537%, 1/15 under 10%, unchanged. A worsens 9/48 windows; S* high-speed 60 s median worsens 0.86 points. AI fails at Vfa 120 s. The <10% benchmark remains unmet. Vfa is validation; S* is reused regression. Route ablation is a reference-path oracle labelled upper bound; real UK map matching was not evaluated. Stair-step geometry was investigated; no small local bug found, so it remains unfixed.

Files: engine `Eskf`, `NavigationFusion`, `LiveNavigation`, `OutageTuning`, `RouteFollower`, `GnssLossDetector`, `SensorCaptureService`, `IovnbdCsvRunner`; native `DrEnginePlugin`; `src/{App,engine,types,RouteGuidance,routeProgress,useRouteNavigation}`; engine/frontend/edge regressions; edge source allowlist, `RUN-CSV.md`, `scripts/test-edge-csv.ps1`; `ml/{evaluate_outages,test_outage_evaluation,java/OutageEvaluation}`; final2 evidence; this file. No layout/CSS, dependency/version, server/cloud, map or model edits. Replaced assertions encoded obsolete two-second 150 m recovery/recovery-reroute timing; new tests require bounded jumps, convergence, honest uncertainty and healthy parity.

Verification: Android 164 pass/1 skip, Python 47 pass, frontend 40 pass, edge JUnit 41 pass; two browser navigation checks pass. Healthy 6,000-row replay is byte-identical to baseline. APK/JAR hashes and payload preservation: `artifacts/eval/final2/verification.json`. Reproduce evidence: `.\.venv-ml\Scripts\python.exe ml/evaluate_outages.py --label final2`. Build: `cd android; .\gradlew.bat testDebugUnitTest assembleDebug` (`IDR_REPLAY=1`). Native CSV/200 Hz command and limits: `edge/RUN-CSV.md`.

Five-minute phone checklist: 0-1 min start a route and establish mounting alignment. 1-3 min drive a straight 500 m stretch around 40 km/h; turn Location off and check continuous speed/position, falling confidence and usable distant guidance. 3-4 min restore Location: gradual recovery, no icon snap or immediate route rebuild. 4-5 min repeat near a gentle bend, then stop: check route progress and held position only at confirmed stop/invalid sensors. A pause near an uncertain maneuver is correct. Export diagnostics if a fixed phone loses valid tracking. Phone verification remains outstanding.
