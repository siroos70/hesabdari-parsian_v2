# Plan 007: Move ManualTransactionSubmitter behind a use case

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 44dd519..HEAD -- app/src/main/java/io/github/mojri/hesabyar/ui/ManualTransactionSubmitter.kt app/src/main/java/io/github/mojri/hesabyar/ui/ManualTransactionDialog.kt app/src/main/java/io/github/mojri/hesabyar/ui/screens/DashboardScreen.kt app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageTransactionUseCase.kt app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageLoanUseCase.kt app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageInstallmentUseCase.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- Priority: P1
- Effort: M
- Risk: MED — changes the contract between dialog and ViewModel layer; composable call sites must be updated atomically.
- Depends on: none
- Category: testability / architecture
- Planned at: commit `44dd519`, 2026-07-23

## Why this matters

`ManualTransactionSubmitter.submit()` takes three ViewModels as parameters, coupling form business logic to Android UI components. That makes it impossible to unit test without a full Compose/Android runtime. Moving the orchestration behind a `SubmitManualTransactionUseCase` lets the object stay testable and keeps the dialog dumb.

## Current state

`app/src/main/java/io/github/mojri/hesabyar/ui/ManualTransactionSubmitter.kt` — plain Kotlin object with validation + submit orchestration.

Lines 46-63:
```kotlin
  fun submit(
    selectedType: String,
    amountDisplay: Long,
    isEditMode: Boolean,
    originalAmountRial: Long,
    amountModified: Boolean,
    selectedCategoryId: Long,
    descriptionText: String,
    personName: String,
    title: String,
    daysFromNowText: String,
    customDate: Long,
    categories: List<Category>,
    transactionViewModel: TransactionViewModel,
    loanViewModel: LoanViewModel,
    installmentViewModel: InstallmentViewModel,
    transactionToEdit: Transaction?
  ): SubmitResult {
```

Lines 118-151, 153-171, 173-197 — `submitTransaction`, `submitLoan`, `submitInstallment` all call ViewModel methods directly (`transactionViewModel.addTransaction`, `loanViewModel.addLoan`, `installmentViewModel.addInstallment`, `transactionViewModel.updateTransaction`).

The object is invoked from:
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/dashboard/dialogs/ManualTransactionDialog.kt` — passes `transactionViewModel`, `loanViewModel`, `installmentViewModel`

Existing use-case pattern (`ManageTransactionUseCase.kt`):
```kotlin
class ManageTransactionUseCase(
  private val repository: HesabyarRepositoryInterface
) {
  suspend fun addTransaction(...) = repository.insertTransaction(Transaction(...))
  suspend fun updateTransaction(transaction: Transaction) = repository.updateTransaction(transaction)
}
```

Test helper reference (`FakeRepository.kt`):
```kotlin
internal class FakeRepository : HesabyarRepositoryInterface {
  override suspend fun insertTransaction(transaction: Transaction): Long = 0L
  override suspend fun updateTransaction(transaction: Transaction) {}
  ...
}
```

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Kotlin tests | `./gradlew test --no-daemon` | all pass |
| Lint | `./gradlew ktlintCheck detekt --no-daemon` | exit 0 |

## Scope

In scope:
- `app/src/main/java/io/github/mojri/hesabyar/ui/ManualTransactionSubmitter.kt`
- `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageTransactionUseCase.kt` — extend contract
- `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageLoanUseCase.kt` — extend if needed
- `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageInstallmentUseCase.kt` — extend if needed
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/dashboard/dialogs/ManualTransactionDialog.kt`
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/DashboardScreen.kt`
- `app/src/test/...` — new unit test for `SubmitManualTransactionUseCase` without Android runtime

Out of scope:
- Any balance-screening, overpayment, or loan business logic (see Plan 001). Keep this plan one refactor only.
- ViewModel internals beyond parameter lists / call site wiring.

## Steps

### Step 1: Add use-case methods for the three submit paths

In `ManageTransactionUseCase`, add:

```kotlin
  suspend fun addTransactionDirect(type: TransactionType, categoryId: Long, amount: Long, description: String, customDate: Long?, personName: String?): Long
```

Already exists as `addTransaction`. Just ensure the parameter set matches what `ManualTransactionSubmitter` needs. If it does, no change required — move to the next use case.

In `ManageLoanUseCase`, add:

```kotlin
  suspend fun addLoanDirect(type: LoanType, personName: String, amount: Long, description: String, customDate: Long?)
```

Thin wrapper around `addLoan`, but exposes the domain-specific type and parameters without any ViewModel.

In `ManageInstallmentUseCase`, add:

```kotlin
  suspend fun addInstallmentDirect(title: String, amount: Long, dueDate: Long, reminderEnabled: Boolean, notes: String)
```

Thin wrapper. Only add this if `addInstallment` does not already expose the same signature.

**STOP**: If `ManageLoanUseCase`/`ManageInstallmentUseCase` already exposes these exact signatures, skip this step and move to Step 2.

**Verify**: `./gradlew ktlintCheck detekt --no-daemon` → exit 0.

### Step 2: Create `SubmitManualTransactionUseCase` 

New file `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/SubmitManualTransactionUseCase.kt`:

```kotlin
package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.utils.TransactionAmountResolver

class SubmitManualTransactionUseCase(
  private val manageTransaction: ManageTransactionUseCase,
  private val manageLoan: ManageLoanUseCase,
  private val manageInstallment: ManageInstallmentUseCase
) {
  sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Error(val message: String) : ValidationResult()
  }

  data class SubmitResult(val success: Boolean, val errorMessage: String? = null)

  fun validate(...): ValidationResult { ... }

  suspend fun submit(...): SubmitResult { ... }
}
```

Body of `submit` and `validate` must be verbatim copies of `ManualTransactionSubmitter`, except ViewModel calls must be replaced with `useCase` calls. Keep `TransactionAmountResolver` in `domain.utils` as a pure helper.

**Verify**: `./gradlew ktlintCheck detekt --no-daemon` → exit 0.

### Step 3: Rewire ManualTransactionSubmitter to be a thin adapter

Replace `ManualTransactionSubmitter.submit()` signature and body so it only delegates to the new use case. Two possible shapes:

**Option A (recommended):** Make the object a deprecated adapter that constructs the use case internally and delegates. This lets call sites migrate lazily.

**Option B:** Remove the object and inline the delegation at the call site. Higher churn, but zero dead code.

Executor: choose Option A unless the call-site review shows only one caller.

**Verify**: `./gradlew test --no-daemon --tests "io.github.mojri.hesabyar.ui.ManualTransactionSubmitter"` (if any), or full test suite if none.

### Step 4: Rewire ManualTransactionDialog and DashboardScreen

In `ManualTransactionDialog.kt` and `DashboardScreen.kt`, replace:

```kotlin
ManualTransactionSubmitter.submit(
  ...
  transactionViewModel = transactionViewModel,
  loanViewModel = loanViewModel,
  installmentViewModel = installmentViewModel,
  ...
)
```

with:

```kotlin
val submitter = SubmitManualTransactionUseCase(
  manageTransaction = viewModel<TransactionViewModel>().let { ... },
  ...
)
```

OR, if the dialog already receives the ViewModels, inject the use cases from the activity/fragment and pass them into the dialog alongside the ViewModels until the dialog itself can consume only the use case.

**STOP**: if `DashboardScreen` also constructs/passes `ManualTransactionSubmitter`, report the exact call site and whether it needs the same lazy migration.

**Verify**: `./gradlew test --no-daemon` → all pass.

### Step 5: Add unit tests for the new use case

In `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/SubmitManualTransactionUseCaseTest.kt`, add tests that:
- Call `validate()` with invalid input and assert `ValidationResult.Error` for each rule.
- Call `submit()` with the `FakeRepository`-backed use cases and assert `SubmitResult(success = true)` and side effects on the fake store.
- Use `runTest` + `StandardTestDispatcher`; no Compose runtime, no Robolectric.

**Verify**: `./gradlew test --no-daemon --tests "io.github.mojri.hesabyar.domain.usecase.SubmitManualTransactionUseCaseTest"` → all pass.

### Step 6: Lint

**Verify**: `./gradlew ktlintCheck detekt --no-daemon` → exit 0.

## Test plan

- New test file: `SubmitManualTransactionUseCaseTest`
- Existing tests to re-run: full `./gradlew test --no-daemon`

## Done criteria

- [ ] `ManualTransactionSubmitter.submit()` no longer takes ViewModel parameters
- [ ] `SubmitManualTransactionUseCase` owns the submit logic
- [ ] `ManualTransactionDialog` and `DashboardScreen` call through the use case
- [ ] New unit test passes without Android runtime
- [ ] `./gradlew test --no-daemon` exits 0
- [ ] `./gradlew ktlintCheck detekt --no-daemon` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

- `ManualTransactionSubmitter.kt` or the call sites don't match the excerpts.
- `ManageLoanUseCase`/`ManageInstallmentUseCase` are `@Singleton`-scoped and require constructor injection changes unavailable to unit tests.
- The new use case test fails twice after a reasonable fix attempt.
- This dirties the dialog contract beyond parameter rename (e.g., state hoisting).

## Maintenance notes

- Future new submit paths (e.g., transfer transaction) go in `SubmitManualTransactionUseCase`, not in `ManualTransactionSubmitter`.
- The adapter can be removed after `ManualTransactionDialog` stops referencing `ManualTransactionSubmitter`.
- If a `SubmitTransactionResult` needs richer diagnostics later, extend `SubmitResult` in the use case; the ViewModel can keep translating it to UI state.
