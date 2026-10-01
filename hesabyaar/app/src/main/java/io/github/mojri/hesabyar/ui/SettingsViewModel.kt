package io.github.mojri.hesabyar.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mojri.hesabyar.core.AppLogger
import io.github.mojri.hesabyar.domain.usecase.GetSettingsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
  @Inject
  constructor(
    private val getSettingsUseCase: GetSettingsUseCase
  ) : ViewModel() {
    var isDarkMode = mutableStateOf(getSettingsUseCase.isDarkMode())
      private set

    var currencyUnit = mutableStateOf(CurrencyUnit.fromKey(getSettingsUseCase.getCurrencyUnit()))
      private set

    fun toggleDarkMode() {
      isDarkMode.value = !isDarkMode.value
      getSettingsUseCase.setDarkMode(isDarkMode.value)
    }

    fun setCurrencyUnit(unit: CurrencyUnit) {
      if (currencyUnit.value == unit) return
      currencyUnit.value = unit
      getSettingsUseCase.setCurrencyUnit(unit.key)
      CurrencyFormatter.setUnit(unit)
    }

    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage = _uiMessage.asSharedFlow()

    fun showMessage(message: String) {
      viewModelScope.launch {
        _uiMessage.emit(message)
      }
    }

    fun getAiLogs(): List<AppLogger.LogEntry> = getSettingsUseCase.getAiLogs()

    fun clearLogs() = getSettingsUseCase.clearLogs()
  }
