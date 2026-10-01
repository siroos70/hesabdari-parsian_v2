package io.github.mojri.hesabyar.ui

import io.github.mojri.hesabyar.ui.theme.DarkBackground
import io.github.mojri.hesabyar.ui.theme.DarkError
import io.github.mojri.hesabyar.ui.theme.DarkErrorContainer
import io.github.mojri.hesabyar.ui.theme.DarkInverseSurface
import io.github.mojri.hesabyar.ui.theme.DarkOnSurface
import io.github.mojri.hesabyar.ui.theme.DarkOutline
import io.github.mojri.hesabyar.ui.theme.DarkPrimary
import io.github.mojri.hesabyar.ui.theme.DarkScrim
import io.github.mojri.hesabyar.ui.theme.DarkSurfaceContainer
import io.github.mojri.hesabyar.ui.theme.DarkSurfaceContainerHigh
import io.github.mojri.hesabyar.ui.theme.DarkSurfaceContainerHighest
import io.github.mojri.hesabyar.ui.theme.DarkSurfaceContainerLow
import io.github.mojri.hesabyar.ui.theme.DarkSurfaceContainerLowest
import io.github.mojri.hesabyar.ui.theme.LightBackground
import io.github.mojri.hesabyar.ui.theme.LightError
import io.github.mojri.hesabyar.ui.theme.LightErrorContainer
import io.github.mojri.hesabyar.ui.theme.LightInverseSurface
import io.github.mojri.hesabyar.ui.theme.LightOnSurface
import io.github.mojri.hesabyar.ui.theme.LightOutline
import io.github.mojri.hesabyar.ui.theme.LightPrimary
import io.github.mojri.hesabyar.ui.theme.LightScrim
import io.github.mojri.hesabyar.ui.theme.LightSurfaceContainer
import io.github.mojri.hesabyar.ui.theme.LightSurfaceContainerHigh
import io.github.mojri.hesabyar.ui.theme.LightSurfaceContainerHighest
import io.github.mojri.hesabyar.ui.theme.LightSurfaceContainerLow
import io.github.mojri.hesabyar.ui.theme.LightSurfaceContainerLowest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that the static dark/light color schemes used by HesabyarTheme
 * have distinct background and surface colors, ensuring visual separation
 * between light and dark modes.
 *
 * Skipped: dynamic color scheme on Android 12+ — requires real Context
 * with system resource access. Compose UI test on device covers that.
 */
class ThemeTest {
  private fun contrastRatio(
    fg: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color
  ): Double {
    val l1 = fg.wcagLuminance().toDouble().coerceAtLeast(bg.wcagLuminance().toDouble())
    val l2 = fg.wcagLuminance().toDouble().coerceAtMost(bg.wcagLuminance().toDouble())
    return (l1 + 0.05) / (l2 + 0.05)
  }

  @Test
  fun `dark and light background colors are distinct`() {
    assertNotEquals("Dark and light backgrounds must differ", DarkBackground, LightBackground)
  }

  @Test
  fun `dark background is visually dark`() {
    assertTrue(
      "DarkBackground luminance ${DarkBackground.wcagLuminance()} should be < 0.1",
      DarkBackground.wcagLuminance() < 0.1f
    )
  }

  @Test
  fun `light background is visually light`() {
    assertTrue(
      "LightBackground luminance ${LightBackground.wcagLuminance()} should be > 0.9",
      LightBackground.wcagLuminance() > 0.9f
    )
  }

  @Test
  fun `dark onSurface has sufficient contrast against dark background`() {
    val ratio = contrastRatio(DarkOnSurface, DarkBackground)
    assertTrue("Dark onSurface contrast $ratio should be >= 4.5", ratio >= 4.5)
  }

  @Test
  fun `light onSurface has sufficient contrast against light background`() {
    val ratio = contrastRatio(LightOnSurface, LightBackground)
    assertTrue("Light onSurface contrast $ratio should be >= 4.5", ratio >= 4.5)
  }

  @Test
  fun `dark and light color schemes define all required surface container levels`() {
    val darkLevels =
      listOf(
        DarkSurfaceContainerLowest.wcagLuminance(),
        DarkSurfaceContainerLow.wcagLuminance(),
        DarkSurfaceContainer.wcagLuminance(),
        DarkSurfaceContainerHigh.wcagLuminance(),
        DarkSurfaceContainerHighest.wcagLuminance()
      )
    for (i in 0 until darkLevels.size - 1) {
      assertTrue(
        "Dark level $i luminance ${darkLevels[i]} should be <= level ${i + 1} ${darkLevels[i + 1]}",
        darkLevels[i] <= darkLevels[i + 1]
      )
    }

    val lightLevels =
      listOf(
        LightSurfaceContainerLowest.wcagLuminance(),
        LightSurfaceContainerLow.wcagLuminance(),
        LightSurfaceContainer.wcagLuminance(),
        LightSurfaceContainerHigh.wcagLuminance(),
        LightSurfaceContainerHighest.wcagLuminance()
      )
    for (i in 0 until lightLevels.size - 1) {
      assertTrue(
        "Light level $i luminance ${lightLevels[i]} should be >= level ${i + 1} ${lightLevels[i + 1]}",
        lightLevels[i] >= lightLevels[i + 1]
      )
    }
  }

  @Test
  fun `dark primary is visually light for contrast on dark surfaces`() {
    assertTrue(
      "DarkPrimary luminance ${DarkPrimary.wcagLuminance()} should be > 0.5",
      DarkPrimary.wcagLuminance() > 0.5f
    )
  }

  @Test
  fun `light primary is visually dark for contrast on light surfaces`() {
    assertTrue(
      "LightPrimary luminance ${LightPrimary.wcagLuminance()} should be < 0.4",
      LightPrimary.wcagLuminance() < 0.4f
    )
  }

  @Test
  fun `error colors exist in both themes`() {
    assertNotEquals("Error must differ between themes", DarkError, LightError)
    assertNotEquals("ErrorContainer must differ", DarkErrorContainer, LightErrorContainer)
  }

  @Test
  fun `inverse surface flips foreground-background roles`() {
    assertTrue("DarkInverseSurface should be light", DarkInverseSurface.wcagLuminance() > 0.5f)
    assertTrue("LightInverseSurface should be dark", LightInverseSurface.wcagLuminance() < 0.5f)
  }

  @Test
  fun `outline colors have moderate visibility in both themes`() {
    val darkOutlineLum = DarkOutline.wcagLuminance()
    val lightOutlineLum = LightOutline.wcagLuminance()
    assertTrue("DarkOutline luminance $darkOutlineLum out of range", darkOutlineLum > 0.1f && darkOutlineLum < 0.8f)
    assertTrue(
      "LightOutline luminance $lightOutlineLum out of range",
      lightOutlineLum > 0.1f && lightOutlineLum < 0.8f
    )
  }

  @Test
  fun `scrim is black in both themes`() {
    assertEquals("Dark scrim should be black", 0f, DarkScrim.red, 0.001f)
    assertEquals("Dark scrim should be black", 0f, DarkScrim.green, 0.001f)
    assertEquals("Dark scrim should be black", 0f, DarkScrim.blue, 0.001f)
    assertEquals("Light scrim should be black", 0f, LightScrim.red, 0.001f)
  }
}
