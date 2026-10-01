package io.github.mojri.hesabyar.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom

object DatabaseKeyManager {
  private const val PREFS_FILE = "hesabyar_db_key_prefs"
  private const val KEY_DB_PASSPHRASE = "db_passphrase"
  private val lock = Any()

  private fun getEncryptedPrefs(context: Context): SharedPreferences =
    try {
      val masterKey =
        MasterKey
          .Builder(context)
          .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
          .build()

      EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
      )
    } catch (e: GeneralSecurityException) {
      throw RuntimeException("Failed to create encrypted preferences", e)
    } catch (e: IOException) {
      throw RuntimeException("Failed to create encrypted preferences", e)
    }

  fun getOrCreateKey(context: Context): ByteArray =
    synchronized(lock) {
      val prefs = getEncryptedPrefs(context)
      val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
      if (existing != null) {
        return try {
          android.util.Base64.decode(existing, android.util.Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
          throw IllegalStateException(
            "Stored database passphrase is corrupt or invalid. Database cannot be initialized.",
            e
          )
        }
      }
      val key = ByteArray(32)
      SecureRandom().nextBytes(key)
      val saved =
        prefs
          .edit()
          .putString(
            KEY_DB_PASSPHRASE,
            android.util.Base64.encodeToString(key, android.util.Base64.DEFAULT)
          ).commit()
      if (!saved) throw IllegalStateException("Failed to persist database key — refusing to return unsaved key")
      return key
    }
}
