package io.github.mojri.hesabyar.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

object PinStorage {
  private const val PREFS_NAME = "hesabyar_auth_prefs"
  private const val PIN_HASH_KEY = "pin_hash"
  private const val PIN_SALT_KEY = "pin_salt"

  @Volatile
  private var cachedPrefs: SharedPreferences? = null

  private fun getPrefs(context: Context): SharedPreferences {
    val existing = cachedPrefs
    if (existing != null) return existing

    synchronized(this) {
      val doubleChecked = cachedPrefs
      if (doubleChecked != null) return doubleChecked

      val appContext = context.applicationContext
      val masterKey =
        MasterKey
          .Builder(appContext)
          .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
          .build()

      return EncryptedSharedPreferences
        .create(
          appContext,
          PREFS_NAME,
          masterKey,
          EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
          EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { cachedPrefs = it }
    }
  }

  fun isPinSet(context: Context): Boolean = getPrefs(context).contains(PIN_HASH_KEY)

  fun setPin(
    context: Context,
    pin: String
  ) {
    val salt = generateSalt()
    val hash = hashPin(pin, salt)
    getPrefs(context)
      .edit()
      .putString(PIN_HASH_KEY, hash)
      .putString(PIN_SALT_KEY, salt)
      .apply()
  }

  fun verifyPin(
    context: Context,
    pin: String
  ): Boolean {
    val prefs = getPrefs(context)
    val storedHash = prefs.getString(PIN_HASH_KEY, null) ?: return false
    val salt = prefs.getString(PIN_SALT_KEY, null) ?: return false

    // Detect hash format by version prefix
    val isVersioned = storedHash.startsWith("2:") || storedHash.startsWith("1:")
    if (isVersioned) {
      // Versioned hashes: try current SHA-256, then old iteration SHA-1
      if (hashPin(pin, salt) == storedHash) {
        return true
      }
      if (hashPinSha1(pin, salt, PBKDF2_ITERATIONS_OLD) == storedHash) {
        setPin(context, pin)
        return true
      }
    } else {
      // Legacy unversioned format: check old SHA-1 PBKDF2 (strip version prefix), then plain SHA-256, migrate on match
      if (hashPinSha1(pin, salt).removePrefix("1:") == storedHash) {
        setPin(context, pin)
        return true
      }
      if (hashPinSha1(pin, salt, PBKDF2_ITERATIONS_OLD).removePrefix("1:") == storedHash) {
        setPin(context, pin)
        return true
      }

      val legacyHash =
        MessageDigest
          .getInstance("SHA-256")
          .digest((pin + salt).toByteArray())
          .joinToString("") { "%02x".format(it) }

      if (legacyHash == storedHash) {
        setPin(context, pin)
        return true
      }
    }

    return false
  }

  fun clearPin(context: Context) {
    getPrefs(context).edit().clear().apply()
  }

  private fun generateSalt(): String {
    val bytes = ByteArray(16)
    java.security.SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private const val PBKDF2_ITERATIONS = 600_000
  private const val PBKDF2_ITERATIONS_OLD = 10_000
  private const val HASH_VERSION_SHA256 = "2"
  private const val HASH_VERSION_SHA1 = "1"

  private fun hashPin(
    pin: String,
    salt: String,
    iterations: Int = PBKDF2_ITERATIONS
  ): String {
    val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt.toByteArray(Charsets.UTF_8), iterations, 256)
    val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val hash = factory.generateSecret(spec).encoded
    return "${HASH_VERSION_SHA256}:${hash.joinToString("") { "%02x".format(it) }}"
  }

  private fun hashPinSha1(
    pin: String,
    salt: String,
    iterations: Int = PBKDF2_ITERATIONS
  ): String {
    val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt.toByteArray(Charsets.UTF_8), iterations, 256)
    val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    val hash = factory.generateSecret(spec).encoded
    return "${HASH_VERSION_SHA1}:${hash.joinToString("") { "%02x".format(it) }}"
  }
}
