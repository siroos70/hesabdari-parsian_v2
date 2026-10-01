package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.BuildConfig
import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.auth.BackupCipher
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.security.GeneralSecurityException

class ManageBackupUseCaseTest {
  private val useCase = ManageBackupUseCase(FakeRepository())

  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun forceKotlinParser() {
    // The "parseBackupJson fallback ..." tests below must exercise the Kotlin
    // parser. The native library is on the test class path for every task (see
    // the `rustJvmArgs` wiring in app/build.gradle.kts), so without forcing the
    // Rust availability decision off, RustBridge would win and the tests would
    // silently run the native parser instead.
    HesabyarApp.setRustInitializedForTesting(false)
  }

  private fun buildBackupJson(block: JSONObject.() -> Unit): String = JSONObject().apply(block).toString()

  // --- success: transactions ---

  @Test
  fun `parseBackupJson fallback parses transaction fields`() {
    val json =
      buildBackupJson {
        put(
          "transactions",
          JSONArray().put(
            JSONObject().apply {
              put("id", 7L)
              put("type", "INCOME")
              put("categoryId", 3L)
              put("amount", 1_250_000L)
              put("description", "salary")
              put("personName", "Boss")
              put("date", 1_700_000_000_000L)
              put("dueDate", 1_800_000_000_000L)
              put("installmentId", 5L)
            }
          )
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertTrue(result != null)
    assertEquals(1, result!!.transactions.size)
    val tx = result.transactions[0]
    assertEquals(7L, tx.id)
    assertEquals(TransactionType.INCOME, tx.type)
    assertEquals(3L, tx.categoryId)
    assertEquals(1_250_000L, tx.amount)
    assertEquals("salary", tx.description)
    assertEquals("Boss", tx.personName)
    assertEquals(1_700_000_000_000L, tx.date)
    assertEquals(1_800_000_000_000L, tx.dueDate)
    assertEquals(5L, tx.installmentId)
  }

  @Test
  fun `parseBackupJson fallback defaults transaction type when omitted`() {
    val json =
      buildBackupJson {
        put(
          "transactions",
          JSONArray().put(
            JSONObject().apply {
              put("id", 1L)
              put("amount", 500L)
            }
          )
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(1, result!!.transactions.size)
    assertEquals(TransactionType.EXPENSE, result.transactions[0].type)
  }

  @Test
  fun `parseBackupJson fallback uses default type for invalid type`() {
    val json =
      buildBackupJson {
        put(
          "transactions",
          JSONArray().apply {
            put(
              JSONObject().apply {
                put("id", 1L)
                put("type", "BOGUS")
                put("amount", 500L)
              }
            )
            put(
              JSONObject().apply {
                put("id", 2L)
                put("type", "INCOME")
                put("amount", 800L)
              }
            )
          }
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(2, result!!.transactions.size)
    assertEquals(1L, result.transactions[0].id)
    assertEquals(TransactionType.EXPENSE, result.transactions[0].type)
    assertEquals(2L, result.transactions[1].id)
    assertEquals(TransactionType.INCOME, result.transactions[1].type)
  }

  // --- success: loans ---

  @Test
  fun `parseBackupJson fallback parses loan fields`() {
    val json =
      buildBackupJson {
        put(
          "loans",
          JSONArray().put(
            JSONObject().apply {
              put("id", 11L)
              put("personName", "Reza")
              put("type", "CREDITOR")
              put("originalAmount", 5_000_000L)
              put("remainingAmount", 2_000_000L)
              put("description", "lent")
              put("date", 1_600_000_000_000L)
              put("isSettled", true)
            }
          )
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(1, result!!.loans.size)
    val loan = result.loans[0]
    assertEquals(11L, loan.id)
    assertEquals("Reza", loan.personName)
    assertEquals(LoanType.CREDITOR, loan.type)
    assertEquals(5_000_000L, loan.originalAmount)
    assertEquals(2_000_000L, loan.remainingAmount)
    assertEquals(true, loan.isSettled)
  }

  // --- success: installments ---

  @Test
  fun `parseBackupJson fallback parses installment fields`() {
    val json =
      buildBackupJson {
        put(
          "installments",
          JSONArray().put(
            JSONObject().apply {
              put("id", 21L)
              put("title", "Car")
              put("amount", 900_000L)
              put("dueDate", 1_500_000_000_000L)
              put("isPaid", true)
              put("reminderEnabled", false)
              put("notes", "n")
            }
          )
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(1, result!!.installments.size)
    val inst = result.installments[0]
    assertEquals(21L, inst.id)
    assertEquals("Car", inst.title)
    assertEquals(900_000L, inst.amount)
    assertEquals(true, inst.isPaid)
    assertEquals(false, inst.reminderEnabled)
  }

  // --- success: categories ---

  @Test
  fun `parseBackupJson fallback parses category fields`() {
    val json =
      buildBackupJson {
        put(
          "categories",
          JSONArray().put(
            JSONObject().apply {
              put("id", 31L)
              put("name", "Food")
              put("key", "food")
              put("icon", "ic_food")
              put("color", 0xFF0000L)
              put("type", "EXPENSE")
              put("isDefault", true)
            }
          )
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(1, result!!.categories.size)
    val cat = result.categories[0]
    assertEquals(31L, cat.id)
    assertEquals("Food", cat.name)
    assertEquals(CategoryType.EXPENSE, cat.type)
    assertEquals(true, cat.isDefault)
  }

  // --- success: paymentHistories ---

  @Test
  fun `parseBackupJson fallback parses paymentHistories`() {
    val json =
      buildBackupJson {
        put(
          "paymentHistories",
          JSONArray().put(
            JSONObject().apply {
              put("id", 41L)
              put("loanId", 11L)
              put("amount", 100_000L)
              put("date", 1_400_000_000_000L)
              put("notes", "partial")
            }
          )
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(1, result!!.paymentHistories.size)
    val ph = result.paymentHistories[0]
    assertEquals(41L, ph.id)
    assertEquals(11L, ph.loanId)
    assertEquals(100_000L, ph.amount)
  }

  // --- success: settings ---

  @Test
  fun `parseBackupJson fallback parses settings darkMode false`() {
    val json =
      buildBackupJson {
        put(
          "settings",
          JSONObject().apply {
            put("darkMode", false)
          }
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(false, result!!.settings.darkMode)
  }

  @Test
  fun `parseBackupJson fallback defaults settings when missing`() {
    val result = runBlocking { useCase.parseBackupJson(buildBackupJson {}) }
    assertEquals(true, result!!.settings.darkMode)
  }

  // --- success: top-level metadata + defaults ---

  @Test
  fun `parseBackupJson fallback uses explicit version and appVersion`() {
    val json =
      buildBackupJson {
        put("version", 3)
        put("appVersion", "2.4")
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertEquals(3, result!!.version)
    assertEquals("2.4", result.appVersion)
  }

  @Test
  fun `parseBackupJson fallback returns defaults for empty object`() {
    val result = runBlocking { useCase.parseBackupJson(buildBackupJson {}) }
    assertTrue(result != null)
    assertEquals(BuildConfig.BACKUP_SCHEMA_VERSION, result!!.version)
    assertEquals(BuildConfig.VERSION_NAME, result.appVersion)
    assertTrue(result.transactions.isEmpty())
    assertTrue(result.loans.isEmpty())
    assertTrue(result.installments.isEmpty())
    assertTrue(result.categories.isEmpty())
    assertTrue(result.paymentHistories.isEmpty())
    assertTrue(result.timestamp > 0)
  }

  // --- failure paths ---

  @Test
  fun `parseBackupJson fallback returns null on malformed JSON`() {
    val result = runBlocking { useCase.parseBackupJson("this is not json {{{") }
    assertNull(result)
  }

  @Test
  fun `parseBackupJson fallback returns null on non-object JSON`() {
    val result = runBlocking { useCase.parseBackupJson("[1,2,3]") }
    assertNull(result)
  }

  @Test
  fun `parseBackupJson skips invalid paymentHistories item and restores rest`() {
    // A non-object entry in paymentHistories is skipped (not a fatal crash),
    // so the rest of the backup still parses and the payload is non-null.
    val json =
      buildBackupJson {
        put(
          "paymentHistories",
          JSONArray().put(JSONObject.NULL)
        )
      }

    val result = runBlocking { useCase.parseBackupJson(json) }
    assertTrue(result != null)
    assertTrue(result!!.paymentHistories.isEmpty())
  }

  @Test
  fun `parseBackupJson fallback tolerates missing arrays`() {
    val result = runBlocking { useCase.parseBackupJson(buildBackupJson {}) }
    assertTrue(result!!.transactions.isEmpty())
    assertTrue(result.loans.isEmpty())
    assertTrue(result.installments.isEmpty())
    assertTrue(result.categories.isEmpty())
    assertFalse(result.toString().isEmpty())
  }

  // --- round-trip: accountId / destinationAccountId preservation ---

  @Test
  fun exportAndParseBackupJsonRoundTripPreservesAccountAndDestinationAccountId() {
    val repo = FakeRepository()
    val useCase = ManageBackupUseCase(repo)

    val nonDefaultAccountId = 5L
    val destAccountId = 3L

    runBlocking {
      repo.insertTransaction(
        Transaction(
          id = 1L,
          type = TransactionType.EXPENSE,
          categoryId = 1L,
          amount = 100_000L,
          description = "transfer out",
          accountId = nonDefaultAccountId,
          destinationAccountId = destAccountId
        )
      )
    }

    val json = runBlocking { useCase.exportBackupJson() }
    val result = runBlocking { useCase.parseBackupJson(json.toString()) }

    assertTrue(result != null)
    assertEquals("transaction count should be 1", 1, result!!.transactions.size)
    val tx = result.transactions[0]
    assertEquals("accountId preserved through round-trip", nonDefaultAccountId, tx.accountId)
    assertEquals("destinationAccountId preserved through round-trip", destAccountId, tx.destinationAccountId)
  }

  // --- encrypted backup round-trip ---

  @Test
  fun exportWithPassphraseThenImportRecoversOriginalValues() {
    val repo = FakeRepository()
    val useCase = ManageBackupUseCase(repo)
    val passphrase = "my-secret-passphrase"

    val realCard = "621986101234567890123456"
    val realAccountNum = "123456789012"
    val realIban = "IR123456789012345678901234"

    runBlocking {
      repo.insertAccount(
        AccountEntity(
          id = 1,
          name = "حساب اصلی",
          type = AccountType.BANK,
          cardNumber = realCard,
          accountNumber = realAccountNum,
          iban = realIban
        )
      )
    }

    // Export with passphrase
    val rootJson = runBlocking { useCase.exportBackupJson(passphrase = passphrase) }
    val jsonString = rootJson.toString()

    // Verify encryption metadata is present
    assertTrue("sensitiveFieldsEncryption must be present", rootJson.has("sensitiveFieldsEncryption"))
    val encMeta = rootJson.getJSONObject("sensitiveFieldsEncryption")
    assertTrue("salt must be present", encMeta.has("salt"))
    assertEquals(
      "iterations must match the current PBKDF2 work factor",
      BackupCipher.PBKDF2_ITERATIONS,
      encMeta.getInt("iterations")
    )

    // Verify fields are encrypted (not plaintext)
    val accountJson = rootJson.getJSONArray("accounts").getJSONObject(0)
    val cardValue = accountJson.get("cardNumber")
    val accountNumValue = accountJson.get("accountNumber")
    val ibanValue = accountJson.get("iban")
    assertTrue("cardNumber must be encrypted string", cardValue is String)
    assertTrue("accountNumber must be encrypted string", accountNumValue is String)
    assertTrue("iban must be encrypted string", ibanValue is String)
    assertFalse("cardNumber must not be plaintext", cardValue == realCard)
    assertFalse("accountNumber must not be plaintext", accountNumValue == realAccountNum)
    assertFalse("iban must not be plaintext", ibanValue == realIban)

    // Parse (Rust or Kotlin path — both accept encrypted base64 strings)
    val parsed = runBlocking { useCase.parseBackupJson(jsonString) }
    assertTrue("backup must parse successfully", parsed != null)

    // Verify isEncryptedBackup detects encryption
    val parsedRoot = JSONObject(jsonString)
    assertTrue("isEncryptedBackup must return true", ManageBackupUseCase.isEncryptedBackup(parsedRoot))

    // Decrypt with correct passphrase
    val decrypted =
      runBlocking {
        useCase.decryptBackupWithPassphrase(parsed!!, parsedRoot, passphrase)
      }

    assertEquals("cardNumber must be recovered", realCard, decrypted.accounts[0].cardNumber)
    assertEquals("accountNumber must be recovered", realAccountNum, decrypted.accounts[0].accountNumber)
    assertEquals("iban must be recovered", realIban, decrypted.accounts[0].iban)
  }

  @Test
  fun exportWithoutPassphraseStoresPlaintext() {
    val repo = FakeRepository()
    val useCase = ManageBackupUseCase(repo)

    val realCard = "6219861012345678"
    val realIban = "IR9876543210"

    runBlocking {
      repo.insertAccount(
        AccountEntity(
          id = 1,
          name = "کیف پول",
          type = AccountType.CASH_WALLET,
          cardNumber = realCard,
          iban = realIban
        )
      )
    }

    // Export without passphrase (plaintext)
    val rootJson = runBlocking { useCase.exportBackupJson() }
    val jsonString = rootJson.toString()

    // Verify no encryption metadata
    assertFalse("sensitiveFieldsEncryption must be absent", rootJson.has("sensitiveFieldsEncryption"))

    // Verify fields are plaintext
    val accountJson = rootJson.getJSONArray("accounts").getJSONObject(0)
    assertEquals("cardNumber must be plaintext", realCard, accountJson.getString("cardNumber"))
    assertEquals("iban must be plaintext", realIban, accountJson.getString("iban"))

    // Verify isEncryptedBackup returns false
    assertFalse("isEncryptedBackup must return false", ManageBackupUseCase.isEncryptedBackup(rootJson))

    // Parse and verify values match
    val parsed = runBlocking { useCase.parseBackupJson(jsonString) }
    assertEquals("cardNumber preserved", realCard, parsed!!.accounts[0].cardNumber)
    assertEquals("iban preserved", realIban, parsed.accounts[0].iban)
  }

  @Test
  fun importWithWrongPassphraseFailsGracefully() {
    val repo = FakeRepository()
    val useCase = ManageBackupUseCase(repo)

    runBlocking {
      repo.insertAccount(
        AccountEntity(
          id = 1,
          name = "حساب اصلی",
          type = AccountType.BANK,
          cardNumber = "6219861012345678",
          iban = "IR12345"
        )
      )
    }

    val rootJson = runBlocking { useCase.exportBackupJson(passphrase = "correct-passphrase") }
    val jsonString = rootJson.toString()
    val parsed = runBlocking { useCase.parseBackupJson(jsonString) }
    val parsedRoot = JSONObject(jsonString)

    // Attempt decryption with wrong passphrase — must throw
    org.junit.Assert.assertThrows(
      "GeneralSecurityException must be thrown on wrong passphrase",
      GeneralSecurityException::class.java
    ) {
      runBlocking {
        useCase.decryptBackupWithPassphrase(parsed!!, parsedRoot, "wrong-passphrase")
      }
    }
  }

  @Test
  fun decryptReorderedRawAccountsMatchesByAccountIdNotByIndex() {
    val repo = FakeRepository()
    val useCase = ManageBackupUseCase(repo)
    val firstCard = "1111111111111111"
    val secondCard = "2222222222222222"
    val firstIban = "IR101010"
    val secondIban = "IR202020"

    runBlocking {
      repo.insertAccount(
        AccountEntity(
          id = 1,
          name = "حساب اول",
          type = AccountType.BANK,
          cardNumber = firstCard,
          iban = firstIban
        )
      )
      repo.insertAccount(
        AccountEntity(
          id = 2,
          name = "حساب دوم",
          type = AccountType.BANK,
          cardNumber = secondCard,
          iban = secondIban
        )
      )
    }

    val passphrase = "correct-passphrase"
    val rootJson = runBlocking { useCase.exportBackupJson(passphrase = passphrase) }
    val jsonString = rootJson.toString()
    val parsed = runBlocking { useCase.parseBackupJson(jsonString) }!!
    assertEquals("sanity: two accounts must be parsed", 2, parsed.accounts.size)

    // Swap the raw JSON accounts so index 0 now holds account id=2's ciphertext
    // and index 1 holds account id=1's. Index-based matching would attach the
    // ciphertext to the wrong account; id-based matching must not.
    val tamperedRoot = JSONObject(jsonString)
    val rawAccounts = tamperedRoot.getJSONArray("accounts")
    val first = rawAccounts.getJSONObject(0)
    val second = rawAccounts.getJSONObject(1)
    tamperedRoot.put("accounts", JSONArray().put(second).put(first))

    val decrypted =
      runBlocking {
        useCase.decryptBackupWithPassphrase(parsed, tamperedRoot, passphrase)
      }

    assertEquals("account id 1 keeps its own card", firstCard, decrypted.accounts[0].cardNumber)
    assertEquals("account id 1 keeps its own iban", firstIban, decrypted.accounts[0].iban)
    assertEquals("account id 2 keeps its own card", secondCard, decrypted.accounts[1].cardNumber)
    assertEquals("account id 2 keeps its own iban", secondIban, decrypted.accounts[1].iban)
  }

  @Test
  fun decryptMalformedRawEntryThrowsInsteadOfMisassigning() {
    val repo = FakeRepository()
    val useCase = ManageBackupUseCase(repo)

    runBlocking {
      repo.insertAccount(
        AccountEntity(
          id = 1,
          name = "حساب اصلی",
          type = AccountType.BANK,
          cardNumber = "6219861012345678",
          iban = "IR12345"
        )
      )
    }

    val passphrase = "correct-passphrase"
    val rootJson = runBlocking { useCase.exportBackupJson(passphrase = passphrase) }
    val jsonString = rootJson.toString()
    val parsed = runBlocking { useCase.parseBackupJson(jsonString) }!!

    // A raw entry that lost its account id makes 1:1 matching by id impossible —
    // matching must fail loudly instead of silently guessing by index.
    val missingIdRoot = JSONObject(jsonString)
    missingIdRoot.getJSONArray("accounts").getJSONObject(0).remove("id")
    org.junit.Assert.assertThrows(
      "Raw entry missing id must fail",
      IllegalStateException::class.java
    ) {
      runBlocking {
        useCase.decryptBackupWithPassphrase(parsed, missingIdRoot, passphrase)
      }
    }

    // A raw entry that is JSON null (not an object) must also fail.
    val nullEntryRoot = JSONObject(jsonString)
    val nullEntryAccounts = nullEntryRoot.getJSONArray("accounts")
    nullEntryAccounts.put(0, JSONObject.NULL)
    org.junit.Assert.assertThrows(
      "JSON null raw entry must fail",
      IllegalStateException::class.java
    ) {
      runBlocking {
        useCase.decryptBackupWithPassphrase(parsed, nullEntryRoot, passphrase)
      }
    }
  }

  @Test
  fun decryptNullAccountIdInEncryptedBackupThrowsInsteadOfSilentlyUsingZero() {
    val repo = FakeRepository()
    val useCase = ManageBackupUseCase(repo)

    runBlocking {
      repo.insertAccount(
        AccountEntity(
          id = 1,
          name = "حساب اصلی",
          type = AccountType.BANK,
          cardNumber = "6219861012345678",
          iban = "IR12345"
        )
      )
    }

    val passphrase = "correct-passphrase"
    val rootJson = runBlocking { useCase.exportBackupJson(passphrase = passphrase) }
    val jsonString = rootJson.toString()
    val parsed = runBlocking { useCase.parseBackupJson(jsonString) }!!

    // "id": null in JSON causes optLong to silently resolve to 0L,
    // which would bypass the duplicate-id check and land in encryptedById[0].
    // The fix rejects id <= 0 with a clear error.
    val nullIdRoot = JSONObject(jsonString)
    nullIdRoot.getJSONArray("accounts").getJSONObject(0).put("id", JSONObject.NULL)
    org.junit.Assert.assertThrows(
      "Null account id must fail loudly, not silently resolve to 0",
      IllegalStateException::class.java
    ) {
      runBlocking {
        useCase.decryptBackupWithPassphrase(parsed, nullIdRoot, passphrase)
      }
    }
  }

  @Test
  fun decryptNonNumericAccountIdInEncryptedBackupThrowsInsteadOfSilentlyUsingZero() {
    val repo = FakeRepository()
    val useCase = ManageBackupUseCase(repo)

    runBlocking {
      repo.insertAccount(
        AccountEntity(
          id = 1,
          name = "حساب اصلی",
          type = AccountType.BANK,
          cardNumber = "6219861012345678",
          iban = "IR12345"
        )
      )
    }

    val passphrase = "correct-passphrase"
    val rootJson = runBlocking { useCase.exportBackupJson(passphrase = passphrase) }
    val jsonString = rootJson.toString()
    val parsed = runBlocking { useCase.parseBackupJson(jsonString) }!!

    // A non-numeric "id" value (e.g. "abc") causes optLong to return the
    // fallback (-1L), which must be rejected as invalid.
    val nonNumericIdRoot = JSONObject(jsonString)
    nonNumericIdRoot.getJSONArray("accounts").getJSONObject(0).put("id", "abc")
    org.junit.Assert.assertThrows(
      "Non-numeric account id must fail loudly instead of silently resolving to fallback",
      IllegalStateException::class.java
    ) {
      runBlocking {
        useCase.decryptBackupWithPassphrase(parsed, nonNumericIdRoot, passphrase)
      }
    }
  }
}
