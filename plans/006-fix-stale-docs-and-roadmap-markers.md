# Plan 006: Fix stale docs and roadmap markers

> **Executor instructions**: Follow this plan step by step. Run every
> verification command heuristically appropriate for docs (diff and grep).
> If anything in the "STOP conditions" section occurs, stop and report —
> do not improvise. When done, update the status row for this plan in
> `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 44dd519..HEAD -- docs/`
> If any doc changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- Priority: P3
- Effort: S
- Risk: LOW — docs-only changes; no code behavior changes.
- Depends on: none
- Category: docs
- Planned at: commit `44dd519`, 2026-07-23

## Why this matters

Two of the project's onboarding docs contradict the codebase. New contributors/migrators reading them will make incorrect assumptions about currency math, package layout, and feature status.

## Current state

**DATABASE_SCHEMA.md** — line 7 and lines 75-77 say the conversion is `Rial ÷ 1000`; the codebase uses `÷ 10`.

Lines 1-7:
```markdown
# RhinoDB Schema
## Schema version 5
RhinoDB uses SQLite and has a schema version of 5.

RhinoDB uses **Rial** as the base unit in the database. User can change
this to **Toman** in the *settings* section of the app.RhinoDB execute the following
formulas to converts Rial to Toman:
<center>
<b>Test version</b><br>
Toman = Rial ÷ 10000<br>
<b>Production version</b><br>
Toman = Rial ÷ 1000<br>
```

Lines 75-77:
```markdown
### `transactions` table changes
The data is fixed while migrating from version 3. These transactions amount would
be divided by 1000 and stored in the database.
```

**ROADMAP.md** — claims `Use Cases Layer`, `Dependency Injection (Hilt)`, and `Full Test Coverage` are `[~] In Progress`.

Lines 89-91:
```markdown
| Dependency Injection (Hilt)       | [~] In Progress  | Hilt setup and @HiltViewModel in place    |
| Use Cases Layer                   | [~] In Progress  | Core use cases defined                   |
| Full Test Coverage                | [~] In Progress  | Unit tests for domain logic              |
```

Reality: every ViewModel uses `@HiltViewModel`, `domain/usecase/` has 14 files, and 50+ test files exist.

**ARCHITECTURE.md** — points contributors to paths that don't exist.

Lines 301-312:
```markdown
### Core AI (`core/ai`)

Provides unified AI processing capabilities. The `core/ai` module handles all AI-related operations through ...
```

Lines 469-484:
```markdown
### Design System (`core/designsystem`)

Provides theming, typography, and Material Design 3 components. The `core/designsystem` module is a
collection of shared UI components and design tokens used across all features. It includes ...
```

Reality: AI code lives under `api/`; shared UI/theme lives under `ui/components/`, `ui/utils/`, `ui/designsystem/`.

**BACKUP_FORMAT.md** — omits `bankLoans` and `settings`; mismatches the Rust schema.

Lines 17-28 in this file repeat the same Kotlin-looking shape as `BackupModels.kt`, but omit `bankLoans` and `settings` (and never mention `paymentHistories`).

## Commands you will need

Verify docs do not reintroduce contradictions:
- `rg "Rial ÷ 1000|÷ 1000|/1000" docs/`
- `rg "core/ai|core/designsystem" docs/`
- `rg "In Progress.*Use Cases Layer|In Progress.*Hilt|In Progress.*Test Coverage" docs/ROADMAP.md`

## Scope

In scope:
- `docs/DATABASE_SCHEMA.md`
- `docs/ROADMAP.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/BACKUP_FORMAT.md`

Out of scope:
- Any other docs, code, or README changes.

## Steps

### Step 1: Fix DATABASE_SCHEMA.md currency factor

In `docs/DATABASE_SCHEMA.md`:
- Change `Toman = Rial ÷ 10000` to `Toman = Rial ÷ 10` (test version was also wrong).
- Change `Toman = Rial ÷ 1000` to `Toman = Rial ÷ 10` (production version).
- Change `dividing by 1000` to `dividing by 10` in the migrations section.
- Add a one-line note that the factor is `10` per `CurrencyFormatter.fromRial()` and `rust/hesabyar-core/src/currency.rs:67`.

**Verify**: `rg "÷ 1000|/1000" docs/DATABASE_SCHEMA.md` returns no matches.

### Step 2: Mark completed roadmap items

In `docs/ROADMAP.md`, change:
- `Dependency Injection (Hilt)` → `[x] Done`
- `Use Cases Layer` → `[x] Done`
- `Full Test Coverage` → `[x] Done`

Keep all other rows unchanged.

**Verify**: `rg "In Progress.*Use Cases Layer|In Progress.*Hilt|In Progress.*Test Coverage" docs/ROADMAP.md` returns no matches.

### Step 3: Fix ARCHITECTURE.md package paths

In `docs/architecture/ARCHITECTURE.md`:
- Replace every occurrence of `core/ai` with `api/`.
- Replace every occurrence of `core/designsystem` with `ui/components/`, `ui/utils/`, `ui/designsystem/`.
- Add a one-line note: "Note: paths above refer to `app/src/main/java/io/github/mojri/hesabyar/...` packages."

**Verify**: `rg "core/ai|core/designsystem" docs/architecture/ARCHITECTURE.md` returns no matches.

### Step 4: Sync BACKUP_FORMAT.md with actual schema

In `docs/BACKUP_FORMAT.md`, append `bankLoans` and `settings` to the JSON example (following existing `camelCase`/`snake_case` convention of the doc). Add `paymentHistories` with a note that it is currently surfaced by Kotlin but only round-trips through Rust after Plan 003 lands.

**Verify**: `rg "paymentHistories|bankLoans|settings" docs/BACKUP_FORMAT.md` returns matches.

## Test plan

- Heuristic: re-run the `rg` grep validations from "Commands you will need" and confirm expected matches/mismatches.

## Done criteria

- [ ] `docs/DATABASE_SCHEMA.md` shows `÷ 10` everywhere it documents the Rial-to-Toman factor
- [ ] `docs/ROADMAP.md` marks Hilt, Use Cases, and Test Coverage as `[x] Done`
- [ ] `docs/architecture/ARCHITECTURE.md` contains no `core/ai` or `core/designsystem`
- [ ] `docs/BACKUP_FORMAT.md` mentions `bankLoans`, `paymentHistories`, and `settings`
- [ ] `plans/README.md` status row updated

## STOP conditions

- A doc contains code examples or formulas that depend on the old incorrect factor.
- The `rg` checks in "Commands you will need" don't produce the expected matches after edits.
- You discover the docs are auto-generated from another source that must be updated first.

## Maintenance notes

- Any future schema change must update both `docs/BACKUP_FORMAT.md` and `docs/DATABASE_SCHEMA.md`.
- When Plan 003 adds Rust support for `paymentHistories`, update `BACKUP_FORMAT.md` to remove the "pending Plan 003" qualifier.
