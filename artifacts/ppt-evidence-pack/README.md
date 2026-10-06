# OFFROUTE PPT evidence pack

Generated from local files on 5 October 2026. PNGs are 2932 × 1650 pixels; SVG/PDF copies are included. No original PPTX was found in the project-file inventory, so these are assets to insert into your existing deck.

## What to put on each slide

- **Slide 4:** `07_slide4_fusion_architecture.png`. Paste: “The live navigation architecture combines IMU propagation, accepted GNSS, physical constraints and road observations in a 15-error-state ESKF. A validated and calibrated neural speed model can supply uncertain velocity observations. The bundled model is not yet validated for active correction.”
- **Slide 4 health-monitor box:** “Monitor fix freshness, reported uncertainty and measurement consistency. Reject unreliable GNSS updates; continue inertial propagation only when alignment and sensor freshness permit. Reconcile on recovery.” Label any proposed C/N0 or RF-jamming detector **planned**. GNSS degradation does not identify its cause, and no RF-jamming experiment is provided here.
- **Slide 5:** Replace guaranteed lane accuracy, zero drift, jam-proof navigation or universal AI improvement with: “Offline navigation prototype with ESKF integration and on-device model inference. Drift and learned correction performance require further physical validation.” Learned reliability estimation, adaptive noise learned from data and learned position-drift correction should be presented as proposed extensions unless independently implemented and tested.
- **Slide 6 main evidence:** `02_real_reference_vs_estimate.png` and `01_real_outage_drift.png`. Paste: “Saved IO-VNBD regression replay: 15 injected 30-second outages across three Driver A recordings; 1/15 below 10% endpoint drift; median drift 70.58%. AI speed correction inactive and no road matching. These historical replay results do not evaluate the October 4 live ESKF update.”
- **Slide 6 speed evidence / appendix:** `03_real_speed_model_comparison.png`. Paste: “Original speed model MAE 4.09 m/s and RMSE 5.71 m/s; refined model MAE 3.72 m/s on reused Driver A regression data.” Keep model metrics separate from trajectory metrics.
- **Dataset appendix:** `04_real_dataset_split.png`.
- **Proposed evaluation appendix:** `05_SYNTHETIC_test_matrix.png`, `06_SYNTHETIC_paths_and_speed.png` and `synthetic_test_matrix.csv`. Keep the SYNTHETIC / ILLUSTRATIVE label visible. These complete the requested 4 × 3 layout, not the actual experiment.

## Real example table (closest to median drift among windows with at least 100 m travel)

Recording S-S1; window near 180s; 300 saved samples.

| Metric | Value |
|---|---:|
| Absolute position ATE RMSE, no alignment | 97.85 m |
| Start-aligned position RMSE | 67.48 m |
| Relative endpoint error | 105.13 m |
| Reference distance including outage-entry step | 146.20 m |
| Relative endpoint drift | 71.91% |

## Definitions and provenance

Position ATE RMSE = sqrt(mean(||estimated position - reference position||²)), using a local planar projection (111320 m/degree latitude and cosine-scaled longitude). No rigid alignment is applied to the absolute metric. Start-aligned metrics independently translate both paths to their first outage point. Drift = 100 × start-aligned endpoint error / max(1 metre, reference path length). To match InertialRecordedEvaluationTest.java, distance includes the step from the preceding sample into the first outage sample; endpoint longitude uses the final reference latitude. MAE = mean absolute speed error; RMSE = sqrt(mean squared speed error). Convert m/s to km/h by multiplying by 3.6.

All 15 outage windows are used. Filters are seconds >= nominal start, seconds < start+30, outage=true, matching the existing reporting script. Rounded timestamp/coordinate export limits precision. Original data are real recordings; GNSS removal is injected in software. Vehicle GNSS is a reference receiver, not surveyed truth. Driver A has been reused. Missing receiver accuracy assumptions, source-axis ambiguity and repeated source fixes limit interpretation. The old QA prose says 0/15 and 77.5%; the current CSVs recompute to 1/15 and 70.584%. The later drift-fix report documents the corrected gravity replay fixture and agrees on 1/15, but its 71.908% median is also stale; this pack recomputes the median from all 15 CSV rows. Do not treat this fixture change as a controlled algorithm improvement.

Model scores are read from saved training reports; training/inference was not rerun for this pack. The selected refined report has no overall speed RMSE, so none was invented. No measured four-configuration × 30/60/120-second × 60 km/h matched experiment was identified. The latest inspected laptop candidate report says training failed; no result from it is claimed.

## Synthetic data assumptions (fully reproducible, no random seed needed)

0.1-second timestep, 120 seconds, reference speed exactly 60/3.6 m/s; heading = 0.002t + 0.08 sin(t/18) radians. Position integrates reference velocity on the right endpoint. Last-speed surrogate holds initial heading and has an assumed +0.8 m/s speed error. ESKF surrogate assumes +0.028t + 0.12 sin(t/7) m/s speed error and +0.0009t rad yaw error. AI surrogate assumes +0.012t + 0.12 sin(t/7) m/s and +0.0004t rad yaw error. Map surrogate removes 80% of cross-track error using the exact synthetic reference normal; its speed is derived from corrected positions. This is an oracle constraint, not a real map matcher. Position and speed metrics include t=0 and the duration endpoint. The assumed errors predetermine apparent benefits. No ESKF implementation, learned reliability estimator, trained AI or actual road graph is evaluated. Use only as a proposed-test illustration.

## Rebuild

From the workspace: `.venv-ml/Scripts/python.exe artifacts/ppt-evidence-pack/build_assets.py` (numpy, pandas, matplotlib required). CSVs permit editing/replotting. source_manifest.json records SHA-256 hashes of the evidence used. Original sources are untouched. No cloud upload or publication was performed.
