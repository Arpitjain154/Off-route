# Cloud trip format v1

Private Supabase bucket: `journeys`. A session lives at `<authenticated-user-id>/<session-uuid>/`.

The app writes immutable file content in numbered objects (`<session>.drlog.part00000`, etc.), each at most 8 MiB. Files include DRLOG, quality CSV, training provenance, sanitized summary and optional fusion trace. Original files stay on the phone. Cloud summaries omit private filesystem paths.

New recordings also declare `rawSidecarsSchemaVersion: 1` in training provenance and the completion manifest. Such trips require both `.sensors.csv` and `.gnss.csv`; the file allowlist now permits seven files. Legacy four/five-file v1 trips remain supported. Capture/write/close failures prevent completion and upload.

`.sensors.csv` retains each accelerometer (Android type 1), gyro (4), magnetic field (2), gravity (9), and rotation vector (11) callback before navigation filtering or DRLOG rate limiting. Columns are `t_ns,sensor_type,accuracy,value_count,v0,v1,v2,v3,v4`; unused values are empty. Timestamps are original elapsed-realtime nanoseconds and axes are Android device axes. Acceleration/gravity use m/s², gyro rad/s, magnetic field microtesla, and rotation vectors retain Android's x/y/z/w plus optional heading accuracy in radians. Android accuracy status is retained, including unreliable events.

`.gnss.csv` retains each receiver callback with `fix_t_ns,arrival_t_ns,utc_ms,latitude_deg,longitude_deg,altitude_m,horizontal_accuracy_m,vertical_accuracy_m,speed_mps,speed_accuracy_mps,bearing_deg,bearing_accuracy_deg,mock,accepted`. Coordinates retain double precision; optional absent fields are `NaN`. Fix and callback arrival times use the same monotonic nanosecond clock as sensors. UTC is Unix milliseconds. Distances/errors use meters, speeds/errors m/s, and bearing/errors degrees. Mock/rejected observations remain available for audit, but cannot become training labels. Acceptance records the live navigation decision, not a filtered position.

Training prefers original accelerometer/gyro timestamps and receiver speed from declared sidecars. Missing/corrupt declared streams fail closed without falling back to DRLOG or fusion estimates. Windows require fresh, accepted, non-mock GNSS with speed error ≤1 m/s and horizontal error ≤15 m; sensor gaps over 100 ms are rejected. Existing DRLOG v1 training remains supported with its older provenance limitations. Neither ESKF estimates nor AI predictions are labels.

`complete.json` is uploaded and downloaded for checksum verification only after all chunks are verified. Consumers must ignore a session without this marker. The manifest contains `schemaVersion: 1`, `source: device`, `sessionId` and `files`. Each file has `originalName`, `sizeBytes`, SHA-256 and an ordered `chunks` array. Every chunk contains `objectName`, `sizeBytes`, SHA-256.

Laptop ingestion authenticates, lists only its user's prefix, reads the complete marker, validates all names against the session UUID, downloads each listed object, checks chunk sizes/hashes, concatenates in manifest order, and verifies full-file size/hash. It reconstructs into a temporary directory and renames only after verification. Unchecked manifest names must never become filesystem paths. The existing phone_learning.py ingests the reconstructed DRLOG and sidecars.

Upload checks do not establish suitability for learning: the existing desktop pipeline still rejects short trips, weak GPS labels, duplicates and missing sensor quality. A successfully uploaded trip may be unsuitable for training.

## Model releases

Private `model-releases` is readable by authenticated app users and writable only by administrators. Put only reviewed releases here; store candidates elsewhere. Upload each binary at `<name>/<version>/<name>.tflite` before publishing `approved.json` last. Do not mutate an existing version.

Example publisher manifest (illustrative; replace size/hash with measured values):

```json
{"models":[{"schemaVersion":1,"name":"speed","version":"1.2.0","approved":true,"validated":true,"minAppVersionCode":12,"featureContract":"idr-iovnbd-v1","dtype":"float32","inputShape":[1,300,6],"outputShape":[1,1],"path":"speed/1.2.0/speed.tflite","sizeBytes":31000,"sha256":"REPLACE_WITH_ACTUAL_SHA256"}]}
```

Motion uses input `[1,42]` and output `[1,5]`; GPS health uses `[1,10]` and `[1,3]`. The feature contract is the current IO-VNBD preprocessing in ml/README.md. Never approve a model based only on training completion or a valid tensor shape. Test complete navigation performance and regressions independently.

The app checks daily when network constraints allow and on request, ignores already-installed versions, verifies compatibility and file integrity, and installs only when tracking is stopped. The previous binary/metadata remain in private app storage. A journal recovers an interrupted install; failed activation versions are blocked until a new publisher version is supplied. This cannot guarantee drift accuracy or automatically detect every numerically plausible but inaccurate model.
