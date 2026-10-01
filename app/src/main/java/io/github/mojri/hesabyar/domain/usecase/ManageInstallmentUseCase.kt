package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Installment
import io.github.mojri.hesabyar.data.TrackedLedgerHelper
import kotlinx.coroutines.flow.Flow

class ManageInstallmentUseCase(
  private val repository: HesabyarRepositoryInterface
) {
  val allInstallments: Flow<List<Installment>> = repository.allInstallments

  suspend fun addInstallment(
    title: String,
    amount: Long,
    dueDate: Long,
    reminderEnabled: Boolean,
    notes: String
  ): Long =
    repository.insertInstallment(
      Installment(
        title = title,
        amount = amount,
        dueDate = dueDate,
        reminderEnabled = reminderEnabled,
        notes = notes
      )
    )

  /**
   * Phase 2 atomic create: installment row plus optional initial expense post
   * in one withTransaction. The initial leg only posts when the row is created
   * already-paid with tracked=true and recordInitial=true; all other
   * combinations store the row without a transaction (already-recorded or
   * ledger-only). Future paid toggles follow the row's tracked flag.
   */
  suspend fun addTrackedInstallment(
    title: String,
    amount: Long,
    dueDate: Long,
    reminderEnabled: Boolean,
    notes: String,
    tracked: Boolean,
    accountId: Long? = null,
    recordInitial: Boolean = true,
    isPaid: Boolean = false,
    bankLoanId: Long? = null
  ): Long {
    // Plan 011 DECISION 2: a bank-loan installment is forced ledger-only
    // (tracked=false, accountId=null) so the parent's disbursement leg stays
    // the single ledger entry for that loan; reject the combination here too
    // instead of letting it silently contradict the delegate's invariant.
    require(!(tracked && bankLoanId != null)) {
      "Bank-loan installments must stay untracked (plan 011 DECISION 2)"
    }
    val normalizedAccountId = TrackedLedgerHelper.normalizeAccountId(tracked, accountId)
    return repository.insertInstallmentWithInitial(
      Installment(
        title = title,
        amount = amount,
        dueDate = dueDate,
        isPaid = isPaid,
        reminderEnabled = reminderEnabled,
        notes = notes,
        bankLoanId = bankLoanId,
        tracked = tracked,
        accountId = normalizedAccountId
      ),
      recordInitial = recordInitial && tracked && isPaid
    )
  }

  suspend fun toggleInstallmentPaid(installment: Installment) =
    repository.updateInstallment(installment.copy(isPaid = !installment.isPaid))

  suspend fun addInstallmentForBankLoan(
    bankLoanId: Long,
    title: String,
    amount: Long,
    dueDate: Long,
    reminderEnabled: Boolean,
    notes: String
  ): Long =
    repository.insertInstallment(
      Installment(
        bankLoanId = bankLoanId,
        title = title,
        amount = amount,
        dueDate = dueDate,
        reminderEnabled = reminderEnabled,
        notes = notes
      )
    )

  suspend fun deleteInstallment(installment: Installment) = repository.deleteInstallment(installment)
}
