# Express + MongoDB API

From the root: `npm install`, start MongoDB, then `npm run dev -w server`. Copy `.env.example` to `.env` in this folder to configure `MONGO_URI`, `UPLOAD_DIR`, `PORT`, `CORS_ORIGINS`, and `API_KEY`. Files live under `uploads/drives/` and `uploads/models/`; MongoDB stores metadata only. Local development defaults to MongoDB on 127.0.0.1:27017.

All endpoints are prefixed with `/api`. Phone access uses `API_KEY` in `X-API-Key`. Model and report publishing uses a separate `ADMIN_API_KEY`; production startup requires both to differ. Never ship the publisher key in the phone. `/api/health` remains public for infrastructure checks. This API is a single trusted workspace, not a multi-user account service.

| Method | Path | Contract |
|---|---|---|
| POST | `/drives` | multipart `file` (.drlog, ≤500 MB), phoneModel, mount, route, notes, sessionId (UUID), durationSeconds, distanceMeters, source (device/simulation), startedAt (ISO UTC) |
| GET | `/drives` | array, newest first; phone/phoneModel, mount, route, source, limit (≤100), offset filters |
| GET | `/drives/:id/file` | streamed original DRLOG |
| POST | `/models` | multipart `file` (.tflite, ≤100 MB), name (motion/gps-health/speed), version (semver), placeholder (true/false) |
| GET | `/models/latest/:name` | metadata, SHA-256, placeholder flag, relative downloadUrl; semantic version ordering |
| GET | `/models/:id/file` | streamed model bytes for OTA |
| POST | `/reports` | driveId, ateMeters, driftPercent, timestamps[], driftMeters[]; validates equal lengths, monotonic seconds |
| GET | `/reports/:driveId` | report object or 404/pending |

Mount is windshield/dashboard/console; route is city/highway/tunnel/mixed. Session IDs make drive-upload retries idempotent. Validation failures remove uploaded files. No GridFS or memory Multer storage is used. Do not place MongoDB directly on a public network.

PowerShell examples (`curl.exe` avoids the Windows PowerShell alias):

```powershell
curl.exe http://localhost:3001/api/health
curl.exe -X POST http://localhost:3001/api/drives -F "file=@recording.drlog" -F "phoneModel=Pixel 8" -F "mount=dashboard" -F "route=city" -F "notes=Bhopal run" -F "sessionId=58c144ec-9f7c-49ac-a7b1-5a751db2c11f" -F "durationSeconds=60" -F "distanceMeters=400" -F "source=device" -F "startedAt=2026-09-12T12:00:00.000Z"
curl.exe http://localhost:3001/api/drives?mount=dashboard
curl.exe -X POST http://localhost:3001/api/models -F "file=@../android/app/src/main/assets/models/speed.tflite" -F "name=speed" -F "version=0.0.0" -F "placeholder=true"
curl.exe http://localhost:3001/api/models/latest/speed
```

`npm test` from the root uses an isolated temporary MongoDB database and tests the complete endpoint flow, malformed input, cleanup, auth, semver, downloads and upload retries. It requires the local MongoDB server running.

## Railway / Render

The complete self-hosted HTTPS/MongoDB/persistent-storage configuration is in [deploy/README.md](../deploy/README.md). Set `ADMIN_API_KEY` as well as `API_KEY`. The Docker build uses the root lockfile and runs the API as a non-root user. Container execution has not been tested on this Windows host because Docker is unavailable.

Deploy with the included Dockerfile (repository-root context), set MONGO_URI to Atlas or your private MongoDB, NODE_ENV=production, API_KEY to a strong secret, CORS_ORIGINS to your UI/Capacitor origins, and UPLOAD_DIR=/data/uploads. **Mount a persistent disk at `/data`**; ephemeral platform filesystems lose recordings on redeploy. Expose PORT through HTTPS and use `/api/health` as the health check. Single-instance deployment is required for local disk storage; multiple API instances need a shared filesystem. Set platform ingress upload limits/timeouts to accommodate recordings. Back up both Mongo metadata and the upload disk.

No hosted service was provisioned automatically. Enter a deployed HTTPS API URL and workspace key in the Android app to use the backend away from the development computer.
