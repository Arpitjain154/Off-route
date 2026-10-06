# SM-E156B field test — 3 October 2026

The stop command was handled through the app's Exit navigation control. Final check confirmed logging=false, no active recording identity, and two saved journeys. All five files from each journey were copied locally with SHA-256 manifests; originals remain on the phone. No calibration constants, models or app code were changed during the drive.

| Measurement | Earlier journey | Second journey |
|---|---:|---:|
| Recorded duration | 493 seconds | 50 seconds |
| Raw IMU samples | 32,091 | 3,253 |
| Actual raw capture rate | 65.1 Hz | 65.5 Hz |
| Largest raw sample interval | 16.6 ms | 16.6 ms |
| Gaps over 100 ms | 0 | 0 |
| Fusion trace rate | 9.69 Hz | 9.68 Hz |
| Quality-approved GPS speed labels | 347 | 29 |
| Experimental AI speed RMSE | 8.14 m/s | 20.17 m/s |

The app requested a 100 Hz raw capture target, and the raw log contains about 65 Hz. A subsequent comparison with the quality log shows the engine actually accepted about 130.5–131.4 Hz: the writer's minimum interval at the 100 Hz setting discarded alternating callbacks. The data is continuous. The existing 200 Hz capture option was selected before the final two trips to retain more delivered samples; the achieved rate must still be measured. Training ingestion resamples timestamped data rather than assuming the requested rate was achieved.

The earlier journey was already recording when live monitoring connected. It contains two clear periods without fresh GPS lasting approximately 28.7 and 20.5 seconds. The inertial estimate remained available throughout. Its first outage speed fell from about 12.0 to 2.5 km/h; the second rose from about 18.6 to 24.9 km/h. True velocity during these outages is unavailable, so these are observed estimates rather than measured errors. The second journey had fresh GPS throughout.

The live wireless capture received 146 samples, all with fresh IMU and experimental AI output; 112 had fresh GPS. The final part of that capture included the first journey ending. A second journey subsequently became active and was explicitly stopped after the user's stop command. Both sessions are retained separately rather than joined or overwritten.

The AI speed model remains unsuitable for navigation. Against accepted GPS labels it has large errors on this phone too. On the earlier journey's 272 accepted near-stopped labels (GPS speed below 0.5 m/s), mean predicted speed was about 8.1 km/h. The second journey has only three such labels, insufficient for a reliable stop-performance statistic. The unvalidated AI stayed excluded from live navigation fusion.

Live mount alignment was available for roughly half of each complete recording. This fraction includes time before movement/calibration and does not by itself prove repeated mounting failure. Further analysis should separate initial alignment from later losses using the newly recorded gravity/forward-axis values. Acceleration bias during the longer journey ranged from -0.382 to +0.142 m/s². Map-matching availability alone does not establish a correct road or lane-level accuracy.

Next refinement priorities: inspect alignment transitions against gravity and device handling; use this phone's stopped/moving labels for independent model evaluation; assess outage trajectories against a separate reference before changing fusion weights. Do not fit a model on these sessions and then call performance on the same sessions validation. This test does not prove the under-10% outage-drift benchmark.

Evidence: `trip-analysis.json`, timestamped telemetry JSONL captures, and per-session files/manifests in this directory. Session identifiers: `e200b4eb-7315-472a-b1d5-eb8c86b901e1` and `1af1f4ce-0775-464e-b610-a1da68f7d510`.
