# Phase 4: بازنویسی Screen به Shell + دیالوگ‌ها

## پیش‌نیاز

- **فاز قبلی:** Phase 3 باید کامل شده باشه (کامپوننت‌ها استخراج شدن) و Phase 2 (ViewModel event-based)
- **تصمیمات معلق:** ندارد

## زمینه

`AccountManagementScreen.kt` بعد از Phase 3 کوچکتر شده ولی هنوز state management قدیمی (`remember { mutableStateOf }`) رو استفاده می‌کنه. `AccountDialogState` هنوز محلیه. این فاز Screen رو به ViewModel-connected shell تبدیل می‌کنه و state management رو کامل می‌کنه.

## هدف دقیق این فاز

اتصال `AccountManagementScreen` به `AccountUiState` از ViewModel، حذف state محلی، و ایجاد دیالوگ‌های جداگانه. در پایان:
- Screen ≤ ۱۰۰ خط (فقط shell)
- تمام state از ViewModel میاد
- `dialogState` از ViewModel collect بشه
- `formState` از ViewModel بیاد
- SideEffect (Snackbar) از ViewModel مدیریت بشه

## فایل‌های درگیر

### فایل‌های جدید
| فایل | توضیح |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountFormDialog.kt` | دیالوگ افزودن/ویرایش |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountDeleteDialog.kt` | دیالوگ حذف |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountArchiveDialog.kt` | دیالوگ آرشیو |

### فایل ویرایشی
| فایل | تغییر |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt` | بازنویسی کامل به shell ساده |

## گام‌های اجرا

### گام ۴.۱: بازنویسی AccountManagementScreen

**ساختار جدید:**
```kotlin
@Composable
fun AccountManagementScreen(
  accountViewModel: AccountViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val uiState by accountViewModel.uiState.collectAsState()

  // SideEffect collection
  LaunchedEffect(Unit) {
    accountViewModel.sideEffect.collect { effect ->
      when (effect) {
        is AccountSideEffect.ShowSnackbar -> { /* show snackbar */ }
      }
    }
  }

  Scaffold(
    topBar = { /* TopAppBar */ },
    floatingActionButton = { /* FAB → onEvent(OnAddAccount) */ }
  ) { innerPadding ->
    AccountManagementContent(
      accounts = uiState.accounts,
      modifier = modifier,
      innerPadding = innerPadding,
      onOverflowClick = { accountViewModel.onEvent(OnAccountOverflow(it)) }
    )
  }

  // Dialog host
  when (val dialog = uiState.dialogState) {
    is AccountDialogState.None -> {}
    is AccountDialogState.Add -> AccountFormDialog(...)
    is AccountDialogState.Edit -> AccountFormDialog(...)
    is AccountDialogState.DeleteConfirmation -> AccountDeleteDialog(...)
    is AccountDialogState.TransactionWarning -> ConfirmDialog(...)
    is AccountDialogState.ArchiveConfirmation -> AccountArchiveDialog(...)
    is AccountDialogState.PendingDelete -> { /* LaunchedEffect for async check */ }
    is AccountDialogState.OverflowMenu -> AccountOverflowMenu(...)
  }
}
```

**نکات:**
- `AccountDialogState` از `AccountUiState` گرفته بشه (نه `remember`)
- `dialogState` با `remember { mutableStateOf }` حذف بشه
- `formState` از `remember` حذف بشه — از ViewModel بیاد

### گام ۴.۲: ایجاد AccountFormDialog

```kotlin
@Composable
fun AccountFormDialog(
  isEdit: Boolean,
  formState: AccountFormState,
  onFormChange: (AccountFormState) -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
  isSaving: Boolean
) {
  HesabyarDialog(
    title = if (isEdit) "ویرایش حساب" else "افزودن حساب جدید",
    onDismissRequest = onDismiss,
    showCloseButton = true,
    actions = {
      HesabyarButton(onClick = onDismiss, text = "انصراف", variant = ButtonVariant.Text)
      HesabyarButton(
        onClick = onSave,
        text = "ذخیره",
        variant = ButtonVariant.Filled,
        enabled = formState.name.isNotBlank() && !isSaving,
        loading = isSaving
      )
    }
  ) {
    AccountFormContent(formState = formState, onFormChange = onFormChange)
  }
}
```

### گام ۴.۳: ایجاد AccountDeleteDialog

```kotlin
@Composable
fun AccountDeleteDialog(
  account: AccountEntity,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  ConfirmDialog(
    title = "حذف حساب",
    message = "آیا از حذف حساب «${account.name}» اطمینان دارید؟",
    confirmText = "حذف",
    dismissText = "انصراف",
    onConfirm = onConfirm,
    onDismiss = onDismiss
  )
}
```

### گام ۴.۴: ایجاد AccountArchiveDialog

```kotlin
@Composable
fun AccountArchiveDialog(
  account: AccountEntity,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  ConfirmDialog(
    title = "آرشیو حساب",
    message = "آیا از آرشیو کردن حساب «${account.name}» اطمینان دارید؟ حساب از داشبورد حذف خواهد شد.",
    confirmText = "آرشیو",
    dismissText = "انصراف",
    onConfirm = onConfirm,
    onDismiss = onDismiss,
    confirmColor = MaterialTheme.colorScheme.primary  // نه error — آرشیو destructive نیست
  )
}
```

### گام ۴.۵: اتصال FormState به ViewModel

- `AccountViewModel.onEvent(OnFormChange(newForm))` → `_uiState.update { it.copy(formState = newForm) }`
- `AccountFormDialog` → `onFormChange = { accountViewModel.onEvent(OnFormChange(it)) }`
- فیلدها رو `formState.name`، `formState.type`، etc. بخونن (نه `remember`)

### گام ۴.۶: اتصال Snackbar

```kotlin
// در AccountManagementScreen:
LaunchedEffect(Unit) {
  accountViewModel.sideEffect.collect { effect ->
    when (effect) {
      is AccountSideEffect.ShowSnackbar -> {
        snackbarHostState.showSnackbar(effect.message)
      }
    }
  }
}
```

### گام ۴.۷: اتصال OverflowMenu

```kotlin
// OverflowMenu باید داخل Box اطراف IconButton باشه (Phase 0 bug #1)
// یا از BoxScope استفاده بشه
Box {
  IconButton(onClick = { onOverflow(account) }) {
    Icon(Icons.Filled.MoreVert, ...)
  }
  // DropdownMenu اینجا رندر بشه
}
```

**نکته:** این گام Phase 0 bug #1 رو نهایی می‌کنه. اگر Phase 0 انجام شده باشه، فقط تأیید کنید که anchoring درسته.

## نکات خاص این فاز

- از چک‌لیست مرکزی:
  - **R2** (اعداد منفی): اگر `AccountFormDialog` مبلغ منفی نمایش بده، از الگوی LRM استفاده کنه
  - **R4** (grep): بعد از بازنویسی، grep کنید که `remember { mutableStateOf<AccountDialogState> }` در فایل اصلی وجود نداره
- **مهم:** `LaunchedEffect(currentDialog.account)` برای `PendingDelete` باید حفظ بشه — این async check برای `canDeleteAccount` هست
- Snackbar duration برای اعمال destructive (حذف/آرشیو) باید ۷ ثانیه باشه

## معیار پذیرش

- [ ] `AccountManagementScreen.kt` ≤ ۱۰۰ خط
- [ ] `AccountFormDialog.kt`، `AccountDeleteDialog.kt`، `AccountArchiveDialog.kt` وجود دارن
- [ ] `dialogState` از `remember { mutableStateOf }` حذف شده و از `uiState.dialogState` collect می‌شه
- [ ] `formState` از `remember` حذف شده و از `uiState.formState` میاد
- [ ] Snackbar بعد از CRUD operations نمایش داده می‌شه
- [ ] OverflowMenu داخل `Box` اطراف `IconButton` رندر بشه (Phase 0 bug #1 نهایی)
- [ ] `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → بدون خطا
- [ ] Manual QA: افزودن، ویرایش، حذف، آرشیو — همه کار کنن

## Rollback

```bash
git log --oneline -10
git revert <phase-4-commits>
# یا
git checkout HEAD -- app/src/main/java/.../ui/screens/account/AccountManagementScreen.kt
rm app/src/main/java/.../ui/screens/account/AccountFormDialog.kt
rm app/src/main/java/.../ui/screens/account/AccountDeleteDialog.kt
rm app/src/main/java/.../ui/screens/account/AccountArchiveDialog.kt
```
