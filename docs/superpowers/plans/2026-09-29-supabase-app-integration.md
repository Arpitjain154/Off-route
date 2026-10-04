# Supabase App Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for recommended inline execution, or superpowers:subagent-driven-development if the user chooses delegation. Steps use checkbox syntax for tracking.

**Goal:** Connect Android trip collection to private Supabase storage and deliver approved model updates without disrupting offline navigation.

**Architecture:** Native authentication/session storage feeds a persistent WorkManager upload queue and model-update worker. React displays status and controls through a Capacitor plugin. A complete-session manifest commits each uploaded trip; an installation journal protects model activation and rollback.

**Tech Stack:** Existing React/TypeScript, Capacitor 6, Java 17 Android, Android Keystore, WorkManager, Supabase Auth/Storage REST, existing TFLite interpreter.

**Spec:** `docs/supabase-app-integration-design.md` (user approved).

## Global Constraints

- Preserve offline navigation and current bundled models; laptop training is outside this change.
- Project URL: `https://bepfbvpwdguupquuthoe.supabase.co`; existing bucket: `journeys`.
- Use email/password Supabase accounts for recoverable user identities, with email verification when configured.
- Store refresh credentials encrypted with Android Keystore; serialize refresh operations.
- New cloud contribution is opt-in and separate from local collection. Wi-Fi-only is the default.
- Existing recordings require an explicit enqueue action; upload no simulation recordings.
- Preserve local originals; missing quality files are errors, not manufactured labels.
- App accounts cannot publish model releases. No service-role key in the app.
- Activate models only between trips. Preserve the prior binary and its metadata.
- Keep legacy Express/MongoDB functionality separate; do not reinterpret its API settings.
- Use one sequential plan: authentication, queue, UI and updates share state and require integration testing together.
- Repository inspection found the application files untracked. Before implementation, create a recoverable local baseline without staging credentials, caches, recordings or unrelated files. Do not use a clean worktree that omits the actual application. Check managed-worktree availability before choosing isolation.

## Review Focus

1. Account or project changes during upload must never transfer the previous account's recordings (Tasks 2–3).
2. A process can die after server acceptance but before local acknowledgement; retries must recover without corrupting data (Task 3).
3. Phone storage exhaustion or a missing sidecar must preserve the original trip and display an actionable error (Tasks 1, 3).
4. Starting navigation during model activation must acquire the same lock, never opening a partially installed model (Task 5).
5. Cloud errors or sign-out must not prevent recording, viewing history or offline navigation (Tasks 4, 6).

## File map

New native code lives under `android/app/src/main/java/com/idr/navigator/cloud/`: CloudConfig, CloudSessionStore, SupabaseClient, TripPackage, CloudQueueStore, CloudUploadWorker, CloudScheduler, ModelRelease, ModelUpdateWorker. Each owns the responsibility named by its class.

New native bridge: `plugins/CloudPlugin.java`. Model transaction code: `engine/ModelInstaller.java`. Native tests mirror these names under `android/app/src/test/java/com/idr/navigator/cloud/` and `engine/`; Android-specific tests live under `android/app/src/androidTest/java/com/idr/navigator/cloud/`.

Frontend: new `src/cloud.ts`, `src/CloudSettings.tsx`, `src/cloud-settings.css`; targeted integration edits in `src/App.tsx` and `src/engine.ts`. Keep credential state out of React localStorage.

Cloud configuration: `supabase/migrations/202609290001_offroute_storage.sql`, `docs/supabase-setup.md`, `docs/cloud-trip-format.md`. New focused frontend tests: `scripts/test-cloud-state.mjs` and `scripts/test-cloud-ui.mjs`.

### Task 1: Define and test complete trip packaging

**Files:** TripPackage.java, TripPackageTest.java, docs/cloud-trip-format.md.

**Interfaces:** `TripPackage.prepare(File drivesDir, String sessionId, File stagingDir): Manifest`; `Manifest` contains schemaVersion=1, sessionId, source=device and `List<FileEntry>`; each FileEntry contains originalName, sizeBytes, sha256 and ordered `List<Chunk>`; each Chunk contains objectName, sizeBytes and sha256. Required files: DRLOG, quality CSV, training JSON and sanitized summary JSON. Optional fusion trace is explicitly listed when present.

- [ ] Write failing tests `requiresQualityAndMatchingIdentity`, `rejectsSimulationAndTraversal`, `sanitizesLocalPaths`, `reassemblesLargeFileExactly`: assert missing metadata fails, UUID/provenance mismatch fails, local paths are absent, and concatenated chunks have the original SHA-256. Use a small injectable chunk size for boundary tests.
- [ ] Run `android\gradlew.bat -p android testDebugUnitTest --tests "*TripPackageTest"`; verify assertion/absent-class failure.
- [ ] Implement streamed hashing and staging with production chunks at most 8 MiB; enforce the actual configured bucket limit if lower. Bound memory to buffer size, not journey size. Clean partial staging on failure without deleting originals. Validate DRLOG framing and completion before packaging; never read an active recording.
- [ ] Repeat targeted tests; expect pass, including simulated disk-write failure preserving originals.
- [ ] Document reconstruction, canonical names and `complete.json` uploaded last. Record checksums for smoke fixtures; checkpoint only task files after baseline is accounted for.

### Task 2: Add private authentication and cloud configuration

**Files:** CloudConfig.java, CloudSessionStore.java, SupabaseClient.java, CloudSessionStoreTest.java, Android credential-store tests, SQL migration and setup documentation.

**Interfaces:** `CloudConfig(projectUrl, publishableKey, journeyBucket, modelBucket)`; `CloudSessionStore.signIn(email,password)`, `signUp(email,password)`, `validSession()`, `signOut()`; Session contains projectUrl, userId, accessToken, refreshToken, expiresAt and generation. `SupabaseClient.upload(path,File,Session)` and `download(path,File,Session)` stream over HTTPS and return classified errors. Redact credentials from errors/logs.

- [ ] Write and run failing tests: expired token refreshes once; concurrent refresh is serialized; sign-out invalidates old generation; project change clears authentication; invalid refresh pauses work; privileged keys and non-HTTPS project URLs are rejected.
- [ ] Implement Keystore AES-GCM persistence, memory-only password use, no session tokens in bridge responses. Provide controllable HTTP transport/clock/storage seams for JVM tests; verify real encrypted persistence in Android instrumentation.
- [ ] Inspect existing bucket privacy, Auth configuration and publishable key in the signed-in dashboard. Document email verification flow; do not disable verification to bypass sign-in problems.
- [ ] Prepare idempotent SQL for private `journeys`, private `model-releases`, authenticated trip access only under `auth.uid()/`, and read-only approved-release access for app users. Model binaries and manifests are written by administrators only; candidates are never published to this namespace. Restrict policies to exact buckets. Preserve existing objects and unrelated policies.
- [ ] Show the concrete policy changes and obtain the browser-required confirmation immediately before applying new access permissions. Verify user A cannot read/write user B's path, unauthenticated requests fail, and app users cannot publish models. User signs in/creates test credentials themselves.
- [ ] Run targeted JVM and device tests. Record results without secrets; checkpoint relevant files.

### Task 3: Implement durable background uploading

**Files:** CloudQueueStore.java, CloudUploadWorker.java, CloudScheduler.java and corresponding tests; modify `android/app/build.gradle`, `engine/SensorCaptureService.java`, `IdrApplication.java`.

**Interfaces:** `CloudScheduler.enqueue(String sessionId, boolean explicitRequest)`, `setPreferences(boolean consent, boolean wifiOnly)`, `reconcile()`, `cancelForAccount()`; `CloudQueueStore` persists project/user/session, authorization generation, per-chunk progress and state (`local`, `queued`, `uploading`, `uploaded`, `action-required`). A queued trip is permanently bound to its original cloud identity.

- [ ] Add failing tests for duplicate scheduling, interrupted upload after remote acceptance, expired login, consent withdrawal, account changes, quota/permission failures and process restart with partial staging. Assert `uploaded` only after complete.json is verified.
- [ ] Add a stable WorkManager version compatible with the project's actual compile/min SDK, verifying official release documentation at implementation time. Use unique work, network constraints and exponential backoff. Retry I/O, 429 and transient 5xx; surface 403, quota, invalid auth and malformed data without an endless retry loop.
- [ ] Stream deterministic chunk uploads to `<uid>/<sessionId>/`, using verified re-download/hash when reconciling an ambiguous prior upload. Commit complete.json only after every required object succeeds. Check consent/session generation before every request and final state commit.
- [ ] After successful native stopLog, schedule background preparation without blocking the sensor handler. Persist eligibility/consent identity for newly completed trips; reconcile on app restart. Do not auto-enqueue older trips, active files or incomplete recovery records.
- [ ] Cancel workers on sign-out/consent withdrawal; keep local files. Persist status/reasons; document Android force-stop limits. Run the queue tests with a deterministic fake transport, then Android worker tests.
- [ ] Checkpoint relevant files and results.

### Task 4: Connect app controls and journey status

**Files:** CloudPlugin.java, MainActivity.java, src/cloud.ts, src/CloudSettings.tsx, src/cloud-settings.css, src/App.tsx; frontend state/UI tests.

**Interfaces:** Capacitor CloudPlugin methods `configure`, `signIn`, `signUp`, `signOut`, `setPreferences`, `getStatus`, `enqueueTrip`, `retryTrip`, `checkModelUpdates`; `getStatus` exposes account email/ID, connected state, consent, wifiOnly, sanitized queue entries and model update state, never tokens/passwords. Native is the source of truth; frontend refreshes status on resume and relevant completion events.

- [ ] Write failing tests for disconnected state, sign-in errors, verification-required state, pause/retry, sign-out and project change. Assert browser preview cannot upload simulation records and cloud errors leave navigation controls usable.
- [ ] Register the bridge and implement native calls on background execution. Enqueue only UUIDs validated against files in the private drives directory, never arbitrary caller-supplied paths.
- [ ] Add Settings controls using current visual styles; default cloud contribution off and Wi-Fi-only on. Add explicit upload/retry for existing journeys and retain local history when disconnected. Distinguish legacy-server uploaded state from Supabase uploaded state; never relabel one as the other.
- [ ] Replace misleading unconditional “No automatic cloud upload” text with actual consent/sync status. Keep navigation layout and fusion behavior intact. Preserve legacy settings and report behavior.
- [ ] Run `node --test scripts/test-cloud-state.mjs scripts/test-cloud-ui.mjs` and `npm run build`; expect all tests/build to pass. Checkpoint task changes.

### Task 5: Add approved update checks and transactional rollback

**Files:** ModelRelease.java, ModelUpdateWorker.java, ModelInstaller.java and tests; modify ModelPlugin.java, TfliteModels.java, SensorCaptureService.java, IdrApplication.java, CloudScheduler.java, src/engine.ts.

**Interfaces:** `ModelRelease.parseAndValidate(String json, InstalledModel current, int appVersionCode)` validates schema=1, supported name/version, approved=true, validated=true, min app version, immutable same-project object path, hash, size and exact feature/tensor contract. `ModelInstaller.install(ModelRelease release, File staged)` and `recover()` own the install lock/journal and previous binary+metadata. Both legacy and Supabase installation paths use this transaction.

- [ ] Write failing tests for unsigned/unapproved metadata flags (flags are authoritative only from the protected publisher namespace), wrong feature contract/type/shape, wrong checksum, truncated file, older/same version, nonfinite smoke output and absent release. No-release must preserve current models without an error alert.
- [ ] Implement authenticated manifest/download reads from the protected release bucket, bounded size checks, tensor validation including dtype, and finite-output smoke inference. Do not accept arbitrary external download URLs.
- [ ] Schedule a daily update check with the same network preference and a manual check action. Authentication or network failure retains working models. Check/download may stage an update; install must defer while tracking.
- [ ] Implement a recoverable journal containing old and new binary/metadata hashes and phase. Preserve a previous model before replacement, persist journal transitions, and atomically rename verified files. Recover interrupted transactions at startup before inference. Record blocked versions after failed activation to avoid repeated failure loops.
- [ ] Share an installation/startup lock with engine startup; test starting a trip during activation and faults at each journal transition. Test restoring bundled baseline when no prior download exists. Do not infer fusion validation from transfer success.
- [ ] Run focused Java tests and Android interpreter/rollback tests; checkpoint task files.

### Task 6: Build, verify on phone and deliver

**Files:** docs/supabase-setup.md, docs/cloud-trip-format.md; verification evidence under artifacts/cloud-integration/ (exclude credentials and private raw recordings).

- [ ] Run `node --test scripts/test-recording-lifecycle.mjs scripts/test-navigation-transition.mjs scripts/test-cloud-state.mjs scripts/test-cloud-ui.mjs` and `powershell -File scripts/build-android.ps1`; require passing frontend checks, Java tests and APK build.
- [ ] Back up the installed APK, verify matching signing identity and preserve app data. Install as an update only; if signature differs, stop instead of uninstalling. User completes cloud sign-in in the app.
- [ ] Verify one authorized physical completed trip uploads all required files and reassembles to identical checksums; verify pending/uploaded UI states. Do not silently upload historical trips.
- [ ] Verify network interruption/retry and app restart retain queue progress; revoke contribution and verify no new requests. Verify a signed-out user can still navigate offline and save a trip.
- [ ] Use nonpublished local test fixtures to reject corrupt/incompatible models and exercise rollback. Do not publish an unvalidated candidate to the approved channel merely for testing. Confirm the current deployed model remains available.
- [ ] Document cloud setup, exact storage format, how a future laptop trainer reads complete sessions, administrator-only publishing, remaining limitations and actual test evidence. Report any unavailable device/live tests explicitly; do not claim connection is complete without a successful authenticated upload.

## Plan self-review

Each approved spec section maps to Tasks 1–6. Queue identity and credentials remain native; frontend status excludes secrets. Large trip support uses documented chunk reconstruction, so existing Python scripts cannot consume cloud chunks directly until the later laptop-ingestion work. The plan adds no trainer and publishes no new model. Privacy configuration and live authentication are dependencies for cloud verification, not reasons to replace security with administrator keys.

## Execution handoff

Recommended: native/inline execution in this chat to minimize context overhead and keep shared authentication/queue/install contracts consistent. Alternative: subagent-driven task implementation with independent task reviews. User reviews this plan and selects execution before product changes begin.
