package io.github.mojri.hesabyar

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.usecase.GetDashboardDataUseCase
import io.github.mojri.hesabyar.ui.AccountDashboardSummary
import io.github.mojri.hesabyar.ui.components.AccountBalanceCard
import io.github.mojri.hesabyar.ui.components.AccountSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for account selection wiring: verifies that clicking
 * an AccountBalanceCard updates selection, that AccountSelector chips
 * and AccountBalanceCard share the same selected state, and that
 * "All Accounts" clears selection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AccountSelectionTest {
  @get:Rule
  val composeRule = createComposeRule()

  private fun summary(
    id: Long,
    name: String,
    balance: Long = 0L,
    accountType: AccountType = AccountType.BANK,
  ) = AccountDashboardSummary(
    accountId = id,
    accountName = name,
    accountType = accountType,
    balance = balance,
    monthlyIncome = 0L,
    monthlyExpenses = 0L,
    accountColor = 0xFF4CAF50L,
  )

  // -- Test a: clicking a card invokes onClick callback ------------------------

  @Test
  fun clickingCardInvokesOnClick() {
    val clickedId = mutableStateOf<Long?>(null)
    val s = summary(id = 42L, name = "My Bank")

    composeRule.setContent {
      AccountBalanceCard(
        summary = s,
        isSelected = false,
        onClick = { clickedId.value = s.accountId },
      )
    }

    composeRule.onNodeWithContentDescription("حساب My Bank").performClick()
    assertEquals(42L, clickedId.value)
  }

  // -- Test b: selected card has isSelected=true semantics ----------------------

  @Test
  fun selectedCardReportsIsSelectedSemantics() {
    val s = summary(id = 1L, name = "Savings")

    composeRule.setContent {
      AccountBalanceCard(summary = s, isSelected = true, onClick = {})
    }

    composeRule.onNodeWithContentDescription("حساب Savings، انتخاب‌شده").assertIsSelected()
  }

  @Test
  fun unselectedCardReportsIsNotSelectedSemantics() {
    val s = summary(id = 1L, name = "Wallet")

    composeRule.setContent {
      AccountBalanceCard(summary = s, isSelected = false, onClick = {})
    }

    composeRule.onNodeWithContentDescription("حساب Wallet").assertIsNotSelected()
  }

  // -- Test c: AccountSelector updates shared state on chip click --------------
  // FilterChip uses Text(label), not contentDescription, so query by text.

  @Test
  fun selectorChipClickUpdatesSharedState() {
    val selectedId = mutableStateOf<Long?>(null)
    val accounts =
      listOf(
        AccountEntity(id = 10L, name = "Bank A", type = AccountType.BANK),
        AccountEntity(id = 20L, name = "Cash B", type = AccountType.CASH_WALLET),
      )

    composeRule.setContent {
      AccountSelector(
        accounts = accounts,
        selectedAccountId = selectedId.value,
        onAccountSelected = { selectedId.value = it },
      )
    }

    composeRule.onNodeWithText("Bank A").performClick()
    assertEquals(10L, selectedId.value)

    composeRule.onNodeWithText("Cash B").performClick()
    assertEquals(20L, selectedId.value)
  }

  // -- Test d: selecting "All Accounts" (null) clears selection ----------------

  @Test
  fun selectAllAccountsClearsSelection() {
    val selectedId = mutableStateOf<Long?>(10L)
    val accounts =
      listOf(
        AccountEntity(id = 10L, name = "Bank A", type = AccountType.BANK),
      )

    composeRule.setContent {
      AccountSelector(
        accounts = accounts,
        selectedAccountId = selectedId.value,
        onAccountSelected = { selectedId.value = it },
      )
    }

    composeRule.onNodeWithText("همه حساب‌ها").performClick()
    assertNull(selectedId.value)
  }

  // -- Test e: card + selector share the same state ----------------------------

  @Test
  fun cardClickAndSelectorShareSameState() {
    val selectedId = mutableStateOf<Long?>(null)
    val accounts =
      listOf(
        AccountEntity(id = 1L, name = "Account A", type = AccountType.BANK),
      )

    composeRule.setContent {
      Column {
        AccountSelector(
          accounts = accounts,
          selectedAccountId = selectedId.value,
          onAccountSelected = { selectedId.value = it },
        )
        AccountBalanceCard(
          summary = summary(id = 1L, name = "Account A"),
          isSelected = selectedId.value == 1L,
          onClick = { selectedId.value = 1L },
        )
      }
    }

    // Initially no card is selected
    composeRule.onNodeWithContentDescription("حساب Account A، انتخاب‌شده").assertDoesNotExist()

    // Click the card — both card and chip should reflect selection
    composeRule.onNodeWithContentDescription("حساب Account A").performClick()
    assertEquals(1L, selectedId.value)
    composeRule.onNodeWithContentDescription("حساب Account A، انتخاب‌شده").assertIsSelected()

    // Chip for Account A should now be selected
    // onNodeWithText matches both chip and card — use the first match (chip renders first)
    composeRule.onAllNodesWithText("Account A")[0].assertIsSelected()

    // Click "All Accounts" to clear
    composeRule.onNodeWithText("همه حساب‌ها").performClick()
    assertNull(selectedId.value)
  }

  // -- Test f: clicking card updates selection state ----------------------------

  @Test
  fun clickingCardUpdatesSelectionState() {
    val selectedId = mutableStateOf<Long?>(null)

    composeRule.setContent {
      Column {
        AccountBalanceCard(
          summary = summary(id = 1L, name = "Account A"),
          isSelected = selectedId.value == 1L,
          onClick = { selectedId.value = 1L },
        )
        AccountBalanceCard(
          summary = summary(id = 2L, name = "Account B"),
          isSelected = selectedId.value == 2L,
          onClick = { selectedId.value = 2L },
        )
      }
    }

    // Initially no card is selected
    composeRule.onNodeWithContentDescription("حساب Account A، انتخاب‌شده").assertDoesNotExist()

    // Click Account A card
    composeRule.onNodeWithContentDescription("حساب Account A").performClick()
    assertEquals(1L, selectedId.value)
    composeRule.onNodeWithContentDescription("حساب Account A، انتخاب‌شده").assertIsSelected()

    // Click Account B card — switches selection
    composeRule.onNodeWithContentDescription("حساب Account B").performClick()
    assertEquals(2L, selectedId.value)
    composeRule.onNodeWithContentDescription("حساب Account B، انتخاب‌شده").assertIsSelected()
    composeRule.onNodeWithContentDescription("حساب Account A، انتخاب‌شده").assertDoesNotExist()
  }

  // -- Test g: fallback dashboard data computes per-account balances ------------

  @Test
  fun selectAccountFiltersDashboardDataCorrectly() {
    val now = 1752580800000L
    val accounts =
      listOf(
        AccountEntity(id = 1L, name = "Account A", type = AccountType.BANK, initialBalance = 1_000_000L),
        AccountEntity(id = 2L, name = "Account B", type = AccountType.CASH_WALLET, initialBalance = 2_000_000L),
      )
    val transactions =
      listOf(
        Transaction(
          type = TransactionType.INCOME,
          categoryId = 1L,
          amount = 500_000L,
          description = "",
          date = now,
          accountId = 1L,
        ),
        Transaction(
          type = TransactionType.EXPENSE,
          categoryId = 1L,
          amount = 100_000L,
          description = "",
          date = now,
          accountId = 2L,
        ),
      )

    val result =
      GetDashboardDataUseCase.computeFallbackDashboardData(
        transactions = transactions,
        loans = emptyList(),
        installments = emptyList(),
        accounts = accounts,
        now = now,
      )

    assertEquals(2, result.accounts.size)

    val accA = result.accounts.first { it.accountId == 1L }
    val accB = result.accounts.first { it.accountId == 2L }

    // Account A: initialBalance 1M + income 500K = 1.5M
    assertEquals(1_500_000L, accA.balance)
    // Account B: initialBalance 2M - expense 100K = 1.9M
    assertEquals(1_900_000L, accB.balance)
  }

  // -- Test h: selecting null (all accounts) returns all data ------------------

  @Test
  fun selectAllAccountsReturnsAllAccountData() {
    val now = 1752580800000L
    val accounts =
      listOf(
        AccountEntity(id = 1L, name = "Account A", type = AccountType.BANK, initialBalance = 1_000_000L),
        AccountEntity(id = 2L, name = "Account B", type = AccountType.CASH_WALLET, initialBalance = 2_000_000L),
      )
    val transactions =
      listOf(
        Transaction(
          type = TransactionType.INCOME,
          categoryId = 1L,
          amount = 500_000L,
          description = "",
          date = now,
          accountId = 1L,
        ),
      )

    val result =
      GetDashboardDataUseCase.computeFallbackDashboardData(
        transactions = transactions,
        loans = emptyList(),
        installments = emptyList(),
        accounts = accounts,
        now = now,
      )

    assertEquals(2, result.accounts.size)
    val accountIds = result.accounts.map { it.accountId }.toSet()
    assertEquals(setOf(1L, 2L), accountIds)
  }

  // -- Test i: LazyRow with large list — verifies scrolling + selection with performScrollToNode ---

  @Test
  fun lazyRowLargeDatasetScrollAndSelect() {
    val selectedId = mutableStateOf<Long?>(null)
    val accounts =
      (1..30).map { id ->
        AccountEntity(id = id.toLong(), name = "Account $id", type = AccountType.BANK)
      }

    composeRule.setContent {
      AccountSelector(
        accounts = accounts,
        selectedAccountId = selectedId.value,
        onAccountSelected = { selectedId.value = it },
      )
    }

    // "همه حساب‌ها" chip is always present and clears selection
    composeRule.onNodeWithText("همه حساب‌ها").performClick()
    assertNull(selectedId.value)

    // Scroll to the last account (Account 30) and click it
    composeRule
      .onNodeWithTag("accountSelectorLazyRow")
      .performScrollToNode(hasText("Account 30"))
    composeRule.onNodeWithText("Account 30").performClick()
    assertEquals(30L, selectedId.value)
  }
}
