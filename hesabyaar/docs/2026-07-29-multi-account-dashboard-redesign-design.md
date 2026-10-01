# Multi-Account Dashboard: Bug Fixes + Visual Redesign

## Overview

Fix data integrity issues with archived accounts leaking into dashboard totals, wire card selection end-to-end, improve scalability with LazyRow, and redesign account cards with icons, selection state, and trend indicators.

## Phase 0 — Data Integrity Fix

> **Note:** This fallback parity fix is a **temporary patch** to Kotlin-side fallback code that is scheduled for removal in the Dashboard fallback-removal phase (Phase 12 of `plans/2026-08-19-rust-fallback-consolidation-plan.md`). It is NOT a permanent implementation strategy — new dashboard logic must go in Rust.

### Problem

`GetDashboardDataUseCase` uses `repository.allAccounts` (calls `getAllAccounts()`, includes archived). When Rust FFI returns null, the Kotlin fallback's `computeAccountSummaries` doesn't filter archived accounts — archived accounts' transactions leak into dashboard totals.

### Fix

1. **Rust path:** Already filters archived accounts (`!a.is_archived` in `compute_account_summaries`). No change needed.
2. **Kotlin fallback (temporary):** Add `account.isArchived` check in `computeAccountSummaries` to skip archived accounts. This fix will be deleted when the Dashboard fallback is removed.
3. **Data source:** Keep `allAccounts` in `GetDashboardDataUseCase` (Account Management screen needs it), but add explicit filtering at the dashboard computation call site.

### Files to modify

- `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/GetDashboardDataUseCase.kt` — filter archived in `computeAccountSummaries`
- `app/src/test/java/io/github/mojri/hesabyar/GetDashboardDataUseCaseTest.kt` — add archived exclusion test

## Phase 1 — Selection Wiring

### Problem

`AccountBalanceCard` has no selection awareness — it's purely presentational. Card `onClick` exists but isn't wired in `DashboardScreen`. Chips and cards use independent state.

### Fix

1. **Add `selectedAccountId: Long?` parameter** to `AccountBalanceCard` composable.
2. **Wire `onClick` in DashboardScreen's LazyRow:** `onClick = { dashboardViewModel.selectAccount(summary.accountId) }`
3. **Single source of truth:** Both `AccountSelector` and `AccountBalanceCard` read `selectedAccountId` from the same `StateFlow`.
4. **Selection visual treatment:**
   - 2px border in account's color (with `animateColorAsState` transition)
   - Small check badge (✅ or `Icons.Filled.Check`) top-right corner
   - Subtle background tint (account color at 8% alpha)
5. **Test:** Compose UI test — tap card → verify `selectedAccountId` updates → verify chip and card show consistent state.

### Files to modify

- `app/src/main/java/io/github/mojri/hesabyar/ui/components/AccountBalanceCard.kt` — add selection params + visual treatment
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/DashboardScreen.kt` — wire onClick, pass selectedAccountId
- New test file for Compose UI selection consistency

## Phase 2 — Scalability (LazyRow)

### Problem

`AccountSelector` uses plain `Row` with `horizontalScroll` — renders all chips eagerly.

### Fix

1. **Convert to `LazyRow`** — only renders visible chips.
2. **First item:** "همه حساب‌ها" (selectedAccountId == null), followed by active accounts.
3. **No threshold** — LazyRow recycling handles 20+ accounts. Collapse affordance is a future enhancement.
4. **Stable key:** `key = { it.id }` on `items()` to prevent unnecessary recompositions.

### Files to modify

- `app/src/main/java/io/github/mojri/hesabyar/ui/components/AccountSelector.kt` — convert Row to LazyRow, add `testTag("accountSelectorLazyRow")` for Compose UI tests
- `app/src/test/java/io/github/mojri/hesabyar/AccountSelectionTest.kt` — add `lazyRowLargeDatasetScrollAndSelect` test (30 accounts, `performScrollToNode` + click)

### Test

- `lazyRowLargeDatasetScrollAndSelect`: creates 30 mock accounts, asserts "همه حساب‌ها" clears selection, scrolls to "Account 30" via `performScrollToNode(hasText(...))`, clicks it, asserts `selectedAccountId == 30L`.

## Phase 3 — Visual Redesign

### New Card Structure

```
┌─────────────────────────────────────────────┐
│  ┌──────┐  Account Name          1,234,567 │
│  │ Icon │  Account Type        +4%  ▲      │
│  └──────┘                                  │
└─────────────────────────────────────────────┘
```

- **Left:** Account type icon (Material Icons) in tinted circle (account color at 15% alpha bg via `IconCircle`, icon in account color)
- **Center:** Name (`titleSmall`), type displayName (`labelMedium`, `onSurfaceVariant`)
- **Right:** Formatted balance (`bodyLarge`, `onSurface`) + trend indicator below (`labelMedium`, green/red)
- **Selected state:** 2px accent border, check badge top-right, background tint (8% alpha)
- **Dimensions:** 88dp height (down from 160dp), min 160dp width

### Account Type → Icon Mapping

Defined in `AccountTypeIcon.kt` via `AccountType.icon()` extension function:

| AccountType | Material Icon |
|---|---|
| BANK | `AccountBalance` |
| CASH_WALLET | `Wallet` |
| SAVINGS_INVESTMENT | `Savings` |
| OTHER | `MoreHoriz` |

### Trend Computation

**Data model change:** Add `monthlyDelta: Double` to `AccountDashboardSummary` (both Rust struct and Kotlin data class).

**Formula:**
```
currentNet = currentMonthIncome - currentMonthExpenses
previousNet = previousMonthIncome - previousMonthExpenses
monthlyDelta = (currentNet - previousNet) / max(abs(previousNet), 1)
```

- If `abs(previousNet) < 1_000` Rial (noise threshold): show `0.0` (no trend) to avoid misleading percentages
- If previous month had zero activity: show `0.0` (no trend)
- Display: `+4% ▲` in `FinancialColors.IncomeGreen`, `-12% ▼` in `FinancialColors.ExpenseRed`
- Zero delta: no trend indicator rendered

**Rust implementation:**
- `compute_account_summaries` now accepts `prev_month_start_ms` and `prev_month_end_ms` parameters
- Previous month boundaries computed in `compute_dashboard_data` via Jalali month arithmetic
- `monthly_delta: f64` added to `AccountDashboardSummary` struct with `#[serde(default)]`
- Noise threshold: `DELTA_PREV_NET_THRESHOLD = 1_000` Rial

**Kotlin fallback:**
- `JalaliCalendarHelper.getUtcJalaliPreviousMonthBoundaries(currentMonthStart)` added
- Mirror same computation in `GetDashboardDataUseCase.computeAccountSummaries`
- Same noise threshold: `DELTA_PREV_NET_THRESHOLD = 1_000L`

### Files modified

- `rust/hesabyar-core/src/models/mod.rs` — added `monthly_delta: f64` to `AccountDashboardSummary` (with `#[serde(default)]`)
- `rust/hesabyar-core/src/dashboard.rs` — compute previous month boundaries + delta in `compute_account_summaries`; 5 new unit tests
- `app/src/main/java/io/github/mojri/hesabyar/ui/UiState.kt` — added `monthlyDelta: Double = 0.0` to Kotlin `AccountDashboardSummary`
- `app/src/main/java/io/github/mojri/hesabyar/rust/RustMappers.kt` — map `monthlyDelta` in `mapAccountDashboardSummary`
- `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/GetDashboardDataUseCase.kt` — compute delta in fallback path
- `app/src/main/java/io/github/mojri/hesabyar/ui/JalaliCalendarHelper.kt` — added `getUtcJalaliPreviousMonthBoundaries`
- `app/src/main/java/io/github/mojri/hesabyar/ui/components/AccountBalanceCard.kt` — redesigned card layout (icon, trend, 88dp height)
- New: `app/src/main/java/io/github/mojri/hesabyar/ui/components/AccountTypeIcon.kt` — icon mapping composable + `AccountType.icon()` extension
- `app/build.gradle.kts` — added `maxParallelForks = 1` + `forkEvery = 1` to isolate Rust JNI state per test class

### Tests

**Rust (5 new tests):**
- `test_monthly_delta_basic_computation` — currentNet=300k, prevNet=200k → delta=0.5
- `test_monthly_delta_zero_previous` — no previous transactions → delta=0.0
- `test_monthly_delta_small_previous_net` — prevNet=500 (< 1000 threshold) → delta=0.0
- `test_monthly_delta_negative` — currentNet < prevNet → negative delta
- `test_monthly_delta_with_transfers` — transfer accounting in delta

**Kotlin (2 new tests):**
- `rustAndKotlinFallbackProduceSameMonthlyDelta` — cross-path consistency with explicit fixedNowMs
- `monthlyDeltaZeroWhenPreviousNetBelowThreshold` — both paths return 0.0 when prevNet is negligible

## Phase 4 — Color Propagation

### Current Usage

- `AccountBalanceCard` (accent bar)
- `AccountSelector` (chip tint)
- `AccountManagementScreen` (color picker)
- `ManualTransactionFormSections` (account indicator)

### Propagation — Implemented

1. **Transaction list rows:** `TransactionMiniItem` — 8dp colored dot on the left side from source account color. For transfers, two 7dp half-dots stacked vertically (source + destination). Account name appears in subtitle as secondary accessibility signal.
2. **Transaction detail:** `TransactionDetailDialog` — account row with 10dp color dot + account name. For transfers, shows "source → dest" format.
3. **Analytics charts:** New `AccountBreakdownCard` with donut chart using account colors for per-account expense breakdown. Legend shows account name alongside color dot.

### Accessibility (second signal)

- **TransactionMiniItem:** Account name appears in subtitle text (`"date | category · accountName"`) alongside the color dot. For transfers, both source and destination names appear (`"accountA → accountB"`).
- **TransactionDetailDialog:** Account row shows both color dot and account name text.
- **Analytics chart:** Legend pairs color dot with account name text — never color alone.

### Files modified

- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/dashboard/components/TransactionMiniItem.kt` — added `accounts` parameter, `AccountColorIndicator` composable, `buildSubtitle` helper
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/dashboard/dialogs/TransactionDetailDialog.kt` — added `accounts` parameter, account row with color dot + name
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/DashboardScreen.kt` — pass `accounts` to `TransactionMiniItem` and `TransactionDetailDialog`
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/ReportsScreen.kt` — pass `accounts` to `TransactionDetailDialog`, added account color dot to transaction rows
- `app/src/main/java/io/github/mojri/hesabyar/ui/UiState.kt` — added `accountBreakdown: List<CategoryBreakdown>` to `AnalyticsData`
- `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/GetAnalyticsUseCase.kt` — added `computeAccountBreakdown` for per-account expense breakdown
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/AnalyticsScreen.kt` — added `AccountBreakdownCard` with donut chart + legend

### Tests

- `TransactionMiniItemColorTest` — 3 tests: non-transfer shows account name, transfer shows both names, no-accounts fallback works

## Phase 5 — Regression Pass

### Manual QA

1. Archive/unarchive account → verify dashboard updates
2. Switch accounts via chip AND card → verify totals update
3. Select "همه حساب‌ها" → verify totals include all active accounts
4. Verify balance computation matches manual calculation
5. Force Rust fallback → verify Kotlin produces same results
6. Verify trend percentages (no NaN, no Infinity)
7. RTL layout verification
8. Dark mode verification

### Automated Tests

**Full suite:** 618 tests, 0 failures, 3m 37s (with `forkEvery=1`, `--no-daemon`)

**New Phase 5 tests (4 tests in `GetDashboardDataUseCaseTest`):**
- `archiveThenUnarchiveRestoresDashboardBalance` — round-trip: both accounts active → archive one → balance drops by exactly that account's transactions → unarchive → balance restored
- `allAccountsSelectionSumsActiveNonArchivedAccounts` — `selectedAccountId=null` mode: sum of active non-archived account balances equals `currentBalance`
- `rustFallbackFullDashboardDataMatchesRustPath` — Rust vs Kotlin fallback: `currentBalance`, `monthlyIncome`, `monthlyExpenses`, per-account `balance`, `monthlyIncome`, `monthlyExpenses`, `monthlyDelta` all match; `sum(accountBalances) == currentBalance` verified for both paths
- `monthlyDeltaNeverNanOrInfinity` — three cases: prevNet=0 → delta=0.0, prevNet below noise threshold (400 < 1000) → delta=0.0, normal case → delta in [-10.0, 10.0]; all cases assert `!isNaN()` and `!isInfinite()`

## Implementation Order

1. Phase 0 (data integrity) — no UI changes, safe to merge first
2. Phase 1 + 2 (selection + LazyRow) — can be combined as they're closely related
3. Phase 3 (visual redesign + trend) — depends on Phase 1 for selection state
4. Phase 4 (color propagation) — depends on Phase 3 for card design
5. Phase 5 (regression) — after all phases complete
