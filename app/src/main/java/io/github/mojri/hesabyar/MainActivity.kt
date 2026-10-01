package io.github.mojri.hesabyar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.mojri.hesabyar.auth.AuthManager
import io.github.mojri.hesabyar.auth.LockScreen
import io.github.mojri.hesabyar.reminder.ReminderScheduler
import io.github.mojri.hesabyar.ui.*
import io.github.mojri.hesabyar.ui.designsystem.ElevationTokens
import io.github.mojri.hesabyar.ui.screens.*
import io.github.mojri.hesabyar.ui.screens.account.AccountManagementScreen
import io.github.mojri.hesabyar.ui.utils.ShakeDetector
import io.github.mojri.hesabyar.ui.theme.HesabyarTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
  @Inject
  lateinit var authManager: AuthManager

  private val settingsViewModel: SettingsViewModel by viewModels()
  private val dashboardViewModel: DashboardViewModel by viewModels()
  private val transactionViewModel: TransactionViewModel by viewModels()
  private val loanViewModel: LoanViewModel by viewModels()
  private val installmentViewModel: InstallmentViewModel by viewModels()
  private val categoryViewModel: CategoryViewModel by viewModels()
  private val aiAssistantViewModel: AiAssistantViewModel by viewModels()
  private val backupViewModel: BackupViewModel by viewModels()
  private val exportViewModel: ExportViewModel by viewModels()
  private val analyticsViewModel: AnalyticsViewModel by viewModels()
  private val bankLoanViewModel: BankLoanViewModel by viewModels()
  private val accountViewModel: AccountViewModel by viewModels()

  // Shake-to-voice: a shake opens the assistant and starts Persian voice input.
  private var pendingVoiceStart by mutableStateOf(false)
  private val shakeDetector =
    ShakeDetector {
      if (!(authManager.isLocked.value && authManager.shouldShowAuth(this))) pendingVoiceStart = true
    }

  override fun onResume() {
    super.onResume()
    shakeDetector.register(getSystemService(Context.SENSOR_SERVICE) as SensorManager)
  }

  override fun onPause() {
    shakeDetector.unregister(getSystemService(Context.SENSOR_SERVICE) as SensorManager)
    super.onPause()
  }

  private val notificationPermissionLauncher =
    registerForActivityResult(
      ActivityResultContracts.RequestPermission()
    ) { isGranted ->
      if (isGranted) {
        ReminderScheduler.scheduleReminders(this)
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    requestNotificationPermission()
    ReminderScheduler.scheduleReminders(this)

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        settingsViewModel.uiMessage.collectLatest { msg ->
          Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
      }
    }

    setContent {
      val isDark by settingsViewModel.isDarkMode
      val isLocked by authManager.isLocked.collectAsState()

      HesabyarTheme(darkTheme = isDark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          if (isLocked && authManager.shouldShowAuth(this@MainActivity)) {
            LockScreen(
              authManager = authManager,
              onUnlocked = { }
            )
          } else {
            val startTab =
              when (intent?.getStringExtra("OPEN_TAB")) {
                "LOANS", "INSTALLMENTS", "BANK_LOANS", "DEBTS" -> "DEBTS"
                else -> "DASHBOARD"
              }
            val startDebtSection =
              when (intent?.getStringExtra("OPEN_TAB")) {
                "LOANS" -> DebtSection.LOANS
                "BANK_LOANS" -> DebtSection.BANK_LOANS
                else -> DebtSection.INSTALLMENTS
              }
            var currentTab by remember { mutableStateOf(startTab) }
            var debtSection by remember { mutableStateOf(startDebtSection) }
            var showCategoryManagement by remember { mutableStateOf(false) }
            var showAccountManagement by remember { mutableStateOf(false) }

            LaunchedEffect(pendingVoiceStart) {
              if (pendingVoiceStart) {
                showCategoryManagement = false
                showAccountManagement = false
                currentTab = "ASSISTANT"
              }
            }

            if (showCategoryManagement) {
              CategoryManagementScreen(
                categoryViewModel = categoryViewModel,
                onBack = { showCategoryManagement = false },
                modifier = Modifier.fillMaxSize()
              )
            } else if (showAccountManagement) {
              AccountManagementScreen(
                accountViewModel = accountViewModel,
                onBack = { showAccountManagement = false },
                modifier = Modifier.fillMaxSize()
              )
            } else {
              var showMoreMenu by remember { mutableStateOf(false) }

              val isCompact = LocalConfiguration.current.screenWidthDp < 600

              val tabs =
                listOf(
                  Triple("DASHBOARD", "داشبورد", Icons.Filled.AccountBalanceWallet),
                  Triple("ASSISTANT", "دستیار هوشمند", Icons.Filled.AutoAwesome),
                  Triple("DEBTS", "مدیریت بدهی‌ها", Icons.Filled.AccountBalance)
                )

              @Composable
              fun currentTabScreen(modifier: Modifier) {
                when (currentTab) {
                  "DASHBOARD" ->
                    DashboardScreen(
                      dashboardViewModel = dashboardViewModel,
                      transactionViewModel = transactionViewModel,
                      installmentViewModel = installmentViewModel,
                      aiAssistantViewModel = aiAssistantViewModel,
                      settingsViewModel = settingsViewModel,
                      onNavigateToAssistant = { currentTab = "ASSISTANT" },
                      modifier = modifier
                    )
                  "ASSISTANT" ->
                    SmartAssistantScreen(
                      aiAssistantViewModel = aiAssistantViewModel,
                      categoryViewModel = categoryViewModel,
                      dashboardViewModel = dashboardViewModel,
                      settingsViewModel = settingsViewModel,
                      startVoiceOnOpen = pendingVoiceStart,
                      onVoiceStarted = { pendingVoiceStart = false },
                      modifier = modifier
                    )
                  "DEBTS" ->
                    DebtHubScreen(
                      initialSection = debtSection,
                      installmentViewModel = installmentViewModel,
                      bankLoanViewModel = bankLoanViewModel,
                      loanViewModel = loanViewModel,
                      settingsViewModel = settingsViewModel,
                      modifier = modifier
                    )
                  "ANALYTICS" ->
                    AnalyticsScreen(
                      analyticsViewModel = analyticsViewModel,
                      modifier = modifier
                    )
                  "REPORTS" ->
                    ReportsScreen(
                      dashboardViewModel = dashboardViewModel,
                      transactionViewModel = transactionViewModel,
                      aiAssistantViewModel = aiAssistantViewModel,
                      modifier = modifier
                    )
                  "SETTINGS" ->
                    SettingsScreen(
                      aiAssistantViewModel = aiAssistantViewModel,
                      backupViewModel = backupViewModel,
                      exportViewModel = exportViewModel,
                      settingsViewModel = settingsViewModel,
                      onNavigateToCategories = { showCategoryManagement = true },
                      modifier = modifier
                    )
                }
              }

              @OptIn(ExperimentalMaterial3Api::class)
              @Composable
              fun moreMenuSheet(
                show: Boolean,
                onDismiss: () -> Unit,
                onSelect: (String) -> Unit
              ) {
                if (show) {
                  ModalBottomSheet(onDismissRequest = onDismiss) {
                    ListItem(
                      headlineContent = { Text("تحلیل و آمار") },
                      leadingContent = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                      modifier = Modifier.clickable { onSelect("ANALYTICS") }
                    )
                    ListItem(
                      headlineContent = { Text("گزارش‌ها") },
                      leadingContent = { Icon(Icons.Filled.Analytics, contentDescription = null) },
                      modifier = Modifier.clickable { onSelect("REPORTS") }
                    )
                    ListItem(
                      headlineContent = { Text("مدیریت حساب‌ها") },
                      leadingContent = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null) },
                      modifier =
                        Modifier.clickable {
                          showMoreMenu = false
                          showAccountManagement = true
                        }
                    )
                    ListItem(
                      headlineContent = { Text("تنظیمات") },
                      leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                      modifier = Modifier.clickable { onSelect("SETTINGS") }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                  }
                }
              }

              @Composable
              fun mainContent(contentModifier: Modifier) {
                Box(
                  modifier =
                    contentModifier
                      .fillMaxSize(),
                  contentAlignment = Alignment.TopCenter
                ) {
                  currentTabScreen(Modifier)
                }

                moreMenuSheet(
                  show = showMoreMenu,
                  onDismiss = { showMoreMenu = false },
                  onSelect = { tab ->
                    currentTab = tab
                    showMoreMenu = false
                  }
                )
              }

              Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                  if (isCompact) {
                    NavigationBar(
                      containerColor = MaterialTheme.colorScheme.surface,
                      tonalElevation = ElevationTokens.Level4
                    ) {
                      tabs.forEach { (tabId, label, icon) ->
                        NavigationBarItem(
                          selected = currentTab == tabId,
                          onClick = { currentTab = tabId },
                          icon = { Icon(imageVector = icon, contentDescription = label) },
                          label = {
                            Text(
                              label,
                              style = MaterialTheme.typography.labelSmall,
                              fontWeight = FontWeight.Bold
                            )
                          },
                          colors =
                            NavigationBarItemDefaults.colors(
                              selectedIconColor = MaterialTheme.colorScheme.primary,
                              selectedTextColor = MaterialTheme.colorScheme.primary,
                              indicatorColor =
                                MaterialTheme.colorScheme.primaryContainer.copy(
                                  alpha = 0.5f
                                )
                            )
                        )
                      }
                      NavigationBarItem(
                        selected = currentTab in listOf("ANALYTICS", "REPORTS", "SETTINGS"),
                        onClick = { showMoreMenu = true },
                        icon = {
                          Icon(
                            imageVector = Icons.Filled.MoreHoriz,
                            contentDescription = "بیشتر"
                          )
                        },
                        label = {
                          Text(
                            "بیشتر",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                          )
                        },
                        colors =
                          NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor =
                              MaterialTheme.colorScheme.primaryContainer.copy(
                                alpha = 0.5f
                              )
                          )
                      )
                    }
                  }
                }
              ) { innerPadding ->
                if (isCompact) {
                  mainContent(Modifier.padding(innerPadding))
                } else {
                  Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                      modifier = Modifier.fillMaxHeight(),
                      containerColor = MaterialTheme.colorScheme.surface
                    ) {
                      tabs.forEach { (tabId, label, icon) ->
                        NavigationRailItem(
                          selected = currentTab == tabId,
                          onClick = { currentTab = tabId },
                          icon = { Icon(imageVector = icon, contentDescription = label) },
                          label = {
                            Text(
                              label,
                              style = MaterialTheme.typography.labelSmall,
                              fontWeight = FontWeight.Bold
                            )
                          },
                          colors =
                            NavigationRailItemDefaults.colors(
                              selectedIconColor = MaterialTheme.colorScheme.primary,
                              selectedTextColor = MaterialTheme.colorScheme.primary,
                              indicatorColor =
                                MaterialTheme.colorScheme.primaryContainer.copy(
                                  alpha = 0.5f
                                )
                            )
                        )
                      }
                      NavigationRailItem(
                        selected = currentTab in listOf("ANALYTICS", "REPORTS", "SETTINGS"),
                        onClick = { showMoreMenu = true },
                        icon = {
                          Icon(
                            imageVector = Icons.Filled.MoreHoriz,
                            contentDescription = "بیشتر"
                          )
                        },
                        label = {
                          Text(
                            "بیشتر",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                          )
                        },
                        colors =
                          NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor =
                              MaterialTheme.colorScheme.primaryContainer.copy(
                                alpha = 0.5f
                              )
                          )
                      )
                    }
                    mainContent(Modifier.padding(innerPadding).weight(1f))
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  override fun onUserInteraction() {
    super.onUserInteraction()
    authManager.onUserInteraction()
  }

  private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }
}
