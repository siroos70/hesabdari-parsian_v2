package io.github.mojri.hesabyar.ui

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.domain.usecase.GetSettingsUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream

/**
 * Owns the export half of the backup UI flow: the passphrase dialog, staging
 * the encrypted/plaintext JSON, and the one-shot SAF picker launch signal.
 * Shared operation/dialog state is passed in by [BackupViewModel], which stays
 * the single owner of cross-flow state.
 */
class BackupExportCoordinator(
  private val application: Context,
  private val manageBackupUseCase: ManageBackupUseCase,
  private val scope: CoroutineScope,
  private val operationState: MutableState<BackupOperationState>,
  private val passphraseDialogState: MutableState<PassphraseDialogState>,
  private val isCryptoInProgress: MutableState<Boolean>,
  private val settingsUseCase: GetSettingsUseCase
) {
  @VisibleForTesting
  internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

  /**
   * Dispatcher for CPU-intensive PBKDF2 key derivation (600k iterations) —
   * CPU-bound work belongs on [Dispatchers.Default], not the I/O pool.
   */
  @VisibleForTesting
  internal var cryptoDispatcher: CoroutineDispatcher = Dispatchers.Default

  private var pendingExportJsonText: String? = null

  /**
   * One-shot signal raised once staging completes so the screen launches the
   * SAF save-location picker from a LaunchedEffect instead of inline in the
   * click handler. The old inline launch raced the async PBKDF2/encryption —
   * the picker callback could run while [pendingExportJsonText] was still null,
   * and the picker opened even when staging would fail.
   */
  val exportPickerLaunchRequest = mutableStateOf(false)

  /**
   * Called from SettingsScreen after export button tap.
   * Shows the export passphrase dialog.
   */
  fun requestExportPassphraseDialog() {
    passphraseDialogState.value = PassphraseDialogState.ExportPassphrase
  }

  /**
   * Called from the export passphrase dialog with the user-entered passphrase.
   * Runs PBKDF2 derivation + encryption on [cryptoDispatcher], stages the JSON,
   * closes the dialog, and raises [exportPickerLaunchRequest] so the screen
   * launches the picker only after staging fully succeeded. The dialog stays
   * visible (with its progress indicator) while crypto is in flight, matching
   * the import flow; on failure it remains open so the user can retry.
   */
  @Suppress("TooGenericExceptionCaught") // Safety net: PBKDF2/Cipher can throw unchecked RuntimeException
  fun exportWithPassphrase(passphrase: String) {
    scope.launch {
      isCryptoInProgress.value = true
      exportPickerLaunchRequest.value = false
      try {
        val rootJson =
          withContext(cryptoDispatcher) {
            manageBackupUseCase.exportBackupJson(isDarkMode = settingsUseCase.isDarkMode(), passphrase = passphrase)
          }
        pendingExportJsonText = rootJson.toString(2)
        passphraseDialogState.value = PassphraseDialogState.Hidden
        operationState.value = BackupOperationState.Exporting
        exportPickerLaunchRequest.value = true
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Safety net: PBKDF2/Cipher can throw unchecked RuntimeException (e.g. NPE from
        // provider failures). All crypto errors surface as a single user-facing message.
        operationState.value =
          BackupOperationState.Error(
            application.getString(
              R.string.error_encrypting_backup,
              e.localizedMessage ?: application.getString(R.string.error_unknown)
            )
          )
      } finally {
        isCryptoInProgress.value = false
      }
    }
  }

  /**
   * Called from the export passphrase dialog when the user skips encryption.
   * Exports without a passphrase (plaintext sensitive fields).
   */
  @Suppress("TooGenericExceptionCaught") // Safety net: repository/JSON operations can throw unchecked exceptions
  fun exportWithoutPassphrase() {
    // In-flight guard: drop a duplicate submission (e.g. a double-tap on "save
    // without encryption"). Setting isCryptoInProgress synchronously before the
    // launch makes the check-and-set atomic on the single-threaded Main
    // dispatcher, mirroring executeRestore's Importing guard.
    if (isCryptoInProgress.value) return
    isCryptoInProgress.value = true
    scope.launch {
      exportPickerLaunchRequest.value = false
      passphraseDialogState.value = PassphraseDialogState.Hidden
      try {
        val rootJson =
          withContext(cryptoDispatcher) {
            manageBackupUseCase.exportBackupJson(isDarkMode = settingsUseCase.isDarkMode())
          }
        pendingExportJsonText = rootJson.toString(2)
        operationState.value = BackupOperationState.Exporting
        exportPickerLaunchRequest.value = true
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Safety net: repository/JSON operations can throw unchecked exceptions.
        operationState.value =
          BackupOperationState.Error(
            application.getString(
              R.string.error_preparing_backup,
              e.localizedMessage ?: application.getString(R.string.error_unknown)
            )
          )
      } finally {
        isCryptoInProgress.value = false
      }
    }
  }

  /**
   * Writes the staged export JSON to the output stream.
   * Called by SettingsScreen after the file picker returns a URI.
   * Nulls out [pendingExportJsonText] immediately after write (success or failure)
   * to avoid keeping plaintext-adjacent sensitive data in ViewModel state.
   */
  fun writeStagedExportToFile(outputStream: OutputStream) {
    val json = pendingExportJsonText
    if (json == null) {
      try {
        outputStream.close()
      } catch (_: IOException) {
        // Best-effort close: nothing to write, but the SAF stream the caller
        // handed over must still be released rather than leaked.
      }
      operationState.value = BackupOperationState.Error(application.getString(R.string.error_backup_data_not_ready))
      return
    }
    scope.launch {
      try {
        val root =
          withContext(ioDispatcher) {
            outputStream.use { os -> os.write(json.toByteArray()) }
            try {
              JSONObject(json)
            } catch (_: JSONException) {
              null
            }
          }
        val summary = buildExportSummary(root)
        operationState.value = BackupOperationState.ExportSuccess(summary)
      } catch (e: IOException) {
        operationState.value =
          BackupOperationState.Error(
            application.getString(
              R.string.error_saving_backup,
              e.localizedMessage ?: application.getString(R.string.error_io)
            )
          )
      } finally {
        pendingExportJsonText = null
      }
    }
  }

  @VisibleForTesting
  internal fun buildExportSummary(root: JSONObject?): String {
    if (root == null) return application.getString(R.string.backup_saved_success)
    val txCount = root.optJSONArray("transactions")?.length() ?: 0
    val loanCount = root.optJSONArray("loans")?.length() ?: 0
    val instCount = root.optJSONArray("installments")?.length() ?: 0
    val catCount = root.optJSONArray("categories")?.length() ?: 0
    val accountCount = root.optJSONArray("accounts")?.length() ?: 0
    val bankLoanCount = root.optJSONArray("bankLoans")?.length() ?: 0
    return application.getString(
      R.string.export_summary_counts,
      txCount,
      loanCount,
      instCount,
      catCount,
      bankLoanCount,
      accountCount
    )
  }

  /**
   * Called when the user cancels the export file picker (SAF).
   * Clears the staged export JSON and resets [operationState] to [BackupOperationState.Idle]
   * so the export button is re-enabled. Cancellation is a normal user action, not an error.
   */
  fun onExportPickerCancelled() {
    pendingExportJsonText = null
    exportPickerLaunchRequest.value = false
    operationState.value = BackupOperationState.Idle
  }

  /**
   * Resets [exportPickerLaunchRequest] once the screen has launched the picker.
   */
  fun consumeExportPickerLaunchRequest() {
    exportPickerLaunchRequest.value = false
  }
}
