# IDR Navigator

**Current version: OFF-ROUTE. The live ESKF integration, raw IMU/GNSS recording format, physical phone checks, and verified Supabase-to-laptop data path are documented in [current implementation and verification](docs/live-eskf-and-training.md). The submission ZIP includes `START-HERE.md` with installation, asset restoration and rebuild instructions. Bundled IO-VNBD models are trained but remain unvalidated for navigation corrections. Driving/outage accuracy remains unverified. The older release notes below describe historical versions and are superseded by the current report where they differ.

A React 18 + Capacitor 6 Android app with a Java sensor-fusion engine, Madhya Pradesh offline maps, and an Express/MongoDB drive and model registry. The browser includes an explicitly labeled simulation for exploring the complete recording/upload workflow.

**Main-app 1.3 development:** shared live/replay Java fusion, gravity and course corrections, stale-sensor handling, GNSS recovery, local diagnostic export, refined IO-VNBD speed/motion models, separate model-publisher credentials, and persistent HTTPS deployment configuration. See [main-app status](artifacts/MAIN-APP-STATUS.md) and [deployment instructions](deploy/README.md). Real-recording replay still has unacceptable drift; this is a field-test build, not a validated navigation release. The earlier judge APK remains available separately.

**Prototype 1.2:** offline coverage now includes Madhya Pradesh statewide roads, water and parks (39,285 tiles), plus the central-Bhopal building overlay and a statewide GraphHopper graph. GPS/stationary-speed fixes and IO-VNBD-trained models are included. Start with [the judges demo](artifacts/JUDGES-DEMO.md); [GPS/map update notes](artifacts/GPS-AND-MAP-UPDATE.md) supersede the original Bhopal-only coverage notes below.

## Run locally

Use Node.js 20 LTS (see `.nvmrc`). The project was also built and tested with the installed Node 24 runtime.

Installation applies an idempotent one-line Capacitor 6 CLI compatibility fix for the security-patched `tar` 7 dependency. If your package manager suppresses lifecycle scripts, run `node scripts/patch-capacitor.cjs` before `cap sync`.

```powershell
npm install
npm run db:start
```

Leave MongoDB running. In a second terminal:

```powershell
npm run dev
```

Open http://localhost:5173. The API runs at http://localhost:3001 and Vite proxies `/api` to it. `db:start` downloads the official MongoDB 7 binary on first run, then starts a **real MongoDB server** with persistent WiredTiger storage in `data/mongo`. It is a local development convenience, not an in-memory database substitute. Existing local/Atlas MongoDB works too: set `MONGO_URI` in `server/.env` from `server/.env.example` and skip `db:start`.

```powershell
node scripts/seed-models.mjs
npm test
npm run build
```

The seed command publishes the three **IO-VNBD-trained** models as `1.0.0`, with separate device-validation flags. Docker users can run `docker compose up --build` for the API and database, then `npm run dev:web` for the interface.

## Build Android

Install Android Studio, JDK 21, Android SDK platform 36 and build tools. Set `JAVA_HOME` to JDK 21 and configure `android/local.properties` with your SDK path. The checked-in wrapper uses Gradle 8.14.3 and AGP 8.13.0.

```powershell
npm run android:sync
cd android
.\gradlew.bat testDebugUnitTest assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Android 8.0/API 26 or newer is required. Install the APK on your phone, allow **precise** location, keep it mounted, and tap **Start engine**. Wait for the first GPS fix before recording. Android system app settings expose the optional “Allow all the time” background location permission separately. Notification and physical-activity runtime permissions are requested on the applicable OS versions. High-sampling sensor permission is declared in the manifest and has no runtime dialog.

For a phone connected by USB, run `adb reverse tcp:3001 tcp:3001`, then set the app’s API address to `http://127.0.0.1:3001`. HTTP is allowed only in the debug build. An emulator uses `http://10.0.2.2:3001`. For an untethered phone use a deployed **HTTPS** API and enter its URL and workspace key in Settings.

## What is implemented

- Onboarding and permission explanation; live map, heading, status, trust details, local recording, upload retry, drive history, drift chart/pending report, settings, dark/light themes.
- 160 locally served Mapbox Vector Tiles from real OpenStreetMap data. Coverage: **central Bhopal**, longitude 77.38–77.43, latitude 23.22–23.27. This is a small city-center extract, not all of Bhopal. No map API key or network is needed on the installed app.
- Android location foreground service, accelerometer/gyro/magnetometer and raw GNSS collection, wake lock, 100/200 Hz DRLOG capture, a single EJML-backed 15-state ESKF, GPS/physics/HMM/AI-speed advisors, and typed TFLite inference.
- GraphHopper 7 HMM with `measurementErrorSigma=30.0`, executed outside the sensor thread. The first engine start copies a bundled, JVM-verified graph into private storage; subsequent starts reuse it. The OSM extract is included as its source.
- Disk-streamed Multer uploads, Mongoose metadata, authenticated deployment option, semantic model versions, SHA-256 model validation, and atomic native model installation.

## Honest limits

This is an engineering build, not a validated navigation instrument. **No physical Android phone or configured emulator was connected during development.** Sensor permissions, device-specific sampling, screen-off behavior, native TFLite execution, OTA installation, and road accuracy require the hardware checks in `android/README.md`.

The three bundled `.tflite` files were trained on the real [IO-VNBD dataset](https://github.com/onyekpeu/IO-VNBD), with entire recordings held out. Speed test MAE is 4.09 m/s versus a 5.98 m/s median baseline; motion balanced accuracy is 63.81%. The GPS proxy classifier failed to generalize (28.23%). Pothole/phone-knock labels and C/N0 are unavailable in this subset. Models execute inference, but learned corrections retain **zero fusion trust** pending device validation. Read [the model card](artifacts/ml/MODEL-CARD.md) and [training instructions](ml/README.md) for exact supervision, limitations and reproducibility.

Inertial position estimates can drift substantially, especially without initial mount alignment, during prolonged outages, or outside the offline map area. The Kalman and map-matching tests demonstrate algorithm integration, not field accuracy. The app is a tracker/logger; turn-by-turn destination routing was not in the brief.

Reports require ground-truth post-processing by your team. The app never invents ATE/drift metrics. Until a report is posted, it displays **Report pending**. A shared API key protects a single team workspace; this version does not offer multi-user accounts or tenant isolation.

## Project layout

| Folder | Purpose |
|---|---|
| `src/` | React UI, Axios API and typed Capacitor bridge |
| `android/app/src/main/java/com/idr/navigator/engine/` | Java capture, ESKF, advisors, inference, DRLOG |
| `android/app/src/main/java/com/idr/navigator/plugins/` | Three custom Capacitor plugins |
| `server/` | Express API, Mongoose schemas, integration tests |
| `ml/` | Offline Python scripts and tensor/log contracts |
| `public/maps/` | Offline map and labeled simulation polyline |
| `scripts/` | Local database, map generation, model seed utilities |

Read `src/README.md`, `android/README.md`, `server/README.md`, and `ml/README.md` for layer-specific contracts.

## Design and source notes

The attached brief omitted DRLOG string lengths; v1 fixes all three strings at 64 bytes (200-byte header total). The stated record layout is 70 bytes, so three hours at 100 Hz is **75.6 MB decimal**, not the brief’s 35 MB. Motion features are frozen to 42 values and GPS health to 10; precise order is documented in `ml/README.md`.

Map data © OpenStreetMap contributors, [ODbL](https://www.openstreetmap.org/copyright), downloaded from the [Overpass API](https://overpass-api.de/) on 2026-09-12. GraphHopper integration follows the [v7 HMM source](https://github.com/graphhopper/graphhopper/blob/7.0/map-matching/src/main/java/com/graphhopper/matching/MapMatching.java). Android plugin structure follows [Capacitor’s Java plugin documentation](https://capacitorjs.com/docs/v6/plugins/android).
