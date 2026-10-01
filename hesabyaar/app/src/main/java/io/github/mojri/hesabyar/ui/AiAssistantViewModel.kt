package io.github.mojri.hesabyar.ui

import android.database.sqlite.SQLiteException
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.api.AiProviderType
import io.github.mojri.hesabyar.api.ParsedResult
import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.data.*
import io.github.mojri.hesabyar.domain.usecase.AiForecastAdviceCache
import io.github.mojri.hesabyar.domain.usecase.GetBudgetAdviceUseCase
import io.github.mojri.hesabyar.domain.usecase.GetForecastUseCase
import io.github.mojri.hesabyar.domain.usecase.ManageAiConfigUseCase
import io.github.mojri.hesabyar.domain.usecase.ParseTransactionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class AiAssistantViewModel
  @Inject
  constructor(
    private val parseTransactionUseCase: ParseTransactionUseCase,
    private val getBudgetAdviceUseCase: GetBudgetAdviceUseCase,
    private val getForecastUseCase: GetForecastUseCase,
    private val manageAiConfigUseCase: ManageAiConfigUseCase,
    private val aiForecastAdviceCache: AiForecastAdviceCache,
  ) : ViewModel() {
    var aiConfigs = mutableStateOf(manageAiConfigUseCase.loadConfigs())
      private set

    var activeConfigId = mutableStateOf(manageAiConfigUseCase.getActiveConfigId() ?: "")
      private set

    var isOnlineMode = mutableStateOf(manageAiConfigUseCase.isOnlineMode())
      private set

    fun toggleOnlineMode() {
      isOnlineMode.value = !isOnlineMode.value
      manageAiConfigUseCase.setOnlineMode(isOnlineMode.value)
      invalidateCaches()
    }

    fun getActiveConfig() = manageAiConfigUseCase.getActiveConfig()

    fun addAiConfig(config: io.github.mojri.hesabyar.api.AiProviderConfig) {
      val newConfig = manageAiConfigUseCase.addConfig(config)
      aiConfigs.value = manageAiConfigUseCase.loadConfigs()
      if (aiConfigs.value.size == 1) {
        activeConfigId.value = newConfig.id
        manageAiConfigUseCase.setActiveConfigId(newConfig.id)
      }
    }

    fun updateAiConfig(config: io.github.mojri.hesabyar.api.AiProviderConfig) {
      manageAiConfigUseCase.updateConfig(config)
      aiConfigs.value = manageAiConfigUseCase.loadConfigs()
    }

    fun deleteAiConfig(id: String) {
      manageAiConfigUseCase.deleteConfig(id)
      aiConfigs.value = manageAiConfigUseCase.loadConfigs()
      activeConfigId.value = manageAiConfigUseCase.getActiveConfigId() ?: ""
    }

    fun setActiveConfig(id: String) {
      activeConfigId.value = id
      manageAiConfigUseCase.setActiveConfigId(id)
      invalidateCaches()
    }

    fun isAiConfigured(): Boolean = manageAiConfigUseCase.isAiConfigured()

    fun getProviderStatusText(): String = manageAiConfigUseCase.getProviderStatusText()

    private val _modelFetchState = MutableStateFlow<UiResult<List<String>>>(UiResult.Idle)
    val modelFetchState = _modelFetchState.asStateFlow()

    fun fetchModels(
      providerType: AiProviderType,
      apiKey: String,
      baseUrl: String? = null
    ) {
      viewModelScope.launch {
        _modelFetchState.value = UiResult.Loading
        manageAiConfigUseCase
          .fetchModels(providerType, apiKey, baseUrl)
          .onSuccess { models ->
            if (models.isNotEmpty()) {
              _modelFetchState.value = UiResult.Success(models)
            } else {
              _modelFetchState.value = UiResult.Error("مدلی یافت نشد")
            }
          }.onFailure { e ->
            AppLogger.e("AiAssistantViewModel", "fetchModels failed", e)
            _modelFetchState.value = UiResult.Error("خطا در دریافت مدل‌ها")
          }
      }
    }

    fun clearModelFetchState() {
      _modelFetchState.value = UiResult.Idle
    }

    // ── Cache via AiForecastAdviceCache ──────────────────────────────────

    private val aiCacheDurationMs = 10 * 60 * 1000L

    /** Warm-start: restore persisted cache snapshots. */
    private val forecastEntry = aiForecastAdviceCache.peekForecast()
    private val adviceEntry = aiForecastAdviceCache.peekAdvice()

    /** Local snapshot of cached forecast content for StateFlow initialisation. */
    private var cachedForecast: String? = forecastEntry?.value

    /** Local snapshot of cached advice content for StateFlow initialisation. */
    private var cachedAdvice: String? = adviceEntry?.value

    private var lastForecastFetchTimeMs: Long = forecastEntry?.fetchedAtMillis ?: 0L
    private var lastAdviceFetchTimeMs: Long = adviceEntry?.fetchedAtMillis ?: 0L

    private var lastKnownForecastSignature = forecastEntry?.signature ?: ""
    private var lastKnownAdviceSignature = adviceEntry?.signature ?: ""

    private var forecastDebounceJob: Job? = null
    private var adviceDebounceJob: Job? = null

    private val _lastForecastFetchTime = MutableStateFlow(lastForecastFetchTimeMs)
    val lastForecastFetchTime: StateFlow<Long> = _lastForecastFetchTime.asStateFlow()

    private val _lastAdviceFetchTime = MutableStateFlow(lastAdviceFetchTimeMs)
    val lastAdviceFetchTime: StateFlow<Long> = _lastAdviceFetchTime.asStateFlow()

    private fun invalidateCaches() {
      cachedAdvice = null
      cachedForecast = null
      lastKnownAdviceSignature = ""
      lastKnownForecastSignature = ""
      lastAdviceFetchTimeMs = 0L
      lastForecastFetchTimeMs = 0L
      aiForecastAdviceCache.clear()
      _lastForecastFetchTime.value = 0L
      _lastAdviceFetchTime.value = 0L
    }

    fun getCachedForecast(): String? = cachedForecast

    fun getCachedAdvice(): String? = cachedAdvice

    fun formatLastFetchTime(timestamp: Long): String {
      if (timestamp == 0L) return "هنوز به‌روز نشده"
      val diff = System.currentTimeMillis() - timestamp
      val minutes = (diff / 60000).toInt()
      return when {
        minutes < 1 -> "همین الان"
        minutes == 1 -> "۱ دقیقه پیش"
        minutes < 60 -> "$minutes دقیقه پیش"
        else -> {
          val hours = minutes / 60
          if (hours == 1) "۱ ساعت پیش" else "$hours ساعت پیش"
        }
      }
    }

    internal fun configSignature(): String {
      val config = manageAiConfigUseCase.getActiveConfig()
      return "${config?.providerType?.name ?: "none"}" +
        "|${config?.model ?: ""}" +
        "|${config?.baseUrl ?: ""}" +
        "|${isOnlineMode.value}"
    }

    internal fun computeDataSignature(
      transactions: List<Transaction>,
      loans: List<Loan>,
      installments: List<Installment>,
      categories: List<Category>,
      bankLoans: List<BankLoan> = emptyList()
    ): String =
      AdviceSignature.computeDataSignature(
        transactions,
        loans,
        installments,
        categories,
        bankLoans
      ) + "|${configSignature()}"

    fun onFinancialDataChanged(
      transactions: List<Transaction>,
      loans: List<Loan>,
      installments: List<Installment>,
      categories: List<Category>,
      bankLoans: List<BankLoan>
    ) {
      val newSignature =
        computeDataSignature(transactions, loans, installments, categories, bankLoans)

      if (newSignature == lastKnownForecastSignature && !cachedForecast.isNullOrEmpty()) {
        return
      }

      forecastDebounceJob?.cancel()

      if (lastForecastFetchTimeMs == 0L) {
        viewModelScope.launch {
          fetchBudgetForecast(
            transactions,
            loans,
            installments,
            categories,
            isOnlineMode.value,
            bankLoans = bankLoans
          )
        }
      } else {
        forecastDebounceJob =
          viewModelScope.launch {
            delay(aiCacheDurationMs)
            fetchBudgetForecast(
              transactions,
              loans,
              installments,
              categories,
              isOnlineMode.value,
              bankLoans = bankLoans
            )
          }
      }
    }

    private val _parserState = MutableStateFlow<ParserUIState>(ParserUIState.Idle)
    val parserState = _parserState.asStateFlow()

    fun parseSmartSentence(
      sentence: String,
      isOnlineMode: Boolean
    ) {
      if (sentence.isBlank()) return
      viewModelScope.launch {
        _parserState.value = ParserUIState.Loading
        try {
          val config = if (isOnlineMode) manageAiConfigUseCase.getActiveConfig() else null
          AppLogger.d(
            "AiAssistantViewModel",
            "parseSmartSentence: isOnlineMode=$isOnlineMode, config=${config?.let {
              "found(${it.providerType}, model=${it.model})"
            } ?: "null"}"
          )
          val result = parseTransactionUseCase.parse(sentence, config)
          if (result != null) {
            _parserState.value = ParserUIState.Confirming(result)
          } else {
            _parserState.value = ParserUIState.Error("خطا در تحلیل متن")
          }
        } catch (e: java.io.IOException) {
          AppLogger.e("AiAssistantViewModel", "parseSmartSentence I/O error", e)
          _parserState.value = ParserUIState.Error("خطا در اتصال")
        } catch (e: IllegalArgumentException) {
          AppLogger.e("AiAssistantViewModel", "parseSmartSentence invalid argument", e)
          _parserState.value = ParserUIState.Error(e.localizedMessage ?: "ورودی نامعتبر")
        }
      }
    }

    fun confirmParsedResult(result: ParsedResult) {
      _parserState.value = ParserUIState.Success(result)
    }

    fun clearParserState() {
      _parserState.value = ParserUIState.Idle
    }

    fun approveParsedResult(
      result: ParsedResult,
      customDate: Long? = null
    ) {
      viewModelScope.launch {
        try {
          parseTransactionUseCase.approveParsedResult(result, customDate)
          _parserState.value = ParserUIState.Idle
        } catch (e: java.io.IOException) {
          AppLogger.e("AiAssistantViewModel", "approveParsedResult I/O failed", e)
        } catch (e: java.time.format.DateTimeParseException) {
          AppLogger.e("AiAssistantViewModel", "approveParsedResult date parse failed", e)
        }
      }
    }

    private val _advisorState =
      MutableStateFlow<UiResult<String>>(
        if (!cachedAdvice.isNullOrEmpty()) UiResult.Success(cachedAdvice.orEmpty()) else UiResult.Idle
      )
    val advisorState = _advisorState.asStateFlow()

    fun fetchBudgetAdvice(
      transactions: List<Transaction>,
      loans: List<Loan>,
      installments: List<Installment>,
      categories: List<Category>,
      isOnlineMode: Boolean,
      bankLoans: List<BankLoan> = emptyList(),
      forceRefresh: Boolean = false
    ) {
      val currentSignature =
        computeDataSignature(transactions, loans, installments, categories, bankLoans)

      if (!forceRefresh &&
        currentSignature == lastKnownAdviceSignature &&
        !cachedAdvice.isNullOrEmpty()
      ) {
        _advisorState.value = UiResult.Success(cachedAdvice.orEmpty())
        return
      }

      viewModelScope.launch {
        _advisorState.value = UiResult.Loading
        try {
          val config = if (isOnlineMode) manageAiConfigUseCase.getActiveConfig() else null
          val advice =
            getBudgetAdviceUseCase.getAdvice(
              transactions,
              loans,
              installments,
              categories,
              config,
              bankLoans
            )
          cachedAdvice = advice
          lastAdviceFetchTimeMs = System.currentTimeMillis()
          lastKnownAdviceSignature = currentSignature
          _lastAdviceFetchTime.value = lastAdviceFetchTimeMs
          aiForecastAdviceCache.putAdvice(currentSignature, advice)
          _advisorState.value = UiResult.Success(advice)
        } catch (e: java.io.IOException) {
          AppLogger.e("AiAssistantViewModel", "Network or I/O error in fetchBudgetAdvice", e)
          _advisorState.value = UiResult.Error("خطای شبکه یا ورودی/خروجی")
        } catch (e: retrofit2.HttpException) {
          AppLogger.e("AiAssistantViewModel", "HTTP error in fetchBudgetAdvice", e)
          _advisorState.value = UiResult.Error("خطای ارتباط با سرور")
        } catch (e: JSONException) {
          AppLogger.e("AiAssistantViewModel", "Data parsing error in fetchBudgetAdvice", e)
          _advisorState.value = UiResult.Error("خطای تجزیه داده‌ها")
        } catch (e: android.database.sqlite.SQLiteException) {
          AppLogger.e("AiAssistantViewModel", "Database error in persistAdviceCache", e)
          _advisorState.value = UiResult.Error("خطای پایگاه داده")
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          AppLogger.e("AiAssistantViewModel", "Unexpected error in fetchBudgetAdvice", e)
          _advisorState.value = UiResult.Error("خطای ناشناخته در دریافت توصیه‌ها")
        }
      }
    }

    fun clearAdvisorState() {
      _advisorState.value = UiResult.Idle
    }

    private val _forecastState =
      MutableStateFlow<UiResult<String>>(
        if (!cachedForecast.isNullOrEmpty()) UiResult.Success(cachedForecast.orEmpty()) else UiResult.Idle
      )
    val forecastState = _forecastState.asStateFlow()

    @Suppress("CyclomaticComplexMethod")
    fun fetchBudgetForecast(
      transactions: List<Transaction>,
      loans: List<Loan>,
      installments: List<Installment>,
      categories: List<Category>,
      isOnlineMode: Boolean,
      bankLoans: List<BankLoan> = emptyList(),
      forceRefresh: Boolean = false
    ) {
      val currentSignature =
        computeDataSignature(transactions, loans, installments, categories, bankLoans)

      if (!forceRefresh &&
        currentSignature == lastKnownForecastSignature &&
        !cachedForecast.isNullOrEmpty()
      ) {
        _forecastState.value = UiResult.Success(cachedForecast.orEmpty())
        return
      }

      viewModelScope.launch {
        _forecastState.value = UiResult.Loading
        try {
          val config = if (isOnlineMode) manageAiConfigUseCase.getActiveConfig() else null
          val forecast =
            getForecastUseCase.getForecast(
              transactions,
              loans,
              installments,
              categories,
              config,
              bankLoans
            )
          cachedForecast = forecast
          lastForecastFetchTimeMs = System.currentTimeMillis()
          lastKnownForecastSignature = currentSignature
          _lastForecastFetchTime.value = lastForecastFetchTimeMs
          aiForecastAdviceCache.putForecast(currentSignature, forecast)
          _forecastState.value = UiResult.Success(forecast)
        } catch (e: IOException) {
          AppLogger.e("AiAssistantViewModel", "fetchBudgetForecast failed I/O", e)
          _forecastState.value = UiResult.Error("خطای I/O در پیش‌بینی بودجه")
        } catch (e: HttpException) {
          AppLogger.e("AiAssistantViewModel", "fetchBudgetForecast failed HTTP", e)
          _forecastState.value = UiResult.Error("خطای شبکه در پیش‌بینی بودجه")
        } catch (e: JSONException) {
          AppLogger.e("AiAssistantViewModel", "fetchBudgetForecast failed JSON parse", e)
          _forecastState.value = UiResult.Error("خطای تجزیه داده‌ها")
        } catch (e: SQLiteException) {
          AppLogger.e("AiAssistantViewModel", "fetchBudgetForecast failed DB", e)
          _forecastState.value = UiResult.Error("خطای پایگاه داده در پیش‌بینی بودجه")
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          AppLogger.e("AiAssistantViewModel", "Unexpected error in fetchBudgetForecast", e)
          _forecastState.value = UiResult.Error("خطای ناشناخته در پیش‌بینی بودجه")
        }
      }
    }

    fun clearForecastState() {
      _forecastState.value = UiResult.Idle
    }
  }
