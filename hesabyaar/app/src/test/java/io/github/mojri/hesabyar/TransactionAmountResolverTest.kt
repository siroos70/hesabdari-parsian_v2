package io.github.mojri.hesabyar

import io.github.mojri.hesabyar.domain.utils.AmountResolutionInput
import io.github.mojri.hesabyar.domain.utils.TransactionAmountResolver
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransactionAmountResolverTest {
  @Before
  fun setup() {
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  @Test
  fun newTransactionUsesInjectedConverter() {
    val input =
      AmountResolutionInput(
        displayedAmount = 100L,
        isEditMode = false,
        originalRialAmount = 0L,
        userModifiedAmount = false
      )

    val result = TransactionAmountResolver.resolveAmount(input) { it * 7L }

    assertEquals(700L, result.rialAmount)
    assertFalse(result.preservedOriginal)
  }

  @Test
  fun newTransaction_convertsDisplayUnitToRial() {
    val input =
      AmountResolutionInput(
        displayedAmount = 100L,
        isEditMode = false,
        originalRialAmount = 0L,
        userModifiedAmount = false
      )

    val result = TransactionAmountResolver.resolveAmount(input, CurrencyFormatter::toRial)

    assertEquals(1000L, result.rialAmount)
    assertFalse(result.preservedOriginal)
  }

  @Test
  fun editTransaction_withoutChangingAmount_preservesOriginalRial() {
    val originalRial = 105L
    val displayedAmount = CurrencyFormatter.fromRial(originalRial)

    val input =
      AmountResolutionInput(
        displayedAmount = displayedAmount,
        isEditMode = true,
        originalRialAmount = originalRial,
        userModifiedAmount = false
      )

    val result = TransactionAmountResolver.resolveAmount(input, CurrencyFormatter::toRial)

    assertEquals(originalRial, result.rialAmount)
    assertTrue(result.preservedOriginal)
  }

  @Test
  fun editTransaction_withAmountChange_convertsNewAmount() {
    val originalRial = 105L
    val newDisplayedAmount = 20L

    val input =
      AmountResolutionInput(
        displayedAmount = newDisplayedAmount,
        isEditMode = true,
        originalRialAmount = originalRial,
        userModifiedAmount = true
      )

    val result = TransactionAmountResolver.resolveAmount(input, CurrencyFormatter::toRial)

    assertEquals(200L, result.rialAmount)
    assertFalse(result.preservedOriginal)
  }

  @Test
  fun editTransaction_preservesNonRoundRialValues() {
    val originalRial = 1005L
    val displayedAmount = CurrencyFormatter.fromRial(originalRial)

    val input =
      AmountResolutionInput(
        displayedAmount = displayedAmount,
        isEditMode = true,
        originalRialAmount = originalRial,
        userModifiedAmount = false
      )

    val result = TransactionAmountResolver.resolveAmount(input, CurrencyFormatter::toRial)

    assertEquals(1005L, result.rialAmount)
    assertTrue(result.preservedOriginal)
  }

  @Test
  fun editTransaction_withRialUnit_preservesExactValue() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)

    val originalRial = 105L
    val displayedAmount = CurrencyFormatter.fromRial(originalRial)

    val input =
      AmountResolutionInput(
        displayedAmount = displayedAmount,
        isEditMode = true,
        originalRialAmount = originalRial,
        userModifiedAmount = false
      )

    val result = TransactionAmountResolver.resolveAmount(input, CurrencyFormatter::toRial)

    assertEquals(105L, result.rialAmount)
    assertTrue(result.preservedOriginal)
  }

  @Test
  fun editTransaction_userChangesAmountAfterPrecisionLoss_usesNewValue() {
    val originalRial = 105L
    val initialDisplayedAmount = CurrencyFormatter.fromRial(originalRial)
    val newDisplayedAmount = 50L

    val input =
      AmountResolutionInput(
        displayedAmount = newDisplayedAmount,
        isEditMode = true,
        originalRialAmount = originalRial,
        userModifiedAmount = true
      )

    val result = TransactionAmountResolver.resolveAmount(input, CurrencyFormatter::toRial)

    assertEquals(500L, result.rialAmount)
    assertFalse(result.preservedOriginal)
  }

  @Test
  fun newTransaction_withZeroAmount_convertsCorrectly() {
    val input =
      AmountResolutionInput(
        displayedAmount = 0L,
        isEditMode = false,
        originalRialAmount = 0L,
        userModifiedAmount = false
      )

    val result = TransactionAmountResolver.resolveAmount(input, CurrencyFormatter::toRial)

    assertEquals(0L, result.rialAmount)
    assertFalse(result.preservedOriginal)
  }

  @Test
  fun editTransaction_withLargeAmount_preservesOriginal() {
    val originalRial = 1_000_000_005L
    val displayedAmount = CurrencyFormatter.fromRial(originalRial)

    val input =
      AmountResolutionInput(
        displayedAmount = displayedAmount,
        isEditMode = true,
        originalRialAmount = originalRial,
        userModifiedAmount = false
      )

    val result = TransactionAmountResolver.resolveAmount(input, CurrencyFormatter::toRial)

    assertEquals(1_000_000_005L, result.rialAmount)
    assertTrue(result.preservedOriginal)
  }

  @Test
  fun editTransaction_userReEntersSameDisplayValue_convertsToRoundRial() {
    val originalRial = 105L
    val displayedAmount = CurrencyFormatter.fromRial(originalRial)

    val input =
      AmountResolutionInput(
        displayedAmount = displayedAmount,
        isEditMode = true,
        originalRialAmount = originalRial,
        userModifiedAmount = true
      )

    val result = TransactionAmountResolver.resolveAmount(input, CurrencyFormatter::toRial)

    assertEquals(100L, result.rialAmount)
    assertFalse(result.preservedOriginal)
  }

  @Test
  fun extremeAmount_nearLongMaxValue_doesNotOverflowToNegative() {
    val input =
      AmountResolutionInput(
        displayedAmount = Long.MAX_VALUE / 10 + 1,
        isEditMode = false,
        originalRialAmount = 0L,
        userModifiedAmount = false
      )

    val result = TransactionAmountResolver.resolveAmount(input, CurrencyFormatter::toRial)

    assertTrue(result.rialAmount >= 0)
  }
}
