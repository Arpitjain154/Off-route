# Focused navigation UI update — 28 September 2026

Scope: active navigation after route selection. Prior dashboard, native estimator, model weights, routing and recording lifecycle are retained.

Implemented a full-map navigation layout, compact maneuver and subsequent-step strips, heading-up follow camera with north-up/recenter controls, speed circle, position confidence card and GPS advisor-trust ring. Dashboard tabs are hidden only on the active map; the information control opens Sensors and its Navigate tab returns to guidance. GPS and IMU chips reveal their live details.

The status follows existing engine data: GPS tracking, degraded GPS, IMU tracking during loss, last position held, or unavailable position. High uncertainty still pauses precise instructions. GPS advisor trust is labelled explicitly; it is not presented as a fabricated GPS/IMU percentage mixture. No voice guidance was introduced.

Reviewed the supplied 49.8-second recording at 12 evenly spaced frames and the attached screenshot. The video shows planner-to-navigation, compact bottom instruments and GPS/IMU detail taps. The desktop and phone frame were excluded.

Verification: TypeScript and production bundle built; 16 focused navigation/route/recording/map tests passed. Browser checks passed for planner-to-navigation, GPS/IMU/unavailable/recovery display, detail taps, Sensors return, exit, portrait and landscape. Sensor transitions in browser checks are explicit fixtures, not physical outage accuracy evidence. Device/build results are stored beside this report as phone-results.json and apk-verification.json.

Recovery: previous installed refinement APK remains artifacts/OffRoute-1.5.1-refinement.apk; changed source files were copied to recovery/navigation-ui-before. The active source remains the user's original project. Existing prior drift-accuracy limitations remain unchanged. A real mounted driving/outage test remains required.
