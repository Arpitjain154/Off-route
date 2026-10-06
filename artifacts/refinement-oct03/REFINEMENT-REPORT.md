# Offroute 1.5.5 refinement - 3 October 2026

## What changed

- Removed Backend API address, Workspace API key, connection test and legacy model controls from Settings. The remaining collection section retains capture rate and consent controls. Supabase is retained. No navigation layout, map style or maneuver redesign.
- Added a separate **AI speed estimate (experimental)** reading in Sensors. This is the model's real output, with units and availability; it is explicitly excluded from navigation until phone validation passes. Advisor trust stays honest and is not forced above zero.
- A stopped or stale IMU stream no longer permits repeated inference over an old three-second feature window. Invalid, missing or stale raw predictions display Unavailable.
- Android-interrupted uploads now return to the retry queue instead of permanent action-required. The previous exact Upload paused failure is recoverable only for the currently authorized account generation. Other permission and account errors stay protected.
- Supabase Storage's wrapped error responses are recognized. The actual approved-model request returned HTTP 400 with NoSuchKey / statusCode 404. It now means no approved release is available instead of a generic Cloud request failed (400). Permission failures remain failures; raw server response text is never shown.
- Empty legacy backend configuration no longer causes periodic requests or remote journey-list requests.

## Evidence examined

- Supplied diagnostics ZIP and its raw, fusion and quality recordings.
- Supplied 244-second video, sampled at 16 timestamps. It shows transitions from GPS to IMU, speed drift during outages, and return to GPS. Screen recording alone is not a ground-truth speed measurement.
- Four phone-local journeys, copied with checksums, plus the installed APK backup.
- Thirteen complete cloud journeys downloaded and verified against their completion manifests. Two remote journeys were incomplete. An initial local download-folder error was recovered on retry.
- Combined evaluation: 16 distinct journeys (one overlaps phone/cloud), 1,323 quality-filtered GPS samples. Raw speed model weighted RMSE was **9.90 m/s**, approximately **35.6 km/h**. In the supplied journey, model output averaged **43.6 km/h** at GPS-labelled stopped samples. These results reject enabling its corrections.
- Combined dataset still fails the existing requirement for at least 100 quality fixes in the validation split. No replacement model was promoted, no validation flag was bypassed, and no experimental model was uploaded.

## What this does not establish

The GPS-outage drift shown in your video is real evidence of remaining navigation limitations, not proof you are using the app incorrectly. This update repairs the confirmed reporting, stale-inference and cloud failures. It does not claim to solve long GPS outages or meet the original drift target. Keeping AI trust at zero prevents an inaccurate model from worsening the route.

An IMU-ready badge only confirms sensor freshness. Reliable inertial tracking also needs a trustworthy starting GPS position, heading and stable mount alignment. Guidance must still pause when the estimate becomes unreliable. It should resume when a reliable GPS fix returns.

## Use and next road check

1. Grant precise location permission and leave Android Location enabled. Use a fixed dashboard/windshield mount and keep that mount unchanged during the journey.
2. Start outdoors, wait for a fresh GPS position, then allow the vehicle to establish a reliable course while moving normally before relying on a GPS interruption bridge.
3. In Sensors, AI speed estimate is diagnostic output. AI speed advisor trust of 0% is expected for this unvalidated model; it is not the vehicle speed display.
4. Use Supabase cloud contribution for uploads. The removed workspace key belonged to the older, separate custom backend and is unrelated to the Supabase connection.
5. Finish a journey so its raw recording closes and upload can complete. In poor connectivity, queued uploads should resume under the existing network preference.
6. For the next test, keep GPS enabled to capture reference measurements. Include normal stops, straight travel and turns with the same fixed mount. Record the actual time of any drift or pause, then export diagnostics. Do not hold or operate the phone while driving.

## Verification

- Android unit suite: 99 tests, 97 passed, 2 skipped; no failures/errors.
- Interface unit suite: 23 passed, including raw speed units, unavailable readings, calibration labels, route and recording behavior.
- TypeScript compile passed. Production UI build passed.
- Independent code review: corrected new test method arguments and one text-encoding issue; no further material regression findings.
- Browser checks passed for Settings removal/preserved controls and planner-to-navigation-to-exit. Screenshots were visually reviewed.
- APK built successfully; archive CRC and UI payload checks passed. The update and original APK signing certificates match.
- Installed **1.5.5-offroute, versionCode 14** in place on the connected SM-S711B. Android reported install Success.
- Live phone check passed: six fresh AI outputs reached the interface, displayed as 0.0 km/h in this stationary check; validation remains false and AI navigation trust remains zero.
- On-phone Settings confirms legacy fields absent and Supabase/data collection retained. Screenshots visually reviewed.
- Paused trip automatically recovered and uploaded: phone now reports two uploaded, zero pending and zero needing attention.
- Model check now reports **No approved model release available**, replacing the generic 400 error.
- Four saved journeys, cloud sign-in, upload consent and Wi-Fi-only preference are preserved.
- This is a stationary smoke test, not a road-accuracy validation. The next controlled road recording remains necessary.

## Files

- Source diff: changes.patch; changed-file inventory: changed-files.json.
- Model evaluation: combined-model-evaluation.json; local-only evaluation: model-evaluation.json.
- Video evidence: video-1-sheet.jpg and individual sampled frames.
- Original APK: ../../recovery/refinement-oct03/installed-original.apk.
- Source snapshots: ../../recovery/refinement-oct03/source-before/.
- Phone recordings: phone/drives/. Cloud download inventory: cloud-download.json.

## Delivered APK

`../Offroute-1.5.5.apk` (221,113,363 bytes). SHA-256: `bb0eeddd7966210e664992913a4e53f526a01b4c0d61bd64059387de2f0ebc21`.

Recording integrity verification: all 20 saved journey files match their pre-update byte counts and SHA-256 checksums.

