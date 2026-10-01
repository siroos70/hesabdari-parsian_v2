package io.github.mojri.hesabyar.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.mojri.hesabyar.ui.components.ImportPassphraseDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [ImportPassphraseDialog]: the inline error from a
 * failed decrypt attempt must clear as soon as the user starts editing the
 * passphrase, while a resubmission must still surface the fresh attempt's
 * outcome instead of hiding it behind the stale edit flag.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ImportPassphraseDialogTest {
  @get:Rule
  val composeRule = createComposeRule()

  private val errorMessage = "رمز عبور اشتباه است یا فایل بکاپ خراب است"

  private fun launchDialog(onConfirm: (String) -> Unit = {}) {
    composeRule.setContent {
      ImportPassphraseDialog(
        errorMessage = errorMessage,
        isCryptoInProgress = false,
        onConfirm = onConfirm,
        onDismiss = {}
      )
    }
  }

  @Test
  fun errorVisibleUntilUserStartsTyping() {
    launchDialog()

    // The failed attempt's inline error is shown...
    composeRule.onNodeWithText(errorMessage).assertIsDisplayed()

    // ...and disappears as soon as the user edits the passphrase.
    composeRule.onNode(hasSetTextAction()).performTextInput("corrected-passphrase")
    composeRule.onNodeWithText(errorMessage).assertDoesNotExist()
  }

  @Test
  fun resubmitSurfacesFreshErrorInsteadOfSuppressingIt() {
    launchDialog()

    // After a failed attempt the user edits the passphrase, which hides the
    // stale error...
    composeRule.onNode(hasSetTextAction()).performTextInput("corrected-passphrase")
    composeRule.onNodeWithText(errorMessage).assertDoesNotExist()

    // ...then resubmits. The fresh attempt's outcome must be visible again,
    // not suppressed by the stale edit flag.
    composeRule.onNodeWithText("رمزگشایی").performClick()
    composeRule.onNodeWithText(errorMessage).assertIsDisplayed()
  }

  @Test
  fun confirmButtonEnabledOnlyAfterTypingPassphrase() {
    launchDialog()

    composeRule.onNodeWithText("رمزگشایی").assertIsNotEnabled()

    composeRule.onNode(hasSetTextAction()).performTextInput("some-passphrase")
    composeRule.onNodeWithText("رمزگشایی").assertIsEnabled()
  }

  @Test
  fun passphraseFieldDisabledWhileCryptoInProgress() {
    composeRule.setContent {
      ImportPassphraseDialog(
        errorMessage = null,
        isCryptoInProgress = true,
        onConfirm = {},
        onDismiss = {}
      )
    }

    // The passphrase cannot be edited while a decrypt attempt is in flight —
    // otherwise the text the error would refer to may have changed by the time
    // the attempt's outcome arrives.
    composeRule.onNodeWithTag("passphrase_field").assertIsNotEnabled()
  }

  @Test
  fun passphraseFieldEnabledWhenIdle() {
    launchDialog()

    composeRule.onNodeWithTag("passphrase_field").assertIsEnabled()
  }
}
