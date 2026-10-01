package io.github.mojri.hesabyar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import io.github.mojri.hesabyar.ui.BankLoanViewModel
import io.github.mojri.hesabyar.ui.InstallmentViewModel
import io.github.mojri.hesabyar.ui.LoanViewModel
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

@Composable
private fun sectionTint(selected: Boolean): Color =
  if (selected) {
    MaterialTheme.colorScheme.primary
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }

@Composable
fun DebtHubScreen(
  initialSection: DebtSection = DebtSection.INSTALLMENTS,
  installmentViewModel: InstallmentViewModel,
  bankLoanViewModel: BankLoanViewModel,
  loanViewModel: LoanViewModel,
  settingsViewModel: SettingsViewModel,
  modifier: Modifier = Modifier
) {
  var section by remember { mutableStateOf(initialSection) }

  Column(modifier = modifier.fillMaxSize()) {
    SecondaryScrollableTabRow(
      selectedTabIndex = DebtSection.entries.indexOf(section),
      edgePadding = SpacingTokens.md,
      containerColor = MaterialTheme.colorScheme.surface,
      divider = {}
    ) {
      DebtSection.entries.forEach { s ->
        val selected = s == section
        LeadingIconTab(
          selected = selected,
          onClick = { section = s },
          text = {
            Text(
              s.label,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
          },
          icon = {
            Icon(
              imageVector = s.icon,
              contentDescription = null,
              tint = sectionTint(selected)
            )
          },
          selectedContentColor = MaterialTheme.colorScheme.primary,
          unselectedContentColor = sectionTint(false)
        )
      }
    }

    when (section) {
      DebtSection.INSTALLMENTS ->
        InstallmentScreen(
          installmentViewModel = installmentViewModel,
          settingsViewModel = settingsViewModel,
          bankLoanViewModel = bankLoanViewModel,
          modifier = Modifier.fillMaxSize()
        )
      DebtSection.BANK_LOANS ->
        BankLoanScreen(
          bankLoanViewModel = bankLoanViewModel,
          modifier = Modifier.fillMaxSize()
        )
      DebtSection.LOANS ->
        LoanManagementScreen(
          loanViewModel = loanViewModel,
          settingsViewModel = settingsViewModel,
          modifier = Modifier.fillMaxSize()
        )
    }
  }
}
