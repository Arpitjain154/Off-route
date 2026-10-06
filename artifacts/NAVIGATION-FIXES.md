# Navigation corrections — 1.4.0

This remains a field-test build. It addresses the reported navigation defects without claiming validated inertial navigation during a GPS outage.

## Diagnosis and changes

1. **Vehicle routing:** the former profile was already `car/fastest`, and tagged footpaths and stairs were excluded. The more specific defects were permissive unspecified gates, ambiguous tracks, no enabled turn costs, and no separate motorcycle profile. A shared import/load policy now supplies car and motorcycle profiles, turn restrictions, conservative gates/barriers, and exclusion of pedestrian ways and ambiguous tracks. The bundled statewide graph was rebuilt; old on-device graph caches are bypassed through a new directory version. Places more than 80 m from an accessible snapped road require choosing a vehicle entrance.

   A real GraphHopper import regression reproduces a 204.3 m shortcut through an unspecified gate with the former policy; both new profiles use the longer open road. This is a controlled fixture, not proof that the particular Patanjali passage is repaired. The RGPV source audit finds a fence and several road crossings; geometric crossings alone do not establish whether an entrance is passable. The recorded-area-to-RGPV audit still produces a 1,504 m car route. An exact pin and physical verification of the reported obstruction remain necessary. Unmapped obstacles cannot be inferred from a residential road tag.

2. **Live position and rerouting:** the marker already consumed pose updates, but a persistent start pin and breadcrumb trail obscured progress, and no live rerouting existed. Active navigation now shows the current marker and remaining route, hides the fixed start pin/trail, and requests a new offline vehicle route after sustained deviation on distinct reliable GPS fixes. Requests are serialized, rate limited, and discarded if their route has been canceled/replaced. Manual-origin route previews remain previews.

3. **Heading:** the old public heading depended on vehicle alignment that required faster travel; recorded magnetic data was not used for public orientation. The app now uses reliable GNSS course while moving and an accuracy-qualified rotation-vector compass at low speed, with circular smoothing and limited blending only when the sources agree. Compass direction is explicitly phone facing, not a claim about the vehicle mount. The map stays north-up and rotates the arrow in the same frame; unavailable heading uses a dot.

4. **Speed and signal loss:** the old display used the experimental filter's constrained velocity magnitude. Lateral constraints could reduce that magnitude during turns, and uncertain inertial output remained visible during outages. The new public navigation output uses validated, fresh receiver speed with symmetric smoothing. It does not estimate turn speed from a long straight chord. Stop detection combines fresh GNSS, a compact accuracy-qualified position history, and quiet accelerometer/gyro data. Ordinary turns no longer activate the research filter's lateral stop-like constraint. When GPS becomes stale or disabled, speed displays “—”, position holds at the last accepted fix, and rerouting pauses. Fresh GPS restores speed in either direction. Experimental INS still runs for research recordings; it is not represented as trustworthy dead reckoning.

## Evidence collected

- `physical-tests/analysis.json` analyzes two actual phone recordings (246 s and 98 s). The latter contains five moving observations where the old fused speed is less than half the receiver speed. This identifies disagreement, not ground-truth speed error.
- `RecordedNavigationTest` processes their original IMU/GPS fields through the new output. Maximum differences from recorded receiver speed after smoothing are 0.215 and 0.176 m/s; dropout output is unavailable. DRLOG v1 lacks exact receiver timestamps, bearing and uncertainty, so it cannot fully reproduce heading. Future trace CSVs now include those fields and the public navigation output.
- 27 selected native regression tests passed, including source validation, turns, two-minute stationary jitter, actual recording processing, real graph routing and the gate-detour fixture.
- Six JavaScript pose/route-progress tests passed. TypeScript and production UI build passed.
- Browser UI checks passed: open, both offline autocomplete fields, swap, invalid input, cancel, Escape, preserved tabs and no JavaScript errors.

## Device results

- **Physical Samsung, Android 16:** five minutes of real GPS, with the user confirming the phone was stationary. All 135 sampled observations were fresh; speed stayed at zero after the initial 20-second settling allowance. Four tab/background cycles preserved one GPS registration, engine generation and process. Early acquisition had transient speed readings (maximum 1.636 m/s); this is not a claim of zero noise immediately on acquisition. Manually disabling Android location displayed “—” and held the last position. Re-enabling location restored GPS without another listener. The first recovered fix was less accurate (29 m) and reported 0.628 m/s before re-confirming a stop; recovery was checked for availability, not a second five-minute stationary pass. Full data: `physical-tests/phone-stationary-and-outage.json`.
- **Android 16 emulator:** `NavigationBehaviorTest` passed in 49.275 seconds using controlled GPS inputs, actual bundled offline route calculation and the rendered WebView. It checked marker movement, a second route request after sustained deviation, 90°/180° headings, constant turn speed, unavailable speed during an outage, recovery to a higher speed and a single listener. An initial test location was rejected by the new 80 m accessible-road check; the corrected fixture starts on a known vehicle road. The initial WebView timeout and rejected-location runs are not passes.
- APK contents verified: 39,285 statewide map tiles, eight vehicle-graph files and three unchanged IO-VNBD models. APK v2 signature, ZIP alignment and both 64-bit native libraries' 16 KB alignment passed.
- Final APK launch/lifecycle regression passed on Android 16 in 16.925 seconds: automatic service start, repeated tab returns and activity recreation retained one GPS listener. Final APK is installed on the Samsung. A last post-install phone launch inspection encountered the locked/off screen, so its final foreground auto-start check awaits unlock; this does not replace or invalidate the earlier five-minute physical run.

The five-minute phone test and emulator test ran against the navigation implementation before a final two-line explanatory-copy correction. Real moving-trip heading accuracy and the exact obstructed Patanjali connection require field confirmation; simulated inputs are not counted as physical travel.

## APK

`IDR-Navigator-1.4.0-navigation-fixes.apk`, version code 8, 221,155,994 bytes.
SHA-256: `009b6853c57d6059ab2ac0346dc080ec6a5a8601986ceff0fa035a3e4fe432d1`.
The earlier test package hash was `6c14fa98ead223e182eea5c6dc92c8468707fc22fd3cf8eda39c8164d28dc4de`; only explanatory UI copy differs. This APK uses the existing debug signing identity for in-place testing; it is not a store-signed production release.
