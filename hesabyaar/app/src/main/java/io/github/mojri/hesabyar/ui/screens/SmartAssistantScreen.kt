@file:Suppress("TooManyFunctions")

package io.github.mojri.hesabyar.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mojri.hesabyar.api.ParsedResult
import io.github.mojri.hesabyar.data.Category
import io.github.mojri.hesabyar.data.CategoryType
import io.github.mojri.hesabyar.data.TransactionType
import io.github.mojri.hesabyar.ui.AiAssistantViewModel
import io.github.mojri.hesabyar.ui.CategoryViewModel
import io.github.mojri.hesabyar.ui.CurrencyFormatter
import io.github.mojri.hesabyar.ui.DashboardViewModel
import io.github.mojri.hesabyar.ui.ParserUIState
import io.github.mojri.hesabyar.ui.SettingsViewModel
import io.github.mojri.hesabyar.ui.UiResult
import io.github.mojri.hesabyar.ui.components.AmountQuickFillButtons
import io.github.mojri.hesabyar.ui.components.ButtonVariant
import io.github.mojri.hesabyar.ui.components.HesabyarButton
import io.github.mojri.hesabyar.ui.components.HesabyarCard
import io.github.mojri.hesabyar.ui.components.HesabyarChip
import io.github.mojri.hesabyar.ui.components.HesabyarInputField
import io.github.mojri.hesabyar.ui.components.JalaliDateTimePicker
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ElevationTokens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens
import io.github.mojri.hesabyar.ui.designsystem.SpacingTokens
import java.util.*
import java.util.Calendar

private const val CONFIDENCE_HIGH_THRESHOLD = 0.9f
private const val CONFIDENCE_MEDIUM_THRESHOLD = 0.7f

@Composable
fun SmartAssistantScreen(
  aiAssistantViewModel: AiAssistantViewModel,
  categoryViewModel: CategoryViewModel,
  dashboardViewModel: DashboardViewModel,
  settingsViewModel: SettingsViewModel,
  modifier: Modifier = Modifier,
  startVoiceOnOpen: Boolean = false,
  onVoiceStarted: () -> Unit = {}
) {
  val context = LocalContext.current

  var activeTab by remember { mutableStateOf(0) } // 0 = Smart Registration, 1 = Budget Advice
  var inputText by remember { mutableStateOf("") }
  val parserState by aiAssistantViewModel.parserState.collectAsState()
  val categories by categoryViewModel.categories.collectAsState()
  val scrollState = rememberScrollState()

  // Intent-based Speech Recognition Launcher for native Persian voice-to-text
  val speechLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        val spokenTextList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val text = spokenTextList?.firstOrNull().orEmpty()
        if (text.isNotBlank()) {
          inputText = text
          aiAssistantViewModel.parseSmartSentence(text, aiAssistantViewModel.isOnlineMode.value)
        }
      }
    }

  fun startVoiceInput() {
    try {
      val intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fa-IR")
          putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "fa-IR")
          putExtra(RecognizerIntent.EXTRA_PROMPT, "صحبت کنید (مثلا: امروز مرغ خریدم ۴۵۰ هزار تومن)")
        }
      speechLauncher.launch(intent)
    } catch (e: Exception) {
      e.printStackTrace()
      settingsViewModel.showMessage("سیستم گفتار به نوشتار در دستگاه شما در دسترس نیست")
    }
  }

  LaunchedEffect(startVoiceOnOpen) {
    if (startVoiceOnOpen) {
      activeTab = 0
      onVoiceStarted()
      startVoiceInput()
    }
  }

  if (parserState is ParserUIState.Confirming) {
    val confirmingState = parserState as ParserUIState.Confirming
    ConfirmationDialog(
      result = confirmingState.result,
      onConfirm = {
        aiAssistantViewModel.confirmParsedResult(confirmingState.result)
      },
      onCancel = { aiAssistantViewModel.clearParserState() }
    )
  } else if (parserState is ParserUIState.Success) {
    val successState = parserState as ParserUIState.Success
    ParsedResultCard(
      result = successState.result,
      categories = categories,
      onApprove = { updatedResult, approvedTimestamp ->
        aiAssistantViewModel.approveParsedResult(updatedResult, approvedTimestamp)
        inputText = ""
      },
      onCancel = { aiAssistantViewModel.clearParserState() },
      modifier =
        modifier
          .fillMaxSize()
          .padding(SpacingTokens.lg)
    )
  } else {
    Column(
      modifier = modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Top,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Material 3 Responsive Tab Selector
      TabRow(
        selectedTabIndex = activeTab,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer) }
      ) {
        Tab(
          selected = activeTab == 0,
          onClick = { activeTab = 0 },
          text = {
            Text(
              "ثبت هوشمند تراکنش",
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.bodyMedium
            )
          },
          icon = {
            Icon(
              Icons.Filled.ReceiptLong,
              contentDescription = null,
              modifier = Modifier.size(20.dp)
            )
          }
        )
        Tab(
          selected = activeTab == 1,
          onClick = { activeTab = 1 },
          text = {
            Text(
              "مشاوره و تحلیل بودجه",
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.bodyMedium
            )
          },
          icon = {
            Icon(
              Icons.Filled.AutoAwesome,
              contentDescription = null,
              modifier = Modifier.size(20.dp)
            )
          }
        )
      }

      if (activeTab == 0) {
        // Main Transaction Entry Scrollable Form Screen
        Column(
          modifier =
            Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(SpacingTokens.lg)
              .verticalScroll(scrollState),
          verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // AI Title / Visual Identity Header
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(ShapeTokens.XLarge)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                .padding(SpacingTokens.xl)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
            ) {
              Box(
                modifier =
                  Modifier
                    .size(Dimens.ButtonHeight)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.AutoAwesome,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(Dimens.IconMedium)
                )
              }
              Column {
                Text(
                  text = "دستیار صوتی و متنی هوشمند",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = "جمله خود را به زبان فارسی ساده تایپ کنید یا بگویید تا هوش مصنوعی آن را تحلیل کند.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                  lineHeight = 18.sp
                )
              }
            }
          }

          // Suggested Examples Card
          HesabyarCard(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeTokens.Large
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
            ) {
              Text(
                text = "💡 عبارت‌های نمونه جهت آزمایش:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
              val examples =
                listOf(
                  "۵۰۰ هزار تومن بابت برق",
                  "امروز حقوق گرفتم ۲۰ میلیون",
                  "به علی ۲ میلیون قرض دادم",
                  "از رضا ۳ میلیون طلب دارم",
                  "قسط وام مسکن رو پرداخت کردم"
                )
              examples.forEach { ex ->
                Row(
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .clip(ShapeTokens.Small)
                      .background(MaterialTheme.colorScheme.surfaceContainer)
                      .clickable {
                        inputText = ex
                        aiAssistantViewModel.parseSmartSentence(
                          ex,
                          aiAssistantViewModel.isOnlineMode.value
                        )
                      }.padding(horizontal = SpacingTokens.md, vertical = SpacingTokens.sm),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = ex,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(Dimens.IconSmall)
                  )
                }
              }
            }
          }

          // Input Area
          HesabyarCard(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeTokens.XLarge
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
            ) {
              HesabyarInputField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier =
                  Modifier
                    .height(110.dp)
                    .testTag("sentence_input"),
                placeholder = "عبارت مالی خود را اینجا بنویسید...",
                shape = ShapeTokens.Medium,
                singleLine = false
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
              ) {
                // Voice Mic button (Aggressive support for speech)
                IconButton(
                  onClick = { startVoiceInput() },
                  modifier =
                    Modifier
                      .size(52.dp)
                      .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                      .testTag("voice_input_button")
                ) {
                  Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "ثبت صوتی",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(Dimens.IconMedium)
                  )
                }

                // Main Text Parse Submit Button
                HesabyarButton(
                  onClick = {
                    if (inputText.isNotBlank()) {
                      aiAssistantViewModel.parseSmartSentence(
                        inputText,
                        aiAssistantViewModel.isOnlineMode.value
                      )
                    } else {
                      settingsViewModel.showMessage("لطفا متنی را جهت تحلیل وارد کنید")
                    }
                  },
                  modifier =
                    Modifier
                      .weight(1f)
                      .testTag("submit_assistant_button"),
                  text = "آماده‌سازی تراکنش با هوش مصنوعی",
                  icon = Icons.Filled.NavigateNext
                )
              }
            }
          }

          // Parser State Outputs (Interactive Results Card)
          AnimatedContent(
            targetState = parserState,
            label = "ParserState"
          ) { state ->
            when (state) {
              is ParserUIState.Idle -> {
                // Say nothing or idle tip
              }
              is ParserUIState.Loading -> {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(SpacingTokens.xl),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
                ) {
                  CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                  Text(
                    text = "درحال تحلیل ساختاری توسط حسابیار هوشمند...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                  )
                }
              }
              is ParserUIState.Error -> {
                HesabyarCard(
                  modifier = Modifier.fillMaxWidth(),
                  cardColors =
                    CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                  contentPadding = PaddingValues(SpacingTokens.lg)
                ) {
                  Text(
                    text =
                      state.message +
                        "\nتلاش برای استخراج خودکار انجام نشد. لطفا واضح‌تر وارد کنید.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                  )
                }
              }
              is ParserUIState.Confirming -> {
                // Handled at top-level
              }
              is ParserUIState.Success -> {
                // Handled at top-level
              }
            }
          }
        }
      } else {
        // Budget Advice Tab (Gemini/Offline)
        val advisorState by aiAssistantViewModel.advisorState.collectAsState()
        val isApiKeyReady = aiAssistantViewModel.isAiConfigured()

        Column(
          modifier =
            Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(SpacingTokens.lg)
              .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          // Advice Header Banner
          HesabyarCard(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeTokens.XLarge,
            cardColors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
              ),
            contentPadding = PaddingValues(SpacingTokens.lg)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
            ) {
              Box(
                modifier =
                  Modifier
                    .size(52.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.TipsAndUpdates,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(26.dp)
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "مشاور مدیریت هوشمند بودجه",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text =
                    if (isApiKeyReady) {
                      "طراحی شده با مدل هوش مصنوعی ابری (${aiAssistantViewModel.getProviderStatusText()})"
                    } else {
                      "طراحی شده با مدل تحلیل هوش مالی محلی (آفلاین)"
                    },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
              }
            }
          }

          // Main recommendations body state machine
          when (val state = advisorState) {
            is UiResult.Idle -> {
              HesabyarCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeTokens.XLarge,
                contentPadding = PaddingValues(SpacingTokens.xl)
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
                ) {
                  Box(
                    modifier =
                      Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Analytics,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(Dimens.IconLarge)
                    )
                  }

                  Text(
                    text = "آماده دریافت توصیه‌های مالی هستید؟",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                  )

                  Text(
                    text =
                      "دستیار مالی حسابیار با پایش ریز تراکنش‌ها، مبالغ اقساط و روند" +
                        " تراز دریافتی و پرداختی‌های شما، بهترین نکات کلیدی کاهش مخارج و" +
                        " بهبود نرخ ثروت‌آفرینی را ارزیابی می‌کند.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                  )

                  HesabyarButton(
                    onClick = {
                      aiAssistantViewModel.fetchBudgetAdvice(
                        dashboardViewModel.transactions.value,
                        dashboardViewModel.loans.value,
                        dashboardViewModel.installments.value,
                        dashboardViewModel.categories.value,
                        aiAssistantViewModel.isOnlineMode.value,
                        dashboardViewModel.bankLoans.value
                      )
                    },
                    text = "تحلیل هوشمند بودجه و مخارج",
                    icon = Icons.Filled.AutoAwesome,
                    modifier = Modifier.fillMaxWidth()
                  )
                }
              }
            }
            is UiResult.Loading -> {
              HesabyarCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeTokens.XLarge,
                contentPadding = PaddingValues(SpacingTokens.xxl)
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
                ) {
                  CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.ButtonHeight)
                  )
                  Text(
                    text = "حسابیار با طعم هوش مصنوعی...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                  )
                  Text(
                    text =
                      "در حال تجمیع اطلاعات حساب‌ها، مخارج جاری، تراز اقساط و کشف" +
                        " الگوهای مخارج پنهان شما برای نگارش گزارش مشورتی شخصی...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                  )
                }
              }
            }
            is UiResult.Success -> {
              val lastAdviceFetchTime by aiAssistantViewModel.lastAdviceFetchTime.collectAsState()
              HesabyarCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeTokens.XLarge,
                contentPadding = PaddingValues(SpacingTokens.lg)
              ) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "💡 نتایج مشاوره و توصیه‌های بودجه",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = {
                      aiAssistantViewModel.fetchBudgetAdvice(
                        dashboardViewModel.transactions.value,
                        dashboardViewModel.loans.value,
                        dashboardViewModel.installments.value,
                        dashboardViewModel.categories.value,
                        aiAssistantViewModel.isOnlineMode.value,
                        dashboardViewModel.bankLoans.value,
                        forceRefresh = true
                      )
                    }) {
                      Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "بروزرسانی گزارش",
                        tint = MaterialTheme.colorScheme.primary
                      )
                    }
                  }

                  HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceContainer
                  )

                  MarkdownText(text = state.data)

                  Text(
                    text = "آخرین به‌روزرسانی: ${aiAssistantViewModel.formatLastFetchTime(
                      lastAdviceFetchTime
                    )}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )

                  Spacer(modifier = Modifier.height(SpacingTokens.md))

                  HesabyarButton(
                    onClick = {
                      aiAssistantViewModel.fetchBudgetAdvice(
                        dashboardViewModel.transactions.value,
                        dashboardViewModel.loans.value,
                        dashboardViewModel.installments.value,
                        dashboardViewModel.categories.value,
                        aiAssistantViewModel.isOnlineMode.value,
                        dashboardViewModel.bankLoans.value,
                        forceRefresh = true
                      )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    text = "به‌روزرسانی مشاوره",
                    icon = Icons.Filled.Refresh
                  )
                }
              }
            }
            is UiResult.Error -> {
              HesabyarCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeTokens.XLarge,
                cardColors =
                  CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                  ),
                contentPadding = PaddingValues(SpacingTokens.xl)
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)
                ) {
                  Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(44.dp)
                  )
                  Text(
                    text = "خطا در تنظیم و ارزیابی عادات مالی",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                  )
                  Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                  )
                  HesabyarButton(
                    onClick = {
                      aiAssistantViewModel.fetchBudgetAdvice(
                        dashboardViewModel.transactions.value,
                        dashboardViewModel.loans.value,
                        dashboardViewModel.installments.value,
                        dashboardViewModel.categories.value,
                        aiAssistantViewModel.isOnlineMode.value,
                        dashboardViewModel.bankLoans.value,
                        forceRefresh = true
                      )
                    },
                    text = "تلاش مجدد",
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                      ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                      )
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun MarkdownText(
  text: String,
  modifier: Modifier = Modifier,
  textStyle: TextStyle = LocalTextStyle.current,
  textColor: Color = Color.Unspecified
) {
  val lines = text.split("\n")
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
  ) {
    lines.forEach { line ->
      val trimmed = line.trim()
      if (trimmed.isEmpty()) {
        Spacer(modifier = Modifier.height(SpacingTokens.xs))
      } else if (trimmed.startsWith("###")) {
        val headerText = trimmed.removePrefix("###").trim()
        Text(
          text = headerText,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = SpacingTokens.sm, bottom = 2.dp)
        )
      } else if (trimmed.startsWith("##")) {
        val headerText = trimmed.removePrefix("##").trim()
        Text(
          text = headerText,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = SpacingTokens.md, bottom = SpacingTokens.xs)
        )
      } else if (trimmed.startsWith("#")) {
        val headerText = trimmed.removePrefix("#").trim()
        Text(
          text = headerText,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = SpacingTokens.md, bottom = SpacingTokens.sm)
        )
      } else if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
        val itemText = trimmed.removePrefix("-").removePrefix("*").trim()
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(horizontal = SpacingTokens.xs),
          horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm),
          verticalAlignment = Alignment.Top
        ) {
          Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = parseBoldMarkdown(itemText),
            style = textStyle,
            color = textColor,
            lineHeight = 22.sp
          )
        }
      } else {
        Text(
          text = parseBoldMarkdown(trimmed),
          style = textStyle,
          color = if (textColor != Color.Unspecified) textColor else MaterialTheme.colorScheme.onSurface,
          lineHeight = 22.sp
        )
      }
    }
  }
}

fun parseBoldMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
  val builder =
    androidx.compose.ui.text.AnnotatedString
      .Builder()
  val parts = text.split("**")
  for (i in parts.indices) {
    if (i % 2 == 1) {
      builder.pushStyle(
        androidx.compose.ui.text
          .SpanStyle(fontWeight = FontWeight.Bold)
      )
      builder.append(parts[i])
      builder.pop()
    } else {
      builder.append(parts[i])
    }
  }
  return builder.toAnnotatedString()
}

@Composable
fun ParsedResultCard(
  result: ParsedResult,
  categories: List<Category>,
  onApprove: (ParsedResult, Long) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  // States for interactive editing
  var amountValue by remember(result) {
    val text = CurrencyFormatter.fromRial(result.amount).toString()
    mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
  }
  var descriptionText by remember(result) { mutableStateOf(result.description) }
  var selectedType by remember(result) { mutableStateOf(result.type) }
  var selectedCategoryKey by remember(result) { mutableStateOf(result.category) }
  var personNameText by remember(result) { mutableStateOf(result.personName.orEmpty()) }
  var titleText by remember(result) { mutableStateOf(result.title.orEmpty()) }
  var daysFromNowText by remember(result) { mutableStateOf(result.daysFromNow?.toString() ?: "30") }

  var customDate by remember(result) {
    val finalCal = Calendar.getInstance()
    val offsetDays =
      if (result.daysFromNow != null && result.daysFromNow != 0) {
        result.daysFromNow
      } else {
        result.dateOffsetDays ?: 0
      }
    finalCal.add(Calendar.DAY_OF_YEAR, offsetDays)
    result.hour?.let { h ->
      finalCal.set(Calendar.HOUR_OF_DAY, h)
    }
    result.minute?.let { m ->
      finalCal.set(Calendar.MINUTE, m)
    }
    mutableStateOf(finalCal.timeInMillis)
  }

  val filteredCategories =
    categories.filter { cat ->
      when (selectedType) {
        TransactionType.INCOME.name -> cat.type == CategoryType.INCOME || cat.type == CategoryType.BOTH
        TransactionType.EXPENSE.name -> cat.type == CategoryType.EXPENSE || cat.type == CategoryType.BOTH
        else -> cat.key == "Loans" || cat.key == "Installments" || cat.key == "Other"
      }
    }

  val typeColor =
    when (selectedType) {
      "INCOME", "LOAN_DEBTOR" -> MaterialTheme.colorScheme.primary
      "EXPENSE", "LOAN_CREDITOR" -> MaterialTheme.colorScheme.error
      else -> MaterialTheme.colorScheme.tertiary
    }

  HesabyarCard(
    modifier = modifier.fillMaxWidth(),
    shape = ShapeTokens.XLarge,
    elevation = ElevationTokens.Level2,
    contentPadding = PaddingValues(SpacingTokens.xl)
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
    ) {
      // Header Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "✏️ ویرایش و تایید نهایی تراکنش",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )

        IconButton(
          onClick = onCancel,
          modifier = Modifier.size(Dimens.IconLarge)
        ) {
          Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "انصراف",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Confidence Score Display
      val confidenceColor =
        when {
          result.confidence >= 0.9f -> MaterialTheme.colorScheme.primary
          result.confidence >= 0.7f -> MaterialTheme.colorScheme.tertiary
          else -> MaterialTheme.colorScheme.error
        }
      HesabyarCard(
        modifier = Modifier.fillMaxWidth(),
        cardColors = CardDefaults.cardColors(containerColor = confidenceColor.copy(alpha = 0.1f)),
        contentPadding = PaddingValues(SpacingTokens.md)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "اطمینان تحلیل:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "${(result.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = confidenceColor
          )
        }
      }

      // Notes Display (if exists)
      if (!result.notes.isNullOrBlank()) {
        HesabyarCard(
          modifier = Modifier.fillMaxWidth(),
          cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          contentPadding = PaddingValues(SpacingTokens.md)
        ) {
          Text(
            text = result.notes,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer)

      // Scrollable Form Content Container
      Column(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg)
      ) {
        // 1. Transaction Type Selector
        Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
          Text(
            text = "نوع تراکنش شناسایی شده:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
          ) {
            val types =
              listOf(
                Pair("EXPENSE", "هزینه"),
                Pair("INCOME", "درآمد"),
                Pair("LOAN_DEBTOR", "طلب (قرض دادم)"),
                Pair("LOAN_CREDITOR", "بدهی (قرض گرفتم)"),
                Pair("INSTALLMENT", "قسط")
              )
            types.forEach { (typeKey, typeLabel) ->
              val isSelected = selectedType == typeKey
              HesabyarChip(
                selected = isSelected,
                onClick = {
                  selectedType = typeKey
                  selectedCategoryKey =
                    when (typeKey) {
                      "INCOME" -> "Income"
                      "LOAN_DEBTOR", "LOAN_CREDITOR" -> "Loans"
                      "INSTALLMENT" -> "Installments"
                      else -> selectedCategoryKey
                    }
                },
                label = typeLabel
              )
            }
          }
        }

        // 2. Amount Input Field
        Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
          Text(
            text = "مبلغ استخراج شده (${CurrencyFormatter.unitLabel}):",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          OutlinedTextField(
            value = amountValue,
            onValueChange = { amountValue = it },
            modifier =
              Modifier
                .fillMaxWidth()
                .testTag("parsed_amount_input"),
            shape = ShapeTokens.Medium,
            leadingIcon = {
              Icon(
                imageVector = Icons.Filled.AttachMoney,
                contentDescription = null,
                tint = typeColor
              )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = typeColor,
                focusedLabelColor = typeColor
              )
          )
          AmountQuickFillButtons(
            amountValue = amountValue,
            onValueChanged = { amountValue = it }
          )
          // Formatted Amount preview in Persian words
          val amtToman = amountValue.text.toLongOrNull() ?: 0L
          if (amtToman > 0L) {
            val amtRial = CurrencyFormatter.toRial(amtToman)
            Text(
              text = "معادل: ${CurrencyFormatter.format(amtRial)}",
              style = MaterialTheme.typography.bodySmall,
              color = typeColor,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = SpacingTokens.xs)
            )
          }
        }

        // 3. Category Selector
        Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
          Text(
            text = "دسته‌بندی مربوطه:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(SpacingTokens.sm)
          ) {
            filteredCategories.forEach { cat ->
              val isSelected = selectedCategoryKey == cat.key
              HesabyarChip(
                selected = isSelected,
                onClick = { selectedCategoryKey = cat.key },
                label = cat.name
              )
            }
          }
        }

        // 4. Description Field
        Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
          Text(
            text = "شرح یا توضیح تراکنش:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          HesabyarInputField(
            value = descriptionText,
            onValueChange = { descriptionText = it },
            modifier = Modifier.testTag("parsed_description_input"),
            shape = ShapeTokens.Medium
          )
        }

        // Shamsi Date & Time Picker
        JalaliDateTimePicker(
          initialTimestamp = customDate,
          onTimestampChanged = { customDate = it }
        )

        // 5. Conditional Person Name Field (Loans)
        if (selectedType == "LOAN_DEBTOR" || selectedType == "LOAN_CREDITOR") {
          Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
            Text(
              text = "طرف حساب (شخص مربوطه):",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HesabyarInputField(
              value = personNameText,
              onValueChange = { personNameText = it },
              modifier = Modifier.testTag("parsed_person_input"),
              placeholder = "مثلا: علی محمودی",
              shape = ShapeTokens.Medium
            )
          }
        }

        // 6. Conditional Installment Fields
        if (selectedType == "INSTALLMENT") {
          Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
              Text(
                text = "عنوان قسط:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              HesabyarInputField(
                value = titleText,
                onValueChange = { titleText = it },
                modifier = Modifier.testTag("parsed_title_input"),
                placeholder = "مثلا: قسط بانک مسکن",
                shape = ShapeTokens.Medium
              )
            }

            Column(verticalArrangement = Arrangement.spacedBy(SpacingTokens.sm)) {
              Text(
                text = "فاصله تا موعد پرداخت (روز):",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              HesabyarInputField(
                value = daysFromNowText,
                onValueChange = {
                  daysFromNowText = it
                  val days = it.toLongOrNull()
                  if (days != null && days > 0) {
                    customDate = System.currentTimeMillis() + days * 24 * 60 * 60 * 1000
                  }
                },
                modifier = Modifier.testTag("parsed_days_input"),
                placeholder = "پیش‌فرض ۳۰ روز دیگر",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = ShapeTokens.Medium
              )
            }
          }
        }
      } // Cerrar Column

      Spacer(modifier = Modifier.height(SpacingTokens.sm))

      // Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpacingTokens.md)
      ) {
        HesabyarButton(
          onClick = onCancel,
          modifier = Modifier.weight(1f),
          text = "انصراف",
          variant = ButtonVariant.Outlined
        )

        HesabyarButton(
          onClick = {
            val finalAmountToman = amountValue.text.toLongOrNull() ?: 0L
            if (finalAmountToman <= 0L) {
              android.widget.Toast
                .makeText(
                  context,
                  "لطفا مبلغ معتبر و بزرگتر از صفر وارد کنید",
                  android.widget.Toast.LENGTH_SHORT
                ).show()
              return@HesabyarButton
            }

            val finalDaysFromNow =
              if (selectedType ==
                "INSTALLMENT"
              ) {
                daysFromNowText.toIntOrNull()
              } else {
                null
              }
            val finalPersonName =
              if (selectedType == "LOAN_DEBTOR" ||
                selectedType == "LOAN_CREDITOR"
              ) {
                personNameText.trim()
              } else {
                null
              }
            val finalTitle = if (selectedType == "INSTALLMENT") titleText.trim() else null

            val updatedResult =
              ParsedResult(
                type = selectedType,
                amount = CurrencyFormatter.toRial(finalAmountToman),
                category = selectedCategoryKey,
                personName = if (finalPersonName.isNullOrBlank()) null else finalPersonName,
                description = descriptionText.ifBlank { "ثبت دستیار هوشمند" },
                daysFromNow = finalDaysFromNow,
                title = if (finalTitle.isNullOrBlank()) null else finalTitle
              )

            onApprove(updatedResult, customDate)
          },
          modifier = Modifier.weight(1.3f),
          text = "ذخیره و ثبت نهایی",
          icon = Icons.Filled.Check,
          colors = ButtonDefaults.buttonColors(containerColor = typeColor)
        )
      }
    }
  }
}

@Composable
fun ConfirmationDialog(
  result: ParsedResult,
  onConfirm: () -> Unit,
  onCancel: () -> Unit
) {
  val confidenceColor = resolveConfidenceColor(result.confidence)
  val typeLabel = resolveTypeLabel(result.type)
  val amountColor = resolveAmountColor(result.type)

  AlertDialog(
    onDismissRequest = onCancel,
    title = {
      Text(
        text = "تایید تراکنش",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      ConfirmationDialogContent(
        result = result,
        confidenceColor = confidenceColor,
        typeLabel = typeLabel,
        amountColor = amountColor
      )
    },
    confirmButton = {
      HesabyarButton(
        onClick = onConfirm,
        modifier = Modifier.fillMaxWidth(),
        text = "تایید و ادامه",
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
      )
    },
    dismissButton = {
      HesabyarButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
        text = "انصراف",
        variant = ButtonVariant.Outlined
      )
    }
  )
}

@Composable
private fun ConfirmationDialogContent(
  result: ParsedResult,
  confidenceColor: Color,
  typeLabel: String,
  amountColor: Color
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    ConfidenceScoreCard(confidence = result.confidence, color = confidenceColor)
    DetailRow("نوع:", typeLabel)
    DetailRow("مبلغ:", CurrencyFormatter.format(result.amount), amountColor)
    DetailRow("دسته:", result.category)
    DetailRow("شرح:", result.description)

    if (!result.personName.isNullOrBlank()) {
      DetailRow("شخص:", result.personName)
    }

    if (!result.notes.isNullOrBlank()) {
      NotesCard(notes = result.notes)
    }

    if (result.confidence < CONFIDENCE_MEDIUM_THRESHOLD) {
      LowConfidenceWarning()
    }
  }
}

@Composable
private fun ConfidenceScoreCard(
  confidence: Float,
  color: Color
) {
  HesabyarCard(
    modifier = Modifier.fillMaxWidth(),
    cardColors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    contentPadding = PaddingValues(SpacingTokens.md)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "اطمینان:",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "${(confidence * 100).toInt()}%",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = color
      )
    }
  }
}

@Composable
private fun DetailRow(
  label: String,
  value: String,
  valueColor: Color = Color.Unspecified
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Text(
      text = value,
      fontWeight = FontWeight.Bold,
      color = if (valueColor != Color.Unspecified) valueColor else Color.Unspecified
    )
  }
}

@Composable
private fun NotesCard(notes: String) {
  HesabyarCard(
    modifier = Modifier.fillMaxWidth(),
    cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    contentPadding = PaddingValues(SpacingTokens.sm)
  ) {
    Text(text = notes, style = MaterialTheme.typography.bodySmall)
  }
}

@Composable
private fun LowConfidenceWarning() {
  HesabyarCard(
    modifier = Modifier.fillMaxWidth(),
    cardColors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
      ),
    contentPadding = PaddingValues(SpacingTokens.sm)
  ) {
    Text(
      text = "⚠️ اطمینان پایین است. لطفاً اطلاعات را بررسی کنید.",
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodySmall,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
private fun resolveConfidenceColor(confidence: Float) =
  when {
    confidence >= CONFIDENCE_HIGH_THRESHOLD -> MaterialTheme.colorScheme.primary
    confidence >= CONFIDENCE_MEDIUM_THRESHOLD -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
  }

private fun resolveTypeLabel(type: String) =
  when (type) {
    "EXPENSE" -> "هزینه"
    "INCOME" -> "درآمد"
    "LOAN_DEBTOR" -> "طلب (قرض دادم)"
    "LOAN_CREDITOR" -> "بدهی (قرض گرفتم)"
    "INSTALLMENT" -> "قسط"
    else -> type
  }

@Composable
private fun resolveAmountColor(type: String) =
  when (type) {
    TransactionType.INCOME.name, "LOAN_DEBTOR" -> MaterialTheme.colorScheme.primary
    TransactionType.EXPENSE.name, "LOAN_CREDITOR" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
  }
