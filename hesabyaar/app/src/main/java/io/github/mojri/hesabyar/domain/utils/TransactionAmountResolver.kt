package io.github.mojri.hesabyar.domain.utils

data class AmountResolutionInput(
  val displayedAmount: Long,
  val isEditMode: Boolean,
  val originalRialAmount: Long,
  val userModifiedAmount: Boolean
)

data class AmountResolutionResult(
  val rialAmount: Long,
  val preservedOriginal: Boolean
)

object TransactionAmountResolver {
  fun resolveAmount(
    input: AmountResolutionInput,
    toRial: (Long) -> Long
  ): AmountResolutionResult {
    if (!input.isEditMode) {
      return AmountResolutionResult(
        rialAmount = safeToRial(input.displayedAmount, toRial),
        preservedOriginal = false
      )
    }

    return if (input.userModifiedAmount) {
      AmountResolutionResult(
        rialAmount = safeToRial(input.displayedAmount, toRial),
        preservedOriginal = false
      )
    } else {
      AmountResolutionResult(
        rialAmount = input.originalRialAmount,
        preservedOriginal = true
      )
    }
  }

  @Suppress("SwallowedException")
  private fun safeToRial(
    displayedAmount: Long,
    toRial: (Long) -> Long
  ): Long {
    if (displayedAmount <= 0L) return 0L
    return try {
      val result = toRial(displayedAmount)
      if (result <= 0L) 0L else result
    } catch (e: ArithmeticException) {
      0L
    }
  }
}
