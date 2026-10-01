package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

/**
 * Passphrase entry dialog shown before an encrypted export. The user must enter
 * and confirm a passphrase; the confirm button stays disabled until both fields
 * match and no crypto work is in flight. The export can be skipped entirely
 * with a plaintext (unencrypted) save.
 */
@Composable
fun ExportPassphraseDialog(
  isCryptoInProgress: Boolean,
  onConfirm: (String) -> Unit,
  onSaveWithoutEncryption: () -> Unit,
  onDismiss: () -> Unit
) {
  var passphrase by remember { mutableStateOf("") }
  var confirmPassphrase by remember { mutableStateOf("") }
  val passwordsMatch = passphrase.isNotEmpty() && passphrase == confirmPassphrase
  val canConfirm = passwordsMatch && !isCryptoInProgress

  AlertDialog(
    onDismissRequest = { if (!isCryptoInProgress) onDismiss() },
    title = { Text(stringResource(R.string.passphrase_export_title), fontWeight = FontWeight.Bold) },
    text = {
      ExportPassphraseContent(
        passphrase = passphrase,
        onPassphraseChange = { passphrase = it },
        confirmPassphrase = confirmPassphrase,
        onConfirmPassphraseChange = { confirmPassphrase = it },
        isCryptoInProgress = isCryptoInProgress
      )
    },
    confirmButton = {
      HesabyarButton(
        onClick = { onConfirm(passphrase) },
        text = stringResource(R.string.passphrase_encrypt_and_save),
        enabled = canConfirm
      )
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
        HesabyarButton(
          onClick = onSaveWithoutEncryption,
          text = stringResource(R.string.passphrase_save_without_encryption),
          variant = ButtonVariant.Outlined,
          enabled = !isCryptoInProgress
        )
        HesabyarButton(
          onClick = onDismiss,
          text = stringResource(R.string.cancel_label),
          variant = ButtonVariant.Text,
          enabled = !isCryptoInProgress
        )
      }
    }
  )
}

@Composable
private fun ExportPassphraseContent(
  passphrase: String,
  onPassphraseChange: (String) -> Unit,
  confirmPassphrase: String,
  onConfirmPassphraseChange: (String) -> Unit,
  isCryptoInProgress: Boolean
) {
  val passwordsMatch = passphrase.isNotEmpty() && passphrase == confirmPassphrase
  Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
    Text(
      text = stringResource(R.string.passphrase_export_warning),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
      fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(SpacingTokens.xs))
    OutlinedTextField(
      value = passphrase,
      onValueChange = onPassphraseChange,
      label = { Text(stringResource(R.string.passphrase_label)) },
      visualTransformation = PasswordVisualTransformation(),
      singleLine = true,
      modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
      value = confirmPassphrase,
      onValueChange = onConfirmPassphraseChange,
      label = { Text(stringResource(R.string.passphrase_confirm_label)) },
      visualTransformation = PasswordVisualTransformation(),
      singleLine = true,
      isError = confirmPassphrase.isNotEmpty() && !passwordsMatch,
      supportingText =
        if (confirmPassphrase.isNotEmpty() && !passwordsMatch) {
          { Text(stringResource(R.string.passphrase_mismatch_error)) }
        } else {
          null
        },
      modifier = Modifier.fillMaxWidth()
    )
    if (isCryptoInProgress) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
  }
}
