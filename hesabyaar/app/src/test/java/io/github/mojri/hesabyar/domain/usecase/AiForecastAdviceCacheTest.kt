package io.github.mojri.hesabyar.domain.usecase

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AiForecastAdviceCache] using an in-memory
 * [SharedPreferences] fake — no Robolectric required.
 */
class AiForecastAdviceCacheTest {
  private lateinit var prefs: FakeSharedPreferences
  private lateinit var cache: SharedPrefsAiForecastAdviceCache

  @Before
  fun setUp() {
    prefs = FakeSharedPreferences()
    cache = SharedPrefsAiForecastAdviceCache(prefs)
  }

  // ── Forecast tests ────────────────────────────────────────────────────

  @Test
  fun putForecastThenGetForecastReturnsSameValue() {
    cache.putForecast("sig-1", "forecast content")

    val entry = cache.getForecast("sig-1")

    assertNotNull(entry)
    assertEquals("forecast content", entry!!.value)
    assertTrue(entry.fetchedAtMillis > 0)
  }

  @Test
  fun getForecastReturnsNullWhenSignatureDoesNotMatch() {
    cache.putForecast("sig-1", "forecast content")

    val entry = cache.getForecast("sig-2")

    assertNull(entry)
  }

  @Test
  fun getForecastReturnsNullWhenContentIsEmpty() {
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST, "")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_TIME, System.currentTimeMillis())
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_SIG, "sig-1")
      .apply()

    val entry = cache.getForecast("sig-1")

    assertNull(entry)
  }

  @Test
  fun getForecastReturnsNullWhenEntryIsExpired() {
    val now = 1_700_000_000_000L
    // Store entry at now, read at now + 11 minutes (beyond 10-min TTL)
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST, "forecast content")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_TIME, now)
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_SIG, "sig-1")
      .apply()

    val entry = cache.getForecast("sig-1", currentTime = now + 11 * 60 * 1000L)

    assertNull(entry)
  }

  @Test
  fun getForecastReturnsEntryWhenWithinTtl() {
    val now = 1_700_000_000_000L
    // Store entry at now, read at now + 5 minutes (within 10-min TTL)
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST, "forecast content")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_TIME, now)
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_SIG, "sig-1")
      .apply()

    val entry = cache.getForecast("sig-1", currentTime = now + 5 * 60 * 1000L)

    assertNotNull(entry)
    assertEquals("forecast content", entry!!.value)
  }

  @Test
  fun getForecastReturnsEntryAtExactTtlBoundary() {
    val now = 1_700_000_000_000L
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST, "forecast content")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_TIME, now)
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_SIG, "sig-1")
      .apply()

    // Exactly at TTL boundary — should still be valid (age == cacheDurationMs)
    val atBoundary = cache.getForecast("sig-1", currentTime = now + 10 * 60 * 1000L)
    assertNotNull(atBoundary)

    // One ms past TTL — should be expired
    val pastBoundary = cache.getForecast("sig-1", currentTime = now + 10 * 60 * 1000L + 1)
    assertNull(pastBoundary)
  }

  @Test
  fun getForecastReturnsNullWhenTimestampIsInFuture() {
    val now = 1_700_000_000_000L
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST, "forecast content")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_TIME, now + 10 * 60 * 1000L)
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_SIG, "sig-1")
      .apply()

    // currentTime is BEFORE the stored timestamp — age is negative
    val entry = cache.getForecast("sig-1", currentTime = now)

    assertNull(entry)
  }

  // ── Advice tests ──────────────────────────────────────────────────────

  @Test
  fun putAdviceThenGetAdviceReturnsSameValue() {
    cache.putAdvice("sig-1", "advice content")

    val entry = cache.getAdvice("sig-1")

    assertNotNull(entry)
    assertEquals("advice content", entry!!.value)
    assertTrue(entry.fetchedAtMillis > 0)
  }

  @Test
  fun getAdviceReturnsNullWhenSignatureDoesNotMatch() {
    cache.putAdvice("sig-1", "advice content")

    val entry = cache.getAdvice("sig-2")

    assertNull(entry)
  }

  @Test
  fun getAdviceReturnsNullWhenContentIsEmpty() {
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_ADVICE, "")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_ADVICE_TIME, System.currentTimeMillis())
      .putString(SharedPrefsAiForecastAdviceCache.KEY_ADVICE_SIG, "sig-1")
      .apply()

    val entry = cache.getAdvice("sig-1")

    assertNull(entry)
  }

  @Test
  fun getAdviceReturnsNullWhenEntryIsExpired() {
    val now = 1_700_000_000_000L
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_ADVICE, "advice content")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_ADVICE_TIME, now)
      .putString(SharedPrefsAiForecastAdviceCache.KEY_ADVICE_SIG, "sig-1")
      .apply()

    val entry = cache.getAdvice("sig-1", currentTime = now + 11 * 60 * 1000L)

    assertNull(entry)
  }

  @Test
  fun getAdviceReturnsNullWhenTimestampIsInFuture() {
    val now = 1_700_000_000_000L
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_ADVICE, "advice content")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_ADVICE_TIME, now + 10 * 60 * 1000L)
      .putString(SharedPrefsAiForecastAdviceCache.KEY_ADVICE_SIG, "sig-1")
      .apply()

    val entry = cache.getAdvice("sig-1", currentTime = now)

    assertNull(entry)
  }

  // ── Clear tests ───────────────────────────────────────────────────────

  @Test
  fun clearEmptiesBothForecastAndAdviceCaches() {
    cache.putForecast("sig-1", "forecast content")
    cache.putAdvice("sig-2", "advice content")

    cache.clear()

    assertNull(cache.getForecast("sig-1"))
    assertNull(cache.getAdvice("sig-2"))
  }

  // ── Overwrite tests ───────────────────────────────────────────────────

  @Test
  fun putForecastOverwritesPreviousEntry() {
    cache.putForecast("sig-1", "old forecast")
    cache.putForecast("sig-1", "new forecast")

    val entry = cache.getForecast("sig-1")

    assertNotNull(entry)
    assertEquals("new forecast", entry!!.value)
  }

  @Test
  fun putAdviceOverwritesPreviousEntry() {
    cache.putAdvice("sig-1", "old advice")
    cache.putAdvice("sig-1", "new advice")

    val entry = cache.getAdvice("sig-1")

    assertNotNull(entry)
    assertEquals("new advice", entry!!.value)
  }

  // ── Independent storage ───────────────────────────────────────────────

  @Test
  fun forecastAndAdviceCachesAreIndependent() {
    cache.putForecast("sig-1", "forecast content")
    cache.putAdvice("sig-1", "advice content")

    cache.clear()

    // After clear, both should be gone
    assertNull(cache.getForecast("sig-1"))
    assertNull(cache.getAdvice("sig-1"))
  }

  // ── Peek tests (signature-agnostic) ──────────────────────────────────

  @Test
  fun peekForecastReturnsEntryRegardlessOfSignature() {
    cache.putForecast("sig-real", "forecast content")

    // getForecast with wrong signature returns null
    assertNull(cache.getForecast("sig-other"))

    // peekForecast returns the entry despite different/unknown signature
    val entry = cache.peekForecast()
    assertNotNull(entry)
    assertEquals("forecast content", entry!!.value)
    assertEquals("sig-real", entry.signature)
  }

  @Test
  fun peekAdviceReturnsEntryRegardlessOfSignature() {
    cache.putAdvice("sig-real", "advice content")

    // getAdvice with wrong signature returns null
    assertNull(cache.getAdvice("sig-other"))

    // peekAdvice returns the entry despite different/unknown signature
    val entry = cache.peekAdvice()
    assertNotNull(entry)
    assertEquals("advice content", entry!!.value)
    assertEquals("sig-real", entry.signature)
  }

  @Test
  fun peekForecastReturnsNullWhenExpired() {
    val now = 1_700_000_000_000L
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST, "forecast content")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_TIME, now)
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_SIG, "sig-1")
      .apply()

    assertNull(cache.peekForecast(currentTime = now + 11 * 60 * 1000L))
  }

  @Test
  fun peekAdviceReturnsNullWhenExpired() {
    val now = 1_700_000_000_000L
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_ADVICE, "advice content")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_ADVICE_TIME, now)
      .putString(SharedPrefsAiForecastAdviceCache.KEY_ADVICE_SIG, "sig-1")
      .apply()

    assertNull(cache.peekAdvice(currentTime = now + 11 * 60 * 1000L))
  }

  @Test
  fun peekForecastReturnsNullWhenTimestampIsInFuture() {
    val now = 1_700_000_000_000L
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST, "forecast content")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_TIME, now + 10 * 60 * 1000L)
      .putString(SharedPrefsAiForecastAdviceCache.KEY_FORECAST_SIG, "sig-1")
      .apply()

    assertNull(cache.peekForecast(currentTime = now))
  }

  @Test
  fun peekAdviceReturnsNullWhenTimestampIsInFuture() {
    val now = 1_700_000_000_000L
    prefs
      .edit()
      .putString(SharedPrefsAiForecastAdviceCache.KEY_ADVICE, "advice content")
      .putLong(SharedPrefsAiForecastAdviceCache.KEY_ADVICE_TIME, now + 10 * 60 * 1000L)
      .putString(SharedPrefsAiForecastAdviceCache.KEY_ADVICE_SIG, "sig-1")
      .apply()

    assertNull(cache.peekAdvice(currentTime = now))
  }
}

/**
 * Minimal in-memory implementation of [SharedPreferences] for unit testing.
 * Supports the subset of operations used by [SharedPrefsAiForecastAdviceCache].
 */
private class FakeSharedPreferences : SharedPreferences {
  private val store = mutableMapOf<String, Any?>()
  private var editor: FakeEditor? = null

  override fun edit(): SharedPreferences.Editor {
    val e = FakeEditor(store)
    editor = e
    return e
  }

  override fun getString(
    key: String?,
    defValue: String?
  ): String? = store[key] as? String ?: defValue

  override fun getLong(
    key: String?,
    defValue: Long
  ): Long = store[key] as? Long ?: defValue

  override fun getInt(
    key: String?,
    defValue: Int
  ): Int = store[key] as? Int ?: defValue

  override fun getFloat(
    key: String?,
    defValue: Float
  ): Float = store[key] as? Float ?: defValue

  override fun getBoolean(
    key: String?,
    defValue: Boolean
  ): Boolean = store[key] as? Boolean ?: defValue

  override fun contains(key: String?): Boolean = store.containsKey(key)

  override fun getAll(): MutableMap<String, *> = store.toMutableMap()

  override fun getStringSet(
    key: String?,
    defValues: MutableSet<String>?
  ): MutableSet<String>? =
    @Suppress("UNCHECKED_CAST")
    (store[key] as? MutableSet<String>)
      ?: defValues

  override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
  }

  override fun unregisterOnSharedPreferenceChangeListener(
    listener: SharedPreferences.OnSharedPreferenceChangeListener?,
  ) {
  }

  private class FakeEditor(
    private val store: MutableMap<String, Any?>,
  ) : SharedPreferences.Editor {
    private val pending = mutableMapOf<String, Any?>()
    private val removals = mutableListOf<String>()

    override fun putString(
      key: String?,
      value: String?
    ): SharedPreferences.Editor {
      if (key != null) pending[key] = value
      return this
    }

    override fun putLong(
      key: String?,
      value: Long
    ): SharedPreferences.Editor {
      if (key != null) pending[key] = value
      return this
    }

    override fun putInt(
      key: String?,
      value: Int
    ): SharedPreferences.Editor {
      if (key != null) pending[key] = value
      return this
    }

    override fun putFloat(
      key: String?,
      value: Float
    ): SharedPreferences.Editor {
      if (key != null) pending[key] = value
      return this
    }

    override fun putBoolean(
      key: String?,
      value: Boolean
    ): SharedPreferences.Editor {
      if (key != null) pending[key] = value
      return this
    }

    override fun putStringSet(
      key: String?,
      values: MutableSet<String>?
    ): SharedPreferences.Editor {
      if (key != null) pending[key] = values
      return this
    }

    override fun remove(key: String?): SharedPreferences.Editor {
      if (key != null) removals.add(key)
      return this
    }

    override fun clear(): SharedPreferences.Editor {
      store.clear()
      return this
    }

    override fun commit(): Boolean {
      apply()
      return true
    }

    override fun apply() {
      store.putAll(pending)
      removals.forEach { store.remove(it) }
      pending.clear()
      removals.clear()
    }
  }
}
