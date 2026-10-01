package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.VisualTransformation
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens

@Composable
fun HesabyarInputField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  placeholder: String? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  isError: Boolean = false,
  supportingText: String? = null,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  singleLine: Boolean = true,
  shape: Shape = ShapeTokens.Large,
  enabled: Boolean = true
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.fillMaxWidth(),
    label = label?.let { { Text(it) } },
    placeholder = placeholder?.let { { Text(it) } },
    trailingIcon = trailingIcon,
    isError = isError,
    supportingText = supportingText?.let { { Text(it) } },
    visualTransformation = visualTransformation,
    keyboardOptions = keyboardOptions,
    singleLine = singleLine,
    enabled = enabled,
    shape = shape,
    colors =
      OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        errorBorderColor = MaterialTheme.colorScheme.error
      )
  )
}
