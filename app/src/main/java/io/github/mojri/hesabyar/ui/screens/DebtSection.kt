package io.github.mojri.hesabyar.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.ui.graphics.vector.ImageVector

enum class DebtSection(
  val id: String,
  val label: String,
  val icon: ImageVector
) {
  INSTALLMENTS("INSTALLMENTS", "اقساط", Icons.Filled.CreditCard),
  BANK_LOANS("BANK_LOANS", "وام بانکی", Icons.Filled.AccountBalance),
  LOANS("LOANS", "قرض و طلب شخصی", Icons.Filled.HistoryEdu)
}
