package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins the DI contract that [BackupJsonValidator] is constructed with an
 * application [Context] in production (UseCaseModule → ManageBackupUseCase):
 * validation messages must resolve to real localized strings from strings.xml,
 * never the `<string-res-...>` sentinel that the plain-JVM tests tolerate.
 * Guards against a regression in the Context wiring that would silently ship
 * sentinel text to users.
 *
 * Also covers [BackupReferenceValidator] messages — those are now resolved
 * through the same [message] helper, so they must likewise resolve to real
 * localized strings (not sentinels) when a Context is present.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BackupJsonValidatorContextTest {
  @Test
  fun contextWiredValidatorResolvesRealLocalizedMessages() {
    val app = RuntimeEnvironment.getApplication()
    val validator = BackupJsonValidator(application = app)

    val result =
      validator.validateBackupKotlin(
        BackupPayload(
          version = 0,
          accounts = listOf(AccountEntity(id = 1L, name = "", type = AccountType.BANK))
        )
      )

    assertTrue(
      "expected Invalid with messages, got $result",
      result is BackupValidationResult.Invalid
    )
    val errors = (result as BackupValidationResult.Invalid).errors
    assertTrue(
      "version message must be the localized string, got: $errors",
      errors.any { it == app.getString(R.string.backup_validation_version_invalid) }
    )
    assertTrue(
      "account message must be the localized string, got: $errors",
      errors.any { it.contains(app.getString(R.string.backup_validation_account_name_blank, 0)) }
    )
    assertFalse(
      "no message may leak the resource-id sentinel, got: $errors",
      errors.any { it.startsWith("<string-res-") }
    )
  }

  @Test
  fun contextWiredValidatorResolvesReferenceValidatorMessages() {
    val app = RuntimeEnvironment.getApplication()
    val validator = BackupJsonValidator(application = app)

    val result =
      validator.validateBackupKotlin(
        BackupPayload(
          accounts =
            listOf(
              AccountEntity(id = 1L, name = "Main", type = AccountType.BANK),
              AccountEntity(id = 1L, name = "Dup", type = AccountType.CASH_WALLET)
            ),
          transactions =
            listOf(
              Transaction(
                type = TransactionType.TRANSFER,
                categoryId = 0L,
                amount = 1_000L,
                description = "t",
                date = 1_700_000_000_000L,
                accountId = 1L,
                destinationAccountId = 1L
              )
            ),
          categories =
            listOf(
              Category(id = 1L, name = "Food", key = "food", icon = "i", color = 0xFF0000L, type = CategoryType.EXPENSE)
            )
        )
      )

    val errors = (result as BackupValidationResult.Invalid).errors

    assertTrue(
      "duplicate-account-id message must be localized, got: $errors",
      errors.any { it.contains(app.getString(R.string.backup_error_duplicate_account_id, "1")) }
    )
    assertTrue(
      "transfer-same-source-destination message must be localized, got: $errors",
      errors.any { it.contains(app.getString(R.string.backup_error_transfer_same_source_destination, 0)) }
    )
    assertFalse(
      "no message may leak the resource-id sentinel, got: $errors",
      errors.any { it.startsWith("<string-res-") }
    )
  }
}
