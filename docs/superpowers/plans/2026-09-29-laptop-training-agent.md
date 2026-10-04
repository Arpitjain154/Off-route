# Laptop training agent implementation plan

**Goal:** Give the owner one Windows entry point for completed-trip download, conservative learning, independent comparison, phone testing, explicit approval, and publication.

**Architecture:** A local Python package wraps the existing `phone_learning.py`. Supabase ingestion uses the signed-in user's prefix and the v1 completion manifest. Candidates and evidence live under ignored `artifacts/laptop-agent`; production assets are never training outputs. A Windows launcher owns setup, interactive credentials, scheduling and the menu.

**Constraints:** GPS labels only; whole-session splits; at least five independent trips; fail closed on insufficient evidence; explicit human approval after phone testing; immutable version binaries with `approved.json` last; learned corrections remain disabled in bundled assets. No navigation behaviour changes.

**Review focus:** Corrupt/hostile manifests, repeated or cloned trips across splits, stale evidence after binary changes, interruptions during downloads/publication, and scheduler overlap or missing login.

1. Write failing ingestion tests; implement authenticated storage, strict manifests, checksum reconstruction and atomic cache publication.
2. Write failing evaluation and data-budget tests; reuse phone quality filtering and ridge training, freeze a candidate and baseline, compare exported TFLite predictions and run the real Java live navigation engine with withheld GPS.
3. Write failing approval/publication tests; add phone inference parity testing, recorded field-test attestation, evidence hashes, explicit approval and binary-first publication.
4. Add Windows setup/menu/Task Scheduler scripts with encrypted user login, no wake timer, overlap protection and adaptive checks.
5. Run Python and applicable Java/project tests; inspect connected-phone upload status; document exact copy/paste commands and any blocked live checks.

The user supplied the scope and requested implementation. Work proceeds in the current checkout because the repository's existing project is entirely untracked; creating a worktree would omit it. No existing files are staged or committed.
