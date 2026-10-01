# Phase 2: معماری State (Event, UiState, ViewModel Refactor)

## پیش‌نیاز

- **فاز قبلی:** Phase 1 باید کامل شده باشه (UseCaseها آماده‌ان)
- **تصمیمات معلق:** هیچ تصمیم جدید — همه قبلاً در Phase 0/1 روشن شدن

## زمینه

`AccountViewModel` فعلی state پراکنده‌ای داره: `accounts` StateFlow در ViewModel و `dialogState` + فرم state در composable. بدون loading، error، یا side effect management. این فاز یک state architecture متمرکز و testable معرفی می‌کنه.

## هدف دقیق این فاز

ایجاد `AccountUiState`، `AccountEvent`، و `AccountSideEffect`، سپس بازنویسی `AccountViewModel` برای استفاده از این pattern. در پایان، ViewModel باید single source of truth برای تمام state باشه و UI فقط state رو render کنه.

## فایل‌های درگیر

### فایل‌های جدید
| فایل | توضیح |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountUiState.kt` | state models |
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountEvent.kt` | event sealed class |
| `app/src/test/java/io/github/mojri/hesabyar/ui/AccountViewModelTest.kt` | تست ViewModel |

### فایل‌های ویرایشی
| فایل | تغییر |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountViewModel.kt` | بازنویسی کامل با event-based pattern |

## گام‌های اجرا

### گام ۲.۱: ایجاد AccountEvent

```kotlin
sealed interface AccountEvent {
  // List
  data object LoadAccounts : AccountEvent
  data class OnAccountOverflow(val account: AccountEntity) : AccountEvent

  // Add
  data object OnAddAccount : AccountEvent
  data class OnSaveNewAccount(val form: AccountFormState) : AccountEvent

  // Edit
  data class OnEditAccount(val account: AccountEntity) : AccountEvent
  data class OnSaveEditedAccount(val account: AccountEntity, val form: AccountFormState) : AccountEvent

  // Delete
  data class OnRequestDelete(val account: AccountEntity) : AccountEvent
  data class OnConfirmDelete(val account: AccountEntity) : AccountEvent

  // Archive
  data class OnRequestArchive(val account: AccountEntity) : AccountEvent
  data class OnConfirmArchive(val account: AccountEntity) : AccountEvent
  data class OnUnarchiveAccount(val account: AccountEntity) : AccountEvent

  // Form
  data class OnFormChange(val form: AccountFormState) : AccountEvent

  // UI
  data object OnDismissDialog : AccountEvent
  data object OnDismissSnackbar : AccountEvent
}
```

### گام ۲.۲: ایجاد AccountUiState

```kotlin
data class AccountUiState(
  val accounts: List<AccountEntity> = emptyList(),
  val dialogState: AccountDialogState = AccountDialogState.None,
  val formState: AccountFormState = AccountFormState(),
  val isLoading: Boolean = false,
  val isSaving: Boolean = false,
  val snackbarMessage: String? = null,
)

data class AccountFormState(
  val name: String = "",
  val type: AccountType = AccountType.BANK,
  val bankName: String = "",
  val cardNumber: String = "",
  val accountNumber: String = "",
  val iban: String = "",
  val initialBalance: String = "0",
  val color: Long = DEFAULT_ACCOUNT_COLOR,
  val errors: Map<String, String> = emptyMap(),
)

sealed interface AccountDialogState {
  data object None : AccountDialogState
  data object Add : AccountDialogState
  data class Edit(val account: AccountEntity) : AccountDialogState
  data class DeleteConfirmation(val account: AccountEntity) : AccountDialogState
  data class TransactionWarning(val account: AccountEntity) : AccountDialogState
  data class ArchiveConfirmation(val account: AccountEntity) : AccountDialogState
  data class PendingDelete(val account: AccountEntity) : AccountDialogState
  data class OverflowMenu(val account: AccountEntity) : AccountDialogState
}

sealed interface AccountSideEffect {
  data class ShowSnackbar(val message: String, val actionLabel: String? = null) : AccountSideEffect
}
```

### گام ۲.۳: بازنویسی AccountViewModel

- `onEvent(event: AccountEvent)` method اصلی
- `_uiState: MutableStateFlow<AccountUiState>` → `uiState: StateFlow<AccountUiState>`
- `_sideEffect: Channel<AccountSideEffect>` → `sideEffect: Flow<AccountSideEffect>`
- `SavedStateHandle` برای `formState` (اگر process death مهمه)

**ساختار کلی:**
```kotlin
@HiltViewModel
class AccountViewModel @Inject constructor(
  private val addAccountUseCase: AddAccountUseCase,
  private val updateAccountUseCase: UpdateAccountUseCase,
  private val deleteAccountUseCase: DeleteAccountUseCase,
  private val archiveAccountUseCase: ArchiveAccountUseCase,
  private val getAccountsUseCase: GetAccountsUseCase,
  private val accountValidator: AccountValidator,
  savedStateHandle: SavedStateHandle
) : ViewModel() {

  private val _uiState = MutableStateFlow(AccountUiState())
  val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

  private val _sideEffect = Channel<AccountSideEffect>()
  val sideEffect: Flow<AccountSideEffect> = _sideEffect.receiveAsFlow()

  init {
    // Collect accounts from GetAccountsUseCase
  }

  fun onEvent(event: AccountEvent) {
    when (event) {
      // handle each event
    }
  }
}
```

**مدیریت SideEffect:**
- Snackbar message بعد از هر عملیات CRUD
- `onDismissSnackbar` → `snackbarMessage = null`

### گام ۲.۴: اضافه کردن Error Handling

- تمام useCase calls در `try-catch`
- Error → `snackbarMessage = "خطا در انجام عملیات"`
- `isSaving = false` در finally block

### گام ۲.۵: نوشتن ViewModel Tests

```kotlin
class AccountViewModelTest {
  // Setup: FakeUseCases, FakeValidator

  @Test fun onAddAccount_showsDialog()
  @Test fun onSaveNewAccount_validForm_insertsAndShowsSnackbar()
  @Test fun onSaveNewAccount_invalidForm_showsErrors()
  @Test fun onDeleteRequest_withTransactions_showsWarning()
  @Test fun onDeleteRequest_withoutTransactions_showsConfirmation()
  @Test fun onConfirmDelete_deletesAndShowsSnackbar()
  @Test fun onConfirmArchive_archivesAndShowsSnackbar()
  @Test fun onFormChange_updatesFormState()
  @Test fun onDismissDialog_clearsDialogState()
  @Test fun onDismissSnackbar_clearsSnackbarMessage()
}
```

## نکات خاص این فاز

- **مهم:** در این فاز، Screen هنوز state قبلی (`dialogState` as `remember`) رو استفاده می‌کنه. Screen refactor در Phase 4 انجام می‌شه. این فاز فقط ViewModel رو آماده می‌کنه.
- **سازگاری:** ViewModel جدید باید API قدیمی (`accounts: StateFlow<List<AccountEntity>>`) رو هنوز export کنه تا Screen قبلی کار کنه. بعداً در Phase 4، Screen به `uiState` switch می‌شه.
- **Channel vs SharedFlow:** برای SideEffect از `Channel` استفاده بشه (نه `SharedFlow`) چون هر message فقط یکبار مصرف بشه.

## معیار پذیرش

- [ ] `AccountEvent.kt` با تمام events تعریف شده
- [ ] `AccountUiState.kt` با تمام state models
- [ ] `AccountViewModel` با `onEvent()` method
- [ ] `AccountViewModelTest` → تمام تستها pass
- [ ] ViewModel API قدیمی (`accounts`) هنوز کار می‌کنه
- [ ] `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → بدون خطا

## Rollback

```bash
git log --oneline -5  # Phase 2 commits
git revert <commits>
# یا
git checkout HEAD -- app/src/main/java/.../ui/AccountViewModel.kt
rm app/src/main/java/.../ui/AccountUiState.kt app/src/main/java/.../ui/AccountEvent.kt
```
