package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.HesabyarRepositoryInterface
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.PaymentHistory
import io.github.mojri.hesabyar.data.Person
import io.github.mojri.hesabyar.domain.utils.PersonNameNormalizer
import kotlinx.coroutines.flow.Flow

/** Request configuration data class for tracked and untracked loan creation. */
data class TrackedLoanRequest(
  val personName: String,
  val type: LoanType,
  val amount: Long,
  val description: String = "",
  val tracked: Boolean = false,
  val accountId: Long? = null,
  val recordInitial: Boolean = true,
  val customDate: Long? = null,
  val personId: Long? = null,
)

class ManageLoanUseCase(
  private val repository: HesabyarRepositoryInterface
) {
  val allLoans: Flow<List<Loan>> = repository.allLoans

  @Suppress("TooGenericExceptionCaught")
  suspend fun addLoan(
    personName: String,
    type: LoanType,
    amount: Long,
    description: String,
    customDate: Long? = null,
    personId: Long? = null
  ): Long {
    val resolved = resolvePerson(personName, personId)
    return try {
      repository.insertLoan(
        Loan(
          personName = personName,
          type = type,
          originalAmount = amount,
          remainingAmount = amount,
          description = description,
          date = customDate ?: System.currentTimeMillis(),
          personId = resolved.personId
        )
      )
    } catch (e: Exception) {
      rollbackCreatedPerson(resolved.newlyCreatedPerson)
      throw e
    }
  }

  /**
   * Phase 2 atomic create: the loan row and its optional initial-leg
   * transaction are written in one withTransaction (see LoanDelegate).
   * Three-state UI maps to (tracked, recordInitial):
   * recordInitial=true posts the initial INCOME/EXPENSE; false skips it
   * (already recorded manually); tracked=false is ledger-only.
   */
  @Suppress("TooGenericExceptionCaught")
  suspend fun addTrackedLoan(request: TrackedLoanRequest): Long {
    io.github.mojri.hesabyar.data.TrackedLedgerHelper
      .validateTrackedAccount(request.tracked, request.accountId)
    val resolved = resolvePerson(request.personName, request.personId)
    return try {
      repository.insertLoanWithInitial(
        Loan(
          personName = request.personName,
          type = request.type,
          originalAmount = request.amount,
          remainingAmount = request.amount,
          description = request.description,
          date = request.customDate ?: System.currentTimeMillis(),
          tracked = request.tracked,
          accountId = request.accountId,
          personId = resolved.personId
        ),
        recordInitial = request.recordInitial && request.tracked
      )
    } catch (e: Exception) {
      rollbackCreatedPerson(resolved.newlyCreatedPerson)
      throw e
    }
  }

  suspend fun addTrackedLoan(
    personName: String,
    type: LoanType,
    amount: Long,
    description: String,
    tracked: Boolean,
    accountId: Long? = null,
    recordInitial: Boolean = true,
    customDate: Long? = null,
    personId: Long? = null
  ): Long =
    addTrackedLoan(
      TrackedLoanRequest(
        personName = personName,
        type = type,
        amount = amount,
        description = description,
        tracked = tracked,
        accountId = accountId,
        recordInitial = recordInitial,
        customDate = customDate,
        personId = personId
      )
    )

  private suspend fun resolvePerson(
    personName: String,
    explicitPersonId: Long?
  ): ResolvedPerson {
    if (explicitPersonId != null) return ResolvedPerson(explicitPersonId)
    val display = PersonNameNormalizer.displayForm(personName)
    val key = PersonNameNormalizer.normalize(display)
    val existing =
      if (key.isNotEmpty()) {
        repository.getAllPersonsIncludingArchived().firstOrNull { it.normalizedName == key }
      } else {
        null
      }
    return when {
      key.isEmpty() -> ResolvedPerson(null)
      existing != null -> ResolvedPerson(existing.id)
      else -> {
        val created = repository.upsertPerson(Person(name = display, normalizedName = key))
        ResolvedPerson(created.id, created)
      }
    }
  }

  @Suppress("TooGenericExceptionCaught")
  private suspend fun rollbackCreatedPerson(person: Person?) {
    if (person != null) {
      try {
        repository.deletePerson(person)
      } catch (_: Exception) {
        // Best-effort cleanup
      }
    }
  }

  private data class ResolvedPerson(
    val personId: Long?,
    val newlyCreatedPerson: Person? = null
  )

  suspend fun makeRepayment(
    loanId: Long,
    amount: Long,
    notes: String,
    customDate: Long? = null
  ): Boolean = repository.addPaymentToLoan(loanId, amount, notes, customDate)

  fun getPaymentHistory(loanId: Long): Flow<List<PaymentHistory>> = repository.getPaymentHistoryForLoan(loanId)

  suspend fun updateLoan(loan: Loan) = repository.updateLoan(loan)

  suspend fun deleteLoan(loan: Loan) = repository.deleteLoan(loan)
}
