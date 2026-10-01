# Phase 6: تست‌ها و Accessibility

## پیش‌نیاز

- **فاز قبلی:** Phase 5 باید کامل شده باشه (تمام features آماده‌ان)
- **تصمیمات معلق:**
  - تصمیم #3 (ابزارهای تست): قبل از شروع، تأیید کنید که Roborazzi و Detekt واقعاً نصب/پیکربندی شدن

## زمینه

بعد از Phase 5، تمام featureها آماده‌ان ولی پوشش تست ضعیفه: AccountViewModel و AccountManagementScreen اصلاً تست نشدن. این فاز پوشش تست رو کامل می‌کنه و accessibility رو بهبود می‌ده.

## هدف دقیق این فاز

رسیدن به ≥ ۹۰٪ line coverage برای ViewModel + UseCaseها، نوشتن Compose UI tests برای تمام user flows، و اضافه کردن accessibility semantics به تمام کامپوننت‌ها.

## فایل‌های درگیر

### فایل‌های تست جدید
| فایل | توضیح |
|---|---|
| `app/src/test/java/io/github/mojri/hesabyar/ui/AccountViewModelTest.kt` | تست کامل ViewModel (اگر Phase 2 ناقص بوده) |
| `app/src/test/java/io/github/mojri/hesabyar/ui/AccountManagementScreenTest.kt` | Compose UI tests |
| `app/src/test/java/io/github/mojri/hesabyar/ui/AccountFormDialogTest.kt` | تست فرم |
| `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/account/ArchiveAccountUseCaseTest.kt` | تست آرشیو |
| `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/account/UnarchiveAccountUseCaseTest.kt` | تست بازیابی |
| `app/src/test/java/io/github/mojri/hesabyar/domain/validation/AccountValidatorTest.kt` | تست کامل validator (اگر Phase 1 ناقص بوده) |

### فایل‌های ویرایشی (accessibility)
| فایل | تغییر |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountListCard.kt` | contentDescription, semantics, testTag |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountColorPicker.kt` | contentDescription برای هر swatch |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountTypeDropdown.kt` | testTag |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountBankFields.kt` | labelFor semantics |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt` | testTag برای FAB, list, dialogs |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountFormDialog.kt` | testTag, announceForAccessibility |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountDeleteDialog.kt` | testTag |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountArchiveDialog.kt` | testTag |

## گام‌های اجرا

### گام ۶.۰: تأیید ابزارهای تست

```bash
grep -rn "roborazzi\|detekt" app/build.gradle.kts build.gradle.kts
```
- اگر Roborazzi نصب نیست: skip snapshot tests
- اگر Detekt نصب نیست: skip detekt check (فقط ktlint)

### گام ۶.۱: تکمیل AccountViewModelTest

```kotlin
// تست‌های لازم:
- onAddAccount_showsAddDialog
- onEditAccount_showsEditDialogWithData
- onSaveNewAccount_validForm_insertsAndShowsSuccessSnackbar
- onSaveNewAccount_invalidForm_showsErrorsAndNoInsert
- onSaveEditedAccount_validForm_updatesAndShowsSnackbar
- onDeleteRequest_noTransactions_showsDeleteConfirmation
- onDeleteRequest_withTransactions_showsTransactionWarning
- onConfirmDelete_deletesAndShowsSnackbarWithUndo
- onConfirmArchive_archivesAndShowsSnackbarWithUndo
- onUndoDelete_reInsertsAccount
- onUndoArchive_restoresAccount
- onFormChange_updatesFormState
- onDismissDialog_clearsDialogState
- onDismissSnackbar_clearsSnackbarMessage
- onUnarchiveAccount_restoresAccount
```

### گام ۶.۲: تکمیل Compose UI Tests

```kotlin
// AccountManagementScreenTest:
- emptyState_showsCorrectMessageAndAction
- list_showsAccountItems
- fab_opensAddDialog
- overflowMenu_showsEditArchiveDelete
- overflowEdit_opensEditDialog
- overflowArchive_showsConfirmationDialog
- overflowDelete_showsDeleteDialogOrWarning

// AccountFormDialogTest:
- emptyForm_showsAllFields
- fillForm_enablesSaveButton
- emptyName_disablesSaveButton
- typeDropdown_showsAllTypes
- bankFields_visibleForBankType
- bankFields_hiddenForOtherTypes
- colorPicker_selectsColor
- previewRow_updatesWithForm
- save_emitsFormWithCorrectData
```

### گام ۶.۳: Accessibility Semantics

**AccountListCard:**
```kotlin
Modifier.semantics {
  contentDescription = "حساب «${account.name}»، ${account.type.displayName}، ${formattedBalance}"
}
```

**AccountColorPicker:**
```kotlin
// هر swatch:
Modifier.semantics {
  contentDescription = "رنگ ${colorName}"
  selected = isSelected
  role = Role.Button
}
```

**AccountFormDialog:**
```kotlin
Modifier.semantics {
  liveRegion = LiveRegion.Polite  // برای اعلام error messages
}
```

**AccountManagementScreen:**
```kotlin
FAB:
Modifier.testTag("addAccountFab")
  .semantics { contentDescription = "افزودن حساب" }

Empty State:
Modifier.testTag("emptyState")

Account List:
Modifier.testTag("accountList")
```

### گام ۶.۴: Snapshot Tests (اگر Roborazzi نصب باشه)

```kotlin
// AccountManagementScreenSnapshotTest:
- emptyState
- listWith3Accounts
- addDialogEmptyForm
- addDialogFilledForm
- editDialog
- deleteConfirmation
- transactionWarning
- archiveConfirmation
```

### گام ۶.۵: اجرای نهایی تست‌ها

```bash
./gradlew test --rerun-tasks --no-daemon
```

## نکات خاص این فاز

- از چک‌لیست مرکزی:
  - **R3** (تأیید تست): خروجی دقیق `./gradlew test --rerun-tasks --no-daemon` رو ثبت کنید
  - **R4** (grep): ادعای "تمام accessibility اضافه شد" رو با grep مستقل تأیید کنید
  - **R2** (اعداد منفی): اگر تست snapshot اضافه بشه، scenario منفی balance رو هم شامل بشه
- **مهم:** `forkEvery = 1` در `build.gradle.kts` باید فعال باشه (برای Rust JNI isolation)
- Test naming convention: camelCase (نه backtick) — طبق AGENTS.md

## معیار پذیرش

- [ ] `AccountViewModelTest` → تمام تستها pass
- [ ] `AccountManagementScreenTest` → تمام تستها pass
- [ ] `AccountFormDialogTest` → تمام تستها pass
- [ ] `ArchiveAccountUseCaseTest` → تمام تستها pass
- [ ] `UnarchiveAccountUseCaseTest` → تمام تستها pass
- [ ] `AccountValidatorTest` → تمام تستها pass
- [ ] تمام کامپوننت‌ها `contentDescription` دارن (grep تأیید)
- [ ] تمام کامپوننت‌ها `testTag` دارن (grep تأیید)
- [ ] `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → بدون خطا (اگر detekt نصب باشه)
- [ ] Test count: تمام تستهای جدید شمارش بشه و در log ثبت بشه

## Rollback

```bash
git log --oneline -5
git revert <phase-6-commits>
# یا حذف فایل‌های تست جدید
rm app/src/test/java/.../AccountViewModelTest.kt
rm app/src/test/java/.../AccountManagementScreenTest.kt
# ... etc
# revert accessibility changes در component files
```
