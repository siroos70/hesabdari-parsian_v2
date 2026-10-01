package io.github.mojri.hesabyar

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.mojri.hesabyar.auth.AuthManager
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
class AuthManagerTest {
  private lateinit var authManager: AuthManager
  private lateinit var app: Application

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    authManager = AuthManager()
    PinStorage.clearPin(app)
  }

  @After
  fun tearDown() {
    PinStorage.clearPin(app)
  }

  @Test
  fun `isAuthEnabled returns false when no PIN is set`() {
    assertFalse(authManager.isAuthEnabled(app))
  }

  @Test
  fun `isAuthEnabled returns true when PIN is set`() {
    PinStorage.setPin(app, "1234")
    assertTrue(authManager.isAuthEnabled(app))
  }

  @Test
  fun `isAuthEnabled returns false after PIN is cleared`() {
    PinStorage.setPin(app, "1234")
    assertTrue(authManager.isAuthEnabled(app))

    PinStorage.clearPin(app)
    assertFalse(authManager.isAuthEnabled(app))
  }

  @Test
  fun `shouldShowAuth delegates to isAuthEnabled`() {
    assertFalse(authManager.shouldShowAuth(app))

    PinStorage.setPin(app, "5678")
    assertTrue(authManager.shouldShowAuth(app))
  }
}
