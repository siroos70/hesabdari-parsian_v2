# Template: Bugfix or Database Optimization

Use this template when diagnosing and fixing bugs or optimizing queries and database access.

## Metadata

- **Bug symptom / issue description:** 
- **Suspected area:** <!-- Select one or more: Rust core | FFI / bridge | Room / DAO | UI / Compose | Backup / Restore -->

## Investigation & Reproduction Checklist

- [ ] Reproduced failure with a failing automated test BEFORE making implementation changes.
- [ ] Documented the exact reproduction command and failure output.
- [ ] Verified whether the defect is pre-existing using `git log` / `git blame` rather than assuming.
- [ ] If Room schema or entity is affected, verified the change against the `Room Migration Checklist` in `AGENTS.md`.
- [ ] If business logic is affected, verified canonical fix belongs in `rust/hesabyar-core`.
- [ ] If FFI boundary is touched, verified binding regeneration and isolated bridge tests.

## Completion Evidence Standard

Before declaring this fix complete, provide exact evidence:

1. **Changed code:** Paste the exact current code from disk.
2. **Raw test output:** Include named test output (JUnit testcase or cargo test line).
3. **Paths and line numbers:** Include exact references (`file:line`).
4. **Verification commands:**
   - For Rust fixes: `./scripts/check-rust.sh`
   - For Android fixes: `./scripts/check-android.sh`
   - For FFI / bridge fixes: `./scripts/check-rust-bridge.sh`
