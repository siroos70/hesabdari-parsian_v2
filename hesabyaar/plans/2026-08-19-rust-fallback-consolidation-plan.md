# Hesabyaar — Rust/Kotlin Fallback Consolidation: Phased Migration Plan

Status: Proposed
Owner: مجتبی
Source audit: Rust Core Integration Audit Report (2026-08-19)
Target architecture: Option C (Hybrid), per ADR-001

## 0. Ground Rules (apply to every phase)

- **Every phase must leave `main` in a buildable, shippable state.** No phase may be merged if it leaves the app in a state where a later phase is required to make it work correctly.
- **Every phase is independently revertable via `git revert` without needing to revert any other phase.**
- **No phase changes production runtime behavior on the happy path** (Rust available, which is >99% of real usage) except the "Remove fallback + routing" phases (P6–P12), which are explicitly scoped and gated below.
- **Evidence-first rule applies to every phase**, per existing project standard: exact current code, raw test-runner output by test function name, exact file/line references. No "done"/"tests pass" claims without this.
- **One phase = one PR = one CodeRabbit/cubic pass = one merge.** Do not start phase N+1 until phase N is merged.
- Phases are ordered so that **safety fixes come first**, **documentation-only decisions come early**, and **fallback removals are ordered from lowest-risk/least-critical feature to highest-risk/most-critical feature** (Dashboard last, since it's the most complex and most user-facing).
- **File paths and line-number references (marked with ~) are approximate anchors** captured at plan-authoring time, not guaranteed-current locations. Each phase's own acceptance criteria require pasting the exact current code before any change — that is the actual source of truth, not the line numbers in this plan.

## 1. Phase Dependency Graph

```
P1 (RuntimeException gap) ─┐
P2 (rustCallSync fix)      ├─→ independent of each other, can be done in any order, but do sequentially for review bandwidth
P3 (overflow fix)          ┘

P4 (CI drift guard, optional) ─→ independent, can be inserted anywhere after P1-P3

P5 (ADR doc commit) ─→ no code dependency, but logically should land before P6 starts (records the decision)

P6 (remove Time-to-Goal fallback)        ─┐
P7 (remove DTI fallback)                  ├─→ each independent of the others; order among P6-P10 is
P8 (remove Financial Health Score fallback)│   flexible, but each depends on P1-P3 being merged first
P9 (remove Budget Advice fallback)         │   (so the error-handling path they route through is fixed)
P10 (remove Budget Forecast fallback)     ─┘

P11 (remove Analytics fallback)  ─→ depends on P1-P3 (same reason), independent of P6-P10
P12 (remove Dashboard fallback)  ─→ depends on P1-P3, do LAST among removals (highest risk)

P13 (test suite cleanup) ─→ depends on whichever of P6-P12 have landed; can be done incrementally
                             (i.e. clean up orphaned tests for each removed feature right after
                             that feature's phase, OR batch at the end — see phase notes)

P14 (CI/Release verification + docs finalization) ─→ last, after all desired phases are merged
```

## 2. Phase Checklist (fill in as you go)

| # | Phase | Risk | Status | PR | Merged |
|---|-------|------|--------|----|----|
| 1 | Fix `ensureRustInitialized` RuntimeException gap | Low | ☑ | (no PR found) | `0c473e0` → `9111e43` (commit `0c473e0` had regressed it; `9111e43` corrects) |
| 2 | Fix `rustCallSync` exception-handling consistency | Low | ☑ | #139 | `d4fab11` |
| 3 | Fix `sumOf` overflow in `localCalculateFinancialHealthScore` | Low | ☑ | #165 | `0419e81` |
| 4 | (Optional) CI guard for Rust↔Kotlin fallback drift | Low | ☐ | | |
| 5 | Commit ADR-001 decision doc | None | ☐ | | |
| 6 | Remove fallback: Time to Goal | Low | ☐ | | |
| 7 | Remove fallback: Debt-to-Income Ratio | Low | ☐ | | |
| 8 | Remove fallback: Financial Health Score | Medium | ☐ | | |
| 9 | Remove fallback: Offline Budget Advice | Medium | ☐ | | |
| 10 | Remove fallback: Offline Budget Forecast | Medium | ☐ | | |
| 11 | Remove fallback: Analytics | High | ☐ | | |
| 12 | Remove fallback: Dashboard Data | High | ☐ | | |
| 13 | Test suite cleanup (orphaned FallbackTest classes) | Low | ☐ | | |
| 14 | CI/Release verification + docs finalization | None | ☐ | | |

**Explicitly out of scope — keep these fallbacks permanently (Option C decision):**
Jalali Calendar, Currency Formatting, Offline Parser (NLP), Backup JSON Parse/Validate, AI Advice Validation. Do not create phases to remove these unless the ADR decision is revisited.

---

## Phase 1 — Fix `ensureRustInitialized` RuntimeException gap

**Risk:** Low. Adds a missing catch branch; does not change any existing successful path.

**Files:** `app/src/main/java/io/github/mojri/hesabyar/HesabyarApp.kt`

**Goal (completed by `9111e43`):** `ensureRustInitialized()` now catches `UnsatisfiedLinkError`, `InternalException`, `SecurityException`, and `RuntimeException`. A UniFFI contract/checksum mismatch thrown from `HesabyarCore.initialize()` is caught and returns `false` (Kotlin fallback) instead of propagating uncaught. Note: commit `0c473e0` had inadvertently replaced `catch (RuntimeException)` with `catch (SecurityException)`, re-introducing the gap; `9111e43` restored the branch and places `RuntimeException` LAST because `SecurityException` is a subclass of `RuntimeException` (a `catch (RuntimeException)` before `catch (SecurityException)` would be an unreachable catch and fail to compile).

**Non-goals:** Do not touch `rustCallSync` (that's Phase 2). Do not touch any routing/fallback-selection logic.

**Acceptance criteria:**
- Exact current code of the catch block (lines ~90-99), then exact new code.
- A new or existing unit test that simulates a `RuntimeException` during init and asserts `ensureRustInitialized()` returns `false` rather than throwing. Raw test output by test function name.
- Full existing `HesabyarAppTest`/init-related test suite still passes — raw output.

**Rollback:** `git revert` — restores previous (narrower) catch behavior.

---

## Phase 2 — Fix `rustCallSync` exception-handling consistency

**Risk:** Low-Medium. Changes which exceptions are swallowed vs rethrown in the fallback wrapper — must be scoped precisely.

**Files:** `app/src/main/java/io/github/mojri/hesabyar/rust/RustBridge.kt`

**Goal:** `rustCallSync` previously had a single `catch (e: Exception)` that swallowed ALL `Exception` subclasses — including `CancellationException`, `InterruptedException`, and `RuntimeException` — silently returning the fallback in every case. (`VirtualMachineError` was never in that catch: it extends `java.lang.Error`, not `Exception`, so the original `catch (e: Exception)` never caught it either — before or after this phase's changes. It has always propagated, and that is correct, intentional behavior; the explicit `catch (e: VirtualMachineError)` rethrow in the current code is defensive documentation of that fact, not a behavior change.) The fix (already merged in `d4fab11`, PR #139) adds explicit rethrow of `CancellationException`, `InterruptedException`, and `VirtualMachineError` before the generic `Exception` catch, and **keeps `RuntimeException` propagating** (throwing it) rather than swallowing it. This is correct because UniFFI-originated errors such as `HesabyarException` extend `kotlin.Exception` (NOT `kotlin.RuntimeException` — confirmed at `hesabyar_core.kt:3018`: `sealed class HesabyarException: kotlin.Exception()`), so they are still caught by the generic `Exception` branch and return the fallback (logged via `AppLogger.e`). Meanwhile, genuine Kotlin programmer-error `RuntimeException`s (e.g. `NullPointerException`, `IllegalStateException`, `IndexOutOfBoundsException` from bugs in calling code) propagate instead of being silently masked as Rust unavailability. Broadening the `RuntimeException` catch to swallow-and-fallback (as an earlier draft of this plan described) would have been a bug — it would silently mask real Kotlin code defects.

**Non-goals:** Do not change any call sites (`BudgetAdvisor`, `GetAnalyticsUseCase`, etc.) — this phase only changes the wrapper's internal exception handling.

**Acceptance criteria:**
- Exact current code of `rustCallSync` (lines 47-69) — confirmed identical to the `d4fab11` merge; no further code changes are needed.
- Test that simulates a `RuntimeException` from the wrapped `block()` and asserts it propagates (is NOT swallowed to fallback).
- Test confirming `CancellationException`/`InterruptedException` still propagate.
- Note on `VirtualMachineError`: it extends `java.lang.Error`, not `Exception`, so no `catch (e: Exception)` branch can intercept it — it propagated before Phase 2 and propagates after. This is intentional documented behavior; no further change to `rustCallSync` is required for it.
- Test confirming a UniFFI `HesabyarException` (which extends `kotlin.Exception`) IS caught by the generic `Exception` branch and returns the fallback (logged via `AppLogger.e`).
- Full `RustBridgeTest.kt` suite passes — raw output by test function name.

**Rollback:** `git revert`.

---

## Phase 3 — Fix `sumOf` overflow in `localCalculateFinancialHealthScore`

**Risk:** Low. Isolated numeric fix in an already-isolated fallback method.

**Files:** `app/src/main/java/io/github/mojri/hesabyar/api/BudgetAdvisor.kt` (~lines 491-492)

**Goal:** Replace `sumOf { it.amount }` for income/expense totals with the same `fold(BigInteger.ZERO)` + saturation pattern already used in `localMonthlyIncomeBaseline` (lines ~565-573), to prevent silent `Long` overflow/wrap on large transaction sums.

**Note:** This fallback path is scheduled for full removal in Phase 8. This fix is still worth doing now because Phase 8 may not land for a while, and until it does, this is a live financial-accuracy bug in the fallback path that's reachable in production on any device where Rust fails to load.

**Acceptance criteria:**
- Exact current code, exact new code.
- A test with income/expense sums that would overflow `Long` under the old `sumOf`, asserting correct (non-wrapped) output under the new code.
- Existing `BudgetAdvisorFallbackTest.kt` suite passes — raw output.

**Rollback:** `git revert`.

---

## Phase 4 — (Optional) CI guard for Rust↔Kotlin fallback drift

**Risk:** Low — tooling only, does not touch app code.

**Goal:** Add a CI check that warns (or fails, your choice) when a commit touches a Rust business-logic file without also touching the corresponding Kotlin counterpart. The guard is driven by an explicit **feature → Rust file → Kotlin file mapping** with three lifecycle states, so it never fires for Rust-only changes after a Kotlin fallback is removed:

- **PERMANENT** — features that keep Kotlin fallbacks forever (the 5 ADR-001 exceptions). The guard fires when a Rust file is touched without its paired Kotlin file:

| Feature | Rust file(s) | Kotlin counterpart |
|---|---|---|
| Jalali Calendar | `calendar.rs` | `JalaliCalendarHelper.kt` + `RustBridge.kt` calendar section |
| Currency Formatting | `currency.rs` | `CurrencyFormatter.kt` + `RustBridge.kt` formatCurrency section |
| Offline Parser (NLP) | `lib.rs` (`parse_sentence_offline` / `parse_sentence_offline_at`) + `parser/*.rs` — core implementation `parse_sentence_offline_full` (`nlp.rs`); helpers in `amount.rs`, `money_detector.rs`, `text_preprocessor.rs` | `api/GeminiParser.kt` — symbols `parseSentenceOffline` and `kotlinFallbackParse` |
| Backup JSON Parse/Validate | `lib.rs` (`parse_backup_json`) / `validation.rs` (`validate_backup_payload`; FFI wrapper in `ffi/mod.rs`) | `domain/usecase/BackupJsonParser.kt` + `domain/usecase/BackupJsonValidator.kt` (`data/BackupModels.kt` maps models only) |
| AI Advice Validation | `ai_validation.rs` (`validate_ai_advice`) | `RustBridge.kt` — the `validateAiAdvice` call site; discard policy in `api/AdviceValidationPolicy.kt` (a policy object, not a separate validator class) |

- **TEMPORARY** — features whose Kotlin fallbacks are scheduled for removal in Phases 6-12. The guard fires when a Rust file is touched without its paired Kotlin file, **but only until the feature's removal phase lands**. After removal, the Rust file is no longer mapped and the guard no longer fires:

| Feature | Rust file(s) | Kotlin counterpart | Removal phase |
|---|---|---|---|
| Time to Goal | `budget.rs` | `BudgetAdvisor.kt` (`predictTimeToGoal`) | P6 |
| Debt-to-Income Ratio | `budget.rs` | `BudgetAdvisor.kt` (`calculateDebtToIncomeRatio`) | P7 |
| Financial Health Score | `budget.rs` | `BudgetAdvisor.kt` (`calculateFinancialHealthScore`) | P8 |
| Offline Budget Advice | `budget.rs` | `BudgetAdvisor.kt` (`getOfflineBudgetAdvice`) | P9 |
| Offline Budget Forecast | `budget.rs` | `BudgetAdvisor.kt` (`getOfflineForecast`) | P10 |
| Analytics | `analytics.rs` | `GetAnalyticsUseCase.kt` (`computeFallbackAnalytics`) | P11 |
| Dashboard Data | `dashboard.rs` | `GetDashboardDataUseCase.kt` (`computeFallbackDashboardData`) | P12 |

- **REMOVED** — features whose Kotlin fallbacks have already been deleted by Phases 6-12. No guard enforcement; the Rust file is Rust-only and touching it alone is expected.

**Note:** This phase can be skipped or deferred — it's not required for the rest of the plan to proceed. Do it whenever convenient.

**Acceptance criteria:**
- CI workflow diff implementing the guard, driven by the mapping tables above (e.g. a config file or script mapping Rust paths to Kotlin paths and lifecycle states).
- A test PR (can be closed after) demonstrating the guard fires correctly on a mismatched change (Rust touched, Kotlin not) for a PERMANENT feature, stays silent on a matched one, and does NOT fire for a REMOVED feature whose Rust file was touched alone.

**Rollback:** Remove the CI step and its mapping config.

---

## Phase 5 — Commit ADR-001 decision doc

**Risk:** None — documentation only.

**Files:** `docs/architecture/ADR-001-rust-sole-implementation.md` (new)

**Goal:** Commit the architecture decision record (Option C, hybrid, with the explicit list of features that keep fallbacks and the list scheduled for removal) so the decision is durable across sessions/agents and doesn't need to be re-derived. Use the ADR draft from the audit report (§21) as the base, updated to reflect Phases 1-3 as already-applied fixes.

**Acceptance criteria:**
- File committed, linked from `AGENTS.md` or `docs/README.md` if such an index exists.

**Rollback:** `git revert` (doc-only, trivial).

---

## Phase 6 — Remove fallback: Time to Goal

**Risk:** Low. Smallest fallback (13 lines), advisory-only, not on a critical path.

**Depends on:** P1, P2, P3 merged.

**Files:**
- `app/src/main/java/io/github/mojri/hesabyar/api/BudgetAdvisor.kt` — remove `localPredictTimeToGoal`, remove the `isAvailable` branch around `predictTimeToGoal`, call Rust directly and let failure propagate as a controlled error.
- Caller(s) in the ViewModel/UI layer that display "time to goal" — add handling for the error case (simple: show "نامشخص" / unavailable state, no crash).

**Non-goals:** Do not touch DTI, Health Score, or any other feature in this phase.

**Acceptance criteria:**
- Exact current vs new code for `BudgetAdvisor.kt` changes.
- Exact current vs new code for the UI-layer error handling.
- `BudgetAdvisorTest.kt` (native path) passes.
- `BudgetAdvisorFallbackTest.kt` — the Time-to-Goal-specific test(s) removed or updated to assert the new error-propagation behavior (not deleted wholesale — that's Phase 13's job for whole files; individual test methods for this feature can be handled here).
- Manual verification note: what does the UI show when Rust is forced unavailable (`setRustInitializedForTesting(false)`) for this feature now?

**Rollback:** `git revert` — restores fallback and routing exactly.

---

## Phase 7 — Remove fallback: Debt-to-Income Ratio

**Risk:** Low. Same shape as Phase 6 (15-line fallback, advisory-only).

**Depends on:** P1, P2, P3 merged. Independent of P6.

**Files:** Same pattern as Phase 6, targeting `localCalculateDebtToIncomeRatio` and its call site.

**Acceptance criteria:**
- Exact current vs new code for `BudgetAdvisor.kt` `localCalculateDebtToIncomeRatio` changes.
- Exact current vs new code for the UI-layer error handling.
- `BudgetAdvisorTest.kt` (native path) passes.
- `BudgetAdvisorFallbackTest.kt` — the DTI-ratio-specific test(s) removed or updated to assert the new error-propagation behavior (not deleted wholesale — that's Phase 13's job for whole files; individual test methods for this feature can be handled here).
- Manual verification note: what does the UI show when Rust is forced unavailable (`setRustInitializedForTesting(false)`) for this feature now?

**Rollback:** `git revert`.

---

## Phase 8 — Remove fallback: Financial Health Score

**Risk:** Medium. Advisory number shown to the user; 57-line fallback; the Phase 3 overflow fix lives in code this phase deletes.

**Depends on:** P1, P2, P3 merged (P3's fix is superseded by this phase's deletion, that's fine).

**Files:** `BudgetAdvisor.kt` — remove `localCalculateFinancialHealthScore` and `localMonthlyIncomeBaseline` (verify `localMonthlyIncomeBaseline` isn't used elsewhere before deleting it — check for shared use with other fallback methods still in the codebase at this point).

**⚠ Before starting:** Confirm `localMonthlyIncomeBaseline` isn't still referenced by Time-to-Goal/DTI fallbacks — if Phases 6-7 already removed those, this should be safe, but verify via `grep`.

**Acceptance criteria:** Same structure as Phase 6, plus explicit confirmation (grep output) that `localMonthlyIncomeBaseline` has no remaining callers before deletion.

**Rollback:** `git revert`.

---

## Phase 9 — Remove fallback: Offline Budget Advice

**Risk:** Medium. Text-output feature — Rust and Kotlin versions produce *different Persian text*, so this isn't just a numeric fallback; removing it changes what error state looks like, not just triggers it less often.

**Depends on:** P1, P2, P3 merged.

**⚠ Needs a UX decision before starting:** what should the user see when Rust is unavailable and offline advice can't be generated? ("توصیه در دسترس نیست" placeholder vs retry button vs silent hide of the section). Decide this before writing code, not during review.

**Files:** `BudgetAdvisor.kt` (remove `buildLocalOfflineAdvice`), `BudgetAdviceGenerator.kt` (error propagation), relevant UI layer.

**Acceptance criteria:** Same structure as Phase 6, plus a note on the UX decision made and where it's implemented.

**Rollback:** `git revert`.

---

## Phase 10 — Remove fallback: Offline Budget Forecast

**Risk:** Medium. Same shape as Phase 9 (66-line fallback, different text output).

**Depends on:** P1, P2, P3 merged. Independent of P9, but same UX-decision note applies — reuse the decision from Phase 9 for consistency if applicable.

**Files:** `BudgetAdvisor.kt` (remove `buildLocalOfflineForecast`), relevant UI layer.

**Acceptance criteria:** Same structure as Phase 9.

**Rollback:** `git revert`.

---

## Phase 11 — Remove fallback: Analytics

**Risk:** High. 76-line fallback, feeds the Analytics screen directly.

**Depends on:** P1, P2, P3 merged. Independent of P6-P10.

**⚠ Needs a UX decision:** what does the Analytics screen show on Rust failure — full-screen error state, cached last-known data, or partial data? Decide before coding.

**Files:** `GetAnalyticsUseCase.kt` (remove `computeFallbackAnalytics`), Analytics ViewModel/screen for error state.

**Acceptance criteria:** Same structure as Phase 6, plus the UX decision documented. Given this is High risk, also require: a manual test pass on a real/emulated device with Rust forcibly disabled, confirming the app doesn't crash and shows the intended error state.

**Rollback:** `git revert`.

---

## Phase 12 — Remove fallback: Dashboard Data

**Risk:** Highest in this plan. 282-line fallback — the largest and most complex, feeds the main Dashboard screen (the app's home screen).

**Depends on:** P1, P2, P3 merged. Do this **last** among all fallback-removal phases — by this point the error-propagation pattern will have been proven three or four times over on smaller features.

**⚠ Needs a UX decision, likely the most important one in this plan:** the Dashboard is the app's home screen. What does a user see if Rust fails to load and there's no more Kotlin fallback? A blank home screen is not acceptable. Consider: cached last-successful dashboard state + a banner, vs a dedicated error screen with retry.

**Files:** `GetDashboardDataUseCase.kt` (remove `computeFallbackDashboardData`), Dashboard ViewModel/screen for error/cached state.

**Acceptance criteria:** Same structure as Phase 11, with mandatory manual device testing (Rust force-disabled) before merge, and a specific written description of the fallback UX behavior in the PR description.

**Rollback:** `git revert`.

---

## Phase 13 — Test suite cleanup

**Risk:** Low, but do this carefully to avoid silently dropping coverage.

**Approach:** Can be done incrementally (recommended) — right after each of Phases 6-12, delete/update only the test file(s) tied to that specific feature's removed fallback, as part of that phase's own PR (folded into the "Acceptance criteria" test-file updates already required above). OR, if preferred, batch all test cleanup into one final pass here after all removal phases are merged.

**If batching:** delete `BudgetAdvisorFallbackTest.kt`, `GetAnalyticsUseCaseFallbackTest.kt`, `GetAnalyticsUseCaseFallbackDenominatorTest.kt`, and remove parity test methods from `GetDashboardDataUseCaseTest.kt` — but only for features actually removed by that point. Do not delete tests for Calendar/Currency/Parser/Backup fallbacks — those are permanent per the ADR.

**Acceptance criteria:**
- List of deleted/modified test files with justification per file (which phase made it orphaned).
- Full test suite run, raw output, confirming no unintended coverage loss (spot-check: coverage % for modified production files stays ≥ pre-removal baseline for the *remaining* code paths).

**Rollback:** `git revert` restores deleted test files.

---

## Phase 14 — CI/Release verification + docs finalization

**Risk:** None — verification and documentation only.

**Goal:**
- Confirm CI workflow still runs correctly end-to-end after all changes.
- Run the clean-checkout build verification (per audit §17) to confirm the project still requires and correctly uses the Rust toolchain.
- Verify final release AAB contains all 4 `.so` files and no orphaned references to deleted Kotlin fallback code.
- Update `AGENTS.md` / `docs/CODE_REVIEW.md` to reflect the final state: which features have Kotlin fallbacks (permanent, per ADR) and which now fail with controlled errors.

**Acceptance criteria:**
- Clean checkout build log.
- `unzip -l` output confirming the four expected native libraries are present in a release AAB, scoped to exact ABI paths:
  - `lib/arm64-v8a/libhesabyar_core.so`
  - `lib/armeabi-v7a/libhesabyar_core.so`
  - `lib/x86/libhesabyar_core.so`
  - `lib/x86_64/libhesabyar_core.so`
- Scoped `grep` over Kotlin source only — `app/src/main` and `app/src/test` — confirming no remaining Kotlin fallback symbols removed in Phases 6–12. Do NOT grep the whole repository: these symbol names also appear in this plan file's own prose (Phases 6–12), `docs/architecture/ADR-001-rust-sole-implementation.md`, and other documentation, so an unscoped search produces false positives forever. Restrict the search paths to `app/src/main app/src/test`; this already excludes `docs/`, `plans/`, this plan file, and all `app/build/` generated output:
  - `localPredictTimeToGoal`, `localCalculateDebtToIncomeRatio`, `localCalculateFinancialHealthScore`, `buildLocalOfflineAdvice`, `buildLocalOfflineForecast`, `computeFallbackAnalytics`, `computeFallbackDashboardData`
  - Example: `rg -n --glob '*.kt' --glob '!**/build/**' "localPredictTimeToGoal|localCalculateDebtToIncomeRatio|localCalculateFinancialHealthScore|buildLocalOfflineAdvice|buildLocalOfflineForecast|computeFallbackAnalytics|computeFallbackDashboardData" app/src/main app/src/test`
  - Interpret the exit code explicitly: `1` = pass (no matches — all symbols removed); `0` = fail (leftover symbols found); `2` or higher = fail as a verification error (the command itself errored — investigate before treating absence of matches as success).
- Updated docs committed.

**Rollback:** N/A (verification/docs only).

---

## 3. Quick-reference: what to tell the agent at the start of each phase

Regardless of session/context resets, paste this file's relevant phase section into the agent's task prompt verbatim, plus:

> "This is Phase N of a pre-approved multi-phase plan (see `plans/2026-08-19-rust-fallback-consolidation-plan.md`). Do not do work belonging to any other phase. Follow the evidence-first rule: exact current code, raw test output by test function name, exact file/line references for every claim of 'done'."
