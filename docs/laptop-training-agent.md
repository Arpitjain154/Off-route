# OFF-ROUTE laptop training agent

The implementation is saved in this project. You do not need to create files or locate Python, Java, ADB, model files, or downloaded trips manually.

## Start here

Paste this into **Command Prompt, PowerShell, or Windows Terminal**:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\surya\Documents\ChatGPT\offroute 3.0\scripts\laptop-agent.ps1"
```

Choose **1 — Setup**. Enter the email and password used **inside the OFF-ROUTE phone app**, not your Supabase dashboard credentials. Password entry is hidden. The launcher verifies dependencies, saves an encrypted refresh token for your Windows account, verifies/downloads completed trips, enables Task Scheduler, and trains only if the data gates pass. No administrator key is needed for downloads or training.

You can also double-click `C:\Users\surya\Documents\ChatGPT\offroute 3.0\START-LAPTOP-AGENT.cmd`.

## Commands without menus

Verify uploads and download:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\surya\Documents\ChatGPT\offroute 3.0\scripts\laptop-agent.ps1" -Action Check
```

Check for usable data and train a candidate when ready:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\surya\Documents\ChatGPT\offroute 3.0\scripts\laptop-agent.ps1" -Action Run
```

Show status, rejected data, and report locations:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\surya\Documents\ChatGPT\offroute 3.0\scripts\laptop-agent.ps1" -Action Status
```

Disable the schedule while retaining all files:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\surya\Documents\ChatGPT\offroute 3.0\scripts\laptop-agent.ps1" -Action RemoveSchedule
```

## Once a candidate passes desktop checks

Use the menu in this order:

1. **6 — Phone inference test.** Connect one physical Android phone, enable USB debugging, and accept its USB prompt. Stop recording first. This builds/installs the test APK and verifies actual candidate TFLite inference against desktop outputs. It does not replace the normal navigation model.
2. **15 — Candidate Test app.** Builds and installs a separate `com.idr.navigator.candidate` app labelled **OFF-ROUTE Candidate Test**, with the candidate enabled only there. Your normal app, trip files, and bundled models remain intact. Grant the trial app its normal location/sensor permissions and configure the same mounting/map conditions. Leave its cloud contribution off.
3. Record a **new controlled test trip** in Candidate Test, including stopped periods, movement, GPS loss, recovery, and the map-matching behaviour under investigation. Record the screen too. Compare against the normal app/current baseline under comparable conditions using independent GPS/reference measurements. Test with a passenger or in a controlled setting.
4. **7 — Import field evidence.** The laptop retrieves completed trial recordings over USB and selects the newest matching trial by its recording file's timestamp. A file picker asks for the screen video. The reviewer confirms speed, stops, GPS loss, and map matching and enters the measured results/test setup. A trip from the normal app cannot satisfy the candidate-hash check. You can explicitly select a different trip through the Python CLI if several trials are present.
5. **8 — Approve.** Review the report, choose a newer semantic version, enter your reviewer name, and type `APPROVE`.
6. **9 — Publish.** Review the measured model hash/version, type `PUBLISH`, then enter the Supabase secret/service-role key privately. The key is used for that process only and is never saved. The binary is uploaded without overwriting a version, downloaded again for verification, and `approved.json` is written last.

Approval here is a named human review. The program cannot objectively judge a screen recording or establish physical accuracy just because someone answers YES. The evidence bundle links the exact model, desktop evaluation, physical runtime result, trial provenance, recording, and review. Changes to any of those invalidate approval.

Publishing does not force the normal phone to activate an update while tracking. Its existing cloud update worker checks compatibility and hashes and installs only when tracking is stopped.

## Data and model safeguards

- Only the logged-in user's UUID prefix is listed. A missing `complete.json` means the trip is ignored. Names, manifest schema, file/chunk order, lengths, and SHA-256 hashes are checked. Verified files appear atomically; a corrupt existing cache fails closed.
- `phone_learning.py` supplies fresh, unique GNSS speed observations: speed accuracy at most 1 m/s, horizontal accuracy at most 15 m, fix age at most one second, finite sensors, fresh gyroscope, and no IMU gap above 100 ms. App predictions, fusion CSV speeds, and map-matched positions are never labels. Candidate trial logs are excluded from ordinary training.
- At least five independent usable trips, at least 100 quality fixes in each whole-trip train/validation/test split, speed diversity, and at least 30 stopped fixes per split are required. Each trip needs at least 60 quality fixes. After a run, at least three new usable trips and 600 new fixes are required. Fixed session hashes determine splits; content fingerprints prevent copied recordings from changing identity across runs.
- Each candidate consumes fresh, never-previously-scored test trips. Failed/interrupted evaluations still reserve those trips; they are never recycled into training. Validation alone chooses ridge regularization. Exported TFLite bytes are scored independently.
- Baseline is the current approved cloud speed model, or the bundled model if no cloud speed release exists. The exact binary and metadata are frozen. A changed cloud baseline blocks publication.
- Require at least 5% held-out RMSE improvement; reject mean/tail speed-error regressions, speed-band regressions, out-of-range outputs, worse stopped false-movement rate/mean speed, worse outage drift, reduced availability, or a replay where candidate corrections never activate.
- Java replay uses the real `LiveNavigation`/`InertialNavigation` sources with 30-second GPS outages. Hidden GPS is reference-only. DRLOG omits some Android inputs, so reconstructed gravity/compass/course are approximations; map matching is not replayed. These limits are recorded and a controlled physical field review is mandatory.
- Training writes into `artifacts\laptop-agent\candidates`. It never overwrites bundled Android/frontend models, and normal bundled learned corrections remain disabled pending validation.

## Schedule and storage

Task name: **OFF-ROUTE Laptop Training Agent**. It runs under your signed-in Windows account, without elevation, while the laptop is awake, online and on AC power. It does not wake the computer or train while you are signed out. Task Scheduler invokes an hourly check, but network/training work is normally due every six hours. Three rejected candidates slow this to 24 hours and require five new trips/1,200 new fixes; six rejections slow it to 72 hours. A candidate awaiting human review pauses further training. Overlapping runs are blocked. Scheduled runs never approve or publish.

All results are under:

```text
C:\Users\surya\Documents\ChatGPT\offroute 3.0\artifacts\laptop-agent
```

Menu **13** opens that folder. `status.json`, `download-report.json`, and `quality-report.json` explain progress and blocked data. `logs` contains scheduled-run output. `login.dpapi` is encrypted to the current Windows account. Do not delete `state.json`: it remembers consumed holdout trips and duplicate identities.

Supabase publication uses an atomic-create `_publisher-lock.json` to serialize publishers using this agent. **All publishers must use this protocol; do not manually edit `approved.json` concurrently.** A crash may leave a lock. Inspect its owner/time, verify no publication is running, and have the administrator remove only that stale lock before retrying. There is no automatic lock stealing. Administrator access could bypass this workflow; local review files are audit records, not cryptographically signed attestations against a malicious administrator.

## Verification and current limits

Implementation checks completed on 2026-09-30: 21 laptop-agent tests (including actual TensorFlow export and Java outage replay), nine phone-learning tests, and 90 Android unit tests passed. The phone instrumentation APK and separate Candidate Test APK built successfully. The launcher status command also passed. Fixture tests establish software behaviour, not real-world model accuracy. The first trial build took about 53 minutes on this laptop because it packages the offline maps; allow time for that step.

The broader server test suite could not pass because its local MongoDB service at `127.0.0.1:27017` was unavailable. The laptop agent uses Supabase directly and does not require that MongoDB service.

On 2026-09-29 the live Supabase dashboard showed `complete.json` (1.76 KB) and five data chunks for session `23337fd9-b0ea-4682-a9ca-3fb0fce1d158`, under the app user's prefix. This verifies completion-marker presence; the laptop's own authenticated download/checksum pass starts after Login.

No live account password or publisher key was supplied to the laptop agent. No new real-data candidate was trained, approved, or published during implementation, and Task Scheduler registration is performed by Setup after login. No matching screen recording was attached. Do not interpret software tests or APK build success as a validated navigation model.

Protocol references: [Supabase storage listing](https://supabase.com/docs/reference/python/storage-from-list), [Supabase API keys](https://supabase.com/docs/guides/getting-started/api-keys), and [Windows task settings](https://learn.microsoft.com/en-us/powershell/module/scheduledtasks/new-scheduledtasksettingsset).

Code entry points: `ml/laptop_agent`, `ml/java/LaptopNavigationReplay.java`, `scripts/laptop-agent.ps1`, and Android's `LaptopCandidateTest` instrumentation test. The trial app adds model provenance metadata; it does not change speed, map matching, or navigation algorithms.
