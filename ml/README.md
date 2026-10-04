# Model / log contracts

For the Windows laptop training agent, run `START-LAPTOP-AGENT.cmd` from the project root. The complete copy/paste guide is [laptop-training-agent.md](../docs/laptop-training-agent.md). It downloads completed Supabase trips, trains separate candidates, checks regressions, and requires phone evidence plus explicit approval before publishing.

The bundled models are trained on **IO-VNBD**, using all 144 CSVs from its synchronized categorized subset at revision `118939602e3422d47b8ab0807b623751c3ac135b`. Motion and speed are **1.1.0**; GPS-health remains **1.0.0**. See the [current model card](../artifacts/ml/refinement/MODEL-CARD.md), [validation comparison](../artifacts/ml/refinement/validation-comparison.json), and [original training results](../artifacts/ml/training-report.json). Historical `bootstrap_flatbuffers.py` and `generate_placeholders.py` are smoke-data utilities only; do not run them over the trained assets. No training code ships inside Android.

## Reproduce on Windows (Python 3.12)

```powershell
py -3.12 -m venv .venv-ml
.venv-ml\Scripts\python.exe -m pip install -r ml\requirements-lock.txt
.venv-ml\Scripts\python.exe ml\download_iovnbd.py
.venv-ml\Scripts\python.exe ml\train_iovnbd.py
.venv-ml\Scripts\python.exe ml\refine_iovnbd.py
.venv-ml\Scripts\python.exe ml\compare_candidates.py
.venv-ml\Scripts\python.exe ml\promote_refined_models.py
.venv-ml\Scripts\python.exe ml\export_feature_fixture.py
node scripts\seed-models.mjs
.\scripts\build-android.ps1
```

The downloader pins the repository revision, resolves Git LFS files, verifies sizes/SHA-256 and caches verified files. Training rebuilds windows from the downloaded data and writes weights, evaluation/provenance, TFLite assets and matching Android/frontend manifests. Allow approximately 1–2 GB of working disk space in addition to TensorFlow. Dataset and intermediate windows are excluded from git. Training may vary slightly across CPU/library versions despite the fixed seed. A new training release should use a new manifest version before publication.

Native inputs are block averaged from 100 Hz to 10 Hz and repeated back to 300 samples before extracting the following features. This matches the dataset's source rate. Java/Python feature parity is tested using a real held-out IO-VNBD window.

All tensors are float32. Input normalization must be included **inside the exported model**, because Java supplies physical-unit features.

| Name | Input | Output |
|---|---|---|
| motion | `[1,42]` | `[1,5]` softmax: driving_normal, idling, pothole, hard_brake, phone_knocked |
| gps-health | `[1,10]` | `[1,3]` softmax: HEALTHY, DEGRADING, BLACKOUT |
| speed | `[1,300,6]` | `[1,1]` m/s |

Motion: 300 samples over ~3 seconds at a fixed 100 Hz feature timebase, 1-second hop. Axes ax, ay, az (m/s²), gx, gy, gz (rad/s); for each axis emit mean, population std, peak-to-peak, RMS, centered zero-crossing fraction, mean spectral energy via Parseval, and correlation with the corresponding acceleration/gyro axis. Concatenate six groups of seven values. Speed uses the same chronological 300×6 window. Acceleration includes gravity in these model inputs. Sampling jitter must be considered when training against actual phone logs.

GPS health: satellite count, mean C/N₀ (dB-Hz), horizontal accuracy (m), speed-valid indicator, fix age (s, capped at 30), then the per-second change of these five values from the previous 1-second sample. The shared raw GNSS feed also drives GPSAdvisor. Placeholder fallback: stale (>2 seconds) or missing fix means BLACKOUT; low satellites or accuracy >20 m means DEGRADING. A real classifier replaces the fallback, while a >3-second stale fix remains a hard blackout guard.

Publish with semantic versions. `placeholder=false` identifies real-data training. The separate `validated=true` flag enables effects on tracking; all bundled models have `validated=false`. These models run inference while GPS/physics rules retain control of navigation. Pothole/phone-knock motion probabilities remain zero because labels are absent. GPS-health C/N0 and fix-age channels are masked because the dataset does not supply them. Models must preserve these shapes and feature orders for OTA updates.

## DRLOG v1

Little endian; header 200 bytes: ASCII DRLOG (5), version uint8 (1), sample rate uint16 (2), then phone_model/mount/session_id as three 64-byte null-padded UTF-8 strings. Strings hold at most 63 bytes plus a zero terminator.

Each record is **70 bytes**, with no alignment padding: int64 monotonic t_ns; 9 float32 ax/ay/az/gx/gy/gz/mx/my/mz; 5 float32 lat/lon/alt/speed_mps/horiz_acc; uint8 satellite count; float32 mean C/N₀; uint8 flags. Bit 0 means valid location fix, bit 1 means GNSS context present. Latitude/longitude are NaN when the fix is stale. Header sample rate is a target, not a promise about actual device delivery. Use t_ns for analysis.

At 100 Hz this is 7,000 bytes/s, approximately 25.2 MB/hour decimal. `read_drlog.py` memory-maps records instead of loading an entire drive in memory.
# Main-app refinement

Current speed and motion research models are 1.1.0; GPS-health remains 1.0.0. Read `artifacts/ml/refinement/MODEL-CARD.md` for validation comparisons and `artifacts/MAIN-APP-STATUS.md` for fusion replay limitations. Learned corrections are still disabled. `refine_iovnbd.py` trains candidates separately; `compare_candidates.py` compares them against the preserved original exports, and `promote_refined_models.py` packages only validation improvements. Device validation is not inferred from training or export success.
