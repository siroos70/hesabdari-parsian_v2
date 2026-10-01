package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.mojri.hesabyar.ui.designsystem.ElevationTokens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

/**
 * Standardized full-featured dialog scaffold built on [Dialog] + [Surface].
 *
 * Encapsulates the boilerplate that was repeated across the custom dialogs in the
 * project ([io.github.mojri.hesabyar.ui.screens.DashboardScreen.ForecastDetailDialog],
 * [io.github.mojri.hesabyar.ui.screens.DashboardScreen.ManualTransactionDialog],
 * [io.github.mojri.hesabyar.ui.screens.JalaliDatePickerDialog],
 * [io.github.mojri.hesabyar.ui.screens.CustomTimePickerDialog] and
 * [io.github.mojri.hesabyar.ui.screens.SettingsScreen.AiConfigDialog]):
 * a title bar with an optional leading icon and close button, a divider, a
 * scrollable body and a bottom action row.
 *
 * @param title Dialog heading shown in the header bar.
 * @param onDismissRequest Called when the dialog is dismissed (back press, scrim).
 * @param modifier Modifier applied to the inner [Surface].
 * @param widthFraction Fraction of the screen width the dialog occupies.
 * @param heightFraction Optional fraction of the screen height; when null the
 *   dialog sizes to its content and the body is not forced to fill height.
 * @param showCloseButton Whether to render the close [IconButton] in the header.
 * @param leadingIcon Optional icon rendered before the title in the header.
 * @param actions Optional composable rendered as a bottom row (e.g. Cancel / Save).
 * @param content Scrollable body content of the dialog.
 */
@Suppress("LongMethod")
@Composable
fun HesabyarDialog(
  title: String,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  widthFraction: Float = 0.92f,
  heightFraction: Float? = null,
  showCloseButton: Boolean = true,
  leadingIcon: ImageVector? = null,
  actions: (@Composable RowScope.() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier =
        modifier
          .fillMaxWidth(widthFraction)
          .then(
            if (heightFraction != null) {
              Modifier.fillMaxHeight(heightFraction)
            } else {
              Modifier
            }
          ),
      shape = ShapeTokens.XLarge,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = ElevationTokens.Level3
    ) {
      Column(
        modifier = Modifier.padding(SpacingTokens.xl)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
          ) {
            if (leadingIcon != null) {
              Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
            }
            Text(
              text = title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }
          if (showCloseButton) {
            IconButton(onClick = onDismissRequest) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "بستن",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        HorizontalDivider(
          color = MaterialTheme.colorScheme.surfaceContainer
        )

        Column(
          modifier =
            Modifier
              .weight(1f, fill = false)
              .fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(vertical = SpacingTokens.lg),
          verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
        ) {
          content()
        }

        if (actions != null) {
          Spacer(modifier = Modifier.size(SpacingTokens.sm))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
          ) {
            actions()
          }
        }
      }
    }
  }
}
