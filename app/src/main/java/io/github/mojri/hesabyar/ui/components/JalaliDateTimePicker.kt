package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.screens.CustomTimePickerDialog
import io.github.mojri.hesabyar.ui.screens.JalaliDatePickerDialog
import java.util.Calendar
import java.util.Locale

private const val DATE_PICKER_WEIGHT = 1.3f

@Suppress("LongMethod")
@Composable
internal fun JalaliDateTimePicker(
  initialTimestamp: Long,
  onTimestampChanged: (Long) -> Unit
) {
  var showJalaliDatePicker by remember { mutableStateOf(false) }
  var showCustomTimePicker by remember { mutableStateOf(false) }

  val calendar =
    remember(initialTimestamp) {
      Calendar.getInstance().apply { timeInMillis = initialTimestamp }
    }

  val jalaliDate =
    remember(initialTimestamp) {
      JalaliCalendarHelper.gregorianToJalali(initialTimestamp)
    }
  val hour = calendar.get(Calendar.HOUR_OF_DAY)
  val minute = calendar.get(Calendar.MINUTE)

  if (showJalaliDatePicker) {
    JalaliDatePickerDialog(
      initialTimestamp = initialTimestamp,
      onDismissRequest = { showJalaliDatePicker = false },
      onDateSelected = onTimestampChanged
    )
  }

  if (showCustomTimePicker) {
    CustomTimePickerDialog(
      initialHour = hour,
      initialMinute = minute,
      onDismissRequest = { showCustomTimePicker = false },
      onTimeSelected = { selectedHour, selectedMinute ->
        val newCal =
          Calendar.getInstance().apply {
            timeInMillis = initialTimestamp
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
          }
        onTimestampChanged(newCal.timeInMillis)
      }
    )
  }

  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(ShapeTokens.Large)
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .padding(SpacingTokens.md),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
  ) {
    Text(
      text = "📅 تنظیم تاریخ و ساعت (شمسی):",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
    ) {
      // Date picker button
      OutlinedButton(
        onClick = { showJalaliDatePicker = true },
        modifier =
          Modifier
            .weight(DATE_PICKER_WEIGHT)
            .height(Dimens.ButtonHeight),
        shape = ShapeTokens.Medium,
        contentPadding = PaddingValues(horizontal = SpacingTokens.sm)
      ) {
        Icon(
          imageVector = Icons.Default.DateRange,
          contentDescription = null,
          modifier = Modifier.size(Dimens.IconSmall),
          tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(SpacingTokens.sm))
        Text(
          text = jalaliDate.toString(),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold
        )
      }

      // Time picker button
      OutlinedButton(
        onClick = { showCustomTimePicker = true },
        modifier =
          Modifier
            .weight(1f)
            .height(Dimens.ButtonHeight),
        shape = ShapeTokens.Medium,
        contentPadding = PaddingValues(horizontal = SpacingTokens.sm)
      ) {
        Icon(
          imageVector = Icons.Default.AccessTime,
          contentDescription = null,
          modifier = Modifier.size(Dimens.IconSmall),
          tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(SpacingTokens.sm))
        Text(
          text = String.format(Locale.US, "%02d:%02d", hour, minute),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}
