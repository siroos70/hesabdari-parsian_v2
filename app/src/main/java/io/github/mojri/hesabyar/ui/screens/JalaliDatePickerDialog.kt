package io.github.mojri.hesabyar.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ElevationTokens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import java.util.Calendar

@Composable
fun JalaliDatePickerDialog(
  initialTimestamp: Long,
  onDismissRequest: () -> Unit,
  onDateSelected: (Long) -> Unit
) {
  val jalaliToday = remember(initialTimestamp) { JalaliCalendarHelper.gregorianToJalali(initialTimestamp) }

  // View state tracks what year/month is currently shown in the calendar grid
  var viewYear by remember { mutableStateOf(jalaliToday.year) }
  var viewMonth by remember { mutableStateOf(jalaliToday.month) }

  // Selection state tracks the currently chosen date
  var selectedYear by remember { mutableStateOf(jalaliToday.year) }
  var selectedMonth by remember { mutableStateOf(jalaliToday.month) }
  var selectedDay by remember { mutableStateOf(jalaliToday.day) }

  // Toggle between standard Calendar Grid view and Month/Year Quick Selector
  var showQuickSelector by remember { mutableStateOf(false) }

  val monthsList =
    listOf(
      "فروردین",
      "اردیبهشت",
      "خرداد",
      "تیر",
      "مرداد",
      "شهریور",
      "مهر",
      "آبان",
      "آذر",
      "دی",
      "بهمن",
      "اسفند"
    )

  // Persian Weekdays starting from Saturday (شنبه) to Friday (جمعه)
  val weekdayNames = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

  // Determine day of the week for the 1st of the displayed month
  val firstDayOffset =
    remember(viewYear, viewMonth) {
      val firstDayGregorian = JalaliCalendarHelper.jalaliToGregorian(viewYear, viewMonth, 1)
      val dayOfWeek = firstDayGregorian?.get(Calendar.DAY_OF_WEEK) ?: Calendar.SATURDAY
      // SATURDAY is the first column (index 0)
      when (dayOfWeek) {
        Calendar.SATURDAY -> 0
        Calendar.SUNDAY -> 1
        Calendar.MONDAY -> 2
        Calendar.TUESDAY -> 3
        Calendar.WEDNESDAY -> 4
        Calendar.THURSDAY -> 5
        Calendar.FRIDAY -> 6
        else -> 0
      }
    }

  val daysInMonth =
    remember(viewYear, viewMonth) {
      JalaliCalendarHelper.getDaysInMonth(viewYear, viewMonth)
    }

  // Days in previous month to display padded items
  val daysInPrevMonth =
    remember(viewYear, viewMonth) {
      var prevM = viewMonth - 1
      var prevY = viewYear
      if (prevM < 1) {
        prevM = 12
        prevY--
      }
      JalaliCalendarHelper.getDaysInMonth(prevY, prevM)
    }

  Dialog(onDismissRequest = onDismissRequest) {
    HesabyarCard(
      modifier =
        Modifier
          .fillMaxWidth()
          .widthIn(max = 380.dp),
      shape = ShapeTokens.XLarge,
      elevation = ElevationTokens.Level2,
      contentPadding = PaddingValues(0.dp)
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(SpacingTokens.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
      ) {
        // Minimalist selected date indicator
        Text(
          text = "تاریخ انتخابی: $selectedDay ${monthsList.getOrNull(selectedMonth - 1) ?: ""} $selectedYear",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(vertical = SpacingTokens.xs)
        )

        // Animated switches between Calendar Grid and Month/Year selectors
        AnimatedContent(
          targetState = showQuickSelector,
          transitionSpec = {
            fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
          },
          label = "PickerViewTransition"
        ) { isQuickMode ->
          if (!isQuickMode) {
            // STANDARD CALENDAR VIEW MODE WITH ARROW NAVIGATION
            var dragAmountOffset by remember { mutableStateOf(0f) }
            Column(
              verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
              modifier =
                Modifier
                  .fillMaxWidth()
                  .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                      onDragEnd = {
                        if (dragAmountOffset > 50f) {
                          // Swipe Right -> Previous month
                          if (viewMonth == 1) {
                            viewMonth = 12
                            viewYear--
                          } else {
                            viewMonth--
                          }
                        } else if (dragAmountOffset < -50f) {
                          // Swipe Left -> Next month
                          if (viewMonth == 12) {
                            viewMonth = 1
                            viewYear++
                          } else {
                            viewMonth++
                          }
                        }
                        dragAmountOffset = 0f
                      },
                      onDragCancel = {
                        dragAmountOffset = 0f
                      },
                      onHorizontalDrag = { change, dragAmountDelta ->
                        change.consume()
                        dragAmountOffset += dragAmountDelta
                      }
                    )
                  }
            ) {
              // Month and Year Nav Headers
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                IconButton(
                  onClick = {
                    if (viewMonth == 12) {
                      viewMonth = 1
                      viewYear++
                    } else {
                      viewMonth++
                    }
                  }
                ) {
                  Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "ماه بعدی",
                    tint = MaterialTheme.colorScheme.primary
                  )
                }

                // Interactive Label for Switching to Quick Selector
                Row(
                  modifier =
                    Modifier
                      .clip(ShapeTokens.Medium)
                      .clickable { showQuickSelector = true }
                      .background(MaterialTheme.colorScheme.surfaceContainer)
                      .padding(horizontal = SpacingTokens.md, vertical = SpacingTokens.xs),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center
                ) {
                  Text(
                    text = "${monthsList[viewMonth - 1]} $viewYear",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "انتخاب سریع",
                    modifier = Modifier.size(Dimens.IconSmall),
                    tint = MaterialTheme.colorScheme.primary
                  )
                }

                IconButton(
                  onClick = {
                    if (viewMonth == 1) {
                      viewMonth = 12
                      viewYear--
                    } else {
                      viewMonth--
                    }
                  }
                ) {
                  Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "ماه قبلی",
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
              }

              // Static Weekday Header labels
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .background(
                      color = MaterialTheme.colorScheme.surfaceContainerLowest,
                      shape = ShapeTokens.Small
                    ).padding(vertical = SpacingTokens.xs),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                weekdayNames.forEachIndexed { idx, dayName ->
                  val isWeekend = idx == 6 // جمعه
                  Text(
                    text = dayName,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                      if (isWeekend) {
                        MaterialTheme.colorScheme.error
                      } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                      }
                  )
                }
              }

              // Beautiful, non-scrollable, responsive Week-Rows Column
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
              ) {
                for (week in 0 until 6) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
                  ) {
                    for (dayOfWeek in 0 until 7) {
                      val index = week * 7 + dayOfWeek
                      Box(
                        modifier =
                          Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                      ) {
                        if (index < firstDayOffset) {
                          // 1. Leading days from the previous month
                          val dayNum = daysInPrevMonth - (firstDayOffset - index - 1)
                          Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                              Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .clickable {
                                  // Instantly go back 1 month and select this day
                                  if (viewMonth == 1) {
                                    viewMonth = 12
                                    viewYear--
                                  } else {
                                    viewMonth--
                                  }
                                  selectedYear = viewYear
                                  selectedMonth = viewMonth
                                  selectedDay = dayNum
                                }.padding(2.dp)
                          ) {
                            Text(
                              text = dayNum.toString(),
                              style = MaterialTheme.typography.bodyMedium,
                              color =
                                MaterialTheme.colorScheme.onSurface.copy(
                                  alpha = 0.3f
                                )
                            )
                          }
                        } else if (index < firstDayOffset + daysInMonth) {
                          // 2. Active month days
                          val dayNum = index - firstDayOffset + 1
                          val isSelected =
                            selectedYear == viewYear &&
                              selectedMonth == viewMonth &&
                              selectedDay == dayNum

                          val isWeekend = (index % 7) == 6 // Saturday starts at 0, Friday is 6

                          Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                              Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                  if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                  } else {
                                    Color.Transparent
                                  }
                                ).clickable {
                                  selectedYear = viewYear
                                  selectedMonth = viewMonth
                                  selectedDay = dayNum
                                }.padding(2.dp)
                          ) {
                            Text(
                              text = dayNum.toString(),
                              style = MaterialTheme.typography.bodyMedium,
                              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                              color =
                                if (isSelected) {
                                  MaterialTheme.colorScheme.onPrimary
                                } else {
                                  if (isWeekend) {
                                    MaterialTheme.colorScheme.error
                                  } else {
                                    MaterialTheme.colorScheme.onSurface
                                  }
                                }
                            )
                          }
                        } else {
                          // 3. Trailing days from the next month
                          val dayNum = index - (firstDayOffset + daysInMonth) + 1
                          Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                              Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .clickable {
                                  // Instantly advance 1 month and select this day
                                  if (viewMonth == 12) {
                                    viewMonth = 1
                                    viewYear++
                                  } else {
                                    viewMonth++
                                  }
                                  selectedYear = viewYear
                                  selectedMonth = viewMonth
                                  selectedDay = dayNum
                                }.padding(2.dp)
                          ) {
                            Text(
                              text = dayNum.toString(),
                              style = MaterialTheme.typography.bodyMedium,
                              color =
                                MaterialTheme.colorScheme.onSurface.copy(
                                  alpha = 0.3f
                                )
                            )
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          } else {
            // RE-ENGINEERED Standard Month & Year Speed Selector
            var tempYear by remember { mutableStateOf(viewYear) }
            var tempMonth by remember { mutableStateOf(viewMonth) }

            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .height(280.dp),
              verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
            ) {
              Text(
                text = "انتخاب سریع دوره:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start)
              )

              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
              ) {
                // Left pane: Large list of months
                LazyVerticalGrid(
                  columns = GridCells.Fixed(2),
                  modifier =
                    Modifier
                      .weight(1.2f)
                      .background(
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                        ShapeTokens.Medium
                      ).padding(SpacingTokens.xs),
                  horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs),
                  verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
                ) {
                  items(monthsList.zip(1..12)) { (name, monthVal) ->
                    val isCurrent = tempMonth == monthVal
                    Box(
                      contentAlignment = Alignment.Center,
                      modifier =
                        Modifier
                          .height(34.dp)
                          .clip(ShapeTokens.Small)
                          .background(
                            if (isCurrent) {
                              MaterialTheme.colorScheme.primaryContainer
                            } else {
                              Color.Transparent
                            }
                          ).clickable { tempMonth = monthVal }
                    ) {
                      Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color =
                          if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                          } else {
                            MaterialTheme.colorScheme.onSurface
                          }
                      )
                    }
                  }
                }

                // Right pane: Expansive range of Years (historical and future: 1300 to 1480+)
                val selectableYears = remember { (1300..1500).toList() }
                val yearsGridState =
                  rememberLazyGridState(
                    initialFirstVisibleItemIndex =
                      (
                        selectableYears.indexOf(
                          tempYear
                        ) - 4
                      ).coerceAtLeast(0)
                  )

                LazyVerticalGrid(
                  state = yearsGridState,
                  columns = GridCells.Fixed(2),
                  modifier =
                    Modifier
                      .weight(1f)
                      .background(
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                        ShapeTokens.Medium
                      ).padding(SpacingTokens.xs),
                  horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs),
                  verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
                ) {
                  items(selectableYears) { yr ->
                    val isCurrent = tempYear == yr
                    Box(
                      contentAlignment = Alignment.Center,
                      modifier =
                        Modifier
                          .height(34.dp)
                          .clip(ShapeTokens.Small)
                          .background(
                            if (isCurrent) {
                              MaterialTheme.colorScheme.primaryContainer
                            } else {
                              Color.Transparent
                            }
                          ).clickable { tempYear = yr }
                    ) {
                      Text(
                        text = yr.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color =
                          if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                          } else {
                            MaterialTheme.colorScheme.onSurface
                          }
                      )
                    }
                  }
                }
              }

              // Secondary view Confirmation row
              Row(
                modifier = Modifier.fillMaxWidth().padding(top = SpacingTokens.xs),
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
              ) {
                OutlinedButton(
                  onClick = { showQuickSelector = false },
                  modifier = Modifier.weight(1f),
                  shape = ShapeTokens.Small
                ) {
                  Text("بازگشت", style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                  onClick = {
                    viewYear = tempYear
                    viewMonth = tempMonth
                    // Update actual chosen year and month as well
                    selectedYear = tempYear
                    selectedMonth = tempMonth
                    showQuickSelector = false
                  },
                  modifier = Modifier.weight(1f),
                  shape = ShapeTokens.Small
                ) {
                  Text("تایید دوره", style = MaterialTheme.typography.bodyMedium)
                }
              }
            }
          }
        }

        // Overall Dialog Buttons
        if (!showQuickSelector) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(top = SpacingTokens.sm),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
          ) {
            OutlinedButton(
              onClick = onDismissRequest,
              modifier = Modifier.weight(1f).height(46.dp),
              shape = ShapeTokens.Medium
            ) {
              Text("انصراف", style = MaterialTheme.typography.labelLarge)
            }

            Button(
              onClick = {
                val javaCal =
                  JalaliCalendarHelper.jalaliToGregorian(
                    selectedYear,
                    selectedMonth,
                    selectedDay
                  ) ?: return@Button
                // Preserve existing hour and minute of the timestamp
                val prevCal = Calendar.getInstance().apply { timeInMillis = initialTimestamp }
                javaCal.set(Calendar.HOUR_OF_DAY, prevCal.get(Calendar.HOUR_OF_DAY))
                javaCal.set(Calendar.MINUTE, prevCal.get(Calendar.MINUTE))
                onDateSelected(javaCal.timeInMillis)
                onDismissRequest()
              },
              modifier = Modifier.weight(1.3f).height(46.dp),
              shape = ShapeTokens.Medium
            ) {
              Text(
                "تایید نهایی",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }
  }
}
