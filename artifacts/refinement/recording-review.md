# OffRoute recording and screenshot review

Reviewed 27 September 2026. This is a visual observation record, not a diagnosis or validation of the positioning algorithm.

## Material and coverage

- `WhatsApp Video 2026-09-27 at 6.54.19 PM.mp4`: 49.77 seconds, 434 × 848. Entire duration extracted and visually inspected at 2 frames/second (100 frames across five contact sheets).
- `WhatsApp Video 2026-09-27 at 7.36.26 PM.mp4`: 9:08.99, 392 × 850. Entire duration extracted and visually inspected at 1 frame/second (549 frames across 28 contact sheets); selected original-size frames inspected for exact labels.
- Six screenshots inspected: `184928`, `184949`, `185004`, `185027`, `185100`, `185113` under `C:\Users\surya\Pictures\Screenshots`.
- Read `C:\Users\surya\Downloads\OffRoute-UI-Replacement-Brief.md` in full.
- Timestamps below are approximate, with up to about one second of sampling uncertainty. Frames are available under `review/videos/stitch` and `review/videos/device`; contact sheets are in their parent directory. No source files were edited.
- Review covers visual content, not a transcription of the audio track. Ground truth speed, actual vehicle motion, raw sensor streams, and algorithm internals cannot be established from these images.

## Target prototype interactions (49.77-second clip)

| Time | Observed behavior | Implementation implication |
| --- | --- | --- |
| 0:00–0:03.5 | Planner begins with destination Bhopal Upper Ridge Trail 4. Input focuses and keyboard appears around 0:02. A saved POI selection changes the destination field and summary heading to Kerwa Dam Quarry Gorge. | Keyboard must preserve access through scrolling. Use one selected destination object across field, summary, and request. Initial prototype summary route text still points to Kerwa even while Bhopal is selected, an inconsistency to avoid. |
| ~0:03.6–0:14.4 | Get directions transitions to map navigation. Top banner says “In 20 m Turn right” onto Halapur Link Trail, next strip says continue 350 m toward Kerwa Gorge, green dead-reckoning badge visible. Blue route and marker stay visually fixed while touches occur. | This establishes screen transition and styling; it does not prove live routing, location, or working map controls. |
| ~0:14.5 | Close returns to planner with Kerwa destination retained. | Use real cleanup and persistence behavior. |
| 0:16.5–0:17.4 | Journeys opens at a lower scroll position, showing session cards. | Preserve scrollability and real records. |
| 0:17.5–0:19.0 | Settings modal opens over blurred history. Console mount, km, and Light are visibly selected. Close dismisses it. | Modal opening/closing is demonstrated; preference persistence is not demonstrated by this clip. |
| 0:20.5–0:22.0 | Navigate returns to planner. | Preserve navigation state coherently. |
| 0:22.5–0:24.0 | Back returns to map dashboard with collapsed bottom sheet. | Dashboard/planner are separate UI states. |
| 0:24.0–0:26.5 | Sheet expands to show location, confidence bar, drift/loss age, rate and filter cards. | Expand/collapse behavior is clearly demonstrated. |
| 0:27.0–0:42.5 | Sheet collapses; map stays visible. Repeated touches occur across map and controls; exact effects are not established except a brief compass icon/orientation change around 0:37.5–0:39. | Do not infer every control works. |
| 0:43.0–0:44.0 | Sheet expands again. | Repeated sheet interaction works in prototype. |
| ~0:44.5–0:45.0 | Sheet collapses again. | Keep bottom navigation clear. |
| 0:45.5–0:47.5 | Journeys opens and scrolls between lower and upper sessions. | History is a scrollable list. |
| 0:48.0–end | Returns to dashboard. | Existing app navigation should map to these states. |

### Target presentation constraints

- Screenshot 184928: expanded rounded white sheet, pale map, top search/status pills, compass/recenter/layers on right, blue/green confidence bar, two small telemetry cards.
- Screenshot 184949: same dashboard collapsed; marker, heading cone, uncertainty ellipse, and recent track visible.
- Screenshot 185004: planner contains origin/destination, three saved POIs, Drive/Walk/Cycle, route summary, blue Get directions, bottom navigation.
- Screenshot 185027: navigation banner, next-step strip, green mode badge, three right-side controls, blue route with white dashed detail, bottom confidence overlay and speed/fusion badges. This screenshot contains overlapping bottom widgets; adapt to a coherent readable phone layout instead of copying the overlap.
- Screenshot 185100: history header and storage, three metrics, session cards, bottom nav with Settings.
- Screenshot 185113: Settings modal with profile, mount setup, units, theme, save action.
- Dashboard/planner bottom nav says Navigate / Journeys / Sensors, while history uses Navigate / Journeys / Settings. This inconsistency is real in references; the brief's consistent-navigation fallback should be applied if compatible with source.
- Desktop taskbar, dotted canvas, device border, and dashed selection outlines are editor artifacts.
- Reference phone playback clips sheet content beneath bottom navigation in collapsed mode. Navigation bottom widgets present in the screenshot are absent from the visible phone clip. Treat those as reference/responsiveness limitations; do not reproduce clipping.
- Clip does not establish a Sensors page, functioning voice search, genuine map gestures, preference save persistence, journeys Details behavior, or a toggle connecting the two navigation overlay arrangements.

## Existing app device recording (9:08.99)

The app remains in active navigation for nearly the whole clip. Destination is “halalpur bus stand” until the abrupt final change. It displays a pale page with stacked route/search cards, a dark map card, fixed dark bottom navigation, and speed/status card inside the map. System location is toggled several times in quick settings. First toggle is explicitly visible changing the location icon from active (~0:28) to inactive (~0:29).

| Time | Observed behavior / visible issue |
| --- | --- |
| 0:00–0:26 | GPS Healthy. Route distance decreases; speed mostly 24–30 km/h. Destination header reports 5.4 km/about 5 min while maneuver card shows its own remaining-distance/time estimate. Map interaction around 0:24 pans the view so the marker briefly approaches/leaves its top edge. |
| 0:27–0:29 | Quick settings opened; location icon changes from on to off. |
| 0:30–1:59 | Red “Sensor tracking · GPS limited” badge; “Following estimated position · reduced precision” plus “Based on sensor estimate · check road signs.” Speed and marker continue changing. |
| 0:41–0:48 | “Updating your route…” appears, route changes, instruction becomes “turn right” (~0:42), distance increases to 5.7 km, then instruction returns to “continue” (~0:48), with remaining distance around 4.9 km. Requires route-decision/log review; no proof of actual wrong turn is available. |
| ~1:05–1:39 | Marker visibly departs from the drawn route in places while instructions continue advancing. Arrow direction rotates substantially. Screen alone does not establish whether this is heading error, raw estimate display, route progress, or actual vehicle path. |
| 1:41–1:46 | User pans the map; marker goes outside viewport. View returns to marker around 1:47. Camera motion here must not be confused with estimator jumping. |
| 1:55–1:56 | Quick settings opened again. |
| ~2:00 | GPS Healthy returns. Marker/route placement changes as updates resume. |
| 2:07–2:12 | Two “Updating your route…” episodes, route redraws, turn instruction changes from keep-left to roundabout/exit onto VIP road, destination summary changes from 5.7 to 5.0 km. |
| 2:17–2:18 | Quick settings opened. |
| 2:19–3:32 | GPS-limited sensor tracking. Speed rises from about 35 to 43–45 km/h; route progresses. |
| 2:30–2:36 | Repeated route updates while GPS limited; destination summary decreases from 5.0 to 4.8 then 4.7 km. |
| 3:30–3:31 | Quick settings opened. |
| 3:33–3:35 | Transitional “GPS signal unavailable” at about 41 km/h, followed by GPS Healthy at 34 then 29 km/h. Marker and indicated remaining maneuver distance shift on recovery. |
| 3:34–4:16 | GPS Healthy. Navigation continues. |
| 4:17–4:19 | Quick settings opened; location switched off again. |
| 4:20–5:10 | GPS limited. Speed climbs from about 39 to 57 km/h; marker eventually appears above/off the route near VIP Road. At ~5:10 telemetry says “Sensor-fused (INS) · estimated accuracy 191 m.” This value is an estimate shown by the app, not measured error. |
| 5:07–5:08 | Quick settings opened. |
| 5:11–5:14 | “GPS signal unavailable” at 52 km/h, then GPS Healthy at about 40, 37, 36 km/h. Route/marker reposition and update follow. This discontinuity is visible; actual vehicle speed cannot be derived from footage. |
| 5:14–5:19 | Rapid route/instruction changes: route update, “turn left” (~5:15; destination summary 2.8 km), “make a U-turn onto VIP road” (~5:17; summary 3.7 km), then roundabout instruction (~5:19). Strong target for inspecting recovery/reroute gating and stale requests. |
| 5:20–5:51 | GPS Healthy, navigation continues; speed mostly mid 30s to 40. |
| 5:52–5:53 | Quick settings opened. |
| 5:54–7:04 | GPS limited. Speed climbs from about 38 to 59 km/h. From ~6:35 marker diverges progressively from drawn road/route; by 6:52–7:04 separation is conspicuous near Sufia Masjid. At 7:04 UI shows 59 km/h and “estimated accuracy 1387 m.” Large uncertainty is only communicated in tiny telemetry text and diffuse marker rings while maneuver guidance continues. |
| 7:01–7:02 | Quick settings opened. |
| 7:05–7:08 | “GPS signal unavailable” at 52 km/h, then GPS Healthy at about 38, 30, 35 km/h. Marker visibly shifts toward road over following seconds; route distance increases. |
| 7:09–9:07 | Persistent inline error: “This place is not close enough to an accessible vehicle road. Select its vehicle entrance or a nearby road.” At the same time GPS Healthy and the speed card’s “You’re on the right track” are visible for much of this interval; maneuver instruction and remaining distance keep updating. The error does not clear when marker is visibly back on the road (~7:32 onward), nor on subsequent GPS-limited period. This is the clearest directly visible stale/conflicting status problem. |
| 7:32 | Map recenters so marker returns to a central position after remaining near lower map area. Camera behavior may involve user recenter and should be checked separately from estimator behavior. |
| 8:18–8:19 | Quick settings opened. |
| 8:20–9:06 | GPS limited again; sensor tracking continues, speed roughly 36–43 km/h. Persistent accessible-road error remains visible. |
| 9:03–9:04 | Quick settings opened. |
| ~9:07 | GPS Healthy returns at roughly 42 km/h. |
| ~9:08–end | Abrupt cut/change to destination “Rajiv Gandhi Proudyogiki Vishwavidyalaya”, different map location around Bhopal Bypass, turn-right instruction, GPS-limited badge, speed 3 km/h, estimated accuracy 41.5 m. No destination-entry interaction is visible. May be a splice or discontinuity in recording; do not classify as automatic destination corruption without independent evidence. |

## Prioritized code/device investigation targets

1. Inspect navigation error ownership and clearing: a reroute failure should not leave a destination-selection error across successful location recovery and ongoing route progress. UI states should distinguish valid retained route, failed reroute, position uncertainty, and actual destination validation.
2. Trace the GPS loss/recovery cycles above with raw GNSS, IMU, speed, heading, covariance, mode, map-match residual, route-snap result, and reroute request/response timestamps. Particularly inspect 5:10–5:19 and 7:04–7:14 against available logs.
3. Investigate progressive speed growth and off-road estimates during loss; potential areas to inspect include acceleration bias, gravity/orientation transform, non-holonomic constraint gating, speed priors, and estimator reset/correction. These are hypotheses, not established root causes from video.
4. Inspect map matching and route progress gating under large estimated uncertainty; avoid treating a highly uncertain estimate as enough evidence for repeated maneuver advancement/recalculation. Confirm whether marker intentionally represents raw rather than matched position.
5. Inspect GPS recovery and asynchronous routing: ignore stale route responses, avoid repeated contradictory maneuvers during convergence, and verify bounded/disclosed corrections without hiding uncertainty.
6. Improve state visibility in new UI: the current State view button overlaps/truncates positioning badge text on this phone width; detailed sensor confidence/accuracy is tiny. Large uncertainty and degraded guidance should have a readable, truthful state.

## What cannot be claimed from this review

- The videos do not reveal ESKF/NHC implementation correctness, model inference, fusion weights, bias values, or map-match internals.
- No proof that AI is active/inactive is visible; absence of an AI label is not evidence about model execution.
- Displayed speed is not ground truth. Apparent drift needs comparison with recorded truth or independent positioning.
- System location toggles simulate unavailable GNSS input at OS level; they do not establish tunnel/obstruction response or live IMU subscription quality.
- No journey persistence, settings persistence, destination entry/start/exit behavior, offline map download, or empty-state behavior is exercised in the existing-app clip.
- Current recording must not be described as a newly performed physical-phone test. It is evidence of previously recorded behavior only.
