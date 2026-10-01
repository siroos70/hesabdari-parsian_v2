package io.github.mojri.hesabyar.ui.screens

import io.github.mojri.hesabyar.HesabyarApp
import io.github.mojri.hesabyar.RustIsolationRule
import io.github.mojri.hesabyar.data.Loan
import io.github.mojri.hesabyar.data.LoanType
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.CurrencyUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Regression tests for the numeric-equality shortcut in [submitLoanEdit]:
 * when the edited amount TEXT differs from the snapshot text but parses to
 * the same number, the stored Rial amounts must be preserved verbatim —
 * especially for odd-Rial loans, where the Toman display round trip
 * (fromRial/toRial) would silently truncate the trailing Rial.
 */
class LoanEditAmountPreservationTest {
  @Rule
  @JvmField
  val rustIsolationRule = RustIsolationRule()

  @Before
  fun forceKotlinFormatter() {
    HesabyarApp.setRustInitializedForTesting(false)
    CurrencyFormatter.setUnit(CurrencyUnit.TOMAN)
  }

  @After
  fun tearDown() {
    CurrencyFormatter.setUnit(CurrencyUnit.RIAL)
  }

  private fun oddRialLoan(): Loan =
    Loan(
      id = 1L,
      personName = "علی",
      type = LoanType.DEBTOR,
      originalAmount = 1_000_001L, // odd Rial — Toman display truncates to 100000
      remainingAmount = 400_001L, // partially repaid, also odd
      description = "d",
      date = 1_000L
    )

  @Test
  fun differentlyFormattedEqualAmountPreservesStoredRials() {
    val loan = oddRialLoan()
    // Snapshot text (what the dialog captured at open): "100000" Toman.
    val initialAmountText = CurrencyFormatter.fromRial(loan.originalAmount).toString()
    // User re-types the same value with a leading zero — different TEXT,
    // same NUMBER. The string-equality shortcut would miss this and recompute.
    val form =
      LoanFormState(
        initialType = loan.type,
        initialPersonName = loan.personName,
        initialAmountRial = loan.originalAmount,
        initialDescription = loan.description,
        initialDate = loan.date
      )
    form.amountText = "0" + initialAmountText

    var updated: Loan? = null
    val shown = mutableListOf<String>()
    submitLoanEdit(
      form = form,
      loan = loan,
      initialAmountText = initialAmountText,
      onUpdate = { updated = it },
      showMessage = { shown.add(it) },
      tooLargeMessage = "too large"
    )

    assertTrue("no validation message expected", shown.isEmpty())
    val result = updated!!
    // The stored Rial amounts survive untouched — the odd Rial is NOT
    // truncated by the Toman round trip.
    assertEquals(1_000_001L, result.originalAmount)
    assertEquals(400_001L, result.remainingAmount)
    assertFalse(result.isSettled)
  }

  @Test
  fun unchangedTextAlsoPreservesStoredRials() {
    val loan = oddRialLoan()
    val initialAmountText = CurrencyFormatter.fromRial(loan.originalAmount).toString()
    val form =
      LoanFormState(
        initialType = loan.type,
        initialPersonName = loan.personName,
        initialAmountRial = loan.originalAmount,
        initialDescription = loan.description,
        initialDate = loan.date
      )

    var updated: Loan? = null
    submitLoanEdit(
      form = form,
      loan = loan,
      initialAmountText = initialAmountText,
      onUpdate = { updated = it },
      showMessage = { },
      tooLargeMessage = "too large"
    )

    assertEquals(1_000_001L, updated!!.originalAmount)
    assertEquals(400_001L, updated!!.remainingAmount)
  }

  @Test
  fun genuinelyDifferentAmountStillRecomputes() {
    val loan = oddRialLoan()
    val initialAmountText = CurrencyFormatter.fromRial(loan.originalAmount).toString()
    val form =
      LoanFormState(
        initialType = loan.type,
        initialPersonName = loan.personName,
        initialAmountRial = loan.originalAmount,
        initialDescription = loan.description,
        initialDate = loan.date
      )
    // User halves the amount: 500000 Toman display = 5_000_000 Rial.
    form.amountText = "500000"

    var updated: Loan? = null
    submitLoanEdit(
      form = form,
      loan = loan,
      initialAmountText = initialAmountText,
      onUpdate = { updated = it },
      showMessage = { },
      tooLargeMessage = "too large"
    )

    // Recompute path: 5_000_000 Rial principal, repaid 600_000 kept intact
    // (5_000_000 − 600_000 = 4_400_000 — the odd-Rial remainder dies here
    // precisely because the user CHANGED the amount).
    assertEquals(5_000_000L, updated!!.originalAmount)
    assertEquals(4_400_000L, updated!!.remainingAmount)
  }

  @Test
  fun unparseableAmountShowsValidationMessageInsteadOfSaving() {
    val loan = oddRialLoan()
    val initialAmountText = CurrencyFormatter.fromRial(loan.originalAmount).toString()
    val form =
      LoanFormState(
        initialType = loan.type,
        initialPersonName = loan.personName,
        initialAmountRial = loan.originalAmount,
        initialDescription = loan.description,
        initialDate = loan.date
      )
    form.amountText = "12abc"

    var updated: Loan? = null
    val shown = mutableListOf<String>()
    submitLoanEdit(
      form = form,
      loan = loan,
      initialAmountText = initialAmountText,
      onUpdate = { updated = it },
      showMessage = { shown.add(it) },
      tooLargeMessage = "too large"
    )

    assertNull("nothing must be saved on invalid input", updated)
    assertEquals(1, shown.size)
  }
}
