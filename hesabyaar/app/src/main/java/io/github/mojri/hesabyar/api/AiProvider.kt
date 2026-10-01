package io.github.mojri.hesabyar.api

import io.github.mojri.hesabyar.core.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object AiProvider {
  private const val TAG = "AiProvider"
  private const val ERR_EMPTY_RESPONSE = "Empty response body"
  private const val HEADER_AUTHORIZATION = "Authorization"
  private const val BEARER_PREFIX = "Bearer "
  private const val KEY_PARTS = "parts"
  private const val KEY_TEXT = "text"
  private const val KEY_ROLE = "role"
  private const val KEY_CONTENT = "content"
  private const val ERR_HTTP_FORMAT = "HTTP %d: %s"

  private fun Request.Builder.addBearerAuthorization(apiKey: String?) {
    apiKey?.takeIf { it.isNotBlank() && it.none { c -> c == '\n' || c == '\r' } }?.let { key ->
      addHeader(HEADER_AUTHORIZATION, "$BEARER_PREFIX$key")
    }
  }

  private val client =
    OkHttpClient
      .Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .build()

  data class ChatMessage(
    val role: String,
    val content: String
  )

  sealed class ApiResult {
    data class Success(
      val text: String
    ) : ApiResult()

    data class Failure(
      val error: String
    ) : ApiResult()
  }

  data class FetchedModel(
    val id: String,
    val displayName: String,
    val provider: String,
    val isFree: Boolean
  )

  suspend fun generateContent(
    config: AiProviderConfig,
    prompt: String,
    systemInstruction: String? = null,
    temperature: Double = 0.1,
    responseMimeType: String? = null
  ): ApiResult =
    withContext(Dispatchers.IO) {
      AppLogger.d(
        TAG,
        "generateContent: provider=${config.providerType}, model=${config.model}, isConfigured=${config.isConfigured}, apiKeyLength=${config.apiKey.length}"
      )
      if (!config.isConfigured) {
        AppLogger.w(TAG, "generateContent: API key not configured")
        return@withContext ApiResult.Failure("API key not configured")
      }

      when (config.providerType) {
        AiProviderType.GEMINI -> callGemini(config, prompt, systemInstruction, temperature, responseMimeType)
        AiProviderType.OPENROUTER ->
          callOpenAiCompatible(
            config,
            prompt,
            systemInstruction,
            temperature,
            responseMimeType,
            isGeminiFormat = true
          )
        AiProviderType.CUSTOM ->
          callOpenAiCompatible(
            config,
            prompt,
            systemInstruction,
            temperature,
            responseMimeType,
            isGeminiFormat = false
          )
      }
    }

  suspend fun fetchModels(
    providerType: AiProviderType,
    apiKey: String,
    baseUrl: String? = null
  ): Result<List<FetchedModel>> =
    withContext(Dispatchers.IO) {
      try {
        Result.success(
          when (providerType) {
            AiProviderType.GEMINI -> fetchGeminiModels(apiKey)
            AiProviderType.OPENROUTER -> fetchOpenRouterModels(apiKey)
            AiProviderType.CUSTOM -> fetchCustomModels(apiKey, baseUrl.orEmpty())
          }
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: IOException) {
        AppLogger.e(TAG, "Failed to fetch models for $providerType due to I/O error", e)
        Result.failure(e)
      } catch (e: JSONException) {
        AppLogger.e(TAG, "Failed to parse model list response for $providerType", e)
        Result.failure(e)
      } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to fetch models for $providerType", e)
        Result.failure(e)
      }
    }

  private fun fetchGeminiModels(apiKey: String): List<FetchedModel> {
    val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
    val request =
      Request
        .Builder()
        .url(url)
        .get()
        .build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        val body = response.body?.string().orEmpty()
        throw IOException(ERR_HTTP_FORMAT.format(response.code, body))
      }
      val body = response.body?.string()
      if (body.isNullOrBlank()) throw IOException(ERR_EMPTY_RESPONSE)
      val json = JSONObject(body)
      val modelsArray = json.getJSONArray("models")

      return (0 until modelsArray.length())
        .mapNotNull { i ->
          val model = modelsArray.getJSONObject(i)
          val name = model.getString("name").removePrefix("models/")
          val displayName = model.optString("displayName", name)
          val methods = model.optJSONArray("supportedGenerationMethods")
          val supportsGenerate =
            (0 until (methods?.length() ?: 0)).any {
              methods!!.getString(it) == "generateContent"
            }
          if (!supportsGenerate) return@mapNotNull null

          val family =
            when {
              name.contains("gemini", ignoreCase = true) -> "Gemini"
              name.contains("gemma", ignoreCase = true) -> "Gemma"
              name.contains("imagen", ignoreCase = true) -> "Imagen"
              else -> "Other"
            }

          FetchedModel(
            id = name,
            displayName = "$displayName ($family)",
            provider = family,
            isFree = false
          )
        }.sortedBy { it.displayName }
    }
  }

  private fun fetchOpenRouterModels(apiKey: String): List<FetchedModel> {
    val url = "https://openrouter.ai/api/v1/models"
    val requestBuilder =
      Request
        .Builder()
        .url(url)
    requestBuilder.addBearerAuthorization(apiKey)
    val request = requestBuilder.get().build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        val body = response.body?.string().orEmpty()
        throw IOException(ERR_HTTP_FORMAT.format(response.code, body))
      }
      val body = response.body?.string()
      if (body.isNullOrBlank()) throw IOException(ERR_EMPTY_RESPONSE)
      val json = JSONObject(body)
      val modelsArray = json.getJSONArray("data")

      return (0 until modelsArray.length())
        .map { i ->
          val model = modelsArray.getJSONObject(i)
          val id = model.getString("id")
          val name = model.optString("name", id)
          val pricing = model.optJSONObject("pricing")
          val promptPrice = pricing?.optString("prompt", "0")?.toDoubleOrNull() ?: 0.0
          val provider = id.split("/").firstOrNull() ?: "unknown"

          FetchedModel(
            id = id,
            displayName = "$name ($provider)",
            provider = provider.replaceFirstChar { it.uppercase() },
            isFree = promptPrice == 0.0
          )
        }.sortedWith(compareBy<FetchedModel> { !it.isFree }.thenBy { it.displayName })
    }
  }

  private fun fetchCustomModels(
    apiKey: String,
    baseUrl: String
  ): List<FetchedModel> {
    if (baseUrl.isBlank()) return emptyList()
    val url = "${baseUrl.trimEnd('/')}/models"
    val requestBuilder =
      Request
        .Builder()
        .url(url)
    requestBuilder.addBearerAuthorization(apiKey)
    val request = requestBuilder.get().build()

    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) {
        val body = response.body?.string().orEmpty()
        throw IOException(ERR_HTTP_FORMAT.format(response.code, body))
      }
      val body = response.body?.string()
      if (body.isNullOrBlank()) throw IOException(ERR_EMPTY_RESPONSE)
      val json = JSONObject(body)
      val modelsArray = json.optJSONArray("data") ?: throw IOException("No 'data' array in response")

      return (0 until modelsArray.length())
        .map { i ->
          val model = modelsArray.getJSONObject(i)
          val id = model.getString("id")
          val name = model.optString("name", id)

          FetchedModel(
            id = id,
            displayName = name,
            provider = "Custom",
            isFree = false
          )
        }.sortedBy { it.displayName }
    }
  }

  private fun callGemini(
    config: AiProviderConfig,
    prompt: String,
    systemInstruction: String?,
    temperature: Double,
    responseMimeType: String?
  ): ApiResult {
    val model = config.model.ifBlank { "gemini-2.0-flash" }

    val requestJson =
      JSONObject().apply {
        put(
          "contents",
          JSONArray().put(
            JSONObject().apply {
              put(
                KEY_PARTS,
                JSONArray().put(
                  JSONObject().apply {
                    put(KEY_TEXT, prompt)
                  }
                )
              )
            }
          )
        )
        if (systemInstruction != null) {
          put(
            "systemInstruction",
            JSONObject().apply {
              put(
                KEY_PARTS,
                JSONArray().put(
                  JSONObject().apply {
                    put(KEY_TEXT, systemInstruction)
                  }
                )
              )
            }
          )
        }
        put(
          "generationConfig",
          JSONObject().apply {
            put("temperature", temperature)
            if (responseMimeType != null) {
              put("responseMimeType", responseMimeType)
            }
          }
        )
      }

    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${config.apiKey}"
    return executePost(url, requestJson, ::parseGeminiResponse, apiKey = null)
  }

  private fun callOpenAiCompatible(
    config: AiProviderConfig,
    prompt: String,
    systemInstruction: String?,
    temperature: Double,
    responseMimeType: String?,
    isGeminiFormat: Boolean
  ): ApiResult {
    val baseUrl =
      config.baseUrl.ifBlank {
        when {
          isGeminiFormat -> "https://openrouter.ai/api/v1"
          else -> ""
        }
      }
    if (baseUrl.isBlank()) {
      return ApiResult.Failure("Base URL is required for custom provider")
    }

    val model = config.model.ifBlank { return ApiResult.Failure("Model is required") }

    val messages = JSONArray()
    if (systemInstruction != null) {
      messages.put(
        JSONObject().apply {
          put(KEY_ROLE, "system")
          put(KEY_CONTENT, systemInstruction)
        }
      )
    }
    messages.put(
      JSONObject().apply {
        put(KEY_ROLE, "user")
        put(KEY_CONTENT, prompt)
      }
    )

    val requestJson =
      JSONObject().apply {
        put("model", model)
        put("messages", messages)
        put("temperature", temperature)
        if (responseMimeType == "application/json") {
          put("response_format", JSONObject().put("type", "json_object"))
        }
      }

    val url = "${baseUrl.trimEnd('/')}/chat/completions"
    return executePost(
      url,
      requestJson,
      ::parseOpenAiResponse,
      apiKey = config.apiKey,
      isOpenRouter = isGeminiFormat
    )
  }

  private fun executePost(
    url: String,
    body: JSONObject,
    responseParser: (String) -> ApiResult,
    apiKey: String? = null,
    isOpenRouter: Boolean = false
  ): ApiResult {
    val mediaType = "application/json; charset=utf-8".toMediaType()
    val requestBody = body.toString().toRequestBody(mediaType)
    val requestBuilder =
      Request
        .Builder()
        .url(url)
        .post(requestBody)

    requestBuilder.addBearerAuthorization(apiKey)
    if (isOpenRouter) {
      requestBuilder.addHeader("HTTP-Referer", "https://github.com/mojri/hesabyar")
      requestBuilder.addHeader("X-Title", "Hesabyar")
    }

    val request = requestBuilder.build()

    return try {
      client.newCall(request).execute().use { response ->
        val bodyStr = response.body?.string()
        if (!response.isSuccessful) {
          AppLogger.e(TAG, "API error ${response.code} for URL ${url.substringBefore("?")}: ${bodyStr ?: "null body"}")
          ApiResult.Failure("API error ${response.code}: ${bodyStr ?: "null body"}")
        } else if (bodyStr.isNullOrBlank()) {
          AppLogger.e(TAG, "Null/empty response body for URL ${url.substringBefore("?")} (HTTP ${response.code})")
          ApiResult.Failure(ERR_EMPTY_RESPONSE)
        } else {
          responseParser(bodyStr)
        }
      }
    } catch (e: IOException) {
      AppLogger.e(TAG, "API call failed", e)
      ApiResult.Failure("Network error: ${e.localizedMessage ?: "خطای شبکه"}")
    }
  }

  private fun parseGeminiResponse(bodyStr: String): ApiResult =
    try {
      val json = JSONObject(bodyStr)
      val candidates = json.getJSONArray("candidates")
      if (candidates.length() > 0) {
        val parts =
          candidates
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
        if (parts.length() > 0) {
          val text = parts.getJSONObject(0).getString("text")
          ApiResult.Success(text)
        } else {
          ApiResult.Failure("Empty response parts")
        }
      } else {
        ApiResult.Failure("No candidates in response")
      }
    } catch (e: JSONException) {
      AppLogger.e(TAG, "Failed to parse Gemini response", e)
      ApiResult.Failure("Failed to parse response: ${e.localizedMessage ?: "خطای پردازش پاسخ"}")
    }

  private fun parseOpenAiResponse(bodyStr: String): ApiResult =
    try {
      val json = JSONObject(bodyStr)
      val choices = json.getJSONArray("choices")
      if (choices.length() > 0) {
        val message = choices.getJSONObject(0).getJSONObject("message")
        val text = message.getString("content")
        ApiResult.Success(text)
      } else {
        ApiResult.Failure("No choices in response")
      }
    } catch (e: JSONException) {
      AppLogger.e(TAG, "Failed to parse OpenAI response", e)
      ApiResult.Failure("Failed to parse response: ${e.localizedMessage ?: "خطای پردازش پاسخ"}")
    }
}
