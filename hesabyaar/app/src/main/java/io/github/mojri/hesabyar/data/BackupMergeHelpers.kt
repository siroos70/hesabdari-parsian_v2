package io.github.mojri.hesabyar.data

import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.domain.utils.PersonNameNormalizer

internal suspend fun backupMergeCategories(
  categories: List<Category>,
  categoryDao: CategoryDao
): Map<Long, Long> {
  val keyToId = mutableMapOf<String, Long>()
  val idToKey = mutableMapOf<Long, String>()
  for (category in categories) {
    val existing = categoryDao.getCategoryByKey(category.key)
    val savedId =
      if (existing != null) {
        // The persisted isDefault flag is authoritative (CategoryDelegate
        // .updateCategory refuses isDefault flips for the same reason): a
        // backup carrying isDefault=false on the live default row must not
        // silently unprotect it, or the delete guard becomes bypassable.
        categoryDao.updateCategory(category.copy(id = existing.id, isDefault = existing.isDefault))
        existing.id
      } else {
        categoryDao.insertCategory(category.copy(id = 0))
      }
    keyToId[category.key] = savedId
    idToKey[category.id] = category.key
  }
  return idToKey.mapValues { keyToId[it.value] ?: it.key }
}

internal suspend fun backupMergeLoans(
  loans: List<Loan>,
  loanDao: LoanDao,
  accountIdMap: Map<Long, Long> = emptyMap(),
  resolvePersonId: (Long?, String?) -> Long?
): Map<Long, Long> =
  loans.associate { loan ->
    val mappedPersonId = resolvePersonId(loan.personId, loan.personName)
    val mappedAccountId = loan.accountId?.let { accountIdMap[it] ?: it }
    loan.id to
      loanDao.insertLoan(
        normalizeLoanForRestore(
          loan.copy(
            id = 0,
            personId = mappedPersonId,
            accountId = mappedAccountId
          )
        )
      )
  }

internal suspend fun backupMergeBankLoans(
  bankLoans: List<BankLoan>,
  bankLoanDao: BankLoanDao,
  accountIdMap: Map<Long, Long> = emptyMap()
): Map<Long, Long> =
  bankLoans.associate {
    val mappedAccountId = it.accountId?.let { id -> accountIdMap[id] ?: id }
    it.id to
      bankLoanDao.insertBankLoan(
        normalizeBankLoanForRestore(
          it.copy(
            id = 0,
            accountId = mappedAccountId
          )
        )
      )
  }

internal suspend fun backupMergeInstallments(
  installments: List<Installment>,
  installmentDao: InstallmentDao,
  bankLoanIdMap: Map<Long, Long>,
  accountIdMap: Map<Long, Long> = emptyMap()
): Map<Long, Long> {
  val idMap = mutableMapOf<Long, Long>()
  for (installment in installments) {
    val mappedBankLoanId = installment.bankLoanId?.let(bankLoanIdMap::get)
    if (installment.bankLoanId != null && mappedBankLoanId == null) {
      // Mirrors backupMergePaymentHistories: an installment whose bankLoanId
      // does not map to a merged bank loan would otherwise silently drop the
      // FK (bankLoanId?.let(map::get) yields null). Skip it with a warning
      // so a malformed backup cannot silently strand orphan installments.
      AppLogger.w(
        "HesabyarRepository",
        "mergeFromBackup: skipping installment=${installment.id} " +
          "bankLoanId=${installment.bankLoanId} because no merged bank loan matches"
      )
      continue
    }
    val mappedAccountId = installment.accountId?.let { accountIdMap[it] ?: it }
    val candidate = installment.copy(id = 0, bankLoanId = mappedBankLoanId, accountId = mappedAccountId)
    val newId = installmentDao.insertInstallment(normalizeInstallmentForRestore(candidate))
    idMap[installment.id] = newId
  }
  return idMap
}

internal suspend fun backupMergeAccounts(
  accounts: List<AccountEntity>,
  accountDao: AccountDao
): Map<Long, Long> {
  val accountIdsByName =
    accountDao
      .getAllAccountsBlocking()
      .associateTo(mutableMapOf()) { it.name to it.id }
  val accountIdMap = mutableMapOf<Long, Long>()
  for (account in accounts) {
    val existingId = accountIdsByName[account.name]
    if (existingId != null) {
      accountDao.update(account.copy(id = existingId))
      accountIdMap[account.id] = existingId
    } else {
      val newId = accountDao.insert(account.copy(id = 0))
      accountIdsByName[account.name] = newId
      accountIdMap[account.id] = newId
    }
  }
  return accountIdMap
}

internal suspend fun backupMergePersons(
  persons: List<Person>,
  personDao: PersonDao
): Map<String, Long> {
  val existingByKey =
    personDao
      .getAllPersonsIncludingArchivedBlocking()
      .associateBy { it.normalizedName }
      .toMutableMap()
  for (person in persons) {
    backupMergeOnePerson(person, existingByKey, personDao)
  }
  return existingByKey.mapValues { it.value.id }
}

internal suspend fun backupMergeOnePerson(
  person: Person,
  existingByKey: MutableMap<String, Person>,
  personDao: PersonDao
) {
  val display = PersonNameNormalizer.displayForm(person.name)
  val key = PersonNameNormalizer.normalize(display)
  if (key.isEmpty()) return
  val existing = existingByKey[key] ?: personDao.getPersonByNormalizedName(key)
  if (existing != null) {
    val merged =
      existing.copy(
        normalizedName = key,
        phone = if (existing.phone.isNullOrBlank()) person.phone else existing.phone,
        notes = if (existing.notes.isNullOrBlank()) person.notes else existing.notes
      )
    personDao.updatePerson(merged)
    existingByKey[key] = merged
  } else {
    val candidate = person.copy(id = 0, name = display, normalizedName = key)
    val insertedId = personDao.insertPerson(candidate)
    val stored =
      if (insertedId != -1L) {
        candidate.copy(id = insertedId)
      } else {
        personDao.getPersonByNormalizedName(key) ?: return
      }
    existingByKey[key] = stored
  }
}

internal suspend fun backupMergeTransactions(
  transactions: List<Transaction>,
  categoryIdMap: Map<Long, Long>,
  installmentIdMap: Map<Long, Long>,
  accountIdMap: Map<Long, Long>,
  categoryDao: CategoryDao,
  accountDao: AccountDao,
  transactionDao: TransactionDao,
  resolvePersonId: (Long?, String?) -> Long?
) {
  val otherCategoryId = categoryDao.getCategoryByKey("Other")?.id
  val localAccountIds = accountDao.getAllAccountsBlocking().map { it.id }.toSet()
  transactions.forEach { transaction ->
    backupMergeOneTransaction(
      transaction,
      categoryIdMap,
      installmentIdMap,
      accountIdMap,
      otherCategoryId,
      localAccountIds,
      transactionDao,
      resolvePersonId
    )
  }
}

private suspend fun backupMergeOneTransaction(
  transaction: Transaction,
  categoryIdMap: Map<Long, Long>,
  installmentIdMap: Map<Long, Long>,
  accountIdMap: Map<Long, Long>,
  otherCategoryId: Long?,
  localAccountIds: Set<Long>,
  transactionDao: TransactionDao,
  resolvePersonId: (Long?, String?) -> Long?
) {
  val mappedAccountId = transaction.accountId.let { accountIdMap[it] ?: it }
  val mappedDestinationAccountId = transaction.destinationAccountId?.let { accountIdMap[it] ?: it }
  val destinationResolved =
    when (mappedDestinationAccountId) {
      null -> transaction.destinationAccountId == null
      else -> localAccountIds.contains(mappedDestinationAccountId)
    }
  if (!localAccountIds.contains(mappedAccountId) || !destinationResolved) {
    AppLogger.w(
      "HesabyarRepository",
      "mergeFromBackup: skipping transaction=${transaction.id} " +
        "accountId=${transaction.accountId}->$mappedAccountId " +
        "destinationAccountId=${transaction.destinationAccountId}->$mappedDestinationAccountId " +
        "because no local account matches"
    )
    return
  }
  val mappedCategoryId =
    categoryIdMap[transaction.categoryId]
      ?: otherCategoryId ?: transaction.categoryId
  val mappedInstallmentId = transaction.installmentId?.let { installmentIdMap[it] }
  if (transaction.installmentId != null && mappedInstallmentId == null) {
    AppLogger.w(
      "HesabyarRepository",
      "mergeFromBackup: skipping transaction=${transaction.id} " +
        "installmentId=${transaction.installmentId} because referenced installment was skipped"
    )
    return
  }
  val mappedPersonId = resolvePersonId(transaction.personId, transaction.personName)
  transactionDao.insertTransaction(
    transaction.copy(
      id = 0,
      categoryId = mappedCategoryId,
      installmentId = mappedInstallmentId,
      accountId = mappedAccountId,
      destinationAccountId = mappedDestinationAccountId,
      personId = mappedPersonId
    )
  )
}

internal suspend fun backupMergePaymentHistories(
  paymentHistories: List<PaymentHistory>,
  loanIdMap: Map<Long, Long>,
  paymentHistoryDao: PaymentHistoryDao
) {
  for (payment in paymentHistories) {
    val mappedLoanId = loanIdMap[payment.loanId]
    if (mappedLoanId == null) {
      AppLogger.w(
        "HesabyarRepository",
        "mergeFromBackup: skipping payment with unmapped loanId=${payment.loanId}"
      )
      continue
    }
    paymentHistoryDao.insertPayment(payment.copy(id = 0, loanId = mappedLoanId))
  }
}
