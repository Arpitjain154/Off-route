# OffRoute refinement — 27 September 2026

Goal: refine the existing com.idr.navigator application without losing its map, native sensors, routing, records, preferences or update APIs. The user supplied the design brief and explicitly requested implementation without further design approvals.

Source: recovered Documents/ChatGPT/offroute 3.0, React 18 + TypeScript + Capacitor 6 + Java. This is a working copy of that application, not a replacement application. The source was already at 1.5.0-qa; the phone was running 1.4.0. Preserve and disclose that distinction.

Current results and checked completion status: `docs/refinement-status.md` and `docs/refinement-report.md`. This is the original planning record.

## Recovery
- [x] Save installed APK; SHA-256 009b6853c57d6059ab2ac0346dc080ec6a5a8601986ceff0fa035a3e4fe432d1.
- [x] Save app-private data with run-as tar; no uninstall or storage clearing.
- [x] Preserve source/assets separately at C:/Users/surya/.codex/.chatgpt-projects/g-p-6aa5938fc2f48191bcebfbb1e379eaac/recovery/source-baseline. The active working directory has no committed Git baseline; use this saved copy for recovery.
- [x] Read both briefs, six supplied screenshots, full-duration sampled review of both videos. No seventh screenshot was supplied; use the active-navigation recording for the missing variant.

## Work and ownership
1. Root: UI and recording coordination. Keep MapView mounted across tabs. Replace dashboard with floating search/status, expandable sheet, navigation controls, journey cards and settings dialog; Sensors tab plus avatar Settings. Retain existing destination selection and car/motorcycle routing; expose unsupported Walk/Cycle honestly. Real data only, explicit unknown metrics. Use local bundled icons/fonts. Preserve upload, report, model-update, permission and diagnostic controls under details/settings.
2. Native agent: test-first corrections in ESKF/NHC and actual published navigation path. Investigate stationary position jitter, unobserved mounting alignment, cross-track road corrections and speed/uncertainty gates. Do not replace the whole estimator or claim measured drift improvements without evaluation.
3. Adaptive-learning agent: verified phone-log ingestion and candidate evaluation, freshness/provenance gates, session-level holdout, no automatic model promotion. Add compatible native quality sidecar if needed. Preserve DRLOG and uploaded record compatibility.

## Recording behavior
Remove the promotional/manual-recording dashboard card. Local training collection begins with an active device navigation session, can be paused in Sensors/Settings, and finishes when navigation exits. Existing manual recordings are retained and recoverable after page/activity changes. No silent cloud upload. Completed sessions remain in Journeys with the existing optional upload/report actions. Persist model/deployment safety gates: collect data now; promote a candidate only after held-out improvement and phone validation.

## Verification
- [x] Baseline build and route/map/native tests.
- [x] Regression tests before native and route-gating fixes; no rerouting from uncertain inertial-only estimates.
- [x] Browser interactions at 360/390/412 px and small height: planner inputs/swap/errors, sheet states, journey empty/detail states, settings persistence/keyboard and map gestures; screenshots.
- [x] Android build/signature/package checks and in-place update only after checks pass. Preserve package ID/signing and data.
- [x] Physical-phone static smoke: launch, map, planner/native route, tab/settings transitions, no duplicate sensor registrations/crash; screenshots.
- [x] Explicitly separate static/device and simulated tests from physical driving/outage accuracy, which requires controlled independent reference data.

## Review focus
- Stale reroute errors and conflicting reassuring text.
- Failed/cancelled routes must not start or strand recordings.
- Page changes must not restart the native service or duplicate logs/listeners.
- Preferences and history from 1.4 must remain readable.
- Unaligned sensors, stale GNSS and missing model/map output must remain unavailable rather than inventing precision.
