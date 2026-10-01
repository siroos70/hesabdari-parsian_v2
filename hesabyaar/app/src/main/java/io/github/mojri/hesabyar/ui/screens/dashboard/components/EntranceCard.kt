package io.github.mojri.hesabyar.ui.screens.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable

@Composable
internal fun entranceCard(content: @Composable () -> Unit) {
  AnimatedVisibility(
    visible = true,
    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 12 })
  ) {
    content()
  }
}
