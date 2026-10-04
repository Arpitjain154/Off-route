# Workspace Memory — offroute 3.0

Last updated: 2026-09-17 (Asia/Calcutta)

## User request

The user asked to save the previous available information into this workspace folder. This file is the durable workspace record for that context.

## Project identity

- Project: IDR Navigator (`idr-navigator`), workspace folder `offroute 3.0`.
- Application: React 18 + Vite frontend, Capacitor 6 Android app, Java sensor-fusion engine, Express/MongoDB backend, and offline ML/model tooling.
- Runtime guidance: Node.js 20 LTS is recommended; the project has also been built with Node 24.

## Current implementation context

- Main-app 1.3 work includes shared live/replay Java fusion, gravity/course corrections, stale-sensor handling, GNSS recovery, local diagnostic export, refined IO-VNBD speed/motion models, separate model-publisher credentials, and persistent HTTPS deployment configuration.
- Prototype 1.2 includes Madhya Pradesh offline roads/water/parks coverage, a central-Bhopal building overlay, a statewide GraphHopper graph, GPS/stationary-speed fixes, and IO-VNBD-trained models.
- The browser has an explicitly labeled simulation for the recording/upload workflow.
- Implemented areas include onboarding/permissions, live map, heading/status/trust details, local recording, upload retry, drive history, drift chart/pending report, settings, themes, Android foreground service, sensor/GNSS capture, ESKF, GPS/physics/HMM/AI-speed advisors, typed TFLite inference, uploads, metadata, authentication option, model versioning/validation, and atomic native model installation.

## Important limitations and validation status

- This is an engineering/field-test build, not a validated navigation instrument.
- No physical Android phone or configured emulator was connected during development; hardware validation remains required.
- Real-recording replay still has unacceptable drift. Inertial position can drift substantially during outages, without mount alignment, or outside offline map coverage.
- Learned corrections currently retain zero fusion trust pending device validation.
- GPS proxy classifier did not generalize; pothole/phone-knock labels and C/N0 are unavailable in the current dataset subset.
- Reports require team-provided ground truth; the app shows “Report pending” until a report is posted.
- The API uses a shared workspace key; there are no multi-user accounts or tenant isolation.
- The app is a tracker/logger; turn-by-turn destination routing was not in scope.

## Data/model facts

- Models were trained on the real IO-VNBD dataset with entire recordings held out.
- Reported speed test MAE: 4.09 m/s versus a 5.98 m/s median baseline.
- Reported motion balanced accuracy: 63.81%.
- Reported GPS proxy classifier accuracy: 28.23%.
- Three bundled TFLite models are seeded as version `1.0.0`.
- DRLOG strings are fixed at 64 bytes; header is 200 bytes. Record layout is 70 bytes; three hours at 100 Hz is 75.6 MB decimal.

## Useful commands

```powershell
npm install
npm run db:start
npm run dev
node scripts/seed-models.mjs
npm test
npm run build
npm run android:sync
cd android
.\gradlew.bat testDebugUnitTest assembleDebug
```

- Local web app: `http://localhost:5173`
- Local API: `http://localhost:3001`
- Debug USB phone API: `http://127.0.0.1:3001` with `adb reverse tcp:3001 tcp:3001`
- Debug emulator API: `http://10.0.2.2:3001`
- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`

## Key folders

- `src/`: React UI, Axios API, typed Capacitor bridge
- `android/`: Android app and Java engine/plugins
- `server/`: Express API, Mongoose schemas, integration tests
- `ml/`: offline Python scripts and tensor/log contracts
- `public/maps/`: offline map and simulation polyline
- `scripts/`: database, map, and model utilities
- `artifacts/`: status, demo, deployment, and model documentation

## Source of truth

For deeper details, consult `README.md`, `src/README.md`, `android/README.md`, `server/README.md`, `ml/README.md`, `artifacts/MAIN-APP-STATUS.md`, `artifacts/JUDGES-DEMO.md`, `artifacts/GPS-AND-MAP-UPDATE.md`, `artifacts/ml/MODEL-CARD.md`, and `deploy/README.md`.

## Scope note

Only information available in the current conversation and the project README/package metadata was recorded here. No unavailable prior chat history or hidden account memory was reconstructed.
