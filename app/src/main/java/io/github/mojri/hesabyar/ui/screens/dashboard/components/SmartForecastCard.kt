package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.UiResult
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.utils.extractForecastPreview

@Suppress("LongMethod")
@Composable
internal fun SmartForecastCard(
  forecastState: UiResult<String>,
  lastForecastFetchTime: Long,
  aiAssistantViewModel: AiAssistantViewModel,
  onShowForecast: () -> Unit,
  onFetchForecast: () -> Unit
) {
  HesabyarCard(
    modifier =
      Modifier
        .fillMaxWidth()
        .testTag("budget_forecast_alert_card")
        .clickable(
          onClick = {
            when (forecastState) {
              is UiResult.Idle, is UiResult.Error -> onFetchForecast()
              else -> onShowForecast()
            }
          },
          enabled = forecastState !is UiResult.Loading
        ),
    shape = ShapeTokens.Large,
    cardColors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
      )
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
    ) {
      IconCircle(
        icon = Icons.Filled.AutoAwesome,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
        iconSize = 18.dp
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "پیش‌بینی بودجه ماه آینده",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
        when (val state = forecastState) {
          is UiResult.Loading -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
              CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary
              )
              Spacer(modifier = Modifier.width(SpacingTokens.sm))
              Text(
                text = "در حال تحلیل...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          is UiResult.Success -> {
            val preview = extractForecastPreview(state.data)
            Column {
              Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
              Spacer(modifier = Modifier.height(SpacingTokens.xs))
              Text(
                text = "آخرین به‌روزرسانی: ${aiAssistantViewModel.formatLastFetchTime(
                  lastForecastFetchTime
                )}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          is UiResult.Error -> {
            Text(
              text = "خطا - برای تلاش مجدد کلیک کنید",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
              maxLines = 1
            )
          }
          is UiResult.Idle -> {
            Text(
              text = "برای دریافت پیش‌بینی کلیک کنید",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
      Icon(
        imageVector = Icons.Filled.ChevronLeft,
        contentDescription = "مشاهده گزارش",
        tint = MaterialTheme.colorScheme.primary
      )
    }
  }
}
