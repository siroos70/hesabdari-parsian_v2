package io.github.mojri.hesabyar.data

import androidx.room.withTransaction
import io.github.mojri.hesabyar.domain.utils.PersonNameNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

internal class PersonDelegate(
  private val personDao: PersonDao,
  private val loanDao: LoanDao,
  private val loanPersonOpsDao: LoanPersonOpsDao,
  private val transactionDao: TransactionDao,
  private val database: AppDatabase
) : PersonRepositoryInterface {
  override val allPersons: Flow<List<Person>> = personDao.getAllPersons()

  override suspend fun getAllPersonsIncludingArchived(): List<Person> =
    // The DAO query is blocking (no suspend signature), so hop off the caller's
    // dispatcher — a Main-dispatcher caller must not run a SQLite read on the
    // UI thread (ANR risk).
    withContext(Dispatchers.IO) {
      personDao.getAllPersonsIncludingArchivedBlocking()
    }

  override suspend fun getPersonById(id: Long): Person? = personDao.getPersonById(id)

  override suspend fun upsertPerson(person: Person): Person =
    database.withTransaction {
      val display = PersonNameNormalizer.displayForm(person.name)
      require(display.isNotEmpty()) { "Person name is blank" }
      val key = PersonNameNormalizer.normalize(display)
      require(key.isNotEmpty()) { "Person name normalizes to empty" }
      val existing = personDao.getPersonByNormalizedName(key)
      if (existing != null) {
        mergeWithExisting(existing, person)
      } else {
        insertOrResolveCollision(person, display, key)
      }
    }

  private suspend fun insertOrResolveCollision(
    person: Person,
    display: String,
    key: String
  ): Person {
    val candidate =
      person.copy(
        id = 0,
        name = display,
        normalizedName = key,
        createdAt = person.createdAt.takeIf { it != 0L } ?: System.currentTimeMillis()
      )
    val id = personDao.insertPerson(candidate)
    if (id != -1L) return candidate.copy(id = id)

    val winner =
      personDao.getPersonByNormalizedName(key)
        ?: run {
          val retryId = personDao.insertPerson(candidate)
          if (retryId != -1L) candidate.copy(id = retryId) else personDao.getPersonByNormalizedName(key)
        }
    return winner?.let { mergeWithExisting(it, person) }
      ?: throw IllegalStateException(
        "upsertPerson: collision winner disappeared and insert retry lost for normalizedName=$key"
      )
  }

  private suspend fun mergeWithExisting(
    existing: Person,
    person: Person
  ): Person {
    if (person.phone == null && person.notes == null) return existing
    val merged =
      existing.copy(
        phone = person.phone ?: existing.phone,
        notes = person.notes ?: existing.notes
      )
    if (merged != existing) personDao.updatePerson(merged)
    return merged
  }

  override suspend fun renamePerson(
    personId: Long,
    newName: String
  ): Boolean {
    val display = PersonNameNormalizer.displayForm(newName)
    require(display.isNotEmpty()) { "Person name is blank" }
    return database.withTransaction {
      val person = personDao.getPersonById(personId) ?: return@withTransaction false
      val key = PersonNameNormalizer.normalize(display)
      require(key.isNotEmpty()) { "Person name normalizes to empty" }
      val clash = personDao.getPersonByNormalizedName(key)
      if (clash != null && clash.id != personId) return@withTransaction false
      personDao.updatePerson(person.copy(name = display, normalizedName = key))
      loanDao.syncLoanPersonNames(personId, display)
      transactionDao.syncTransactionPersonNames(personId, display)
      // Legacy rows predate the personId column backfill and keep a NULL id
      // with only the denormalized name. Sync them with the OLD name so a
      // rename does not leave stale names behind.
      loanDao.syncLoanPersonNamesForNullId(person.name, display)
      transactionDao.syncTransactionPersonNamesForNullId(person.name, display)
      true
    }
  }

  override suspend fun deletePerson(person: Person) {
    database.withTransaction {
      val persisted = personDao.getPersonById(person.id) ?: return@withTransaction
      // Drop the id references but keep the denormalized personName, so
      // history stays readable and backups never export a personId without
      // a matching persons row (the validator rejects those payloads).
      loanPersonOpsDao.clearLoanPersonIds(persisted.id)
      transactionDao.clearTransactionPersonIds(persisted.id)
      personDao.deletePerson(persisted)
    }
  }
}
