package io.github.mojri.hesabyar.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

internal class InstallmentDelegate(
  private val installmentDao: InstallmentDao,
  private val transactionDao: TransactionDao,
  private val transactionLinkDao: TransactionLinkDao,
  private val categoryDao: CategoryDao,
  private val database: AppDatabase
) : InstallmentOps {
  override val allInstallments: Flow<List<Installment>> = installmentDao.getAllInstallments()

  override suspend fun insertInstallment(installment: Installment): Long {
    TrackedLedgerHelper.validateTrackedAccount(installment.tracked, installment.accountId)
    val normalizedInstallment =
      installment.copy(
        accountId = TrackedLedgerHelper.normalizeAccountId(installment.tracked, installment.accountId)
      )
    return installmentDao.insertInstallment(normalizedInstallment)
  }

  override suspend fun insertInstallmentWithInitial(
    installment: Installment,
    recordInitial: Boolean
  ): Long {
    TrackedLedgerHelper.validateTrackedAccount(installment.tracked, installment.accountId)
    val normalizedInstallment =
      installment.copy(
        accountId = TrackedLedgerHelper.normalizeAccountId(installment.tracked, installment.accountId)
      )
    return database.withTransaction {
      val id = installmentDao.insertInstallment(normalizedInstallment)
      if (recordInitial && normalizedInstallment.tracked && normalizedInstallment.isPaid) {
        val category =
          categoryDao.getCategoryByKey("Installments")
            ?: throw IllegalStateException(
              "Installments category is missing from database"
            )
        val stored = installmentDao.getInstallmentById(id) ?: normalizedInstallment.copy(id = id)
        transactionDao.insertTransaction(
          Transaction(
            type = TransactionType.EXPENSE,
            categoryId = category.id,
            amount = stored.amount,
            description = "پرداخت قسط: ${stored.title} - ${stored.notes}",
            installmentId = id,
            accountId = TrackedLedgerHelper.resolveAccountId(stored.accountId)
          )
        )
      }
      id
    }
  }

  override suspend fun updateInstallment(installment: Installment) {
    database.withTransaction {
      // A stale or already-deleted installment updates zero rows and must not
      // trigger a payment transition for a row that does not exist.
      val existing =
        installmentDao.getInstallmentById(installment.id)
          ?: return@withTransaction
      // Enforce the ledger invariants on the incoming row before persisting,
      // exactly like insert does: untracked rows carry no account, tracked
      // rows require a positive one. Then write exactly once.
      TrackedLedgerHelper.validateTrackedAccount(installment.tracked, installment.accountId)
      val normalized =
        installment.copy(
          accountId = TrackedLedgerHelper.normalizeAccountId(installment.tracked, installment.accountId)
        )
      installmentDao.updateInstallment(normalized)
      val justPaid = normalized.isPaid && !existing.isPaid
      val justUnpaid = !normalized.isPaid && existing.isPaid
      // Phase 2 opt-in: only tracked rows post or reverse ledger entries.
      // Untracked rows only flip isPaid; historical transactions stay untouched.
      // A new expense is posted per the final state, while a reversal keys on
      // the persisted state: an expense could only have been posted while the
      // row was tracked, so an opt-out (tracked true → false) combined with a
      // paid → unpaid flip still reverses it instead of stranding the money.
      if (justPaid && normalized.tracked) {
        val category =
          categoryDao.getCategoryByKey("Installments")
            ?: throw IllegalStateException(
              "Installments category is missing; cannot record the paid installment expense"
            )
        transactionDao.insertTransaction(
          Transaction(
            type = TransactionType.EXPENSE,
            categoryId = category.id,
            amount = normalized.amount,
            description = "پرداخت قسط: ${normalized.title} - ${normalized.notes}",
            installmentId = normalized.id,
            accountId = TrackedLedgerHelper.resolveAccountId(normalized.accountId)
          )
        )
      } else if (justUnpaid && existing.tracked) {
        // Reverse the expense recorded when the installment was first paid, so
        // toggling paid → unpaid → paid never double-counts the money. A
        // missing category aborts the whole update — the paid→unpaid flip rolls
        // back with the transaction — instead of leaving the expense behind an
        // unpaid row.
        val category =
          categoryDao.getCategoryByKey("Installments")
            ?: throw IllegalStateException(
              "Installments category is missing; cannot reverse the paid installment expense"
            )
        transactionLinkDao.deleteTransactionForInstallment(
          installmentId = normalized.id,
          categoryId = category.id
        )
      }
    }
  }

  override suspend fun deleteInstallment(installment: Installment) {
    database.withTransaction {
      // Decide from the persisted row, not the caller's object: a stale
      // unpaid snapshot deleting a row that is actually paid would skip the
      // linked-expense cleanup and strand the money behind a dead row. A row
      // already deleted through another path (bank-loan cascade) can still
      // carry a linked expense behind it — clean it before returning.
      val existing = installmentDao.getInstallmentById(installment.id)
      if (existing == null) {
        transactionLinkDao.deleteTransactionsForInstallment(installment.id)
        return@withTransaction
      }
      if (existing.isPaid) {
        transactionLinkDao.deleteTransactionsForInstallment(existing.id)
      }
      installmentDao.deleteInstallment(installment)
    }
  }
}
