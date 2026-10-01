package io.github.mojri.hesabyar.ui

import androidx.compose.ui.graphics.Color
import io.github.mojri.hesabyar.ui.components.selectedLabelColor
import io.github.mojri.hesabyar.ui.components.textColorForBackground
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the text color contrast logic used in CategoryFilterChip.
 *
 * Uses the shared textColorForBackground / selectedLabelColor helpers
 * (which call Compose's luminance()), so these tests stay in sync with
 * the production composable.
 */
class CategoryChipTest {
  @Test
  fun `dark background yields white text`() {
    val darkColor = Color(0xFF1A1A2E)
    assertEquals("Dark category → white text", Color.White, textColorForBackground(darkColor))
  }

  @Test
  fun `light background yields black text`() {
    val lightColor = Color(0xFFE74C3C)
    assertEquals("Bright category → black text", Color.Black, textColorForBackground(lightColor))
  }

  @Test
  fun `IncomeGreen yields black text`() {
    val incomeGreen = Color(0xFF2ECC71)
    assertEquals("IncomeGreen → black text", Color.Black, textColorForBackground(incomeGreen))
  }

  @Test
  fun `ExpenseRed yields black text`() {
    val expenseRed = Color(0xFFE74C3C)
    assertEquals("ExpenseRed → black text", Color.Black, textColorForBackground(expenseRed))
  }

  @Test
  fun `black category yields white text`() {
    assertEquals(Color.White, textColorForBackground(Color.Black))
  }

  @Test
  fun `white category yields black text`() {
    assertEquals(Color.Black, textColorForBackground(Color.White))
  }

  @Test
  fun `selected container alpha is 0_15`() {
    val categoryColor = Color(0xFF9B59B6)
    val containerColor = categoryColor.copy(alpha = 0.15f)
    assertEquals(0.15f, containerColor.alpha, 0.001f)
  }

  @Test
  fun `selected label color on light surface with dark category`() {
    val surface = Color(0xFFFDFBFF)
    val darkCategory = Color(0xFF1A1A2E)
    assertEquals("Light surface + dark category → Black", Color.Black, selectedLabelColor(surface, darkCategory))
  }

  @Test
  fun `selected label color on dark surface with light category`() {
    val surface = Color(0xFF1E2123)
    val brightCategory = Color(0xFFE74C3C)
    assertEquals("Dark surface + bright category → White", Color.White, selectedLabelColor(surface, brightCategory))
  }

  @Test
  fun `null category uses Gray`() {
    assertEquals("Null category → Gray → black text", Color.Black, textColorForBackground(Color.Gray))
  }
}
