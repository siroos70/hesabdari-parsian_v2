package io.github.mojri.hesabyar.domain.usecase

import android.content.SharedPreferences

/**
 * Abstraction for persisting AI forecast/advice cache entries.
 *
 * The implementation lives in [SharedPrefsAiForecastAdviceCache] and uses
 * the same SharedPreferences keys the ViewModel used directly before this
 * refactor. Each entry is validated against its stored signature and TTL
 * before being returned.
 */
interface AiForecastAdviceCache {
  /**
   * Return the cached forecast entry if the stored [signature] matches
   * the one provided AND the entry has not expired. Returns `null` otherwise.
   */
  fun getForecast(
    signature: String,
    currentTime: Long = System.currentTimeMillis()
  ): CacheEntry?

  /**
   * Return the stored forecast entry if it exists and has not expired,
   * regardless of signature. Useful for warm-start cache restoration.
   */
  fun peekForecast(currentTime: Long = System.currentTimeMillis()): CacheEntry?

  /** Persist [value] alongside [signature] with the current timestamp. */
  fun putForecast(
    signature: String,
    value: String
  )

  /**
   * Return the cached advice entry if the stored [signature] matches
   * the one provided AND the entry has not expired. Returns `null` otherwise.
   */
  fun getAdvice(
    signature: String,
    currentTime: Long = System.currentTimeMillis()
  ): CacheEntry?

  /**
   * Return the stored advice entry if it exists and has not expired,
   * regardless of signature. Useful for warm-start cache restoration.
   */
  fun peekAdvice(currentTime: Long = System.currentTimeMillis()): CacheEntry?

  /** Persist [value] alongside [signature] with the current timestamp. */
  fun putAdvice(
    signature: String,
    value: String
  )

  /** Remove all cached forecast and advice entries. */
  fun clear()
}

data class CacheEntry(
  val value: String,
  val fetchedAtMillis: Long,
  val signature: String,
)

/**
 * SharedPreferences-backed implementation of [AiForecastAdviceCache].
 *
 * Stores forecast and advice content alongside their fetch timestamps and
 * signature keys. An entry is considered valid only when:
 * - The stored signature matches the lookup signature
 * - The entry has not exceeded [cacheDurationMs] (10 minutes)
 *
 * @param sharedPrefs The SharedPreferences instance to use. Must be injected
 *   (not read from Context) to keep the class testable.
 */
class SharedPrefsAiForecastAdviceCache(
  private val sharedPrefs: SharedPreferences,
) : AiForecastAdviceCache {
  /** Cache TTL — matches the ViewModel's original 10-minute window. */
  val cacheDurationMs: Long = CACHE_DURATION_MS

  // ── Forecast ──────────────────────────────────────────────────────────

  override fun getForecast(
    signature: String,
    currentTime: Long
  ): CacheEntry? = getEntry(KEY_FORECAST, KEY_FORECAST_TIME, KEY_FORECAST_SIG, signature, currentTime)

  override fun peekForecast(currentTime: Long): CacheEntry? =
    peekEntry(KEY_FORECAST, KEY_FORECAST_TIME, KEY_FORECAST_SIG, currentTime)

  override fun putForecast(
    signature: String,
    value: String
  ) = putEntry(KEY_FORECAST, KEY_FORECAST_TIME, KEY_FORECAST_SIG, signature, value)

  // ── Advice ────────────────────────────────────────────────────────────

  override fun getAdvice(
    signature: String,
    currentTime: Long
  ): CacheEntry? = getEntry(KEY_ADVICE, KEY_ADVICE_TIME, KEY_ADVICE_SIG, signature, currentTime)

  override fun peekAdvice(currentTime: Long): CacheEntry? =
    peekEntry(KEY_ADVICE, KEY_ADVICE_TIME, KEY_ADVICE_SIG, currentTime)

  override fun putAdvice(
    signature: String,
    value: String
  ) = putEntry(KEY_ADVICE, KEY_ADVICE_TIME, KEY_ADVICE_SIG, signature, value)

  // ── Clear ─────────────────────────────────────────────────────────────

  override fun clear() {
    sharedPrefs
      .edit()
      .remove(KEY_FORECAST)
      .remove(KEY_FORECAST_TIME)
      .remove(KEY_FORECAST_SIG)
      .remove(KEY_ADVICE)
      .remove(KEY_ADVICE_TIME)
      .remove(KEY_ADVICE_SIG)
      .apply()
  }

  // ── Internal helpers ──────────────────────────────────────────────────

  private fun getEntry(
    contentKey: String,
    timeKey: String,
    storedSigKey: String,
    lookupSignature: String,
    currentTime: Long = System.currentTimeMillis(),
  ): CacheEntry? {
    val entry = peekEntry(contentKey, timeKey, storedSigKey, currentTime) ?: return null
    return if (entry.signature == lookupSignature) entry else null
  }

  private fun peekEntry(
    contentKey: String,
    timeKey: String,
    storedSigKey: String,
    currentTime: Long = System.currentTimeMillis(),
  ): CacheEntry? {
    val content = sharedPrefs.getString(contentKey, null)
    val time = sharedPrefs.getLong(timeKey, 0L)
    val storedSignature = sharedPrefs.getString(storedSigKey, null) ?: ""
    val age = currentTime - time
    val isValid =
      !content.isNullOrEmpty() &&
        time > 0L &&
        age >= 0 &&
        age <= cacheDurationMs
    return if (isValid) CacheEntry(value = content!!, fetchedAtMillis = time, signature = storedSignature) else null
  }

  private fun putEntry(
    contentKey: String,
    timeKey: String,
    storedSigKey: String,
    signature: String,
    value: String,
  ) {
    sharedPrefs
      .edit()
      .putString(contentKey, value)
      .putLong(timeKey, System.currentTimeMillis())
      .putString(storedSigKey, signature)
      .apply()
  }

  companion object {
    private const val CACHE_DURATION_MS = 10 * 60 * 1000L // 10 minutes

    // SharedPreferences keys — kept in sync with the original ViewModel constants
    const val KEY_FORECAST = "cached_forecast"
    const val KEY_FORECAST_TIME = "forecast_fetch_time"
    const val KEY_FORECAST_SIG = "forecast_signature"
    const val KEY_ADVICE = "cached_advice"
    const val KEY_ADVICE_TIME = "advice_fetch_time"
    const val KEY_ADVICE_SIG = "advice_signature"
  }
}
