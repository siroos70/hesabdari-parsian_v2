package io.github.mojri.hesabyar.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinScreen(
  onPinEntered: (String) -> Unit,
  onBiometricClick: (() -> Unit)? = null,
  error: String? = null
) {
  var pin by remember { mutableStateOf("") }
  var showError by remember { mutableStateOf(false) }

  LaunchedEffect(error) {
    if (error != null) {
      showError = true
    } else {
      showError = false
    }
  }

  val pinLength = 6
  val errorColor = MaterialTheme.colorScheme.error

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = "رمز عبور را وارد کنید",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    if (error != null) {
      Text(
        text = error,
        color = errorColor,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 16.dp)
      )
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.padding(vertical = 32.dp)
    ) {
      repeat(pinLength) { index ->
        val isFilled = index < pin.length
        val dotColor by animateColorAsState(
          targetValue =
            if (showError && isFilled) {
              errorColor
            } else if (isFilled) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.outline
            },
          label = "dotColor"
        )

        Box(
          modifier =
            Modifier
              .size(16.dp)
              .clip(CircleShape)
              .background(dotColor)
        )
      }
    }

    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      for (row in 0..3) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          for (col in 0..2) {
            val number = row * 3 + col + 1
            if (row == 3 && col == 0) {
              if (onBiometricClick != null) {
                IconButton(
                  onClick = onBiometricClick,
                  modifier = Modifier.size(72.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "احراز هویت اثر انگشت",
                    modifier = Modifier.size(32.dp)
                  )
                }
              } else {
                Spacer(modifier = Modifier.size(72.dp))
              }
            } else if (row == 3 && col == 2) {
              IconButton(
                onClick = {
                  if (pin.isNotEmpty()) {
                    pin = pin.dropLast(1)
                    showError = false
                  }
                },
                modifier = Modifier.size(72.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Backspace,
                  contentDescription = "پاک کردن",
                  modifier = Modifier.size(32.dp)
                )
              }
            } else {
              val displayNumber = if (row == 3 && col == 1) "0" else number.toString()
              Box(
                modifier =
                  Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable {
                      if (pin.length < pinLength) {
                        pin += displayNumber
                        showError = false
                        if (pin.length == pinLength) {
                          onPinEntered(pin)
                        }
                      }
                    },
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = displayNumber,
                  fontSize = 24.sp,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }
        }
      }
    }
  }
}
