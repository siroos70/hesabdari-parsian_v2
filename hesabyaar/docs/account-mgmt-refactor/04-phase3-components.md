# Phase 3: استخراج کامپوننت‌های UI

## پیش‌نیاز

- **فاز قبلی:** Phase 2 باید کامل شده باشه (AccountUiState و AccountEvent آماده‌ان)
- **تصمیمات معلق:** ندارد

## زمینه

`AccountManagementScreen.kt` فعلی ۷۳۱ خط و شامل ۱۲+ کامپوننت خصوصیه. این فاز کامپوننت‌ها رو از فایل اصلی استخراج می‌کنه و به فایل‌های جداگانه منتقل می‌کنه. در پایان، `AccountManagementScreen.kt` باید ≤ ۲۰۰ خط باشه.

## هدف دقیق این فاز

استخراج ۷ کامپوننت از `AccountManagementScreen.kt` به فایل‌های جداگانه در `ui/components/account/`، و اضافه کردن `@Preview` به هر کدوم.

## فایل‌های درگیر

### فایل‌های جدید
| فایل | توضیح |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountListCard.kt` | کارت لیست حساب |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountFormContent.kt` | بدنه فرم (مشترک Add/Edit) |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountColorPicker.kt` | انتخابگر رنگ |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountTypeDropdown.kt` | انتخابگر نوع حساب |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountBankFields.kt` | فیلدهای بانکی (شرطی) |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountPreviewRow.kt` | پیش‌نمایش زنده |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountOverflowMenu.kt` | منوی overflow |

### فایل ویرایشی
| فایل | تغییر |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt` | حذف composable‌های خصوصی، import کامپوننت‌های جدید |

## گام‌های اجرا

### گام ۳.۱: ساخت دایرکتوری و فایل‌های خالی
```bash
mkdir -p app/src/main/java/io/github/mojri/hesabyar/ui/components/account
```

### گام ۳.۲: استخراج AccountListCard

**منبع:** `AccountManagementScreen.kt` خطوط ۳۲۶-۳۹۷ (`AccountItem`)  
**ورودی‌ها:** `account: AccountEntity`, `onOverflow: (AccountEntity) -> Unit`  
**ساختار:** `HesabyarCard` → `Row` → `IconCircle` + `Column` + `IconButton`  
**@Preview:** با یک `AccountEntity` نمونه

### گام ۳.۳: استخراج AccountFormContent

**منبع:** `AccountManagementScreen.kt` خطوط ۴۶۸-۵۵۱ (`AccountDialogForm`)  
**ورودی‌ها:** `formState: AccountFormState`, `onFormChange: (AccountFormState) -> Unit`, `onSave: () -> Unit`, `isSaving: Boolean`  
**نکته:** در Phase 4، state از `remember` به `AccountFormState` از ViewModel تغییر می‌کنه. در این فاز، هنوز `remember` استفاده می‌شه.  
**@Preview:** فرم خالی + فرم پر

### گام ۳.۴: استخراج AccountColorPicker

**منبع:** `AccountManagementScreen.kt` خطوط ۶۴۶-۶۹۳  
**ورودی‌ها:** `selectedColor: Long`, `palette: List<Long>`, `columns: Int`, `onColorSelected: (Long) -> Unit`  
**@Preview:** با رنگ‌های نمونه

### گام ۳.۵: استخراج AccountTypeDropdown

**منبع:** `AccountManagementScreen.kt` خطوط ۵۵۴-۵۹۶  
**ورودی‌ها:** `selectedType: AccountType`, `onTypeSelected: (AccountType) -> Unit`  
**@Preview:** با نوع پیش‌فرض

### گام ۳.۶: استخراج AccountBankFields

**منبع:** `AccountManagementScreen.kt` خطوط ۵۹۸-۶۴۴  
**ورودی‌ها:** bankName, cardNumber, accountNumber, iban + callbacks  
**@Preview:** با مقادیر نمونه

### گام ۳.۷: استخراج AccountPreviewRow

**منبع:** `AccountManagementScreen.kt` خطوط ۶۹۵-۷۳۰  
**ورودی‌ها:** `name: String`, `type: AccountType`, `color: Long`  
**@Preview:** با مقادیر نمونه

### گام ۳.۸: استخراج AccountOverflowMenu

**منبع:** `AccountManagementScreen.kt` خطوط ۴۰۰-۴۴۳  
**ورودی‌ها:** `onEdit`, `onArchive`, `onDelete`, `onDismiss`  
**@Preview:** با expanded=true

### گام ۳.۹: به‌روزرسانی AccountManagementScreen

- تمام composable‌های خصوصی حذف بشن
- import‌های جدید اضافه بشن
- فایل باید ≤ ۲۰۰ خط باشه
- `ACCOUNT_TYPE_ICONS` map (اگر در Phase 0 حذف نشده) حذف بشه

### گام ۳.۱۰: اضافه کردن ColorPickerGrid (Generic)

- فایل جدید `ui/components/shared/ColorPickerGrid.kt`
- `AccountColorPicker` از این component استفاده کنه
- **قابل بازاستفاده:** برای رنگ دسته‌بندی، تگ، و هر انتخابگر رنگ دیگه

## نکات خاص این فاز

- از چک‌لیست مرکزی:
  - **R2** (نمایش اعداد منفی): اگر `AccountListCard` مبلغ منفی نمایش بده، از الگوی LRM استفاده کنه
  - **R4** (تأیید grep): بعد از حذف composable‌ها از فایل اصلی، grep کنید که هیچ reference قدیمی باقی نمونده
- **مهم:** در این فاز state هنوز `remember`-based هست. هدف فقط استخراج فیزیکی فایل‌هاست، نه تغییر state architecture (اون Phase 4 هست).
- هر composable باید `Modifier` parameter داشته باشه برای testability
- هر composable باید `@Preview` function داشته باشه

## معیار پذیرش

- [ ] ۷ فایل جدید در `ui/components/account/` وجود داره
- [ ] ۱ فایل جدید `ColorPickerGrid.kt` در `ui/components/shared/` وجود داره
- [ ] `AccountManagementScreen.kt` ≤ ۲۰۰ خط هست
- [ ] هر فایل جدید `@Preview` function داره
- [ ] با grep تأیید: `AccountItem`، `AccountDialogForm`، `AccountDialogColorPicker`، `AccountDialogTypeField`، `AccountDialogBankDetailsFields`، `AccountDialogPreviewRow`، `AccountOverflowMenu` در فایل اصلی وجود ندارن
- [ ] `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → بدون خطا
- [ ] Previewها در Android Studio render می‌شن

## Rollback

```bash
# rollback کل Phase 3:
git log --oneline -10
git revert <phase-3-commits>

# یا rollback دستی:
# فایل‌های جدید رو حذف کنید
rm app/src/main/java/.../ui/components/account/*.kt
rm app/src/main/java/.../ui/components/shared/ColorPickerGrid.kt
# فایل اصلی رو از git برگردونید
git checkout HEAD -- app/src/main/java/.../ui/screens/account/AccountManagementScreen.kt
```
