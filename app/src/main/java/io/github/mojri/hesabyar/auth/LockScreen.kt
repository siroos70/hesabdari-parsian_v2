package io.github.mojri.hesabyar.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity

@Composable
fun LockScreen(
  authManager: AuthManager,
  onUnlocked: () -> Unit
) {
  val context = LocalContext.current
  val activity = context as? FragmentActivity
  var error by remember { mutableStateOf<String?>(null) }
  var showPinInput by remember { mutableStateOf(false) }

  val hasBiometric = authManager.hasBiometric(context)
  val hasPin = authManager.isAuthEnabled(context)

  LaunchedEffect(Unit) {
    if (hasBiometric && activity != null && hasPin) {
      authManager.authenticateWithBiometric(
        activity = activity,
        onError = { errorMsg ->
          error = errorMsg
          showPinInput = true
        },
        onFailed = {
          showPinInput = true
        }
      )
    } else if (!hasPin) {
      authManager.unlock()
      onUnlocked()
    } else {
      showPinInput = true
    }
  }

  LaunchedEffect(authManager.isLocked.collectAsState().value) {
    if (!authManager.isLocked.value) {
      onUnlocked()
    }
  }

  if (showPinInput) {
    PinScreen(
      onPinEntered = { pin ->
        if (authManager.authenticateWithPin(context, pin)) {
          onUnlocked()
        } else {
          error = "رمز عبور اشتباه است"
        }
      },
      onBiometricClick =
        if (hasBiometric && activity != null) {
          {
            authManager.authenticateWithBiometric(
              activity = activity,
              onError = { errorMsg ->
                error = errorMsg
              },
              onFailed = {
                error = "احراز هویت ناموفق بود"
              }
            )
          }
        } else {
          null
        },
      error = error
    )
  } else if (!hasPin) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "در حال بارگذاری...",
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }
}
