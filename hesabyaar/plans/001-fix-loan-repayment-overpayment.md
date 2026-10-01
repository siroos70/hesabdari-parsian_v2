# Plan 001: Fix loan repayment overpayment recording

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 44dd519..HEAD -- app/src/main/java/io/github/mojri/hesabyar/data/HesabyarRepository.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- Priority: P1
- Effort: S
- Risk: MED — changes financial-adjacent logic; must preserve transaction accuracy and SQLite atomicity.
- Depends on: none
- Category: bug
- Planned at: commit `44dd519`, 2026-07-23

## Why this matters

`HesabyarRepository.addPaymentToLoan` caps the loan balance at zero via `coerceAtLeast(0L)`, but the generated `Transaction` and `PaymentHistory` still carry the user's original uncapped `amount`. If a user overpays, financial reports show more money flowing than actually adjusted the balance, creating an audit-trail inconsistency.

## Current state

`app/src/main/java/io/github/mojri/hesabyar/data/HesabyarRepository.kt` — Room repository, owns loan CRUD plus repayment transaction generation.

Lines 67-103:
```kotlin
  override suspend fun addPaymentToLoan(
    loanId: Long,
    amount: Long,
    notes: String,
    customDate: Long?
  ): Boolean {
    val loan = loanDao.getLoanById(loanId) ?: return false
    val loansCategory = getCategoryByKey("Loans") ?: return false
    val newRemaining = (loan.remainingAmount - amount).coerceAtLeast(0L)
    val isSettled = newRemaining <= 0L
    val date = customDate ?: System.currentTimeMillis()

    val updatedLoan = loan.copy(remainingAmount = newRemaining, isSettled = isSettled)
    val desc =
      if (loan.type == LoanType.CREDITOR) {
        "بازپرداخت بدهی به ${loan.personName} - $notes"
      } else {
        "دریافت بازپرداخت از ${loan.personName} - $notes"
      }
    val tx =
      Transaction(
        type = if (loan.type == LoanType.CREDITOR) TransactionType.EXPENSE else TransactionType.INCOME,
        categoryId = loansCategory.id,
        amount = amount,
        description = desc,
        personName = loan.personName,
        date = date
      )
    val payment = PaymentHistory(loanId = loanId, amount = amount, notes = notes, date = date)

    database.withTransaction {
      loanDao.updateLoan(updatedLoan)
      paymentHistoryDao.insertPayment(payment)
      transactionDao.insertTransaction(tx)
    }
    return true
  }
```

Repo conventions:
- All monetary amounts in this file are `Long` Rial. No `Double`/`Float` for money.
- Multi-DAO writes use `database.withTransaction` to keep SQLite atomic.
- `PaymentHistory` and `Transaction` both store the payment amount in Rial.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Tests | `./gradlew test --no-daemon` | `BUILD SUCCESSFUL`, all tests pass |
| Lint | `./gradlew ktlintCheck detekt --no-daemon` | exit 0 |

## Scope

In scope:
- `app/src/main/java/io/github/mojri/hesabyar/data/HesabyarRepository.kt`

Out of scope:
- `LoanViewModel`, UI screens, or DAO/entity changes. Tests must stay within existing test files: `LoanInstallmentTest.kt` and `RepositoryLogicTest.kt`.

## Steps

### Step 1: Record the effective repaid amount

Replace the two `amount = amount` usages inside `addPaymentToLoan` with the capped effective amount so the transaction and payment history match what actually changed the balance.

Target shape:
```kotlin
val effectiveAmount = amount.coerceAtMost(loan.remainingAmount)
```
Then use `effectiveAmount` for:
- `Transaction(... amount = effectiveAmount, ...)`
- `PaymentHistory(... amount = effectiveAmount, ...)`
- Keep `newRemaining` logic unchanged.

**Verify**: `./gradlew test --no-daemon` → all pass, including `LoanInstallmentTest` and `RepositoryLogicTest`.

### Step 2: Add an overpayment regression test

In `app/src/test/java/io/github/mojri/hesabyar/LoanInstallmentTest.kt` or `RepositoryLogicTest.kt`, add a test that:
1. Creates a loan with `remainingAmount = 5000`.
2. Calls `addPaymentToLoan(loanId, 10000, ...)`.
3. Asserts the inserted transaction amount is `5000`, not `10000`.
4. Asserts the loan `remainingAmount` is `0` and `isSettled` is `true`.

Follow the existing `RepositoryLogicTest.kt` pattern: use `runTest`, `StandardTestDispatcher`, and an in-memory `FakeRepository` or the existing `FakeRepository` helper at `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/FakeRepository.kt`.

**Verify**: `./gradlew test --no-daemon --tests "io.github.mojri.hesabyar.LoanInstallmentTest"` → new test passes.

### Step 3: Lint

**Verify**: `./gradlew ktlintCheck detekt --no-daemon` → exit 0.

## Test plan

- New test: overpayment does not inflate transaction amount.
- Existing tests to re-run: `LoanInstallmentTest`, `RepositoryLogicTest`.

## Done criteria

- [ ] `git diff --stat 44dd519..HEAD -- app/src/main/java/io/github/mojri/hesabyar/data/HesabyarRepository.kt` shows only the `effectiveAmount` change
- [ ] `./gradlew test --no-daemon` exits 0
- [ ] `./gradlew ktlintCheck detekt --no-daemon` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

- The code at `HesabyarRepository.kt:75-100` doesn't match the excerpts.
- The new test fails twice after a reasonable fix attempt.
- The fix appears to require touching an out-of-scope file (DAO/entity).
- You discover the assumption "remainingAmount is the field that tracks balance" is false.

## Maintenance notes

- Future UI that shows payment history must rely on `PaymentHistory.amount`, which now matches actual balance reduction.
- If an explicit overpayment warning UX is desired later, it should be a separate feature that still records `effectiveAmount` for financial accuracy.
