package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens

enum class ButtonVariant {
  Filled,
  Outlined,
  Text
}

@Composable
fun HesabyarButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  text: String? = null,
  icon: ImageVector? = null,
  iconContentDescription: String? = null,
  variant: ButtonVariant = ButtonVariant.Filled,
  enabled: Boolean = true,
  loading: Boolean = false,
  colors: ButtonColors? = null
) {
  val buttonModifier = modifier.height(Dimens.ButtonHeight)
  val resolvedColors =
    colors ?: when (variant) {
      ButtonVariant.Filled -> ButtonDefaults.buttonColors()
      ButtonVariant.Outlined -> ButtonDefaults.outlinedButtonColors()
      ButtonVariant.Text -> ButtonDefaults.textButtonColors()
    }

  when (variant) {
    ButtonVariant.Filled ->
      Button(
        onClick = onClick,
        modifier = buttonModifier,
        enabled = enabled && !loading,
        shape = ShapeTokens.Full,
        colors = resolvedColors
      ) { ButtonContent(loading, icon, iconContentDescription, text) }

    ButtonVariant.Outlined ->
      OutlinedButton(
        onClick = onClick,
        modifier = buttonModifier,
        enabled = enabled && !loading,
        shape = ShapeTokens.Full,
        colors = resolvedColors
      ) { ButtonContent(loading, icon, iconContentDescription, text) }

    ButtonVariant.Text ->
      TextButton(
        onClick = onClick,
        modifier = buttonModifier,
        enabled = enabled && !loading,
        shape = ShapeTokens.Full,
        colors = resolvedColors
      ) { ButtonContent(loading, icon, iconContentDescription, text) }
  }
}

@Composable
private fun ButtonContent(
  loading: Boolean,
  icon: ImageVector?,
  iconContentDescription: String?,
  text: String?
) {
  if (loading) {
    CircularProgressIndicator(
      modifier = Modifier.size(Dimens.IconSmall),
      strokeWidth = Dimens.DividerThickness,
      color = LocalContentColor.current
    )
  } else {
    icon?.let {
      Icon(
        imageVector = it,
        contentDescription = iconContentDescription,
        modifier = Modifier.size(Dimens.IconMedium)
      )
    }
    text?.let {
      if (icon != null) {
        Spacer(modifier = Modifier.width(SpacingTokens.xs))
      }
      Text(text = it)
    }
  }
}
