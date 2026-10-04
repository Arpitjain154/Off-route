# OFF-ROUTE cloud connection

Project `bepfbvpwdguupquuthoe`, URL `https://bepfbvpwdguupquuthoe.supabase.co`. Mobile configuration uses a publishable key, never a privileged secret. The existing Express server is independent of this connection.

The user approved and the dashboard executed the policies in `supabase/migrations/202609290001_offroute_storage.sql`: private journeys with user-prefix SELECT/INSERT/UPDATE, and private model-releases with authenticated SELECT only. No app-user DELETE policy. No existing files were deleted. The journeys bucket retains its global 50 MB file cap; model-releases has an explicit 50 MiB cap. Uploaded pieces are 8 MiB or smaller.

## Phone setup

1. Open Settings → Cloud contribution.
2. Create an app account with email/password; confirm the email if requested, then sign in. This is separate from the Supabase organization dashboard login.
3. Enable contribution for new trips. Wi-Fi-only is enabled by default. Changing network preference or revoking contribution cancels/reschedules background work.
4. End a navigation session to save its complete files. Its eligible package uploads when Android permits and network constraints match. Old trips require the explicit Supabase upload/retry action in Journey details.
5. Observe queued/uploading/uploaded/action-required states. A cloud failure does not stop local recording. Keep local originals until you independently verify your backup.

Email verification, Auth rate limits and bucket quotas are enforced by Supabase. Repeated registration may require waiting; do not disable verification or place an administrator key in the app to bypass errors. A public key alone is not a logged-in account.

## Background behavior

Queue progress survives process death and reboot. Android may delay work due to power/network constraints; force-stop requires reopening the app. Sign-out removes encrypted local session credentials and stops cloud work, preserving local files and previously uploaded objects. Account switching does not transfer queue ownership. New recordings taken without contribution enabled remain local.

The server stores trips; the app does not train itself. See cloud-trip-format.md for future laptop reconstruction and publisher format.

## Recovery assets

`artifacts/cloud-integration/source-before-cloud.zip` contains the pre-change source snapshot. `installed-before-cloud.apk` is the previous installed APK; the signing certificate was checked against the new APK before installing with `adb install -r`. Do not uninstall the app to replace a build, because that removes private recordings.

## Verification record

See `artifacts/cloud-integration/` for build/test evidence. Live authenticated upload, device retry and update validation must be recorded separately; a successful build alone does not prove a live cloud connection.
