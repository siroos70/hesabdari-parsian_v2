package io.github.mojri.hesabyar.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mojri.hesabyar.domain.usecase.GetSettingsUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageBackupUseCase
import javax.inject.Inject

/** UI state for the passphrase dialog shown during export or import. */
sealed class PassphraseDialogState {
  data object Hidden : PassphraseDialogState()

  /** Export dialog: user can enter a passphrase or skip (plaintext export). */
  data object ExportPassphrase : PassphraseDialogState()

  /** Import dialog: backup contains encrypted fields, user must enter passphrase. */
  data class ImportPassphrase(
    val salt: String,
    val errorMessage: String? = null
  ) : PassphraseDialogState()
}

/**
 * Orchestrates the backup UI flows. The export half lives in
 * [BackupExportCoordinator] and the import/restore half in
 * [BackupImportCoordinator]; this class owns the cross-flow state
 * ([operationState], [passphraseDialogState], [isCryptoInProgress]) and
 * re-exposes the coordinators' remaining state fields so SettingsScreen keeps
 * reading them off the ViewModel.
 */
@HiltViewModel
class BackupViewModel
  @Inject
  constructor(
    @ApplicationContext application: Context,
    manageBackupUseCase: ManageBackupUseCase,
    getSettingsUseCase: GetSettingsUseCase
  ) : ViewModel() {
    val operationState = mutableStateOf<BackupOperationState>(BackupOperationState.Idle)

    /** Current passphrase dialog state, shared by the import and export flows. */
    val passphraseDialogState = mutableStateOf<PassphraseDialogState>(PassphraseDialogState.Hidden)

    /** True while PBKDF2 derivation + encrypt/decrypt is running (shows loading spinner). */
    val isCryptoInProgress = mutableStateOf(false)

    /** Import flow: passphrase dialog, validation, restore execution. */
    val importCoordinator =
      BackupImportCoordinator(
        application = application,
        manageBackupUseCase = manageBackupUseCase,
        scope = viewModelScope,
        operationState = operationState,
        passphraseDialogState = passphraseDialogState,
        isCryptoInProgress = isCryptoInProgress
      )

    /** Export flow: passphrase dialog, staging, SAF picker launch signal. */
    val exportCoordinator =
      BackupExportCoordinator(
        application = application,
        manageBackupUseCase = manageBackupUseCase,
        scope = viewModelScope,
        operationState = operationState,
        passphraseDialogState = passphraseDialogState,
        isCryptoInProgress = isCryptoInProgress,
        settingsUseCase = getSettingsUseCase
      )

    // State re-exposed from the coordinators so existing `by` reads on the
    // ViewModel keep working without call-site changes.

    /** One-shot signal raised once staging completes so the screen launches the SAF picker. */
    val exportPickerLaunchRequest get() = exportCoordinator.exportPickerLaunchRequest

    /** Backup validated and awaiting restore mode confirmation. */
    val pendingRestoreBackup get() = importCoordinator.pendingRestoreBackup

    /** Restore mode selected in the confirm-restore dialog (REPLACE or MERGE). */
    val selectedRestoreMode get() = importCoordinator.selectedRestoreMode

    fun clearOperationState() {
      operationState.value = BackupOperationState.Idle
    }
  }
