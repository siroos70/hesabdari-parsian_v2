package io.github.mojri.hesabyar

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.auth.PinStorage
import io.github.mojri.hesabyar.shadows.ShadowPinStorage
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowPinStorage::class], sdk = [34])
class PinVerificationTest {
  private lateinit var app: Application

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    PinStorage.clearPin(app)
  }

  @After
  fun tearDown() {
    PinStorage.clearPin(app)
  }

  @Test
  fun `verifyPin returns true for correct PIN`() {
    PinStorage.setPin(app, "123456")
    assertTrue(PinStorage.verifyPin(app, "123456"))
  }

  @Test
  fun `verifyPin returns false for incorrect PIN`() {
    PinStorage.setPin(app, "123456")
    assertFalse(PinStorage.verifyPin(app, "000000"))
  }

  @Test
  fun `verifyPin returns false when no PIN is set`() {
    assertFalse(PinStorage.verifyPin(app, "123456"))
  }

  @Test
  fun `verifyPin returns false after PIN is cleared`() {
    PinStorage.setPin(app, "123456")
    PinStorage.clearPin(app)
    assertFalse(PinStorage.verifyPin(app, "123456"))
  }

  @Test
  fun `setPin followed by clearPin makes isPinSet return false`() {
    PinStorage.setPin(app, "987654")
    assertTrue(PinStorage.isPinSet(app))
    PinStorage.clearPin(app)
    assertFalse(PinStorage.isPinSet(app))
  }

  @Test
  fun `can set new PIN after clearing old one`() {
    PinStorage.setPin(app, "111111")
    PinStorage.clearPin(app)
    PinStorage.setPin(app, "222222")
    assertTrue(PinStorage.verifyPin(app, "222222"))
    assertFalse(PinStorage.verifyPin(app, "111111"))
  }
}
