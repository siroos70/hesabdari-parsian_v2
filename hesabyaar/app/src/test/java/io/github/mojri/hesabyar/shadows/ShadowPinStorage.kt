package io.github.mojri.hesabyar.shadows

import android.content.Context
import io.github.mojri.hesabyar.auth.PinStorage
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter

@Implements(PinStorage::class)
class ShadowPinStorage {
  companion object {
    private val store = mutableMapOf<String, String>()

    @Resetter
    @JvmStatic
    fun reset() {
      store.clear()
    }

    @JvmStatic
    private fun key(context: Context) = System.identityHashCode(context).toString()

    @Implementation
    @JvmStatic
    fun isPinSet(context: Context): Boolean = store.containsKey(key(context))

    @Implementation
    @JvmStatic
    fun setPin(
      context: Context,
      pin: String
    ) {
      store[key(context)] = pin
    }

    @Implementation
    @JvmStatic
    fun verifyPin(
      context: Context,
      pin: String
    ): Boolean = store[key(context)] == pin

    @Implementation
    @JvmStatic
    fun clearPin(context: Context) {
      store.remove(key(context))
    }
  }
}
