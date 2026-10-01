package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.data.AccountEntity
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import io.github.mojri.hesabyar.ui.designsystem.toComposeColor

@Composable
fun AccountSelector(
  accounts: List<AccountEntity>,
  selectedAccountId: Long?,
  onAccountSelected: (Long?) -> Unit,
  modifier: Modifier = Modifier,
  includeAllAccountsOption: Boolean = true,
) {
  LazyRow(
    modifier = modifier.testTag("accountSelectorLazyRow"),
    horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (includeAllAccountsOption) {
      item {
        HesabyarChip(
          selected = selectedAccountId == null,
          onClick = { onAccountSelected(null) },
          label = stringResource(id = io.github.mojri.hesabyar.R.string.all_accounts_option),
          shape = ShapeTokens.Small,
        )
      }
    }

    items(
      items = accounts.filter { !it.isArchived },
      key = { it.id },
    ) { account ->
      HesabyarChip(
        selected = selectedAccountId == account.id,
        onClick = { onAccountSelected(account.id) },
        label = account.name,
        leadingIcon = {
          IconCircle(
            icon = account.type.icon(),
            tint = account.color.toComposeColor(),
            backgroundColor = account.color.toComposeColor(),
            iconSize = 12.dp,
            containerSize = Dimens.IconSmall,
          )
        },
        shape = ShapeTokens.Small,
      )
    }
  }
}
