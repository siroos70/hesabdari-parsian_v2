package io.github.mojri.hesabyar.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.data.Transaction
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.domain.usecase.SubmitManualTransactionUseCase
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.DashboardViewModel
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import io.github.mojri.hesabyar.ui.TransactionViewModel
import io.github.mojri.hesabyar.ui.UiResult
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.CategoryFilterChip
import io.github.mojri.hesabyar.ui.components.ConfirmDialog
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.IconCircle
import io.github.mojri.hesabyar.ui.components.SectionHeader
import io.github.mojri.hesabyar.ui.components.icon
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.designsystem.toComposeColor
import io.github.mojri.hesabyar.ui.screens.dashboard.dialogs.ManualTransactionDialog
import io.github.mojri.hesabyar.ui.screens.dashboard.dialogs.TransactionDetailDialog
import io.github.mojri.hesabyar.ui.utils.formatPersianDate
import java.util.*

@Composable
fun ReportsScreen(
  dashboardViewModel: DashboardViewModel,
  transactionViewModel: TransactionViewModel,
  aiAssistantViewModel: AiAssistantViewModel,
  onSubmitTransaction: (
  suspend (
    SubmitManualTransactionUseCase.SubmitManualTransactionRequest
  ) -> SubmitManualTransactionUseCase.SubmitResult
  ) = { request ->
    transactionViewModel.submitManualTransaction(request)
  },
  modifier: Modifier = Modifier
) {
  val transactions by dashboardViewModel.transactions.collectAsState()
  val categories by dashboardViewModel.categories.collectAsState()
  val accounts by dashboardViewModel.accounts.collectAsState()

  var selectedCategoryFilter by remember { mutableStateOf<Long?>(null) }
  var selectedPreset by remember { mutableStateOf<String?>(null) }
  var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
  var deletingTransaction by remember { mutableStateOf<Transaction?>(null) }
  var showDetailTransaction by remember { mutableStateOf<Transaction?>(null) }

  val now = System.currentTimeMillis()
  var startDate by remember { mutableStateOf(now - 30L * 24 * 60 * 60 * 1000) }
  var endDate by remember { mutableStateOf(now) }
  var showStartPicker by remember { mutableStateOf(false) }
  var showEndPicker by remember { mutableStateOf(false) }

  if (showStartPicker) {
    JalaliDatePickerDialog(
      initialTimestamp = startDate,
      onDismissRequest = { showStartPicker = false },
      onDateSelected = {
        startDate = it
        selectedPreset = null
      }
    )
  }
  if (showEndPicker) {
    JalaliDatePickerDialog(
      initialTimestamp = endDate,
      onDismissRequest = { showEndPicker = false },
      onDateSelected = {
        endDate = it
        selectedPreset = null
      }
    )
  }

  val filteredList = transactions.filter { it.date in startDate..endDate }
  val displayList =
    transactions
      .filter { it.date in startDate..endDate }
      .let { list ->
        if (selectedCategoryFilter !=
          null
        ) {
          list.filter { it.categoryId == selectedCategoryFilter }
        } else {
          list
        }
      }.sortedByDescending { it.date }

  var totalIncome = 0L
  var totalExpense = 0L
  val categoryTotals = HashMap<Long, Long>()

  filteredList.forEach {
    if (it.type == TransactionType.INCOME) {
      totalIncome += it.amount
    } else {
      totalExpense += it.amount
      val catTotal = categoryTotals[it.categoryId] ?: 0L
      categoryTotals[it.categoryId] = catTotal + it.amount
    }
  }

  val balance = totalIncome - totalExpense

  LazyColumn(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = SpacingTokens.lg),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg),
    contentPadding = PaddingValues(top = SpacingTokens.sm, bottom = SpacingTokens.xl)
  ) {
    // Title
    item {
      SectionHeader(title = "کیف پول و گزارش‌های تحلیلی")
    }

    // Date range filter
    item {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(ShapeTokens.Medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(SpacingTokens.sm),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
      ) {
        // Preset buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.xs)
        ) {
          val presets =
            listOf(
              "امروز" to { Pair(now - (now % (24L * 60 * 60 * 1000)), now) },
              "هفته اخیر" to { Pair(now - 7L * 24 * 60 * 60 * 1000, now) },
              "ماه اخیر" to { Pair(now - 30L * 24 * 60 * 60 * 1000, now) },
              "سال اخیر" to { Pair(now - 365L * 24 * 60 * 60 * 1000, now) }
            )

          presets.forEach { (label, rangeFn) ->
            val isSelected = selectedPreset == label
            HesabyarButton(
              onClick = {
                val (s, e) = rangeFn()
                startDate = s
                endDate = e
                selectedPreset = label
              },
              modifier = Modifier.weight(1f),
              text = label,
              variant = ButtonVariant.Filled,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                  contentColor =
                    if (isSelected) {
                      MaterialTheme.colorScheme.onPrimary
                    } else {
                      MaterialTheme.colorScheme.onSurface
                    }
                )
            )
          }
        }

        // Jalali date picker buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
        ) {
          HesabyarButton(
            onClick = { showStartPicker = true },
            modifier = Modifier.weight(1f),
            text = "از ${JalaliCalendarHelper.gregorianToJalali(startDate)}",
            icon = Icons.Default.DateRange,
            variant = ButtonVariant.Outlined
          )

          HesabyarButton(
            onClick = { showEndPicker = true },
            modifier = Modifier.weight(1f),
            text = "تا ${JalaliCalendarHelper.gregorianToJalali(endDate)}",
            icon = Icons.Default.DateRange,
            variant = ButtonVariant.Outlined
          )
        }
      }
    }

    // Balance Card
    item {
      HesabyarCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeTokens.XLarge
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
        ) {
          Text(
            text = "📈 تراز عملکرد سود و زیان دوره",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("مجموع کل دریافتی‌ها (درآمد):", style = MaterialTheme.typography.bodyMedium)
            Text(
              CurrencyFormatter.format(totalIncome),
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("مجموع کل پرداختی‌ها (مخارج):", style = MaterialTheme.typography.bodyMedium)
            Text(
              CurrencyFormatter.format(totalExpense),
              color = MaterialTheme.colorScheme.error,
              fontWeight = FontWeight.Bold
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              "پس‌انداز خالص دوره:",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                val (sign, amount) = CurrencyFormatter.formatSignedParts(balance)
                val balanceColor =
                  if (balance >=
                    0
                  ) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.error
                  }
                Text(
                  text = sign,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = balanceColor
                )
                Text(
                  text = amount,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = balanceColor
                )
              }
            }
          }
        }
      }
    }

    // AI Budget Advisor Card
    item {
      val budgetState by aiAssistantViewModel.advisorState.collectAsState()
      val providerStatus = aiAssistantViewModel.getProviderStatusText()

      HesabyarCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeTokens.XLarge,
        cardColors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
          )
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
            ) {
              Box(
                modifier =
                  Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.AutoAwesome,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(18.dp)
                )
              }
              Column {
                Text(
                  text = "توصیه‌های هوشمند بودجه",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = providerStatus,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            if (budgetState is UiResult.Success) {
              IconButton(
                onClick = { aiAssistantViewModel.clearAdvisorState() },
                modifier = Modifier.size(28.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = "بستن",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(Dimens.IconSmall)
                )
              }
            }
          }

          when (val state = budgetState) {
            is UiResult.Idle -> {
              Column(
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
                horizontalAlignment = Alignment.Start
              ) {
                Text(
                  text =
                    "با تحلیل عمیق تراکنش‌ها، قسط‌ها و امور مالی ثبت شده، توصیه‌های" +
                      " اختصاصی جهت بهبود وضعیت بودجه خود را از هوش مصنوعی دریافت کنید.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 18.sp
                )
                HesabyarButton(
                  onClick = {
                    aiAssistantViewModel.fetchBudgetAdvice(
                      dashboardViewModel.transactions.value,
                      dashboardViewModel.loans.value,
                      dashboardViewModel.installments.value,
                      dashboardViewModel.categories.value,
                      aiAssistantViewModel.isOnlineMode.value,
                      dashboardViewModel.bankLoans.value,
                      false
                    )
                  },
                  modifier = Modifier.fillMaxWidth(),
                  text = "تحلیل هوشمند و دریافت توصیه",
                  icon = Icons.Filled.AutoAwesome,
                  variant = ButtonVariant.Filled
                )
              }
            }
            is UiResult.Loading -> {
              Column(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpacingTokens.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(24.dp),
                  color = MaterialTheme.colorScheme.primary,
                  strokeWidth = 2.5.dp
                )
                Text(
                  text = "درحال تحلیل تراکنش‌ها و دریافت راهکارها...",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
            is UiResult.Success -> {
              val lastAdviceFetchTime by aiAssistantViewModel.lastAdviceFetchTime.collectAsState()
              Column(
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
              ) {
                MarkdownText(
                  text = state.data,
                  modifier = Modifier.fillMaxWidth(),
                  textStyle = MaterialTheme.typography.bodySmall,
                  textColor = MaterialTheme.colorScheme.onSurface
                )

                Text(
                  text = "آخرین به‌روزرسانی: ${aiAssistantViewModel.formatLastFetchTime(
                    lastAdviceFetchTime
                  )}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
                ) {
                  HesabyarButton(
                    onClick = {
                      aiAssistantViewModel.fetchBudgetAdvice(
                        dashboardViewModel.transactions.value,
                        dashboardViewModel.loans.value,
                        dashboardViewModel.installments.value,
                        dashboardViewModel.categories.value,
                        aiAssistantViewModel.isOnlineMode.value,
                        dashboardViewModel.bankLoans.value,
                        true
                      )
                    },
                    modifier = Modifier.weight(1f),
                    text = "بروزرسانی تحلیل",
                    icon = Icons.Filled.Refresh,
                    variant = ButtonVariant.Outlined
                  )

                  HesabyarButton(
                    onClick = { aiAssistantViewModel.clearAdvisorState() },
                    modifier = Modifier.weight(1f),
                    text = "بستن",
                    variant = ButtonVariant.Outlined
                  )
                }
              }
            }
            is UiResult.Error -> {
              Column(
                verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
                ) {
                  Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                  )
                  Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                  )
                }
                HesabyarButton(
                  onClick = {
                    aiAssistantViewModel.fetchBudgetAdvice(
                      dashboardViewModel.transactions.value,
                      dashboardViewModel.loans.value,
                      dashboardViewModel.installments.value,
                      dashboardViewModel.categories.value,
                      aiAssistantViewModel.isOnlineMode.value,
                      dashboardViewModel.bankLoans.value,
                      true
                    )
                  },
                  text = "تلاش مجدد",
                  variant = ButtonVariant.Filled,
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.error
                    )
                )
              }
            }
          }
        }
      }
    }

    // Category breakdown header
    item {
      SectionHeader(
        title = "📊 سهم دسته‌بندی‌ها از کل هزینه‌ها",
        modifier = Modifier.padding(top = SpacingTokens.sm)
      )
    }

    if (categoryTotals.isEmpty()) {
      item {
        HesabyarCard(
          modifier = Modifier.fillMaxWidth(),
          shape = ShapeTokens.Medium,
          cardColors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
          contentPadding = PaddingValues(SpacingTokens.xl)
        ) {
          Text(
            text = "در این بازه هزینه ثبت شده‌ای یافت نشد.",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    } else {
      // Display visual progress bars for each expense category
      items(categoryTotals.toList().sortedByDescending { it.second }) { (categoryId, total) ->
        val ratio = if (totalExpense > 0) (total.toDouble() / totalExpense.toDouble()).toFloat() else 0f
        val percent = (ratio * 100).toInt()
        val isSelected = selectedCategoryFilter == categoryId
        val category = categories.find { it.id == categoryId }

        val categoryColor = Color(category?.color ?: 0xFF757575)

        HesabyarCard(
          modifier =
            Modifier
              .fillMaxWidth()
              .clickable { selectedCategoryFilter = if (isSelected) null else categoryId },
          shape = ShapeTokens.Large,
          cardColors =
            CardDefaults.cardColors(
              containerColor =
                if (isSelected) {
                  MaterialTheme.colorScheme.primaryContainer.copy(
                    alpha = 0.25f
                  )
                } else {
                  MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
        ) {
          Column(
            verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
              ) {
                Box(
                  modifier =
                    Modifier
                      .size(10.dp)
                      .background(categoryColor, CircleShape)
                )
                Text(
                  text = category?.name ?: "سایر",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Bold
                )
              }

              Text(
                text = "$percent٪ | " + CurrencyFormatter.format(total),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
              )
            }

            LinearProgressIndicator(
              progress = { ratio },
              modifier =
                Modifier
                  .fillMaxWidth()
                  .height(SpacingTokens.xs)
                  .clip(ShapeTokens.Small),
              color = categoryColor,
              trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
          }
        }
      }
    }

    // Transactions listed for the targeted period
    item {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(top = SpacingTokens.sm),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
      ) {
        SectionHeader(title = "📜 تمامی تراکنش‌های ثبت شده (${displayList.size} مورد)")

        // Horizontal category filter chips row
        val categoryFilterList =
          listOf<Pair<Long?, String>>(null to "همه") +
            categories.map { it.id to it.name }

        androidx.compose.foundation.lazy.LazyRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
          contentPadding = PaddingValues(vertical = SpacingTokens.xs)
        ) {
          items(categoryFilterList) { (catId, _) ->
            val isSelected = selectedCategoryFilter == catId
            val cat = categories.find { it.id == catId }

            CategoryFilterChip(
              category = cat,
              selected = isSelected,
              onClick = { selectedCategoryFilter = catId }
            )
          }
        }
      }
    }

    if (displayList.isEmpty()) {
      item {
        Text(
          text =
            if (selectedCategoryFilter !=
              null
            ) {
              "هیچ تراکنشی با این دسته‌بندی یافت نشد."
            } else {
              "تراکنشی یافت نشد."
            },
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(SpacingTokens.xl),
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      items(displayList) { transaction ->
        val sourceAccount = accounts.find { it.id == transaction.accountId }
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(ShapeTokens.Medium)
              .background(MaterialTheme.colorScheme.surface)
              .padding(SpacingTokens.md)
              .clickable { showDetailTransaction = transaction },
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Account color indicator — primary signal for account identity
          if (sourceAccount != null) {
            IconCircle(
              icon = sourceAccount.type.icon(),
              tint = sourceAccount.color.toComposeColor(),
              backgroundColor = sourceAccount.color.toComposeColor(),
              iconSize = 12.dp,
              containerSize = 20.dp,
              contentDescription = stringResource(id = R.string.account_content_description, sourceAccount.name)
            )
            Spacer(modifier = Modifier.width(SpacingTokens.sm))
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = transaction.description,
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "${formatPersianDate(
                transaction.date
              )} | ${categories.find { it.id == transaction.categoryId }?.name ?: "سایر"}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            ReportsTransactionAmount(transaction = transaction)
            IconButton(onClick = { deletingTransaction = transaction }, modifier = Modifier.size(32.dp)) {
              Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "حذف تراکنش",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }
    }
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

  if (editingTransaction != null) {
    ManualTransactionDialog(
      onSubmit = onSubmitTransaction,
      categories = categories,
      transactionToEdit = editingTransaction,
      accounts = accounts,
      // Seed the dialog from the transaction being edited; the selection is
      // held in local dialog state, so it must not drive (or be driven by)
      // any screen-level account filter while editing.
      selectedAccountId = editingTransaction?.accountId,
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
}

/**
 * Renders the signed amount for a transaction row in the reports list.
 *
 * The sign and amount are kept in separate Texts under an LTR layout-direction
 * provider so the bidi algorithm cannot reorder the sign across the digits in
 * the app's RTL locale.  Income and Transfer render a positive sign with
 * [MaterialTheme.colorScheme.primary] / [MaterialTheme.colorScheme.tertiary]
 * respectively; Expense renders a negative sign with
 * [MaterialTheme.colorScheme.error].
 */
@Composable
internal fun ReportsTransactionAmount(transaction: Transaction) {
  val isIncome = transaction.type == TransactionType.INCOME
  val isTransfer = transaction.type == TransactionType.TRANSFER
  val (txSign, txAmount) =
    CurrencyFormatter.formatSignedParts(
      if (isIncome || isTransfer) transaction.amount else -transaction.amount
    )
  val txColor =
    if (isIncome) {
      MaterialTheme.colorScheme.primary
    } else if (isTransfer) {
      MaterialTheme.colorScheme.tertiary
    } else {
      MaterialTheme.colorScheme.error
    }
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier
          .testTag(
            "reports_tx_amount_" +
              when (transaction.type) {
                TransactionType.INCOME -> "income"
                TransactionType.TRANSFER -> "transfer"
                else -> "expense"
              }
          )
    ) {
      Text(
        text = txSign,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = txColor
      )
      Text(
        text = txAmount,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = txColor
      )
    }
  }
}
