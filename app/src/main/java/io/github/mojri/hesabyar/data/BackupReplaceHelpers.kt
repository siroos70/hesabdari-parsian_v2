package io.github.mojri.hesabyar.data

import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.domain.utils.PersonNameNormalizer

internal suspend fun backupClearAllTables(
  transactionDao: TransactionDao,
  loanDao: LoanDao,
  installmentDao: InstallmentDao,
  paymentHistoryDao: PaymentHistoryDao,
  bankLoanDao: BankLoanDao,
  accountDao: AccountDao,
  personDao: PersonDao
) {
  transactionDao.deleteAllTransactions()
  loanDao.deleteAllLoans()
  installmentDao.deleteAllInstallments()
  paymentHistoryDao.deleteAllPaymentHistory()
  bankLoanDao.deleteAllBankLoans()
  accountDao.deleteAllAccounts()
  personDao.deleteAllPersons()
}

internal suspend fun backupReseedDefaultAccountIfNeeded(
  accountDao: AccountDao,
  isEmpty: Boolean
) {
  if (isEmpty) accountDao.insert(AccountEntity.DEFAULT_ACCOUNT)
}

internal fun recoverPersonsFromLoansAndTransactions(
  loans: List<Loan>,
  transactions: List<Transaction>
): List<Person> {
  val distinct = LinkedHashMap<String, String>()
  for (loan in loans) {
    val raw = loan.personName
    if (raw.isBlank()) continue
    val display = PersonNameNormalizer.displayForm(raw)
    val key = PersonNameNormalizer.normalize(display)
    if (key.isNotEmpty()) distinct.putIfAbsent(key, display)
  }
  for (tx in transactions) {
    val raw = tx.personName
    if (raw == null || raw.isBlank()) continue
    val display = PersonNameNormalizer.displayForm(raw)
    val key = PersonNameNormalizer.normalize(display)
    if (key.isNotEmpty()) distinct.putIfAbsent(key, display)
  }
  return distinct.map { (key, display) -> Person(name = display, normalizedName = key) }
}

/**
 * Heals the persons list before a merge/replace restore:
 * - Legacy backups (empty `persons`) get every identity recovered from the
 *   denormalized loan/transaction names.
 * - Newer-but-stripped payloads can reference a person id on a loan/transaction
 *   while the persons array omits that row. Only those referenced-but-missing
 *   identities are appended (deduplicated by normalized key); name-only rows
 *   with `personId == null` keep their unlinked state — the user may have
 *   unlinked them on purpose (see deletePerson), and re-creating the person
 *   would undo that decision.
 */
internal fun withRecoveredReferencedPersons(backup: BackupPayload): List<Person> {
  if (backup.persons.isEmpty()) {
    return recoverPersonsFromLoansAndTransactions(backup.loans, backup.transactions)
  }
  val declaredIds = backup.persons.map { it.id }.toSet()
  val byKey = LinkedHashMap<String, Person>()
  for (person in backup.persons) {
    val key = PersonNameNormalizer.normalize(PersonNameNormalizer.displayForm(person.name))
    if (key.isNotEmpty()) byKey.putIfAbsent(key, person)
  }
  // Only names carried by rows whose person id is missing from the persons
  // array participate in recovery; a name-only row is deliberately unlinked.
  val referencedLoans =
    backup.loans.filter { loan ->
      loan.personId != null && loan.personId !in declaredIds && loan.personName.isNotBlank()
    }
  val referencedTransactions =
    backup.transactions.filter { tx ->
      tx.personId != null && tx.personId !in declaredIds && !tx.personName.isNullOrBlank()
    }
  val recovered = recoverPersonsFromLoansAndTransactions(referencedLoans, referencedTransactions)
  for (person in recovered) {
    byKey.putIfAbsent(person.normalizedName, person)
  }
  return byKey.values.toList()
}

internal suspend fun backupInsertPersonsForReplace(
  persons: List<Person>,
  personDao: PersonDao,
  allowNullIdNameFallback: Boolean
): PersonKeyMaps {
  val sourceIdToKey = mutableMapOf<Long, String>()
  val keyToLocalId = mutableMapOf<String, Long>()
  for (raw in persons) {
    val survived = backupInsertOnePersonForReplace(raw, keyToLocalId, personDao)
    if (survived) {
      val key = PersonNameNormalizer.normalize(PersonNameNormalizer.displayForm(raw.name))
      if (key.isNotEmpty()) sourceIdToKey[raw.id] = key
    }
  }
  return PersonKeyMaps(sourceIdToKey, keyToLocalId, allowNullIdNameFallback)
}

internal suspend fun backupInsertOnePersonForReplace(
  raw: Person,
  keyToLocalId: MutableMap<String, Long>,
  personDao: PersonDao
): Boolean {
  val display = PersonNameNormalizer.displayForm(raw.name)
  val key = PersonNameNormalizer.normalize(display)
  val storedId =
    if (key.isEmpty() || keyToLocalId.containsKey(key)) {
      if (key.isNotEmpty() && keyToLocalId.containsKey(key)) {
        AppLogger.w(
          "HesabyarRepository",
          "insertOnePersonForReplace: person '${raw.name}' collides on normalized key " +
            "'$key' — reusing existing person id ${keyToLocalId[key]}"
        )
      }
      keyToLocalId[key] ?: -1L
    } else {
      val inserted = personDao.insertPerson(raw.copy(name = display, normalizedName = key, id = 0))
      if (inserted != -1L) inserted else personDao.getPersonByNormalizedName(key)?.id ?: -1L
    }
  if (storedId != -1L) keyToLocalId[key] = storedId
  return storedId != -1L
}

internal suspend fun backupInsertLoansWithPersonRemap(
  loans: List<Loan>,
  maps: PersonKeyMaps,
  loanDao: LoanDao
) {
  for (loan in loans) {
    val mappedPersonId = resolvePersonId(loan.personId, loan.personName, maps)
    loanDao.insertLoan(normalizeLoanForRestore(loan.copy(personId = mappedPersonId)))
  }
}

internal suspend fun backupInsertTransactionsWithPersonRemap(
  transactions: List<Transaction>,
  maps: PersonKeyMaps,
  transactionDao: TransactionDao
) {
  for (tx in transactions) {
    val mappedPersonId = resolvePersonId(tx.personId, tx.personName, maps)
    transactionDao.insertTransaction(tx.copy(personId = mappedPersonId))
  }
}

internal fun normalizeInstallmentForRestore(installment: Installment): Installment {
  val isBankLoanInstallment = installment.bankLoanId != null
  val isValidTracked =
    !isBankLoanInstallment &&
      installment.tracked &&
      installment.accountId != null &&
      installment.accountId > 0L
  return if (isValidTracked) {
    installment
  } else {
    installment.copy(tracked = false, accountId = null)
  }
}

internal fun normalizeLoanForRestore(loan: Loan): Loan {
  val isValidTracked = loan.tracked && loan.accountId != null && loan.accountId > 0L
  return if (isValidTracked) {
    loan
  } else {
    loan.copy(tracked = false, accountId = null)
  }
}

internal fun normalizeBankLoanForRestore(bankLoan: BankLoan): BankLoan {
  val isValidTracked = bankLoan.tracked && bankLoan.accountId != null && bankLoan.accountId > 0L
  return if (isValidTracked) {
    bankLoan
  } else {
    bankLoan.copy(tracked = false, accountId = null)
  }
}
