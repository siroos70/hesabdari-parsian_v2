package io.github.mojri.hesabyar.data

/**
 * Shared helper for Phase 2 tracked ledger writes (Loan, BankLoan, Installment).
 *
 * Choice rationale (documented per plan 011): the three entities' repayment shapes
 * are meaningfully different (Loan posts to payment_history + Loans category,
 * Installment toggles isPaid + Installments category with reversal, BankLoan
 * creates N installments + optional disbursement INCOME). A single generic
 * repayment abstraction would force false uniformity.
 *
 * Account validation rule (DECISION 2):
 * - If tracked = true: accountId MUST NOT be null and must be > 0. Silent fallback
 *   to DEFAULT_ACCOUNT_ID is forbidden for new tracked writes.
 * - If tracked = false: accountId is normalized to null (no account link).
 */
internal object TrackedLedgerHelper {
  /**
   * Validates that tracked entities have an explicit non-null account.
   * Throws [IllegalArgumentException] if tracked is true but accountId is missing/invalid.
   */
  fun validateTrackedAccount(
    tracked: Boolean,
    accountId: Long?
  ) {
    if (tracked) {
      require(accountId != null && accountId > 0L) {
        "Tracked mode requires an explicit, valid non-null accountId"
      }
    }
  }

  /**
   * Normalizes accountId: explicit accountId if tracked, null if untracked.
   */
  fun normalizeAccountId(
    tracked: Boolean,
    accountId: Long?
  ): Long? =
    if (tracked) {
      validateTrackedAccount(tracked, accountId)
      accountId
    } else {
      null
    }

  /**
   * Resolve the posting account for a tracked write. Enforces the same
   * positive-ID invariant as [validateTrackedAccount] so every posting path is
   * governed by one contract, not just the entry points that normalize first.
   */
  fun resolveAccountId(accountId: Long?): Long = resolveAccountId(tracked = true, accountId = accountId)

  /**
   * Overload that verifies the entity is [tracked] before asserting a valid non-null [accountId].
   */
  fun resolveAccountId(
    tracked: Boolean,
    accountId: Long?
  ): Long {
    require(tracked) { "Cannot resolve posting account for untracked entity" }
    require(accountId != null && accountId > 0L) {
      "Cannot resolve posting account: tracked entity requires a valid non-null accountId (> 0)"
    }
    return accountId
  }
}
