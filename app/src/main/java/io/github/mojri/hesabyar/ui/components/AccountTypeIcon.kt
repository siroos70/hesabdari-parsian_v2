package io.github.mojri.hesabyar.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.mojri.hesabyar.data.AccountType
import io.github.mojri.hesabyar.ui.designsystem.Dimens

/**
 * Maps [AccountType] to a representative Material icon.
 *
 * | AccountType         | Icon            |
 * |---------------------|-----------------|
 * | BANK                | AccountBalance  |
 * | CASH_WALLET         | Wallet          |
 * | SAVINGS_INVESTMENT  | Savings         |
 * | OTHER               | MoreHoriz       |
 */
fun AccountType.icon(): ImageVector =
  when (this) {
    AccountType.BANK -> Icons.Filled.AccountBalance
    AccountType.CASH_WALLET -> Icons.Filled.Wallet
    AccountType.SAVINGS_INVESTMENT -> Icons.Filled.Savings
    AccountType.OTHER -> Icons.Filled.MoreHoriz
  }

/**
 * Circular badge showing the account type icon tinted with [accountColor].
 *
 * Uses [IconCircle] with the default 15% background alpha (matching the
 * design system's `ICON_CIRCLE_BACKGROUND_ALPHA`).
 */
@Composable
fun AccountTypeIcon(
  accountType: AccountType,
  accountColor: Color,
  modifier: Modifier = Modifier,
  contentDescription: String? = null,
) {
  IconCircle(
    icon = accountType.icon(),
    modifier = modifier,
    tint = accountColor,
    backgroundColor = accountColor,
    iconSize = Dimens.IconMedium,
    containerSize = Dimens.AvatarMedium,
    contentDescription = contentDescription,
  )
}
