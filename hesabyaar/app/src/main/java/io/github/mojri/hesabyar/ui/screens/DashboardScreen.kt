package io.github.mojri.hesabyar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.domain.usecase.SubmitManualTransactionUseCase
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.DashboardViewModel
import io.github.mojri.hesabyar.ui.InstallmentViewModel
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.TransactionViewModel
import io.github.mojri.hesabyar.ui.components.AccountBalanceCard
import io.github.mojri.hesabyar.ui.components.AccountSelector
import io.github.mojri.hesabyar.ui.components.BalanceCard
import io.github.mojri.hesabyar.ui.components.ConfirmDialog
import io.github.mojri.hesabyar.ui.components.EmptyState
import io.github.mojri.hesabyar.ui.components.SectionHeader
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.screens.dashboard.components.BankLoansSummaryCard
import io.github.mojri.hesabyar.ui.screens.dashboard.components.DashboardHeader
import io.github.mojri.hesabyar.ui.screens.dashboard.components.DebtorCreditorCards
import io.github.mojri.hesabyar.ui.screens.dashboard.components.IncomeExpenseCards
import io.github.mojri.hesabyar.ui.screens.dashboard.components.InstallmentMiniItem
import io.github.mojri.hesabyar.ui.screens.dashboard.components.KpiCards
import io.github.mojri.hesabyar.ui.screens.dashboard.components.SmartForecastCard
import io.github.mojri.hesabyar.ui.screens.dashboard.components.SmartParsingBanner
import io.github.mojri.hesabyar.ui.screens.dashboard.components.TransactionMiniItem
import io.github.mojri.hesabyar.ui.screens.dashboard.components.entranceCard
import io.github.mojri.hesabyar.ui.screens.dashboard.dialogs.ForecastDetailDialog
import io.github.mojri.hesabyar.ui.screens.dashboard.dialogs.ManualTransactionDialog
import io.github.mojri.hesabyar.ui.screens.dashboard.dialogs.TransactionDetailDialog

@Composable
fun DashboardScreen(
  dashboardViewModel: DashboardViewModel,
  transactionViewModel: TransactionViewModel,
  installmentViewModel: InstallmentViewModel,
  aiAssistantViewModel: AiAssistantViewModel,
  settingsViewModel: SettingsViewModel,
  onSubmitTransaction: (
  suspend (
    SubmitManualTransactionUseCase.SubmitManualTransactionRequest
  ) -> SubmitManualTransactionUseCase.SubmitResult
  ) = { request ->
    transactionViewModel.submitManualTransaction(request)
  },
  onNavigateToAssistant: () -> Unit,
  modifier: Modifier = Modifier
) {
  val dashboardData by dashboardViewModel.dashboardState.collectAsState()
  val transactions by dashboardViewModel.transactions.collectAsState()
  val loans by dashboardViewModel.loans.collectAsState()
  val installments by dashboardViewModel.installments.collectAsState()
  val categories by dashboardViewModel.categories.collectAsState()
  val bankLoans by dashboardViewModel.bankLoans.collectAsState()
  val forecastState by aiAssistantViewModel.forecastState.collectAsState()
  val lastForecastFetchTime by aiAssistantViewModel.lastForecastFetchTime.collectAsState()
  val accounts by dashboardViewModel.accounts.collectAsState()
  val selectedAccountId by dashboardViewModel.selectedAccountId.collectAsState()

  var showManualAddDialog by remember { mutableStateOf(false) }
  var showFullForecast by remember { mutableStateOf(false) }
  var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
  var deletingTransaction by remember { mutableStateOf<Transaction?>(null) }
  var showDetailTransaction by remember { mutableStateOf<Transaction?>(null) }

  LaunchedEffect(transactions, loans, installments, categories, bankLoans) {
    aiAssistantViewModel.onFinancialDataChanged(transactions, loans, installments, categories, bankLoans)
  }

  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(horizontal = SpacingTokens.lg),
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg),
      contentPadding = PaddingValues(top = SpacingTokens.sm, bottom = 80.dp)
    ) {
      item { DashboardHeader(settingsViewModel) }

      // Account Selector
      if (accounts.isNotEmpty()) {
        item {
          AccountSelector(
            accounts = accounts,
            selectedAccountId = selectedAccountId,
            onAccountSelected = { dashboardViewModel.selectAccount(it) }
          )
        }
      }

      // Wallet Balance Card
      item {
        entranceCard {
          BalanceCard(
            balance = dashboardData.currentBalance,
            income = dashboardData.monthlyIncome,
            expense = dashboardData.monthlyExpenses,
            modifier = Modifier.testTag("balance_card")
          )
        }
      }

      // Per-Account Balance Cards
      if (dashboardData.accounts.isNotEmpty()) {
        item {
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md),
            modifier = Modifier.fillMaxWidth().height(160.dp)
          ) {
            items(
              items = dashboardData.accounts,
              key = { it.accountId },
            ) { summary ->
              AccountBalanceCard(
                summary = summary,
                isSelected = selectedAccountId == summary.accountId,
                onClick = { dashboardViewModel.selectAccount(summary.accountId) },
              )
            }
          }
        }
      }

      item {
        entranceCard {
          SmartForecastCard(
            forecastState = forecastState,
            lastForecastFetchTime = lastForecastFetchTime,
            aiAssistantViewModel = aiAssistantViewModel,
            onShowForecast = { showFullForecast = true },
            onFetchForecast = {
              aiAssistantViewModel.fetchBudgetForecast(
                dashboardViewModel.transactions.value,
                dashboardViewModel.loans.value,
                dashboardViewModel.installments.value,
                dashboardViewModel.categories.value,
                aiAssistantViewModel.isOnlineMode.value,
                bankLoans = dashboardViewModel.bankLoans.value,
                forceRefresh = true
              )
            }
          )
        }
      }

      item {
        entranceCard {
          IncomeExpenseCards(dashboardData)
        }
      }

      item {
        entranceCard {
          KpiCards(dashboardData)
        }
      }

      // Debtors and Creditors summary Row
      item {
        entranceCard {
          DebtorCreditorCards(dashboardData)
        }
      }

      item {
        entranceCard {
          SmartParsingBanner(onNavigateToAssistant)
        }
      }

      // Upcoming Installments Header
      item {
        SectionHeader(
          title = "اقساط پیش‌رو",
          modifier = Modifier.padding(top = SpacingTokens.sm),
          action = {
            if (dashboardData.upcomingInstallments.isNotEmpty()) {
              Text(
                text = "باقی مانده: ${dashboardData.upcomingInstallments.size} مورد",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
              )
            }
          }
        )
      }

      // List of Upcoming Installments
      if (dashboardData.upcomingInstallments.isEmpty()) {
        item {
          EmptyState(
            icon = Icons.Filled.DateRange,
            title = "هیچ قسط پرداخت‌نشده پیش‌رویی ثبت نشده است."
          )
        }
      } else {
        items(dashboardData.upcomingInstallments.take(3)) { installment ->
          InstallmentMiniItem(
            installment = installment,
            onTogglePaid = { installmentViewModel.toggleInstallmentPaid(installment) }
          )
        }
      }

      if (dashboardData.bankLoans.isNotEmpty()) {
        item { BankLoansSummaryCard(dashboardData) }
      }

      // Recent Activity Banner
      item {
        SectionHeader(
          title = "آخرین فعالیت‌ها",
          modifier = Modifier.padding(top = SpacingTokens.sm)
        )
      }

      if (transactions.isEmpty()) {
        item {
          EmptyState(
            icon = Icons.Filled.ReceiptLong,
            title = "هنوز هیچ تراکنشی ثبت نشده است."
          )
        }
      } else {
        items(transactions.take(5)) { transaction ->
          TransactionMiniItem(
            transaction = transaction,
            categories = categories,
            accounts = accounts,
            onClick = { showDetailTransaction = transaction },
            onDelete = { deletingTransaction = transaction }
          )
        }
      }
    }

    FloatingActionButton(
      onClick = { showManualAddDialog = true },
      modifier =
        Modifier
          .align(Alignment.BottomEnd)
          .padding(SpacingTokens.lg)
          .testTag("add_transaction_fab"),
      containerColor = MaterialTheme.colorScheme.primary,
      contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
      Icon(
        imageVector = Icons.Filled.Add,
        contentDescription = "ثبت تراکنش دستی"
      )
    }
  }

  if (showManualAddDialog) {
    ManualTransactionDialog(
      onSubmit = onSubmitTransaction,
      categories = categories,
      accounts = accounts,
      selectedAccountId = selectedAccountId,
      onAccountSelected = { dashboardViewModel.selectAccount(it) },
      onDismiss = { showManualAddDialog = false }
    )
  }

  if (editingTransaction != null) {
    ManualTransactionDialog(
      onSubmit = onSubmitTransaction,
      categories = categories,
      transactionToEdit = editingTransaction,
      accounts = accounts,
      // Seed the dialog from the transaction being edited; the selection is
      // held in local dialog state, so it must not drive (or be driven by)
      // the dashboard's account filter while editing.
      selectedAccountId = editingTransaction?.accountId ?: selectedAccountId,
      onDismiss = { editingTransaction = null }
    )
  }

  if (deletingTransaction != null) {
    ConfirmDialog(
      title = "حذف تراکنش",
      message = "آیا از حذف این تراکنش اطمینان دارید؟",
      confirmText = "حذف",
      onConfirm = {
        transactionViewModel.deleteTransaction(deletingTransaction!!)
        deletingTransaction = null
      },
      onDismiss = { deletingTransaction = null }
    )
  }

  if (showDetailTransaction != null) {
    TransactionDetailDialog(
      transaction = showDetailTransaction!!,
      categories = categories,
      accounts = accounts,
      onEdit = {
        editingTransaction = showDetailTransaction
        showDetailTransaction = null
      },
      onDelete = {
        deletingTransaction = showDetailTransaction
        showDetailTransaction = null
      },
      onDismiss = { showDetailTransaction = null }
    )
  }

  if (showFullForecast) {
    ForecastDetailDialog(
      forecastState = forecastState,
      onDismiss = { showFullForecast = false },
      onRefresh = {
        aiAssistantViewModel.fetchBudgetForecast(
          dashboardViewModel.transactions.value,
          dashboardViewModel.loans.value,
          dashboardViewModel.installments.value,
          dashboardViewModel.categories.value,
          aiAssistantViewModel.isOnlineMode.value,
          bankLoans = dashboardViewModel.bankLoans.value,
          forceRefresh = true
        )
      }
    )
  }
}
