package io.github.mojri.hesabyar.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.mojri.hesabyar.ui.components.ExportPassphraseDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [ExportPassphraseDialog]: the confirm button must stay
 * disabled until both passphrase fields match and no crypto work is in flight,
 * and a mismatched confirmation must show an inline error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ExportPassphraseDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val confirmLabel = "رمزگذاری و ذخیره"
  private val mismatchError = "رمز عبور مطابقت ندارد"

  private fun launchDialog(
    isCryptoInProgress: Boolean = false,
    onConfirm: (String) -> Unit = {},
    onSaveWithoutEncryption: () -> Unit = {},
    onDismiss: () -> Unit = {}
  ) {
    composeRule.setContent {
      ExportPassphraseDialog(
        isCryptoInProgress = isCryptoInProgress,
        onConfirm = onConfirm,
        onSaveWithoutEncryption = onSaveWithoutEncryption,
        onDismiss = onDismiss
      )
    }
  }

  private fun typePassphrase(text: String) {
    composeRule.onNodeWithText("رمز عبور").performTextInput(text)
  }

  private fun typeConfirmation(text: String) {
    composeRule.onNodeWithText("تأیید رمز عبور").performTextInput(text)
  }

  @Test
  fun confirmButtonEnabledOnlyWhenPasswordsMatch() {
    launchDialog()

    // Empty fields — disabled
    composeRule.onNodeWithText(confirmLabel).assertIsNotEnabled()

    // Only the passphrase filled — still disabled
    typePassphrase("secret-passphrase")
    composeRule.onNodeWithText(confirmLabel).assertIsNotEnabled()

    // Matching confirmation — enabled
    typeConfirmation("secret-passphrase")
    composeRule.onNodeWithText(confirmLabel).assertIsEnabled()

    // Confirmation diverges again — disabled
    composeRule.onNodeWithText("تأیید رمز عبور").performTextClearance()
    typeConfirmation("different-passphrase")
    composeRule.onNodeWithText(confirmLabel).assertIsNotEnabled()
  }

  @Test
  fun mismatchErrorShownWhileConfirmationDiffers() {
    launchDialog()

    typePassphrase("secret-passphrase")
    typeConfirmation("different-passphrase")
    composeRule.onNodeWithText(mismatchError).assertIsDisplayed()

    composeRule.onNodeWithText("تأیید رمز عبور").performTextClearance()
    typeConfirmation("secret-passphrase")
    composeRule.onNodeWithText(mismatchError).assertDoesNotExist()
  }

  @Test
  fun confirmInvokesCallbackWithEnteredPassphrase() {
    var confirmedPassphrase: String? = null
    launchDialog(onConfirm = { confirmedPassphrase = it })

    typePassphrase("secret-passphrase")
    typeConfirmation("secret-passphrase")
    composeRule.onNodeWithText(confirmLabel).performClick()

    assertEquals("Confirm must pass the entered passphrase", "secret-passphrase", confirmedPassphrase)
  }

  @Test
  fun saveWithoutEncryptionInvokesCallback() {
    var plaintextSaveRequested = false
    launchDialog(onSaveWithoutEncryption = { plaintextSaveRequested = true })

    composeRule.onNodeWithText("ذخیره بدون رمز").performClick()

    assertTrue("Save-without-encryption button must request a plaintext export", plaintextSaveRequested)
  }

  @Test
  fun cancelInvokesDismissCallback() {
    var dismissed = false
    launchDialog(onDismiss = { dismissed = true })

    composeRule.onNodeWithText("انصراف").performClick()

    assertTrue("Cancel button must dismiss the dialog", dismissed)
  }

  @Test
  fun confirmDisabledWhileCryptoInProgress() {
    launchDialog(isCryptoInProgress = true)

    typePassphrase("secret-passphrase")
    typeConfirmation("secret-passphrase")

    composeRule.onNodeWithText(confirmLabel).assertIsNotEnabled()
  }

  @Test
  fun saveWithoutEncryptionDisabledWhileCryptoInProgress() {
    launchDialog(isCryptoInProgress = true)

    // The save-without-encryption button must not be clickable while crypto runs,
    // otherwise a user could bypass encryption while a key-derivation job is active.
    composeRule.onNodeWithText("ذخیره بدون رمز").assertIsNotEnabled()
  }

  @Test
  fun cancelDisabledWhileCryptoInProgress() {
    launchDialog(isCryptoInProgress = true)

    // The dialog must not be dismissible while crypto runs — otherwise the export
    // job would continue and launch the save picker after the dialog is gone.
    composeRule.onNodeWithText("انصراف").assertIsNotEnabled()
  }
}
