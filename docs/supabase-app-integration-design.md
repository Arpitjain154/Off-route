# Supabase app integration

Status: design approved by the user, 2026-09-29. Implementation plan awaiting review. No application changes or remote policy changes have been made.

## Outcome

Extend the existing working Android application to upload complete navigation recordings to the user's Supabase project and consume approved model releases. Laptop training is outside this change. Preserve offline navigation and current bundled models.

## Existing components verified

- SensorCaptureService writes DRLOG, training provenance, quality, fusion trace and summary files. Queue only after files have closed successfully.
- SensorPlugin exports diagnostics, but the current frontend journey uploader sends only DRLOG and basic metadata to Express/MongoDB.
- ModelPlugin verifies checksums and tensor shapes and blocks installation during tracking. It does not retain a complete rollback transaction.
- Cloud facts verified later in this review are recorded below. The project key, bucket privacy and authentication configuration still require inspection. The organization dashboard URL alone is not an API endpoint.

## Selected approach and alternatives

Use direct Supabase Auth/Storage integration with a native persistent worker. This avoids requiring the existing Express/MongoDB server for cloud recording storage. A JS-only queue would stop when the WebView closes; extending Express would introduce another hosted service. Keep existing legacy API features available separately.

## Authentication and privacy

Use email/password Supabase accounts for recoverable user identities, with email verification when configured. Users enter credentials in the app; no passwords or privileged keys are committed. Publishable/anon keys identify the project but do not grant administrative access. Store refresh credentials encrypted with Android Keystore; serialize refresh operations. Sign-out cancels pending work and removes credentials. Bind queue entries to project and user identity so signing into another account cannot upload another user's recordings.

New cloud contribution is opt-in and separate from local collection. Wi-Fi-only is the default. Existing recordings require an explicit enqueue action; new eligible trips enqueue automatically after consent. Upload no simulation recordings. Preserve local originals and show missing-metadata errors rather than inventing quality labels.

## Storage contract

Inspect the existing bucket before applying changes. Preserve its contents. Use a private trip bucket and paths under `<user-id>/<session-id>/`. Scope list/read/insert/update permissions to the authenticated user's prefix and exact bucket. Audit existing policies because permissive policies combine; adding a restrictive policy alone cannot cancel broad existing access.

Required payloads are the original `.drlog`, `.quality.csv`, `.training.json`, plus portable summary metadata. Retain optional fusion traces for diagnostic evaluation when included. Exclude device-local paths from cloud metadata.

Use a versioned manifest with original filenames, byte counts, SHA-256 hashes, schema and provenance. Split large files into independently retryable objects below the verified bucket size limit. Specify chunk ordering and full-file checksum so future desktop ingestion can reconstruct the exact original files. Mark a session complete only by uploading its final manifest after all required objects are verified. Partial sessions must not be eligible for training.

## Background queue

Use Android WorkManager with unique jobs scoped to project/user/session and persistent per-file progress. Stream files instead of loading a whole journey into the WebView. Hash and prepare on a background executor, never the navigation sensor handler. Retry transient network/rate-limit/server errors with backoff; surface denied access, quota and malformed-data errors as actionable states. Refresh expired authentication once through the serialized session manager; pause on invalid refresh credentials.

Network changes, app process loss and reboot preserve queued work; force-stop may require reopening the app before Android resumes jobs. Consent withdrawal cancels work and prevents further requests; already-uploaded objects are not automatically deleted. Reconcile completed native recordings after restart without uploading an active file.

## User interface

Add cloud setup/sign-in, contribution consent, Wi-Fi preference and sync status within Settings. Journey details show local, queued, uploading, uploaded or action-required states with retry. Cloud failure must never prevent navigation, saving a trip or reading local journey history. Keep all current recording controls and navigation presentation intact.

## Approved model releases

Use a separate private model bucket or equivalent separately protected namespace. App accounts can read approved releases but cannot publish binaries or approval manifests. Publishing remains an administrator operation for now; no laptop trainer is built in this phase.

Release metadata includes schema version, model name, semantic version, immutable object path, byte size, SHA-256, feature contract, tensor types/shapes, minimum app version and approved/validated state. Do not treat file existence as approval. No release available is a normal state; retain bundled models.

Check periodically under Android scheduling limits and when the user requests it. Download to staging; verify origin, size, checksum, compatibility, interpreter loading and finite smoke-test outputs. Activate only between trips through a lock shared with engine startup. Retain prior binary AND metadata and use a crash-recoverable installation journal. Restore the previous version if activation fails. Never alter tracking/model trust merely because a binary downloaded successfully.

## Verification and delivery

Test queue persistence, duplicate requests, interrupted multipart uploads, expired authentication, account switches, revoked consent, denied cross-user access and missing sidecars. Verify server objects reassemble byte-for-byte with local originals. Test corrupted/incompatible/unapproved model rejection, downgrade rejection, tracking-time deferral and rollback after interrupted activation.

Run TypeScript build, targeted Java/unit tests and Android APK build. On the connected phone verify offline navigation still works, a complete test trip uploads, a retry survives network interruption and an invalid model cannot replace the working version. Back up the current APK and retain app data; do not uninstall the working application. Live connection completion requires access to the actual Supabase project and an authenticated test account.

## Verified cloud configuration (signed-in inspection)

- Organization: Team Path-Finder (`ygeuajpmgtyawspwwtck`).
- Project: OFF-ROUTE (`bepfbvpwdguupquuthoe`), healthy, Free plan, Seoul region.
- API URL displayed by dashboard: `https://bepfbvpwdguupquuthoe.supabase.co`.
- Existing bucket: `journeys`; contents loaded and empty.
- Bucket file-size limit: Unset (50 MB); allowed MIME types: Any.
- Policies page shows no journeys policies, no other storage.objects policies and no storage.buckets policies.
- Bucket public/private flag and Auth provider settings remain unverified. No cloud settings have been changed.

## External setup dependency

The user has signed in to the supplied organization dashboard in the Codex browser. Verify bucket privacy and enabled Auth providers, then obtain the publishable project key through the dashboard before connecting the app. Never request a service-role key for the mobile app. Apply storage policies only after the required approval for granting new access to private journey data.

References: https://supabase.com/docs/guides/storage/security/access-control and https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
