package io.github.mojri.hesabyar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import io.github.mojri.hesabyar.data.BankLoan
import io.github.mojri.hesabyar.ui.BankLoanViewModel
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.JalaliCalendarHelper
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.ConfirmDialog
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.HesabyarInputField
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.designsystem.WindowSizeTokens

@Composable
fun BankLoanScreen(
  bankLoanViewModel: BankLoanViewModel,
  modifier: Modifier = Modifier
) {
  val bankLoans by bankLoanViewModel.bankLoans.collectAsState()
  var showAddDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier,
    floatingActionButton = {
      FloatingActionButton(onClick = { showAddDialog = true }) {
        Icon(Icons.Filled.Add, contentDescription = "افزودن وام بانکی")
      }
    }
  ) { innerPadding ->
    if (bankLoans.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentAlignment = Alignment.Center
      ) {
        Text("وام بانکی ثبت نشده است", style = MaterialTheme.typography.bodyLarge)
      }
    } else {
      LazyColumn(
        modifier =
          Modifier
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(
              max = WindowSizeTokens.ContentMaxWidth
            ).fillMaxSize()
            .padding(innerPadding)
            .padding(SpacingTokens.md),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
      ) {
        items(bankLoans, key = { it.id }) { loan ->
          BankLoanItem(
            loan = loan,
            onToggleSettled = { bankLoanViewModel.toggleSettled(loan.id) },
            onDelete = { bankLoanViewModel.deleteBankLoan(loan) }
          )
        }
      }
    }
  }

  if (showAddDialog) {
    AddBankLoanDialog(
      onDismiss = { showAddDialog = false },
      onConfirm = { bankName, loanName, received, monthly, count, start, desc ->
        bankLoanViewModel.addBankLoan(bankName, loanName, received, monthly, count, start, desc)
        showAddDialog = false
      }
    )
  }
}

@Composable
private fun BankLoanItem(
  loan: BankLoan,
  onToggleSettled: () -> Unit,
  onDelete: () -> Unit
) {
  var showDeleteConfirm by remember { mutableStateOf(false) }
  if (showDeleteConfirm) {
    ConfirmDialog(
      title = "حذف وام بانکی",
      message = "آیا از حذف این وام و اقساط تولید شده اطمینان دارید؟ این عمل قابل بازگشت نیست.",
      confirmText = "حذف",
      dismissText = "انصراف",
      onDismiss = { showDeleteConfirm = false },
      onConfirm = {
        showDeleteConfirm = false
        onDelete()
      }
    )
  }
  HesabyarCard {
    Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.xs)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${loan.bankName} — ${loan.loanName}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        if (loan.isSettled) {
          AssistChip(
            onClick = {},
            label = { Text("تسویه شده") },
            leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) }
          )
        }
      }
      bankLoanDetailRows(loan)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
      ) {
        HesabyarButton(
          onClick = onToggleSettled,
          text = if (loan.isSettled) "باز کردن" else "تسویه",
          variant = ButtonVariant.Outlined,
          modifier = Modifier.weight(1f)
        )
        HesabyarButton(
          onClick = { showDeleteConfirm = true },
          text = "حذف",
          variant = ButtonVariant.Outlined,
          modifier = Modifier.weight(1f)
        )
      }
    }
  }
}

@Composable
private fun bankLoanDetailRows(loan: BankLoan) {
  Text(
    "مبلغ دریافتی: ${CurrencyFormatter.format(loan.receivedAmount)}",
    style = MaterialTheme.typography.bodyMedium
  )
  Text(
    "قسط ماهانه: ${CurrencyFormatter.format(loan.monthlyInstallmentAmount)}",
    style = MaterialTheme.typography.bodyMedium
  )
  Text("تعداد اقساط: ${loan.numberOfInstallments}", style = MaterialTheme.typography.bodyMedium)
  Text(
    "مبلغ کل بازپرداخت: ${CurrencyFormatter.format(loan.totalRepayableAmount)}",
    style = MaterialTheme.typography.bodyMedium
  )
  Text(
    "سود کل: ${CurrencyFormatter.format(loan.totalInterest)}",
    style = MaterialTheme.typography.bodyMedium
  )
  if (loan.description.isNotBlank()) {
    Text("توضیحات: ${loan.description}", style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun AddBankLoanDialog(
  onDismiss: () -> Unit,
  onConfirm: (
    bankName: String,
    loanName: String,
    receivedAmount: Long,
    monthlyInstallmentAmount: Long,
    numberOfInstallments: Int,
    startDate: Long,
    description: String
  ) -> Unit
) {
  var bankName by remember { mutableStateOf("") }
  var loanName by remember { mutableStateOf("") }
  var received by remember { mutableStateOf("") }
  var monthly by remember { mutableStateOf("") }
  var count by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var startDate by remember { mutableStateOf(System.currentTimeMillis()) }
  var showDatePicker by remember { mutableStateOf(false) }

  if (showDatePicker) {
    JalaliDatePickerDialog(
      initialTimestamp = startDate,
      onDismissRequest = { showDatePicker = false },
      onDateSelected = { startDate = it }
    )
  }

  val jDate = JalaliCalendarHelper.gregorianToJalali(startDate)
  val countVal = count.toIntOrNull() ?: 0
  val receivedVal = received.toLongOrNull() ?: 0L
  val monthlyVal = monthly.toLongOrNull() ?: 0L
  val canConfirm =
    bankName.isNotBlank() &&
      receivedVal > 0 &&
      monthlyVal > 0 &&
      countVal > 0

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      HesabyarButton(
        onClick = {
          onConfirm(
            bankName.trim(),
            loanName.trim(),
            receivedVal,
            monthlyVal,
            countVal,
            startDate,
            description.trim()
          )
        },
        text = "ثبت",
        enabled = canConfirm
      )
    },
    dismissButton = {
      HesabyarButton(onClick = onDismiss, text = "انصراف", variant = ButtonVariant.Outlined)
    },
    title = { Text("ثبت وام بانکی") },
    text = {
      BankLoanForm(
        bankName = bankName,
        onBankName = { bankName = it },
        loanName = loanName,
        onLoanName = { loanName = it },
        received = received,
        onReceived = { received = it },
        monthly = monthly,
        onMonthly = { monthly = it },
        count = count,
        onCount = { count = it },
        description = description,
        onDescription = { description = it },
        startDateLabel = "تاریخ شروع: ${jDate.year}/${jDate.month}/${jDate.day}",
        onPickDate = { showDatePicker = true }
      )
    }
  )
}

@Composable
private fun BankLoanForm(
  bankName: String,
  onBankName: (String) -> Unit,
  loanName: String,
  onLoanName: (String) -> Unit,
  received: String,
  onReceived: (String) -> Unit,
  monthly: String,
  onMonthly: (String) -> Unit,
  count: String,
  onCount: (String) -> Unit,
  description: String,
  onDescription: (String) -> Unit,
  startDateLabel: String,
  onPickDate: () -> Unit
) {
  Column(
    modifier = Modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
  ) {
    HesabyarInputField(value = bankName, onValueChange = onBankName, label = "نام بانک")
    HesabyarInputField(value = loanName, onValueChange = onLoanName, label = "نام وام")
    HesabyarInputField(
      value = received,
      onValueChange = onReceived,
      label = "مبلغ دریافتی (ریال)",
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    HesabyarInputField(
      value = monthly,
      onValueChange = onMonthly,
      label = "مبلغ قسط ماهانه (ریال)",
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    HesabyarInputField(
      value = count,
      onValueChange = onCount,
      label = "تعداد اقساط",
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    HesabyarInputField(value = description, onValueChange = onDescription, label = "توضیحات")
    HesabyarButton(
      onClick = onPickDate,
      text = startDateLabel,
      variant = ButtonVariant.Outlined
    )
  }
}
