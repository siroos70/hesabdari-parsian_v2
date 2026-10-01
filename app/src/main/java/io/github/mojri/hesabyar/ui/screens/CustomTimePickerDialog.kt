package io.github.mojri.hesabyar.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.designsystem.ElevationTokens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class TimePickerTab {
  HOUR,
  MINUTE
}

@Composable
fun CustomTimePickerDialog(
  initialHour: Int,
  initialMinute: Int,
  onDismissRequest: () -> Unit,
  onTimeSelected: (Int, Int) -> Unit
) {
  var selectedHour by remember { mutableStateOf(initialHour) }
  var selectedMinute by remember { mutableStateOf(initialMinute) }
  var activeTab by remember { mutableStateOf(TimePickerTab.HOUR) }

  val density = LocalDensity.current
  val outerLabelRadiusPx = remember(density) { with(density) { 96.dp.toPx() } }
  val innerLabelRadiusPx = remember(density) { with(density) { 64.dp.toPx() } }
  val thresholdPx = remember(density) { with(density) { 80.dp.toPx() } }

  fun computeAngleDegrees(
    dx: Double,
    dy: Double
  ): Double {
    val angle = Math.toDegrees(atan2(dy, dx)) + 90.0
    return if (angle < 0) angle + 360.0 else angle
  }

  fun resolveHour(
    angle: Double,
    distance: Float,
    thresholdPx: Float
  ): Int {
    var hour12 = Math.round(angle / 30.0).toInt()
    if (hour12 == 0 || hour12 == 12) hour12 = 12
    return if (distance < thresholdPx) {
      if (hour12 == 12) 0 else hour12 + 12
    } else {
      hour12
    }
  }

  // Interactive event handler for tap & drag calculations
  fun handleTouchEvent(
    offset: Offset,
    width: Float,
    height: Float
  ) {
    val (dx, dy) = offset.x - width / 2f to offset.y - height / 2f
    val distance = sqrt(dx * dx + dy * dy)
    if (distance <= 10f) return

    val angle = computeAngleDegrees(dx.toDouble(), dy.toDouble())
    if (activeTab == TimePickerTab.HOUR) {
      selectedHour = resolveHour(angle, distance, thresholdPx)
    } else {
      selectedMinute = (Math.round(angle / 6.0).toInt() % 60)
    }
  }

  Dialog(onDismissRequest = onDismissRequest) {
    HesabyarCard(
      modifier =
        Modifier
          .fillMaxWidth()
          .widthIn(max = 350.dp),
      shape = ShapeTokens.XLarge,
      elevation = ElevationTokens.Level2,
      contentPadding = PaddingValues(0.dp)
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(SpacingTokens.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
      ) {
        // Header (Persian title)
        Text(
          text = "تنظیم ساعت سند",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = SpacingTokens.xs)
        )

        // Swap Hour & Minute Box to correctly align as HH : MM (Left to Right)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .background(
                  color = MaterialTheme.colorScheme.surfaceContainer,
                  shape = ShapeTokens.Large
                ).padding(SpacingTokens.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // 1. Separate hour indicator Box (on the left in LTR)
            val isHourActive = activeTab == TimePickerTab.HOUR
            Box(
              modifier =
                Modifier
                  .weight(1f)
                  .height(56.dp)
                  .clip(ShapeTokens.Medium)
                  .background(
                    if (isHourActive) {
                      MaterialTheme.colorScheme.primaryContainer
                    } else {
                      Color.Transparent
                    }
                  ).clickable { activeTab = TimePickerTab.HOUR },
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = String.format(Locale.forLanguageTag("fa-IR"), "%02d", selectedHour),
                  style = MaterialTheme.typography.headlineLarge,
                  fontWeight = FontWeight.Bold,
                  color =
                    if (isHourActive) {
                      MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                      MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                  text = "ساعت",
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (isHourActive) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(
                          alpha = 0.7f
                        )
                    }
                )
              }
            }

            // Separation Colon
            Text(
              text = ":",
              style = MaterialTheme.typography.headlineLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = SpacingTokens.md)
            )

            // 2. Separate minute indicator Box (on the right in LTR)
            val isMinuteActive = activeTab == TimePickerTab.MINUTE
            Box(
              modifier =
                Modifier
                  .weight(1f)
                  .height(56.dp)
                  .clip(ShapeTokens.Medium)
                  .background(
                    if (isMinuteActive) {
                      MaterialTheme.colorScheme.primaryContainer
                    } else {
                      Color.Transparent
                    }
                  ).clickable { activeTab = TimePickerTab.MINUTE },
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = String.format(Locale.forLanguageTag("fa-IR"), "%02d", selectedMinute),
                  style = MaterialTheme.typography.headlineLarge,
                  fontWeight = FontWeight.Bold,
                  color =
                    if (isMinuteActive) {
                      MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                      MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                  text = "دقیقه",
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (isMinuteActive) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(
                          alpha = 0.7f
                        )
                    }
                )
              }
            }
          }
        }

        // Interactive Canvas Dial (Analog Clock face)
        val primaryColor = MaterialTheme.colorScheme.primary
        val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface

        val textPaintSize = with(LocalDensity.current) { 14.sp.toPx() }

        Box(
          modifier =
            Modifier
              .size(240.dp)
              .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = CircleShape
              ).padding(SpacingTokens.md),
          contentAlignment = Alignment.Center
        ) {
          Canvas(
            modifier =
              Modifier
                .fillMaxSize()
                .pointerInput(activeTab) {
                  detectTapGestures(
                    onTap = { offset ->
                      handleTouchEvent(offset, size.width.toFloat(), size.height.toFloat())
                      if (activeTab == TimePickerTab.HOUR) {
                        activeTab = TimePickerTab.MINUTE
                      }
                    }
                  )
                }.pointerInput(activeTab) {
                  detectDragGestures(
                    onDragEnd = {
                      if (activeTab == TimePickerTab.HOUR) {
                        activeTab = TimePickerTab.MINUTE
                      }
                    },
                    onDragCancel = {
                      if (activeTab == TimePickerTab.HOUR) {
                        activeTab = TimePickerTab.MINUTE
                      }
                    },
                    onDrag = { change, dragAmount ->
                      change.consume()
                      handleTouchEvent(change.position, size.width.toFloat(), size.height.toFloat())
                    }
                  )
                }
          ) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // 1. Draw elegant center pivot pint
            drawCircle(
              color = primaryColor,
              radius = 6.dp.toPx(),
              center = Offset(cx, cy)
            )

            // 2. Determine selector hand properties
            val isInnerRingActive =
              activeTab == TimePickerTab.HOUR && (selectedHour == 0 || selectedHour > 12)
            val activeLabelRadius = if (isInnerRingActive) innerLabelRadiusPx else outerLabelRadiusPx

            val angleDegrees =
              if (activeTab == TimePickerTab.HOUR) {
                val hValue =
                  if (selectedHour ==
                    0
                  ) {
                    12
                  } else if (selectedHour > 12) {
                    selectedHour - 12
                  } else {
                    selectedHour
                  }
                hValue * 30.0 - 90.0
              } else {
                selectedMinute * 6.0 - 90.0
              }
            val rads = Math.toRadians(angleDegrees)
            val handTargetX = cx + activeLabelRadius * cos(rads).toFloat()
            val handTargetY = cy + activeLabelRadius * sin(rads).toFloat()

            // Hand Line
            drawLine(
              color = primaryColor,
              start = Offset(cx, cy),
              end = Offset(handTargetX, handTargetY),
              strokeWidth = 3.dp.toPx()
            )

            // Selection indicator circular blob
            drawCircle(
              color = primaryColor,
              radius = 16.dp.toPx(),
              center = Offset(handTargetX, handTargetY)
            )

            // 3. Draw numbers inside native canvas
            val paint =
              android.graphics.Paint().apply {
                textSize = textPaintSize
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
              }

            drawIntoCanvas { canvas ->
              if (activeTab == TimePickerTab.HOUR) {
                // Outer Ring (1..12)
                val outerNumerals =
                  listOf("12", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")
                outerNumerals.forEachIndexed { index, numericText ->
                  val degrees = index * 30.0 - 90.0
                  val angleRad = Math.toRadians(degrees)
                  val numX = cx + outerLabelRadiusPx * cos(angleRad).toFloat()
                  val numY = cy + outerLabelRadiusPx * sin(angleRad).toFloat()

                  val itemHour = if (index == 0) 12 else index
                  val isMatched = selectedHour == itemHour

                  paint.color = if (isMatched) onPrimaryColor.toArgb() else onSurfaceColor.toArgb()
                  paint.alpha = if (isMatched) 255 else 220
                  val textYOffset = numY - (paint.descent() + paint.ascent()) / 2

                  canvas.nativeCanvas.drawText(numericText, numX, textYOffset, paint)
                }

                // Inner Ring (00, 13..23)
                val innerNumerals =
                  listOf("00", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23")
                val innerPaint =
                  android.graphics.Paint(paint).apply {
                    textSize = textPaintSize * 0.85f
                  }
                innerNumerals.forEachIndexed { index, numericText ->
                  val degrees = index * 30.0 - 90.0
                  val angleRad = Math.toRadians(degrees)
                  val numX = cx + innerLabelRadiusPx * cos(angleRad).toFloat()
                  val numY = cy + innerLabelRadiusPx * sin(angleRad).toFloat()

                  val itemHour = if (index == 0) 0 else index + 12
                  val isMatched = selectedHour == itemHour

                  innerPaint.color =
                    if (isMatched) {
                      onPrimaryColor.toArgb()
                    } else {
                      onSurfaceColor
                        .copy(
                          alpha = 0.6f
                        ).toArgb()
                    }
                  val textYOffset = numY - (innerPaint.descent() + innerPaint.ascent()) / 2

                  canvas.nativeCanvas.drawText(numericText, numX, textYOffset, innerPaint)
                }
              } else {
                // Minutes Ring (00, 05, 10, ... 55)
                val numerals =
                  listOf("00", "05", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55")
                numerals.forEachIndexed { index, numericText ->
                  val degrees = index * 30.0 - 90.0
                  val angleRad = Math.toRadians(degrees)
                  val numX = cx + outerLabelRadiusPx * cos(angleRad).toFloat()
                  val numY = cy + outerLabelRadiusPx * sin(angleRad).toFloat()

                  val itemMinute = index * 5
                  val isMatched = selectedMinute == itemMinute

                  paint.color = if (isMatched) onPrimaryColor.toArgb() else onSurfaceColor.toArgb()
                  paint.alpha = if (isMatched) 255 else 220
                  val textYOffset = numY - (paint.descent() + paint.ascent()) / 2

                  canvas.nativeCanvas.drawText(numericText, numX, textYOffset, paint)
                }
              }
            }
          }
        }

        // Inline quick assist reminder tip
        Text(
          text =
            if (activeTab ==
              TimePickerTab.HOUR
            ) {
              "برای ساعت‌های ۱-۱۲ لایه بیرونی و ۱۳-۲۴ لایه درونی را لمس کنید"
            } else {
              "عقربه را برای تعیین دقیقه بچرخانید"
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
          textAlign = TextAlign.Center,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(horizontal = SpacingTokens.sm)
        )

        // Navigation switches bottom shortcuts
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = SpacingTokens.xs),
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
        ) {
          HesabyarButton(
            onClick = onDismissRequest,
            modifier = Modifier.weight(1f),
            text = "انصراف",
            variant = ButtonVariant.Outlined
          )

          HesabyarButton(
            onClick = {
              onTimeSelected(selectedHour, selectedMinute)
              onDismissRequest()
            },
            modifier = Modifier.weight(1.3f),
            text = "تایید",
            variant = ButtonVariant.Filled
          )
        }
      }
    }
  }
}
