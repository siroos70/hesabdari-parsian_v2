package io.github.mojri.hesabyar.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.mojri.hesabyar.core.AppLogger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

enum class AiProviderType(
  val displayName: String
) {
  GEMINI("Google Gemini"),
  OPENROUTER("OpenRouter"),
  CUSTOM("Custom Endpoint")
}

enum class AiModel(
  val displayName: String,
  val providerType: AiProviderType,
  val modelId: String,
  val supportsJson: Boolean = true,
  val supportsVision: Boolean = false
) {
  GEMINI_2_0_FLASH("Gemini 2.0 Flash", AiProviderType.GEMINI, "gemini-2.0-flash", supportsVision = true),
  GEMINI_1_5_PRO("Gemini 1.5 Pro", AiProviderType.GEMINI, "gemini-1.5-pro", supportsVision = true),
  GEMINI_1_5_FLASH("Gemini 1.5 Flash", AiProviderType.GEMINI, "gemini-1.5-flash", supportsVision = true),
  MISTRAL_LARGE("Mistral Large", AiProviderType.OPENROUTER, "mistralai/mistral-large-latest"),
  MISTRAL_SMALL("Mistral Small", AiProviderType.OPENROUTER, "mistralai/mistral-small-latest"),
  LLAMA_3_70B("Llama 3 70B", AiProviderType.OPENROUTER, "meta-llama/llama-3-70b-instruct"),
  LLAMA_3_8B("Llama 3 8B", AiProviderType.OPENROUTER, "meta-llama/llama-3-8b-instruct"),
  CLAUDE_SONNET("Claude Sonnet", AiProviderType.OPENROUTER, "anthropic/claude-3.5-sonnet"),
  CLAUDE_HAIKU("Claude Haiku", AiProviderType.OPENROUTER, "anthropic/claude-3.5-haiku"),
  CUSTOM_MODEL("Custom Model", AiProviderType.CUSTOM, "");

  companion object {
    fun forProvider(type: AiProviderType): List<AiModel> = entries.filter { it.providerType == type }
  }
}

data class AiProviderConfig(
  val id: String = UUID.randomUUID().toString(),
  val providerType: AiProviderType = AiProviderType.GEMINI,
  val apiKey: String = "",
  val model: String = "",
  val baseUrl: String = "",
  val label: String = ""
) {
  val isConfigured: Boolean
    get() = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"

  val displayName: String
    get() =
      label.ifBlank {
        when (providerType) {
          AiProviderType.GEMINI -> "Google Gemini"
          AiProviderType.OPENROUTER -> "OpenRouter"
          AiProviderType.CUSTOM -> "Custom"
        }
      }
}

data class ModelCacheEntry(
  val models: List<String>,
  val fetchedAt: Long = System.currentTimeMillis()
) {
  val isExpired: Boolean
    get() = System.currentTimeMillis() - fetchedAt > 24 * 60 * 60 * 1000
}

class AiConfigManager(
  context: Context
) {
  private val prefs: SharedPreferences = createEncryptedPrefs(context)

  companion object {
    private const val TAG = "AiConfigManager"
    private const val ENCRYPTED_PREFS_FILE = "ai_provider_config_encrypted"
    private const val LEGACY_PREFS_FILE = "ai_provider_config"
    private const val KEY_CONFIGS_JSON = "configs_json"
    private const val KEY_ACTIVE_ID = "active_id"
    private const val KEY_ONLINE_MODE = "online_mode"
    private const val KEY_MODEL_CACHE = "model_cache"
    private const val KEY_MIGRATION_COMPLETE = "migration_complete_v1"
  }

  private fun createEncryptedPrefs(context: Context): SharedPreferences {
    val masterKey =
      MasterKey
        .Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val encryptedPrefs =
      EncryptedSharedPreferences.create(
        context,
        ENCRYPTED_PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
      )

    migrateFromLegacyIfNeeded(context, encryptedPrefs)

    return encryptedPrefs
  }

  private fun migrateFromLegacyIfNeeded(
    context: Context,
    encryptedPrefs: SharedPreferences
  ) {
    if (encryptedPrefs.getBoolean(KEY_MIGRATION_COMPLETE, false)) return

    try {
      val legacyPrefs =
        context.getSharedPreferences(
          LEGACY_PREFS_FILE,
          Context.MODE_PRIVATE
        )

      val hasLegacyData =
        legacyPrefs.contains(KEY_CONFIGS_JSON) ||
          legacyPrefs.contains(KEY_ACTIVE_ID) ||
          legacyPrefs.contains(KEY_ONLINE_MODE) ||
          legacyPrefs.contains(KEY_MODEL_CACHE)

      if (!hasLegacyData) {
        AppLogger.d(TAG, "No legacy data found, marking migration complete")
        encryptedPrefs.edit().putBoolean(KEY_MIGRATION_COMPLETE, true).apply()
        return
      }

      val editor = encryptedPrefs.edit()

      val configsJson = legacyPrefs.getString(KEY_CONFIGS_JSON, null)
      if (!configsJson.isNullOrBlank()) {
        editor.putString(KEY_CONFIGS_JSON, configsJson)
      }

      val activeId = legacyPrefs.getString(KEY_ACTIVE_ID, null)
      if (!activeId.isNullOrBlank()) {
        editor.putString(KEY_ACTIVE_ID, activeId)
      }

      editor.putBoolean(KEY_ONLINE_MODE, legacyPrefs.getBoolean(KEY_ONLINE_MODE, true))

      val modelCache = legacyPrefs.getString(KEY_MODEL_CACHE, null)
      if (!modelCache.isNullOrBlank()) {
        editor.putString(KEY_MODEL_CACHE, modelCache)
      }

      editor.putBoolean(KEY_MIGRATION_COMPLETE, true)
      editor.apply()

      legacyPrefs.edit().clear().apply()

      AppLogger.i(TAG, "Successfully migrated AI configs to EncryptedSharedPreferences")
    } catch (e: SecurityException) {
      AppLogger.e(TAG, "Failed to migrate from legacy SharedPreferences", e)
      try {
        encryptedPrefs.edit().clear().apply()
      } catch (e: SecurityException) {
        AppLogger.e(TAG, "Failed to clear encrypted prefs after migration failure", e)
      }
    } catch (e: IllegalStateException) {
      AppLogger.e(TAG, "Failed to migrate from legacy SharedPreferences", e)
      try {
        encryptedPrefs.edit().clear().apply()
      } catch (e2: IllegalStateException) {
        AppLogger.e(TAG, "Failed to clear encrypted prefs after migration failure", e2)
      }
    }
  }

  private fun parseProviderType(obj: JSONObject): AiProviderType {
    val raw = obj.opt("providerType")
    return when (raw) {
      is String -> {
        try {
          AiProviderType.valueOf(raw)
        } catch (e: IllegalArgumentException) {
          AiProviderType.GEMINI
        }
      }
      is Number -> {
        val ordinal = raw.toInt()
        AiProviderType.entries.getOrElse(ordinal) { AiProviderType.GEMINI }
      }
      else -> AiProviderType.GEMINI
    }
  }

  fun loadConfigs(): List<AiProviderConfig> {
    val json = prefs.getString(KEY_CONFIGS_JSON, null)
    AppLogger.d(TAG, "loadConfigs: json ${if (json != null) "found (${json.length} chars)" else "is null"}")
    if (json == null) return emptyList()
    return try {
      val arr = JSONArray(json)
      val configs =
        (0 until arr.length())
          .map { i ->
            val obj = arr.getJSONObject(i)
            AiProviderConfig(
              id = obj.optString("id", ""),
              providerType = parseProviderType(obj),
              apiKey = obj.optString("apiKey", ""),
              model = obj.optString("model", ""),
              baseUrl = obj.optString("baseUrl", ""),
              label = obj.optString("label", "")
            )
          }.filter { it.id.isNotBlank() }
      AppLogger.d(TAG, "loadConfigs: parsed ${configs.size} configs")
      configs
    } catch (e: JSONException) {
      AppLogger.e(TAG, "Failed to parse configs", e)
      emptyList()
    }
  }

  fun saveConfigs(configs: List<AiProviderConfig>) {
    val arr = JSONArray()
    configs.forEach { c ->
      arr.put(
        JSONObject().apply {
          put("id", c.id)
          put("providerType", c.providerType.name)
          put("apiKey", c.apiKey)
          put("model", c.model)
          put("baseUrl", c.baseUrl)
          put("label", c.label)
        }
      )
    }
    val jsonStr = arr.toString()
    AppLogger.d(TAG, "saveConfigs: saving ${configs.size} configs (${jsonStr.length} chars)")
    prefs.edit().putString(KEY_CONFIGS_JSON, jsonStr).apply()
    AppLogger.d(TAG, "saveConfigs: write completed")
  }

  fun getActiveConfigId(): String? = prefs.getString(KEY_ACTIVE_ID, null)

  fun setActiveConfigId(id: String) {
    prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
  }

  fun getActiveConfig(): AiProviderConfig? {
    val configs = loadConfigs()
    val activeId = getActiveConfigId()
    AppLogger.d(TAG, "getActiveConfig: activeId=$activeId, configsCount=${configs.size}")
    val result = configs.find { it.id == activeId } ?: configs.firstOrNull { it.isConfigured }
    AppLogger.d(
      TAG,
      "getActiveConfig: result=${result?.let { "found(${it.providerType}, model=${it.model})" } ?: "null"}"
    )
    return result
  }

  fun isOnlineMode(): Boolean {
    val value = prefs.getBoolean(KEY_ONLINE_MODE, true)
    AppLogger.d(TAG, "isOnlineMode: $value")
    return value
  }

  fun setOnlineMode(online: Boolean) {
    AppLogger.d(TAG, "setOnlineMode: $online")
    prefs.edit().putBoolean(KEY_ONLINE_MODE, online).apply()
  }

  fun addConfig(config: AiProviderConfig): AiProviderConfig {
    val configs = loadConfigs().toMutableList()
    val newConfig = config.copy(id = UUID.randomUUID().toString())
    configs.add(newConfig)
    saveConfigs(configs)
    if (configs.size == 1) {
      setActiveConfigId(newConfig.id)
    }
    return newConfig
  }

  fun updateConfig(config: AiProviderConfig) {
    val configs = loadConfigs().toMutableList()
    val idx = configs.indexOfFirst { it.id == config.id }
    if (idx >= 0) {
      configs[idx] = config
      saveConfigs(configs)
    }
  }

  fun deleteConfig(id: String) {
    val configs = loadConfigs().toMutableList()
    configs.removeAll { it.id == id }
    saveConfigs(configs)
    if (getActiveConfigId() == id) {
      setActiveConfigId(configs.firstOrNull()?.id.orEmpty())
    }
  }

  fun getCachedModels(
    providerType: AiProviderType,
    apiKey: String = "",
    baseUrl: String = ""
  ): ModelCacheEntry? {
    val cacheKey = "${providerType.name}|$apiKey|$baseUrl"
    val json = prefs.getString(KEY_MODEL_CACHE, null) ?: return null
    return try {
      val obj = JSONObject(json)
      val providerJson = obj.optString(cacheKey, "")
      if (providerJson.isBlank()) return null
      val entry = JSONObject(providerJson)
      val modelsArr = entry.getJSONArray("models")
      val models = (0 until modelsArr.length()).map { modelsArr.getString(it) }
      ModelCacheEntry(
        models = models,
        fetchedAt = entry.getLong("fetchedAt")
      )
    } catch (e: Exception) {
      AppLogger.e(TAG, "Failed to retrieve cached models for $providerType", e)
      null
    }
  }

  fun cacheModels(
    providerType: AiProviderType,
    models: List<String>,
    apiKey: String = "",
    baseUrl: String = ""
  ) {
    val cacheKey = "${providerType.name}|$apiKey|$baseUrl"
    val existing =
      try {
        val json = prefs.getString(KEY_MODEL_CACHE, null)
        if (json != null) JSONObject(json) else JSONObject()
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to parse cached model JSON", e)
        JSONObject()
      }

    existing.put(
      cacheKey,
      JSONObject().apply {
        val arr = JSONArray()
        models.forEach { arr.put(it) }
        put("models", arr)
        put("fetchedAt", System.currentTimeMillis())
      }
    )

    prefs.edit().putString(KEY_MODEL_CACHE, existing.toString()).apply()
  }
}
