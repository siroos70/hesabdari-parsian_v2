package io.github.mojri.hesabyar.ui.screens.account

import android.content.Context
import io.github.mojri.hesabyar.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Verifies that the `LastAccountWarning` dialog uses the active-account-specific
 * message (not the generic "last account" message) so users with archived
 * accounts are not misled.
 *
 * The dialog state mapping (`resolveDeleteDialogState` → `LastAccountWarning`)
 * is already covered in [AccountManagementScreenDeleteDialogTest]. This test
 * pins the string-resource choice: the warning must reference "active"
 * accounts and acknowledge that archived accounts exist but are unusable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [31])
class AccountDeleteWarningMessageTest {
  private val context: Context = RuntimeEnvironment.getApplication()

  @Test
  fun lastActiveAccountWarningMessageMentionsActiveAccounts() {
    val message = context.getString(R.string.account_delete_last_active_account_warning_message)
    assertTrue(
      "warning message must mention active accounts, got: $message",
      message.contains("فعال")
    )
  }

  @Test
  fun lastActiveAccountWarningMessageMentionsArchivedAccounts() {
    val message = context.getString(R.string.account_delete_last_active_account_warning_message)
    assertTrue(
      "warning message must mention archived accounts, got: $message",
      message.contains("آرشیو")
    )
  }

  @Test
  fun lastAccountWarningMessageDoesNotClaimToBeLastAccount() {
    // The old message ("آخرین حساب است") was misleading when archived accounts
    // existed. The new message must NOT claim to be the sole account.
    val oldMessage = context.getString(R.string.account_delete_last_account_warning_message)
    assertTrue(
      "old last-account message should still mention 'last account', got: $oldMessage",
      oldMessage.contains("آخرین حساب")
    )

    val newMessage = context.getString(R.string.account_delete_last_active_account_warning_message)
    assertTrue(
      "new message must NOT claim to be the last account, got: $newMessage",
      !newMessage.contains("آخرین حساب است")
    )
  }
}
