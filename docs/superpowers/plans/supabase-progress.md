# Execution ledger: 2026-09-29-supabase-app-integration.md

User approved design, plan and inline implementation.
Ruling: work in the supplied workspace with a source backup because this repository has no commits and a clean worktree would omit all application code. Do not stage unrelated untracked files.
Ruling: keep publisher-only releases and user-scoped trip access; do not bypass remote permission approval or use privileged keys in Android.
Tasks: packaging, auth/configuration, persistent queue, UI, model transactions, verification/delivery.
Interface audit: package manifest feeds queue; native session and identity generation gate queue and release workers; bridge exposes sanitized status only; installer shared by cloud and legacy update paths.
Baseline backup: artifacts/cloud-integration/source-before-cloud.zip (local only).

Verified 2026-09-29:
- Implemented private Supabase auth/storage, encrypted native session, persistent WorkManager uploads, account/consent gates, cloud settings and transactional approved-model updates.
- Applied user-approved private bucket access policies through dashboard; results verified both buckets public=false.
- Independent review findings addressed: incomplete quality capture, installer-owned staged hashing, terminal queue reconciliation, authorization/revocation dispatch race.
- Reviewed build succeeded; 80 Android unit tests passed (17 cloud tests), plus cloud UI/state, recording lifecycle, navigation transition and server checks recorded in artifacts.
- Final reviewed APK installed with adb install -r: Success. App launched; native CloudPlugin.getStatus returned configured Supabase URL, connected=false, consent=false, wifiOnly=false, empty queue. Preserved current user preferences. Recent AndroidRuntime error log empty.
- Pending: user must sign in/create account in Settings > Cloud contribution, then opt into contribution. Live authenticated upload, device interruption/retry, cross-account access denial and real model update tests remain unverified. No accuracy/training claims; laptop trainer is outside current scope.
- Setup instructions: docs/supabase-setup.md. Delivery APK: artifacts/IDR-Navigator-debug.apk. Dashboard proof: artifacts/cloud-integration/supabase-private-buckets.png.

Login follow-up (2026-09-29):
- Reproduced phone message: Cloud request failed (400). Dashboard Auth logs confirmed six password-login requests rejected as Invalid login credentials; Users table showed no registered app users. Project key was not missing: input is a replacement field while native configuration uses the bundled publishable key.
- Added safe error-code messages for invalid credentials, email confirmation, registration/provider limits, password strength and rate limits; raw server response is not exposed. Trim email input. Added native keyConfigured boolean and explicit connection/first-time registration guidance.
- Three authentication error regression tests pass; full Android tests/build pass (83 total). User must create/confirm their app account to verify a successful login; dashboard credentials are separate.

Signup button investigation (2026-09-29):
- User confirmed Create account was greyed out. Phone inspection showed password length 0; server Auth logs showed /token failures and no /signup requests. Independent code review confirmed failed sign-in's finally block erased password, and signup silently required six characters.
- Replaced silent disabled validation with inline email/password checks. Both auth buttons remain available except during an active request. Failed requests preserve in-memory credentials so signup can follow sign-in; successful auth clears password. Status now appears beside auth controls. No credentials stored in browser storage, and passwords were not read during investigation.
- Focused isolated signup UI test passed: empty/invalid input does not call native auth, failed sign-in retains password, direct signup works afterward, buttons disable during requests, email is trimmed without changing password, successful signup clears password and shows nearby confirmation. Mock bridge only: no real signup/email sent.
- Build and Android tests succeeded. Supabase permissions/auth configuration unchanged; live signup still requires the user's own credentials.
