package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

/**
 * Passphrase entry dialog shown when an imported backup contains encrypted
 * fields. Passphrase and edit-state are local to this composable: after a
 * failed decrypt attempt the inline error stays visible until the user starts
 * editing the passphrase again, while a resubmission always surfaces the
 * fresh attempt's outcome instead of hiding it behind the stale edit flag.
 */
@Composable
fun ImportPassphraseDialog(
  errorMessage: String?,
  isCryptoInProgress: Boolean,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var passphrase by remember { mutableStateOf("") }
  // Suppresses the inline error while the user edits the passphrase after a
  // failed decrypt attempt. Reset on resubmit so a freshly-arrived error from
  // the ViewModel is not hidden by the stale edit flag.
  var userEditedSinceError by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.passphrase_import_title), fontWeight = FontWeight.Bold) },
    text = {
      PassphraseForm(
        passphrase = passphrase,
        onPassphraseChange = {
          passphrase = it
          userEditedSinceError = true
        },
        errorMessage = errorMessage,
        showError = !userEditedSinceError,
        isCryptoInProgress = isCryptoInProgress
      )
    },
    confirmButton = {
      HesabyarButton(
        onClick = {
          // A resubmission starts a fresh attempt, so show its outcome rather
          // than suppressing a new error with the stale edit flag.
          userEditedSinceError = false
          onConfirm(passphrase)
        },
        text = stringResource(R.string.passphrase_decrypt),
        enabled = passphrase.isNotEmpty() && !isCryptoInProgress
      )
    },
    dismissButton = {
      HesabyarButton(
        onClick = onDismiss,
        text = stringResource(R.string.cancel_label),
        variant = ButtonVariant.Text
      )
    }
  )
}

/**
 * Passphrase input column for [ImportPassphraseDialog]. The field is gated
 * while a decrypt attempt is in flight — editing mid-decrypt would otherwise
 * present the failed attempt's error against text the user has since changed.
 */
@Composable
private fun PassphraseForm(
  passphrase: String,
  onPassphraseChange: (String) -> Unit,
  errorMessage: String?,
  showError: Boolean,
  isCryptoInProgress: Boolean
) {
  val decryptInProgressLabel = stringResource(R.string.decrypt_in_progress_label)
  Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
    Text(
      text = stringResource(R.string.passphrase_import_body),
      style = MaterialTheme.typography.bodyMedium
    )
    OutlinedTextField(
      value = passphrase,
      onValueChange = onPassphraseChange,
      label = { Text(stringResource(R.string.passphrase_label)) },
      visualTransformation = PasswordVisualTransformation(),
      singleLine = true,
      enabled = !isCryptoInProgress,
      isError = errorMessage != null && showError,
      supportingText =
        if (errorMessage != null && showError) {
          {
            Text(
              text = errorMessage,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error
            )
          }
        } else {
          null
        },
      modifier = Modifier.fillMaxWidth().testTag("passphrase_field")
    )
    if (isCryptoInProgress) {
      LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = decryptInProgressLabel }
      )
    }
  }
}
