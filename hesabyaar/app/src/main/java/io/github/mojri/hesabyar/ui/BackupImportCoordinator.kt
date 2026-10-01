package io.github.mojri.hesabyar.ui

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.BackupPayload
import io.github.mojri.hesabyar.data.BackupSettings
import io.github.mojri.hesabyar.data.BackupValidationResult
import io.github.mojri.hesabyar.data.RestoreMode
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream

/**
 * Owns the import/restore half of the backup UI flow: reading and validating
 * the backup file, the import passphrase dialog, and executing the restore.
 * Shared operation/dialog state is passed in by [BackupViewModel], which stays
 * the single owner of cross-flow state.
 */
class BackupImportCoordinator(
  private val application: Context,
  private val manageBackupUseCase: ManageBackupUseCase,
  private val scope: CoroutineScope,
  private val operationState: MutableState<BackupOperationState>,
  private val passphraseDialogState: MutableState<PassphraseDialogState>,
  private val isCryptoInProgress: MutableState<Boolean>
) {
  @VisibleForTesting
  internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

  /**
   * Dispatcher for CPU-intensive PBKDF2 key derivation (600k iterations) —
   * CPU-bound work belongs on [Dispatchers.Default], not the I/O pool.
   */
  @VisibleForTesting
  internal var cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default

  /**
   * Tracks the in-flight decrypt coroutine so [cancelPassphraseDialog] can stop
   * it. Without this, cancelling the dialog only cleared UI state while the
   * launched job kept running and could still stage/error after the user
   * believed the import was cancelled. Nulled on completion.
   */
  @VisibleForTesting
  internal var decryptJob: Job? = null

  val pendingRestoreBackup = mutableStateOf<BackupPayload?>(null)
  val selectedRestoreMode = mutableStateOf(RestoreMode.REPLACE)

  @VisibleForTesting
  internal var pendingImportRawJson: String? = null

  @VisibleForTesting
  internal var pendingImportSalt: String? = null

  /**
   * In-flight guard shared by the import and restore entry points: drops a
   * submission while an import/restore is running, while a staging pass is still
   * reading/parsing, while a PBKDF2 decrypt is in flight (isCryptoInProgress),
   * or while an export is in flight (Exporting is busy, not available). The
   * check and the Importing set are both synchronous on the Main thread, so a
   * concurrent call sees the busy state immediately — no check-then-set race.
   * Returns true when the caller must drop the submission.
   */
  private fun claimImportState(): Boolean {
    if (operationState.value is BackupOperationState.Importing ||
      operationState.value is BackupOperationState.Exporting ||
      isCryptoInProgress.value
    ) {
      return true
    }
    operationState.value = BackupOperationState.Importing
    return false
  }

  /**
   * Reads the backup file, parses it, and checks for encrypted fields.
   * If encrypted, stages the raw JSON and shows the import passphrase dialog.
   * If not encrypted, proceeds directly to validation.
   */
  fun validateAndStageImport(inputStream: InputStream) {
    if (claimImportState()) return
    scope.launch {
      try {
        val (text, rootJson) =
          withContext(ioDispatcher) {
            val raw = inputStream.bufferedReader().use { it.readText() }
            val parsed =
              try {
                JSONObject(raw)
              } catch (_: JSONException) {
                null
              }
            raw to parsed
          }

        if (rootJson != null && ManageBackupUseCase.isEncryptedBackup(rootJson)) {
          // Encrypted backup — pause and ask for passphrase
          val salt = ManageBackupUseCase.getEncryptionSalt(rootJson)
          if (salt == null) {
            // Encryption marker present but no PBKDF2 salt (foreign or hand-edited
            // backup): reject at detection. Opening the dialog with an empty-salt
            // fallback would be a dead-end — decryptAndStageImport silently hides
            // the dialog when pendingImportSalt is null, with no error surfaced.
            pendingImportRawJson = null
            pendingImportSalt = null
            operationState.value =
              BackupOperationState.Error(
                application.getString(R.string.error_backup_encryption_incomplete)
              )
            return@launch
          }
          pendingImportRawJson = text
          pendingImportSalt = salt
          passphraseDialogState.value = PassphraseDialogState.ImportPassphrase(salt)
          return@launch
        }

        // Plaintext backup — proceed directly
        val backup = parseBackupOrReportError(text) ?: return@launch
        stageValidatedBackup(backup)
      } catch (e: IOException) {
        operationState.value =
          BackupOperationState.Error(
            application.getString(
              R.string.error_reading_backup_file,
              e.localizedMessage ?: application.getString(R.string.error_unknown)
            )
          )
      } catch (e: JSONException) {
        operationState.value =
          BackupOperationState.Error(
            application.getString(
              R.string.error_parsing_backup_file,
              e.localizedMessage ?: application.getString(R.string.error_unknown)
            )
          )
      } finally {
        // Release the busy state unless a terminal state (Error /
        // ValidationFailed) already replaced it — a later flow may set a new one.
        if (operationState.value is BackupOperationState.Importing) {
          operationState.value = BackupOperationState.Idle
        }
      }
    }
  }

  /**
   * Called from the import passphrase dialog.
   * Derives the key and decrypts the backup on [cryptoDispatcher].
   * Uses a single generic error message since wrong-passphrase and tampered-file
   * are cryptographically indistinguishable.
   */
  @Suppress("TooGenericExceptionCaught") // CancellationException is rethrown first for structured cancellation
  fun decryptAndStageImport(passphrase: String) {
    val rawJson = pendingImportRawJson
    val salt = pendingImportSalt
    if (rawJson == null || salt == null) {
      passphraseDialogState.value = PassphraseDialogState.Hidden
      return
    }

    // Cancel any prior in-flight decrypt before launching a new attempt so only
    // one job can ever write dialog/operation/pending state.
    decryptJob?.cancel()
    val job =
      scope.launch {
        isCryptoInProgress.value = true
        try {
          val parsed =
            parseBackupOrReportError(rawJson) ?: run {
              // Structural parse error — clear staged data and dismiss dialog
              pendingImportRawJson = null
              pendingImportSalt = null
              passphraseDialogState.value = PassphraseDialogState.Hidden
              return@launch
            }
          val rootJson = JSONObject(rawJson)
          val decrypted =
            manageBackupUseCase.decryptBackupWithPassphrase(parsed, rootJson, passphrase, cryptoDispatcher)
          // Success — clear staged data and dismiss dialog
          pendingImportRawJson = null
          pendingImportSalt = null
          passphraseDialogState.value = PassphraseDialogState.Hidden
          stageValidatedBackup(decrypted)
        } catch (e: CancellationException) {
          // Coroutine cancelled (e.g. ViewModel cleared or dialog cancelled) —
          // propagate, do not surface it as a wrong-passphrase/corrupt-backup failure.
          throw e
        } catch (_: Exception) {
          // Wrong passphrase or tampered ciphertext — keep staged data for retry
          passphraseDialogState.value =
            PassphraseDialogState.ImportPassphrase(
              salt,
              application.getString(R.string.passphrase_wrong_or_corrupt)
            )
          operationState.value =
            BackupOperationState.Error(application.getString(R.string.passphrase_wrong_or_corrupt))
        } finally {
          // Only clear state if this coroutine's job is still the tracked one —
          // a newer decryptAndStageImport call may have cancelled and replaced it
          // mid-flight. Clearing unconditionally would reset isCryptoInProgress
          // (or drop the job reference) for the newer job that is still running.
          if (decryptJob === coroutineContext[Job]) {
            isCryptoInProgress.value = false
            decryptJob = null
          }
        }
      }
    decryptJob = job
  }

  /**
   * Cancel the passphrase dialog, stop any in-flight decrypt, and clean up any
   * staged import data.
   */
  fun cancelPassphraseDialog() {
    decryptJob?.cancel()
    decryptJob = null
    // The cancelled job's finally skips its cleanup because decryptJob no longer
    // points at it (ownership check), so the cancel path clears the flag itself.
    isCryptoInProgress.value = false
    passphraseDialogState.value = PassphraseDialogState.Hidden
    pendingImportRawJson = null
    pendingImportSalt = null
  }

  private suspend fun stageValidatedBackup(backup: BackupPayload) {
    val result = manageBackupUseCase.validateBackup(backup)
    when (result) {
      is BackupValidationResult.Invalid -> {
        operationState.value = BackupOperationState.ValidationFailed(result.errors)
      }
      is BackupValidationResult.Valid -> {
        pendingRestoreBackup.value = backup
      }
    }
  }

  // --- Restore execution ---

  // Safety-net: catch general Exception (e.g. Room/SQLite write failure) → Error state;
  // CancellationException is rethrown first. Placed on the enclosing function per convention.
  @Suppress("TooGenericExceptionCaught")
  fun executeRestore() {
    // Check the pending backup first — if null, bail out before claiming
    // Importing state so the guard does not get stuck (see finally below).
    val backup = pendingRestoreBackup.value ?: return
    val mode = selectedRestoreMode.value
    // claimImportState handles the in-flight guard (Importing/Exporting/isCryptoInProgress)
    // and atomically sets Importing before any launching.
    if (claimImportState()) return
    scope.launch {
      try {
        manageBackupUseCase.executeRestore(backup, mode)
        applySettings(backup.settings)
        operationState.value =
          BackupOperationState.ImportSuccess(
            when (mode) {
              RestoreMode.REPLACE ->
                application.getString(
                  R.string.restore_success_replace,
                  backup.transactions.size,
                  backup.loans.size,
                  backup.installments.size,
                  backup.categories.size,
                  backup.bankLoans.size,
                  backup.accounts.size
                )
              RestoreMode.MERGE -> application.getString(R.string.restore_success_merge)
            }
          )
        pendingRestoreBackup.value = null
      } catch (e: CancellationException) {
        // Coroutine cancelled (e.g. ViewModel cleared) — propagate, do not surface
        // it as a restore failure (matches decryptAndStageImport convention).
        throw e
      } catch (e: Exception) {
        operationState.value = restoreErrorState(e)
      } finally {
        // Release the busy state unless a terminal state (Error /
        // ImportSuccess) already replaced it — a cancelled coroutine must not
        // leave operationState stuck in Importing, or the in-flight guard
        // blocks any retry.
        if (operationState.value is BackupOperationState.Importing) {
          operationState.value = BackupOperationState.Idle
        }
      }
    }
  }

  /**
   * Maps an exception thrown during restore execution to the user-facing Error
   * state. The `when` mirrors the specific-catches-first convention of
   * [executeRestore]: dedicated messages for the known failure types, with the
   * generic branch as the safety-net for anything else (e.g. a Room/SQLite
   * write failure).
   */
  private fun restoreErrorState(e: Exception): BackupOperationState.Error =
    BackupOperationState.Error(
      when (e) {
        is IOException ->
          application.getString(
            R.string.error_backup_file_access,
            e.localizedMessage ?: application.getString(R.string.error_io)
          )
        is SecurityException ->
          application.getString(
            R.string.error_backup_file_access_denied,
            e.localizedMessage ?: application.getString(R.string.error_unspecified)
          )
        is IllegalArgumentException ->
          application.getString(
            R.string.error_backup_invalid_settings,
            e.localizedMessage
              ?: application.getString(R.string.error_unspecified)
          )
        else ->
          application.getString(
            R.string.error_restore_generic,
            e.localizedMessage ?: application.getString(R.string.error_unspecified)
          )
      }
    )

  private fun applySettings(settings: BackupSettings) {
    application
      .getSharedPreferences("hesabyar_prefs", Context.MODE_PRIVATE)
      .edit()
      .putBoolean("dark_mode", settings.darkMode)
      .apply()
  }

  private fun reportInvalidBackupParse() {
    pendingRestoreBackup.value = null
    operationState.value =
      BackupOperationState.Error(
        application.getString(R.string.error_invalid_backup_structure)
      )
  }

  private suspend fun parseBackupOrReportError(text: String): BackupPayload? {
    val backup = manageBackupUseCase.parseBackupJson(text)
    if (backup == null) reportInvalidBackupParse()
    return backup
  }

  fun cancelPendingRestore() {
    pendingRestoreBackup.value = null
  }
}
