package io.github.mojri.hesabyar.domain.usecase

import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType

/**
 * Shared fixture builders for the analytics use-case test suites
 * ([GetAnalyticsUseCaseRustTest], [GetAnalyticsUseCaseFallbackTest]).
 * The Rust path only needs the source account while the fallback tests also
 * exercise destination-account filtering, so [destId] defaults to null.
 */
internal fun analyticsTx(
  type: TransactionType,
  amount: Long,
  accountId: Long,
  destId: Long? = null
) = Transaction(
  type = type,
  categoryId = 1L,
  amount = amount,
  description = "test",
  date = System.currentTimeMillis(),
  accountId = accountId,
  destinationAccountId = destId
)

internal fun analyticsCat(
  id: Long,
  name: String
) = io.github.mojri.hesabyar.data.Category(
  id = id,
  name = name,
  key = "test",
  icon = "",
  color = 0xFF000000L,
  type = CategoryType.EXPENSE
)
