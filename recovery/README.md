# OffRoute recovery copies

The phone was updated in place to com.idr.navigator 1.5.1-refinement (10). App storage was not cleared.

- installed-IDR-1.4.0.apk: original installed 1.4.0 (8), saved before changes.
- phone-data-pre-update.tar: private app data captured immediately before the update. This was a live snapshot and is not guaranteed transactionally consistent.
- phone-local-storage-before.json: original preferences/configuration, retained privately. Do not publish this file.
- Original recovered project source/assets: C:/Users/surya/.codex/.chatgpt-projects/g-p-6aa5938fc2f48191bcebfbb1e379eaac/recovery/source-baseline. That source was 1.5.0-qa (9), newer than the original installed APK.

For a rollback, first save another copy of the current phone data, then reinstall the original signed APK in place using Android's update and downgrade options. Never uninstall or clear app storage to work around a signature/version error. The original and refined APK signing certificates were verified to match. Retain the existing Android signing key for future source builds.

The new source ZIP and installable APK are in the parent project's artifacts directory. See docs/refinement-report.md for changes, verification and the still-unmet drift target.
