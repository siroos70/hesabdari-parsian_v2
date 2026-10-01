package io.github.mojri.hesabyar.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.domain.usecase.GetSettingsUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
  private lateinit var viewModel: BackupViewModel
  private lateinit var fakeRepo: FakeRepository
  private lateinit var context: Context
  private lateinit var useCase: ManageBackupUseCase
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    context = RuntimeEnvironment.getApplication()
    fakeRepo = FakeRepository()
    useCase = ManageBackupUseCase(fakeRepo, testDispatcher)
    viewModel =
      BackupViewModel(
        context,
        useCase,
        GetSettingsUseCase(context.getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE))
      )
    viewModel.importCoordinator.ioDispatcher = testDispatcher
    viewModel.importCoordinator.cryptoDispatcher = testDispatcher
    viewModel.exportCoordinator.ioDispatcher = testDispatcher
    viewModel.exportCoordinator.cryptoDispatcher = testDispatcher
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun onExportPickerCancelledResetsStateToIdle() =
    runTest {
      viewModel.exportCoordinator.exportWithoutPassphrase()
      testDispatcher.scheduler.advanceUntilIdle()

      assertTrue(
        "Expected Exporting after staging export, got ${viewModel.operationState.value}",
        viewModel.operationState.value is BackupOperationState.Exporting
      )

      viewModel.exportCoordinator.onExportPickerCancelled()

      assertTrue(
        "Expected Idle after picker cancel, got ${viewModel.operationState.value}",
        viewModel.operationState.value is BackupOperationState.Idle
      )
    }

  @Test
  fun onExportPickerCancelledClearsStagedData() =
    runTest {
      viewModel.exportCoordinator.exportWithoutPassphrase()
      testDispatcher.scheduler.advanceUntilIdle()

      viewModel.exportCoordinator.onExportPickerCancelled()

      val outputStream = ByteArrayOutputStream()
      viewModel.exportCoordinator.writeStagedExportToFile(outputStream)
      testDispatcher.scheduler.advanceUntilIdle()

      val state = viewModel.operationState.value
      assertTrue("Expected Error after cancel, got $state", state is BackupOperationState.Error)
      assertTrue((state as BackupOperationState.Error).message.contains("آماده نیست"))
    }

  @Test
  fun writeStagedExportToFileWithNoStagedDataClosesTheStream() =
    runTest {
      // Null-json early return: staging was cancelled (or never ran), yet a
      // picker callback can still arrive with a live SAF stream. The stream
      // must be closed on this path too, not leaked.
      viewModel.exportCoordinator.onExportPickerCancelled()
      val stream = CloseTrackingOutputStream()

      viewModel.exportCoordinator.writeStagedExportToFile(stream)
      testDispatcher.scheduler.advanceUntilIdle()

      val state = viewModel.operationState.value
      assertTrue("Expected Error after cancel, got $state", state is BackupOperationState.Error)
      assertTrue((state as BackupOperationState.Error).message.contains("آماده نیست"))
      assertTrue("Stream must be closed on the null-json early return", stream.isClosed)
    }

  @Test
  fun writeStagedExportToFileSuccessAfterExportStaging() =
    runTest {
      viewModel.exportCoordinator.exportWithoutPassphrase()
      testDispatcher.scheduler.advanceUntilIdle()

      val outputStream = ByteArrayOutputStream()
      viewModel.exportCoordinator.writeStagedExportToFile(outputStream)
      testDispatcher.scheduler.advanceUntilIdle()

      val state = viewModel.operationState.value
      assertTrue(
        "Expected ExportSuccess, got $state",
        state is BackupOperationState.ExportSuccess
      )
    }

  @Test
  fun exportPickerLaunchRequestStaysFalseUntilStagingCompletes() =
    runTest {
      viewModel.exportCoordinator.exportWithoutPassphrase()
      // Staging is queued on the test dispatcher; this mirrors the instant
      // between the click handler returning and the picker callback firing.
      assertTrue(
        "Picker launch must not be requested before staging completes",
        !viewModel.exportPickerLaunchRequest.value
      )

      testDispatcher.scheduler.advanceUntilIdle()

      assertTrue(
        "Expected picker launch request after staging, got ${viewModel.operationState.value}",
        viewModel.exportPickerLaunchRequest.value
      )
    }

  @Test
  fun exportWithPassphraseRaisesPickerLaunchRequestOnlyAfterStaging() =
    runTest {
      viewModel.exportCoordinator.exportWithPassphrase("secret")
      assertTrue(
        "Picker launch must not be requested before encrypted staging completes",
        !viewModel.exportPickerLaunchRequest.value
      )

      testDispatcher.scheduler.advanceUntilIdle()

      assertTrue(
        "Expected picker launch request after encrypted staging, got ${viewModel.operationState.value}",
        viewModel.exportPickerLaunchRequest.value
      )
    }

  @Test
  fun failedExportStagingDoesNotRaisePickerLaunchRequest() =
    runTest {
      fakeRepo.exportShouldThrow = IllegalStateException("simulated export failure")

      viewModel.exportCoordinator.exportWithPassphrase("secret")
      testDispatcher.scheduler.advanceUntilIdle()

      assertTrue(
        "Expected Error state, got ${viewModel.operationState.value}",
        viewModel.operationState.value is BackupOperationState.Error
      )
      assertTrue(
        "Export failure must surface the encryption error message",
        (viewModel.operationState.value as BackupOperationState.Error)
          .message
          .contains("رمزگذاری پشتیبان")
      )
      assertTrue(
        "isCryptoInProgress must be false after failed export staging",
        !viewModel.isCryptoInProgress.value
      )
      assertTrue(
        "Failed staging must not raise the picker launch request",
        !viewModel.exportPickerLaunchRequest.value
      )
    }

  @Test
  fun consumeExportPickerLaunchRequestClearsTheSignal() =
    runTest {
      viewModel.exportCoordinator.exportWithoutPassphrase()
      testDispatcher.scheduler.advanceUntilIdle()
      assertTrue(viewModel.exportPickerLaunchRequest.value)

      viewModel.exportCoordinator.consumeExportPickerLaunchRequest()

      assertTrue(
        "Signal must clear after the screen launches the picker",
        !viewModel.exportPickerLaunchRequest.value
      )
    }

  @Test
  fun decryptAndStageImportWrongPassphrasePreservesStagedData() =
    runTest {
      // Use a plaintext backup JSON that parseBackupJson can definitely parse.
      // decryptBackupWithPassphrase will throw because there is no encryption
      // metadata, simulating a wrong-passphrase / corrupted-file error.
      val plainJson =
        """
        {
          "version": 1,
          "timestamp": 1710000000000,
          "appVersion": "1.0",
          "transactions": [],
          "loans": [],
          "installments": [],
          "categories": [],
          "accounts": []
        }
        """.trimIndent()
      val salt = "test-salt"

      viewModel.importCoordinator.pendingImportRawJson = plainJson
      viewModel.importCoordinator.pendingImportSalt = salt

      viewModel.importCoordinator.decryptAndStageImport("wrong-passphrase")
      testDispatcher.scheduler.advanceUntilIdle()

      // Staged data must be preserved so the user can retry
      assertTrue(
        "pendingImportRawJson must be preserved after decryption failure",
        viewModel.importCoordinator.pendingImportRawJson != null
      )
      assertTrue(
        "pendingImportSalt must be preserved after decryption failure",
        viewModel.importCoordinator.pendingImportSalt != null
      )

      // Error state allows retry
      assertTrue(
        "Expected Error after decryption failure, got ${viewModel.operationState.value}",
        viewModel.operationState.value is BackupOperationState.Error
      )
    }

  @Test
  fun decryptAndStageImportCorrectPassphraseAfterWrongAttemptSucceeds() =
    runTest {
      val (rawJson, salt) = encryptedBackupFixture()

      viewModel.importCoordinator.pendingImportRawJson = rawJson
      viewModel.importCoordinator.pendingImportSalt = salt

      // First attempt with a wrong passphrase — fails AES-GCM authentication
      viewModel.importCoordinator.decryptAndStageImport("wrong-passphrase")
      testDispatcher.scheduler.advanceUntilIdle()

      assertTrue(
        "Expected Error after decryption failure, got ${viewModel.operationState.value}",
        viewModel.operationState.value is BackupOperationState.Error
      )
      assertTrue(
        "pendingImportRawJson must be preserved after the wrong passphrase",
        viewModel.importCoordinator.pendingImportRawJson != null
      )

      // Second attempt with the correct passphrase — must decrypt from the
      // preserved staged data and stage the backup for restore.
      viewModel.importCoordinator.decryptAndStageImport("secret")
      testDispatcher.scheduler.advanceUntilIdle()

      val staged = viewModel.pendingRestoreBackup.value
      assertTrue(
        "Expected staged backup after successful retry, got ${viewModel.pendingRestoreBackup.value}",
        staged != null
      )
      assertTrue(
        "Staged raw JSON must be cleared after successful retry",
        viewModel.importCoordinator.pendingImportRawJson == null
      )
      assertTrue(
        "Dialog must close after successful retry, got ${viewModel.passphraseDialogState.value}",
        viewModel.passphraseDialogState.value is PassphraseDialogState.Hidden
      )
    }

  @Test
  fun cancelPassphraseDialogClearsStagedImportData() =
    runTest {
      viewModel.importCoordinator.pendingImportRawJson = "some-json"
      viewModel.importCoordinator.pendingImportSalt = "some-salt"

      viewModel.importCoordinator.cancelPassphraseDialog()

      assertTrue(
        "pendingImportRawJson must be null after cancel",
        viewModel.importCoordinator.pendingImportRawJson == null
      )
      assertTrue(
        "pendingImportSalt must be null after cancel",
        viewModel.importCoordinator.pendingImportSalt == null
      )
    }

  @Test
  fun requestExportPassphraseDialogShowsExportDialog() =
    runTest {
      assertTrue(
        "Expected Hidden initially, got ${viewModel.passphraseDialogState.value}",
        viewModel.passphraseDialogState.value is PassphraseDialogState.Hidden
      )

      viewModel.exportCoordinator.requestExportPassphraseDialog()

      assertTrue(
        "Expected ExportPassphrase after request, got ${viewModel.passphraseDialogState.value}",
        viewModel.passphraseDialogState.value is PassphraseDialogState.ExportPassphrase
      )
    }

  @Test
  fun exportWithPassphraseKeepsDialogOpenDuringCryptoAndClosesAfterStaging() =
    runTest {
      val gate = CompletableDeferred<Unit>()
      fakeRepo.exportGate = gate
      viewModel.exportCoordinator.requestExportPassphraseDialog()

      viewModel.exportCoordinator.exportWithPassphrase("secret")
      // Staging suspends on the gate; while PBKDF2 derivation + encryption is
      // in flight the dialog must stay visible (with its progress indicator)
      // — matching the import flow — and the crypto flag must be raised.
      testDispatcher.scheduler.runCurrent()
      assertTrue(
        "isCryptoInProgress must be true during crypto work",
        viewModel.isCryptoInProgress.value
      )
      assertTrue(
        "Dialog must stay open while encryption is in progress, got ${viewModel.passphraseDialogState.value}",
        viewModel.passphraseDialogState.value is PassphraseDialogState.ExportPassphrase
      )

      gate.complete(Unit)
      testDispatcher.scheduler.advanceUntilIdle()

      assertTrue(
        "isCryptoInProgress must be false after crypto work finishes",
        !viewModel.isCryptoInProgress.value
      )
      assertTrue(
        "Dialog must close after encrypted staging completes, got ${viewModel.passphraseDialogState.value}",
        viewModel.passphraseDialogState.value is PassphraseDialogState.Hidden
      )
      assertTrue(
        "Expected Exporting after staging, got ${viewModel.operationState.value}",
        viewModel.operationState.value is BackupOperationState.Exporting
      )
      assertTrue(
        "Expected picker launch request after staging",
        viewModel.exportPickerLaunchRequest.value
      )
    }

  @Test
  fun exportWithoutPassphraseClearsDialogAndStagesExport() =
    runTest {
      viewModel.exportCoordinator.requestExportPassphraseDialog()

      viewModel.exportCoordinator.exportWithoutPassphrase()
      testDispatcher.scheduler.advanceUntilIdle()

      assertTrue(
        "Dialog must close on plaintext export",
        viewModel.passphraseDialogState.value is PassphraseDialogState.Hidden
      )
      assertTrue(
        "Expected Exporting after staging, got ${viewModel.operationState.value}",
        viewModel.operationState.value is BackupOperationState.Exporting
      )
      assertTrue(
        "Expected picker launch request after staging",
        viewModel.exportPickerLaunchRequest.value
      )
    }

  private suspend fun encryptedBackupFixture(): Pair<String, String> {
    fakeRepo.accountsList +=
      AccountEntity(
        id = 1,
        name = "کارت نمونه",
        type = AccountType.BANK,
        bankName = "ملی",
        cardNumber = "6037-9971-1234-5678",
        accountNumber = "123456789",
        iban = "IR123456789012345678901234"
      )
    val encryptedJson = useCase.exportBackupJson(passphrase = "secret")
    val salt =
      requireNotNull(ManageBackupUseCase.getEncryptionSalt(encryptedJson)) {
        "Encrypted backup must carry a PBKDF2 salt"
      }
    return encryptedJson.toString(2) to salt
  }

  @Test
  fun decryptAndStageImportCorrectPassphraseOnFirstTrySucceeds() =
    runTest {
      // Build a genuinely encrypted backup (PBKDF2 + AES-GCM) via the use case,
      // then decrypt it with the correct passphrase on the first attempt.
      val (rawJson, salt) = encryptedBackupFixture()

      viewModel.importCoordinator.pendingImportRawJson = rawJson
      viewModel.importCoordinator.pendingImportSalt = salt

      viewModel.importCoordinator.decryptAndStageImport("secret")
      testDispatcher.scheduler.advanceUntilIdle()

      val staged = viewModel.pendingRestoreBackup.value
      assertTrue(
        "Expected staged backup after successful decrypt, got ${viewModel.pendingRestoreBackup.value}",
        staged != null
      )
      assertEquals(
        "Sensitive fields must round-trip through encryption",
        "6037-9971-1234-5678",
        staged!!.accounts.single().cardNumber
      )
      assertTrue(
        "Dialog must close after successful decrypt",
        viewModel.passphraseDialogState.value is PassphraseDialogState.Hidden
      )
      assertTrue(
        "Staged raw JSON must be cleared after successful decrypt",
        viewModel.importCoordinator.pendingImportRawJson == null
      )
      assertTrue(
        "Staged salt must be cleared after successful decrypt",
        viewModel.importCoordinator.pendingImportSalt == null
      )
      assertTrue(
        "isCryptoInProgress must be false after decrypt completes",
        !viewModel.isCryptoInProgress.value
      )
    }

  @Test
  fun decryptAndStageImportWrongPassphraseOnEncryptedBackupKeepsStagedData() =
    runTest {
      val (rawJson, salt) = encryptedBackupFixture()

      viewModel.importCoordinator.pendingImportRawJson = rawJson
      viewModel.importCoordinator.pendingImportSalt = salt

      viewModel.importCoordinator.decryptAndStageImport("wrong-passphrase")
      testDispatcher.scheduler.advanceUntilIdle()

      // The wrong key fails the AES-GCM authentication check; staged data must
      // be preserved so the user can retry with the correct passphrase.
      assertTrue(
        "Expected Error after failed decrypt, got ${viewModel.operationState.value}",
        viewModel.operationState.value is BackupOperationState.Error
      )
      assertTrue(
        "pendingImportRawJson must be preserved after failed decrypt",
        viewModel.importCoordinator.pendingImportRawJson != null
      )
      assertTrue(
        "pendingImportSalt must be preserved after failed decrypt",
        viewModel.importCoordinator.pendingImportSalt != null
      )
      assertTrue(
        "Dialog must stay open for retry, got ${viewModel.passphraseDialogState.value}",
        viewModel.passphraseDialogState.value is PassphraseDialogState.ImportPassphrase
      )
      val message = context.getString(R.string.passphrase_wrong_or_corrupt)
      assertEquals(
        "Dialog must show the wrong-passphrase error message",
        message,
        (viewModel.passphraseDialogState.value as PassphraseDialogState.ImportPassphrase).errorMessage
      )
      assertEquals(
        "Operation state must show the wrong-passphrase error message",
        message,
        (viewModel.operationState.value as BackupOperationState.Error).message
      )
      assertTrue(
        "isCryptoInProgress must be false after failed decrypt",
        !viewModel.isCryptoInProgress.value
      )
    }

  @Test
  fun decryptAndStageImportCancellationPropagatesNotWrongPassphraseError() =
    runTest {
      val plainJson =
        """
        {
          "version": 1,
          "timestamp": 1710000000000,
          "appVersion": "1.0",
          "transactions": [],
          "loans": [],
          "installments": [],
          "categories": [],
          "accounts": []
        }
        """.trimIndent()
      viewModel.importCoordinator.pendingImportRawJson = plainJson
      viewModel.importCoordinator.pendingImportSalt = "test-salt"

      // A dispatcher with its OWN scheduler (never advanced) keeps the
      // PBKDF2/decrypt step suspended mid-flight so the scope can be cancelled
      // while the coroutine is inside the try block. Note: StandardTestDispatcher()
      // without a scheduler would fall back to Dispatchers.Main's scheduler
      // (set to testDispatcher here), defeating the purpose.
      val blockedDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
      viewModel.importCoordinator.cryptoDispatcher = blockedDispatcher

      viewModel.importCoordinator.decryptAndStageImport("passphrase")
      testDispatcher.scheduler.runCurrent()
      assertTrue(
        "Decrypt must be in flight before cancellation, got isCryptoInProgress=${viewModel.isCryptoInProgress.value}",
        viewModel.isCryptoInProgress.value
      )

      // Cancel the scope while the decrypt is suspended — the cancellation must
      // propagate (CancellationException rethrown), not be swallowed by the
      // generic catch and shown as a wrong-passphrase/corrupt-backup failure.
      viewModel.viewModelScope.cancel()
      blockedDispatcher.scheduler.advanceUntilIdle()
      testDispatcher.scheduler.advanceUntilIdle()

      assertTrue(
        "Cancellation must not surface as a wrong-passphrase error, got ${viewModel.operationState.value}",
        viewModel.operationState.value is BackupOperationState.Idle
      )
      assertTrue(
        "Dialog must show no wrong-passphrase error after cancellation, got ${viewModel.passphraseDialogState.value}",
        viewModel.passphraseDialogState.value is PassphraseDialogState.Hidden
      )
      assertTrue(
        "Staged raw JSON must be preserved on cancellation",
        viewModel.importCoordinator.pendingImportRawJson != null
      )
      assertTrue(
        "Staged salt must be preserved on cancellation",
        viewModel.importCoordinator.pendingImportSalt != null
      )
      assertTrue(
        "isCryptoInProgress must be reset by the finally block after cancellation",
        !viewModel.isCryptoInProgress.value
      )
    }

  @Test
  fun validateAndStageImportEncryptedBackupShowsImportPassphraseDialog() =
    runTest {
      // End-to-end detection: a genuinely encrypted backup read from an input
      // stream must stage the raw JSON + salt and open the passphrase dialog.
      val (rawJson, salt) = encryptedBackupFixture()

      viewModel.importCoordinator.validateAndStageImport(ByteArrayInputStream(rawJson.toByteArray()))
      testDispatcher.scheduler.advanceUntilIdle()

      val dialog = viewModel.passphraseDialogState.value
      assertTrue(
        "Expected ImportPassphrase dialog for encrypted backup, got $dialog",
        dialog is PassphraseDialogState.ImportPassphrase
      )
      assertEquals(
        "Dialog must carry the backup's PBKDF2 salt",
        salt,
        (dialog as PassphraseDialogState.ImportPassphrase).salt
      )
      assertEquals(
        "Raw JSON must be staged for decryption",
        rawJson,
        viewModel.importCoordinator.pendingImportRawJson
      )
      assertEquals(
        "Salt must be staged for decryption",
        salt,
        viewModel.importCoordinator.pendingImportSalt
      )
      assertTrue(
        "Detection must not report an error, got ${viewModel.operationState.value}",
        viewModel.operationState.value is BackupOperationState.Idle
      )
    }

  @Test
  fun validateAndStageImportEncryptedBackupWithoutSaltShowsErrorInsteadOfDialog() =
    runTest {
      // Foreign/hand-edited backup with the encryption marker but no PBKDF2 salt.
      // Detection must reject it with an error: opening the dialog would be a
      // dead-end, because decryptAndStageImport silently hides the dialog when
      // pendingImportSalt is null (no passphrase can ever succeed).
      val rawJson =
        """
        {
          "version": 1,
          "timestamp": 1710000000000,
          "appVersion": "1.0",
          "sensitiveFieldsEncryption": {},
          "transactions": [],
          "loans": [],
          "installments": [],
          "categories": [],
          "accounts": []
        }
        """.trimIndent()

      viewModel.importCoordinator.validateAndStageImport(ByteArrayInputStream(rawJson.toByteArray()))
      testDispatcher.scheduler.advanceUntilIdle()

      val state = viewModel.operationState.value
      assertTrue(
        "Expected Error for salt-less encrypted backup, got $state",
        state is BackupOperationState.Error
      )
      assertEquals(
        "Error must name the incomplete encryption metadata",
        context.getString(R.string.error_backup_encryption_incomplete),
        (state as BackupOperationState.Error).message
      )
      assertTrue(
        "Dialog must NOT open for a salt-less encrypted backup, got ${viewModel.passphraseDialogState.value}",
        viewModel.passphraseDialogState.value is PassphraseDialogState.Hidden
      )
      assertTrue(
        "Staged raw JSON must be cleared, pendingImportRawJson=${viewModel.importCoordinator.pendingImportRawJson}",
        viewModel.importCoordinator.pendingImportRawJson == null
      )
      assertTrue(
        "Staged salt must be cleared, pendingImportSalt=${viewModel.importCoordinator.pendingImportSalt}",
        viewModel.importCoordinator.pendingImportSalt == null
      )
    }

  @Test
  fun cancelPassphraseDialogStopsInFlightDecryptAndDoesNotStageRestore() =
    runTest {
      val (rawJson, salt) = encryptedBackupFixture()
      viewModel.importCoordinator.pendingImportRawJson = rawJson
      viewModel.importCoordinator.pendingImportSalt = salt

      // A crypto dispatcher with its OWN scheduler (never advanced alongside
      // Main) keeps the PBKDF2/decrypt step suspended mid-flight, so the user
      // can cancel while the coroutine is inside the try block.
      val blockedCrypto = StandardTestDispatcher(TestCoroutineScheduler())
      viewModel.importCoordinator.cryptoDispatcher = blockedCrypto

      viewModel.importCoordinator.decryptAndStageImport("secret")
      testDispatcher.scheduler.runCurrent()
      assertTrue(
        "Decrypt must be in flight before cancel, isCryptoInProgress=${viewModel.isCryptoInProgress.value}",
        viewModel.isCryptoInProgress.value
      )

      // Cancel while decrypting — must stop the whole decrypt job, not just hide
      // the dialog, so no restore is staged after the user believes they cancelled.
      viewModel.importCoordinator.cancelPassphraseDialog()

      blockedCrypto.scheduler.advanceUntilIdle()
      testDispatcher.scheduler.advanceUntilIdle()

      assertTrue(
        "No restore may be staged after cancel, pending=${viewModel.pendingRestoreBackup.value}",
        viewModel.pendingRestoreBackup.value == null
      )
      assertTrue(
        "isCryptoInProgress must be reset after cancel",
        !viewModel.isCryptoInProgress.value
      )
      assertTrue(
        "Dialog must remain hidden after cancel, got ${viewModel.passphraseDialogState.value}",
        viewModel.passphraseDialogState.value is PassphraseDialogState.Hidden
      )
    }

  @Test
  fun validateAndImportIsDroppedWhileRestoreAlreadyImporting() =
    runTest {
      viewModel.operationState.value = BackupOperationState.Importing
      var readAttempted = false
      val explodingStream =
        object : InputStream() {
          override fun read(): Int {
            readAttempted = true
            throw IOException("stream must not be read when a restore is already importing")
          }
        }

      viewModel.importCoordinator.validateAndStageImport(explodingStream)
      testDispatcher.scheduler.advanceUntilIdle()

      assertFalse(
        "Duplicate submission must be dropped before reading the stream",
        readAttempted
      )
      assertTrue(
        "operationState must remain Importing, got ${viewModel.operationState.value}",
        viewModel.operationState.value is BackupOperationState.Importing
      )
    }

  @Test
  fun executeRestoreIgnoresDuplicateSubmission() =
    runTest {
      val (rawJson, salt) = encryptedBackupFixture()
      viewModel.importCoordinator.pendingImportRawJson = rawJson
      viewModel.importCoordinator.pendingImportSalt = salt
      viewModel.importCoordinator.decryptAndStageImport("secret")
      testDispatcher.scheduler.advanceUntilIdle()
      assertTrue(
        "Backup must be staged for restore, pending=${viewModel.pendingRestoreBackup.value}",
        viewModel.pendingRestoreBackup.value != null
      )

      // Two rapid submissions (e.g. a double-tap on the restore-confirm button).
      fakeRepo.executeRestoreCount = 0
      viewModel.importCoordinator.executeRestore()
      viewModel.importCoordinator.executeRestore()
      testDispatcher.scheduler.advanceUntilIdle()

      assertEquals("Only one restore may execute on duplicate tap", 1, fakeRepo.executeRestoreCount)
      assertTrue(
        "Restore must report success, got ${viewModel.operationState.value}",
        viewModel.operationState.value is BackupOperationState.ImportSuccess
      )
    }

  /** Records whether close() ran, so tests can assert stream lifecycle on error paths. */
  private class CloseTrackingOutputStream : OutputStream() {
    private val delegate = ByteArrayOutputStream()

    var isClosed = false
      private set

    override fun write(b: Int) {
      delegate.write(b)
    }

    override fun close() {
      isClosed = true
      delegate.close()
    }
  }
}
