package io.github.mojri.hesabyar.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Standardized confirmation / delete dialog used across the app for
 * "are you sure?" prompts (delete transaction, delete loan, delete category, etc.).
 *
 * Replaces the many near-identical [AlertDialog] blocks that were duplicated in
 * [io.github.mojri.hesabyar.ui.screens.DashboardScreen],
 * [io.github.mojri.hesabyar.ui.screens.SmartAssistantScreen],
 * [io.github.mojri.hesabyar.ui.screens.BankLoanScreen],
 * [io.github.mojri.hesabyar.ui.screens.CategoryManagementScreen] and others.
 *
 * @param title Heading shown at the top of the dialog.
 * @param message Body text describing the consequence of confirming.
 * @param confirmText Label for the confirm (destructive) action, e.g. "حذف".
 * @param dismissText Label for the dismiss action, e.g. "لغو".
 * @param onConfirm Called when the user accepts the action.
 * @param onDismiss Called when the dialog is dismissed without confirming.
 * @param confirmColor Color used for the confirm button; defaults to the
 *   semantic "expense / destructive" error color from the theme.
 */
@Composable
fun ConfirmDialog(
  title: String,
  message: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  confirmText: String = "تایید",
  dismissText: String = "لغو",
  confirmColor: Color = MaterialTheme.colorScheme.error
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = modifier,
    title = {
      Text(
        text = title,
        fontWeight = FontWeight.Bold
      )
    },
    text = { Text(text = message) },
    confirmButton = {
      OutlinedButton(onClick = onConfirm) {
        Text(text = confirmText, color = confirmColor)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = dismissText)
      }
    }
  )
}
