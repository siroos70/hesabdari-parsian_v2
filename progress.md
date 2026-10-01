# Current Progress

> This file tracks the current working state for AI coding agents.
> It is not an architecture specification, changelog, or source of truth.
> Always verify important claims against the current repository and `AGENTS.md`.

## Active Task

None.

## Goal

Prepare repository guidelines, quick checks, workflows, and PR template for AI agent and vibe coding workflows.

## Completed

- **Task:** Vibe Coding preparation
- **Status:** Completed with known pre-existing verification failure
- **Completed Date:** 2026-09-25
- **Deliverables:**
  - [x] `AGENTS.md` additions (Architecture Pattern, UI Constraints, Room Migration Checklist, Progress Tracking, Quick-Check Scripts)
  - [x] `rust/hesabyar-core/README.md` (UniFFI procedural macro architecture, canonical money rules, method lifecycle, quick checks)
  - [x] `scripts/check-rust.sh` (clippy + workspace tests)
  - [x] `scripts/check-android.sh` (ktlintCheck + detekt + testDebugUnitTest + lintDebug)
  - [x] `scripts/check-rust-bridge.sh` (testDebugUnitTestRust without `--rerun-tasks` per user decision)
  - [x] Prompt templates in `.github/prompts/` (`new-compose-screen.md`, `new-rust-method.md`, `bugfix-or-db-optimization.md`)
  - [x] Pull request template in `.github/pull_request_template.md`
  - [x] `progress.md` lifecycle documentation and baseline

## In Progress

None.

## Blocked

None.

## Findings

1. **Room Schema Documentation Drift:** `AppDatabase.kt` defines schema `version = 9` with `exportSchema = false`. However, `docs/DATABASE_SCHEMA.md` lists schema version 3, and `docs/MIGRATION_NOTES.md` documents migrations only through v3.
2. **Missing MigrationTestHelper Test Infrastructure:** While JVM unit tests exist for migrations (e.g. `AppDatabaseMigrationTest.kt`, `AppDatabaseMigration7to8Test.kt`, `AppDatabaseMigration8to9Test.kt`), AndroidX `MigrationTestHelper` test suite using exported Room JSON schemas is not currently present in `app/src/test`.
3. **UniFFI Implementation:** UniFFI uses procedural macros (`#[uniffi::export]`) and scaffolding (`uniffi::setup_scaffolding!()`). No `.udl` interface definition files exist.
4. **Android Lint Failure in Pre-existing Code:** `scripts/check-android.sh` fails on task `:app:lintDebug` with 4 errors:
   - 1 local machine issue: `local.properties:8` (`PropertyEscape` on unescaped Windows backslashes in `sdk.dir`).
   - 3 pre-existing Compose issues: `ManualTransactionDialog.kt:132, 138, 149` (`LocalContextGetResourceValueCall` from querying resources using `LocalContext.current`).
   Per task rules, application code was left unchanged.

## Decisions

- Retained existing `rust/hesabyar-core/README.md` technical build and pre-commit hook instructions while adding UniFFI architecture, canonical money rules, and new method lifecycle.
- Kept all check scripts strictly read-only (`check-android.sh` does not run `ktlintFormat`).
- Removed `--rerun-tasks` from `scripts/check-rust-bridge.sh` per direct user instruction to avoid 11-14 min NDK rebuilds and preserve incremental Gradle task checking.

## Verification

| Check | Command | Result |
|---|---|---|
| Rust | `./scripts/check-rust.sh` | PASS (490 unit tests passed; clippy clean; exit 0) |
| Android | `./scripts/check-android.sh` | FAILED at lintDebug (ktlintCheck UP-TO-DATE; detekt UP-TO-DATE; testDebugUnitTest PASS; lintDebug failed with 4 errors in pre-existing files; exit 1) |
| Rust Bridge | `./scripts/check-rust-bridge.sh` | PASS (45 actionable tasks executed; exit 0) |

## Next Steps

1. Address pre-existing `LocalContextGetResourceValueCall` lint errors in `ManualTransactionDialog.kt` in a dedicated task.
2. Align `docs/DATABASE_SCHEMA.md` and `docs/MIGRATION_NOTES.md` with database version 9.
3. Configure Room schema export and add `MigrationTestHelper` integration tests.

## Last Updated

2026-09-25
