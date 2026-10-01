package io.github.mojri.hesabyar.ui.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class FinancialPalette(
  val incomeGreen: Color,
  val expenseRed: Color,
  val warningOrange: Color,
  val infoBlue: Color,
  val purpleAccent: Color
)

/** Curated palette for account colour picker (material 300-range tones). */
val ACCOUNT_PICKER_COLORS =
  listOf(
    0xFF4CAF50L,
    0xFFFF9800L,
    0xFF2196F3L,
    0xFF009688L,
    0xFFF44336L,
    0xFF9C27B0L,
    0xFF757575L,
    0xFFE91E63L,
    0xFF3F51B5L,
    0xFF00BCD4L,
    0xFF8BC34AL,
    0xFFFF5722L,
    0xFF607D8BL,
    0xFF795548L,
    0xFFCDDC39L,
    0xFF03A9F4L,
  )

const val DEFAULT_ACCOUNT_COLOR = 0xFF4CAF50L

private val LightFinancialPalette =
  FinancialPalette(
    incomeGreen = Color(0xFF2ECC71),
    expenseRed = Color(0xFFE74C3C),
    warningOrange = Color(0xFFF39C12),
    infoBlue = Color(0xFF3498DB),
    purpleAccent = Color(0xFF9B59B6)
  )

private val DarkFinancialPalette =
  FinancialPalette(
    incomeGreen = Color(0xFF4CAF50),
    expenseRed = Color(0xFFEF5350),
    warningOrange = Color(0xFFFFB300),
    infoBlue = Color(0xFF4FC3F7),
    purpleAccent = Color(0xFFCE93D8)
  )

object FinancialColors {
  private val incomeGreenState = mutableStateOf(LightFinancialPalette.incomeGreen)
  private val expenseRedState = mutableStateOf(LightFinancialPalette.expenseRed)
  private val warningOrangeState = mutableStateOf(LightFinancialPalette.warningOrange)
  private val infoBlueState = mutableStateOf(LightFinancialPalette.infoBlue)
  private val purpleAccentState = mutableStateOf(LightFinancialPalette.purpleAccent)

  val IncomeGreen: Color get() = incomeGreenState.value
  val ExpenseRed: Color get() = expenseRedState.value
  val WarningOrange: Color get() = warningOrangeState.value
  val InfoBlue: Color get() = infoBlueState.value
  val PurpleAccent: Color get() = purpleAccentState.value

  internal fun setPalette(palette: FinancialPalette) {
    incomeGreenState.value = palette.incomeGreen
    expenseRedState.value = palette.expenseRed
    warningOrangeState.value = palette.warningOrange
    infoBlueState.value = palette.infoBlue
    purpleAccentState.value = palette.purpleAccent
  }
}

internal fun applyFinancialPalette(darkTheme: Boolean) {
  FinancialColors.setPalette(if (darkTheme) DarkFinancialPalette else LightFinancialPalette)
}
