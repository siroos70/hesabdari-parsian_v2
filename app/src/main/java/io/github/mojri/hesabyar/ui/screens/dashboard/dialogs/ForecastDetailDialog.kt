package io.github.mojri.hesabyar.ui.screens.dashboard.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.github.mojri.hesabyar.ui.UiResult
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarDialog
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.screens.MarkdownText

@Suppress("LongMethod")
@Composable
internal fun ForecastDetailDialog(
  forecastState: UiResult<String>,
  onDismiss: () -> Unit,
  onRefresh: () -> Unit
) {
  HesabyarDialog(
    title = "پیش‌بینی وضعیت بودجه ماه آینده",
    onDismissRequest = onDismiss,
    widthFraction = 0.95f,
    heightFraction = 0.85f,
    leadingIcon = Icons.Filled.AutoAwesome,
    showCloseButton = true
  ) {
    when (val state = forecastState) {
      is UiResult.Loading -> {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(Dimens.IconLarge),
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "در حال تحلیل و پیش‌بینی وضعیت بودجه...",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
      is UiResult.Error -> {
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "⚠️ خطا در دریافت پیش‌بینی",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
          )
          Spacer(modifier = Modifier.height(SpacingTokens.sm))
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(SpacingTokens.lg))
          Button(onClick = onRefresh) {
            Text("تلاش مجدد")
          }
        }
      }
      is UiResult.Success -> {
        Column(modifier = Modifier.fillMaxSize()) {
          Column(modifier = Modifier.weight(1f)) {
            MarkdownText(text = state.data)
          }
          Spacer(modifier = Modifier.height(SpacingTokens.md))
          Button(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeTokens.Medium
          ) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = null,
              modifier = Modifier.size(Dimens.IconSmall)
            )
            Spacer(modifier = Modifier.width(SpacingTokens.sm))
            Text("بروزرسانی پیش‌بینی", fontWeight = FontWeight.Bold)
          }
        }
      }
      is UiResult.Idle -> {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          HesabyarButton(onClick = onRefresh, text = "دریافت پیش‌بینی")
        }
      }
    }
  }
}
