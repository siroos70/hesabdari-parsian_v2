package io.github.mojri.hesabyar.domain.utils

import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoansCategoryExclusionTest {
  private val tag = "LoansCategoryExclusionTest"

  private fun category(
    id: Long,
    key: String,
  ): Category =
    Category(
      id = id,
      name = key,
      key = key,
      icon = "",
      color = 0xFF000000L,
      type = CategoryType.BOTH,
    )

  @Test
  fun resolvePopulatedCategoriesReturnsDynamicLoansId() {
    val categories =
      listOf(
        category(id = 10L, key = "Food"),
        category(id = 77L, key = "Loans"),
      )

    assertEquals(listOf(77L), LoansCategoryExclusion.resolve(categories, tag))
  }

  @Test
  fun resolveMissingLoansCategoryReturnsEmptyListAndLogsWarning() {
    AppLogger.clear()
    val categories = listOf(category(id = 10L, key = "Food"))

    assertTrue(LoansCategoryExclusion.resolve(categories, tag).isEmpty())
    assertTrue(
      AppLogger.getLogsForTag(tag).any {
        it.level == "W" &&
          it.message == "Loans category not found by key; defaulting to empty exclusion list"
      },
    )
  }
}
