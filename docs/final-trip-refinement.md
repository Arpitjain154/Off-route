# Final trip refinement — Offroute 1.5.7

This update uses the two final recordings from Samsung SM-E156B. No calibration constants or learned weights are copied between SM-E156B and SM-S711B. The recording phone is the installation target for this update.

## Changes

- **GPS return:** retain the continuous first recovery frame, but finish the position blend within two seconds instead of up to eight. Both final recordings show substantial INS/GPS discrepancies on return. Holding the old offset for eight seconds unnecessarily prolongs the wrong displayed position after an accepted GPS fix. The existing GPS freshness, quality and jump rejection checks remain in place, and uncertainty continues to include the unresolved offset during blending. A large correction will visibly move faster; the screen layout is unchanged.
- **Recording cadence:** schedule writes against the requested interval instead of restarting the interval after every accepted sample. Previously, a 130 Hz sensor stream could be reduced to 65 Hz at a 100 Hz capture target, and small timing jitter could discard alternate samples. The recorder now keeps approximately the requested rate, records only real source timestamps, rejects reordered samples, and avoids catch-up bursts after gaps. A 200 Hz target still cannot exceed the phone's actual sensor rate.

## Findings and changes deliberately held back

The final trips captured 45,784 and 84,786 raw samples, at approximately 121 Hz, with ten deliberate Location-off cycles in total. They contain no independent continuous position or speed reference during those outages. GPS-return differences are useful diagnostics, not certified outage accuracy.

The acceleration pipeline clips raw acceleration to asymmetric limits before filtering. A controlled test shows that this can convert symmetric vibration into false braking. Filtering before clipping removes that synthetic defect. However, approximate replays of the final trips show mixed real-data results, including increased overshoot near stops. For example, one Trip 2 return discrepancy changes from approximately 47 m to 87 m, and a near-rest interval changes from less than 1 m to 11 m. The candidate therefore **is not included in the installed app**. Its source, tests and replay results remain under `artifacts/post-final-refinement/estimator/` for further investigation.

Those replays hold recorded gravity, mounting direction and bias between low-rate diagnostic samples and reuse recorded heading and stop decisions. They are not full Android sensor replays. Some moving segments improve substantially, but those improvements do not justify disregarding the stopping regressions.

The experimental AI speed model remains visible as a diagnostic, with its existing validation gate. Trip 1's overlapping good-GPS telemetry comparisons showed roughly 9.03 m/s model RMSE, so it is not a trustworthy replacement for the live speed estimate. No new model is enabled, no minimum-speed floor is added, and no stronger map snapping or NHC tuning is applied. Map geometry does not establish along-road speed, and the saved traces do not expose enough candidate-road information to validate a map-matching change.

## Validation and preservation

Both new fixes have tests that fail on the previous code and pass on the final implementation. The GPS tests cover continuity, both signs of large offsets, repeated fixes during recovery, stale/poor fixes and small offsets. Recorder tests cover 100/200 Hz targets, source jitter, timestamp ordering and delivery gaps.

Final build, complete unit-test totals, package hash, unchanged bundled assets and phone checks are recorded in `artifacts/post-final-refinement/`. Existing journey files are backed up with SHA-256 manifests under `recovery/post-final-refinement/`; installation is an in-place update.

Final unit results: **114 passed, 2 skipped, 0 failed**. The optional IO-VNBD replay and state-graph build tests were skipped by their existing opt-in conditions. Replaying the two saved timestamp streams through the recorder scheduling rules gives 99.19/99.23 Hz at a 100 Hz target, compared with 62.61/62.63 Hz previously. Both implementations preserve every available sample at the configured 200 Hz target used in these trips.

Android compilation and unit testing completed before the standard packaging task was cancelled for excessive runtime. The delivered APK combines every freshly compiled Gradle dex partition and linked resources with the unchanged prior asset/native-library payload, followed by Android zip alignment and signing. All 39,486 asset/library entries match the previous APK byte-for-byte. Signature and alignment verification passed. APK SHA-256: `6414315b015517118c9212e1ac8c23866c0e73519c38963106b94a2bd5762b1e`.

**Installed and checked:** Offroute 1.5.7, version code 16, on the USB-connected Samsung SM-E156B. An eight-second live check received 16/16 fresh IMU readings, 16/16 fresh GPS readings and 16/16 available diagnostic AI estimates; inference count increased by seven. The five saved journeys and all 25 associated files are byte-for-byte unchanged. The 200 Hz target and dashboard mounting preference are preserved. No new journey recording was started. This is an on-phone launch/sensor check, not another moving road test or validation of AI accuracy.

The original problem statement's lane-level / less-than-10%-drift requirement **is not demonstrated**. The existing IO-VNBD replay passes that drift threshold in only 1 of 15 simulated outages, and this release does not change its inertial estimator. The two phone trips cannot supply independent outage ground truth. This release completes the supported reliability changes; it does not claim the navigation accuracy or AI validation work is finished.
