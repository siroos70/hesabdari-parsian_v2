# Phase 0: رفع باگ‌های واقعی (بدون تغییر معماری)

## پیش‌نیاز

- **فاز قبلی:** ندارد — این اولین فاز است
- **تصمیمات معلق:** تصمیم #4 (یکسان‌سازی `DEFAULT_ACCOUNT_COLOR`) باید قبل از شروع تأیید بشه

## زمینه

گزارش Gap Analysis چهار باگ واقعی در Account Management شناسایی کرده که هیچ‌کدام تغییر معماری نمی‌خوان — فقط فیکس‌های کوچک و کم‌ریسک در فایل‌های موجود. هر کدوم باید commit جداگانه داشته باشه.

## هدف دقیق این فاز

رفع ۴ باگ مشخص بدون تغییر ساختار معماری. خروجی: ۴ commit تمیز روی شاخه `feature/multi-account-wallet`.

## فایل‌های درگیر

| فایل | تغییر |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt` | باگ #1 (overflow anchoring) + باگ #2 (archive confirmation) |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/AccountTypeIcon.kt` | باگ #3 ('icon OTHER' — ممکنه نیاز به تغییر نداشته باشه) |
| `app/src/main/java/io/github/mojri/hesabyar/ui/designsystem/FinancialColors.kt` | باگ #4 (منبع اصلی) |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageBackupUseCase.kt` | باگ #4 (حذف کپی تکراری) |

## گام‌های اجرا

### باگ #1: فیکس anchoring نادرست AccountOverflowMenu

**مشکل:** `AccountOverflowMenu` (DropdownMenu) در `AccountManagementDialogs` رندر می‌شه که sibling Scaffold content هست، نه داخل `Box` اطراف `IconButton`. DropdownMenu باید نسبت به والد `Box` خودش لنگر بگیره.

**مسیر فایل:** `AccountManagementScreen.kt`  
**مسیر کد فعلی:** خطوط ۳۸۳-۳۹۵ (IconButton داخل Box) و خطوط ۱۸۸-۲۲۸ (AccountManagementDialogs)

**راه‌حل:**
- `AccountOverflowMenu` باید مستقیماً داخل `Box` اطراف `IconButton` در `AccountItem` رندر بشه
- state `OverflowMenu(account)` باید از `AccountManagementDialogs` به `AccountItem` منتقل بشه
- یکی از دو راه:
  - الف) `dialogState` رو به `AccountItem` پاس بدیم و overflow menu رو داخل `Box` رندر کنیم
  - ب) `AccountOverflowMenu` رو به‌عنوان child مستقیم `IconButton` wrapper کنیم

**چک بعد از اجرا:**
```bash
grep -n "AccountOverflowMenu" app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt
# باید داخل Box/Column مربوط به AccountItem باشه، نه در سطح Scaffold
```

**Commit:** `fix(ui): anchor AccountOverflowMenu to overflow button instead of Scaffold`

---

### باگ #2: اضافه کردن دیالوگ تأیید قبل از آرشیو

**مشکل:** آرشیو کردن حساب با یک تپ انجام می‌شه (خط ۲۲۴) بدون هیچ confirmation. حساب آرشیوشده از داشبورد حذف می‌شه و راه بازیابی آسانی نداره.

**مسیر فایل:** `AccountManagementScreen.kt`  
**مسیر کد فعلی:** خط ۲۲۴ — `OverflowAction.ARCHIVE -> accountViewModel.archiveAccount(account)`

**راه‌حل:**
- یک state جدید `ArchiveConfirmation(account: AccountEntity)` به `AccountDialogState` اضافه بشه
- در `onOverflowAction` وقتی `OverflowAction.ARCHIVE` انتخاب می‌شه، `dialogState = ArchiveConfirmation(account)` بشه نه `archiveAccount`
- در `AccountManagementDialogs` case جدید `ArchiveConfirmation` با `ConfirmDialog` اضافه بشه
- پیام تأیید: `آیا از آرشیو کردن حساب «{name}» اطمینان دارید؟ حساب از داشبورد حذف خواهد شد.`
- دکمه تأیید: "آرشیو" با رنگ `MaterialTheme.colorScheme.primary` (نه error — چون آرشیو destructive نیست)

**چک بعد از اجرا:**
```bash
grep -n "ArchiveConfirmation" app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt
# باید حداقل ۳ reference باشه: sealed interface, when case, ConfirmDialog call
```

**Commit:** `fix(ui): add confirmation dialog before archiving an account`

---

### باگ #3: رفع ناهماهنگی نقشه آیکون OTHER

**مشکل:** نگاشت `AccountType → icon` در دو مکان وجود دارد و برای نوع `OTHER` متفاوته:
- `AccountManagementScreen.kt:91` → `ACCOUNT_TYPE_ICONS[OTHER] = Icons.Filled.Payments`
- `AccountTypeIcon.kt:30` → `AccountType.icon(OTHER) = Icons.Filled.MoreHoriz`

**مسیر فایل:**
- `AccountManagementScreen.kt` — خطوط ۸۶-۹۲ (`ACCOUNT_TYPE_ICONS`)
- `ui/components/AccountTypeIcon.kt` — خطوط ۲۵-۳۱ (`AccountType.icon()`)

**راه‌حل:**
- `ACCOUNT_TYPE_ICONS` map در `AccountManagementScreen.kt` حذف بشه
- در تمام جاهایی که از `ACCOUNT_TYPE_ICONS` استفاده شده (خطوط ۳۳۱، ۷۱۲)، به `AccountType.icon()` رفرنس داده بشه
- یکی از دو آیکون انتخاب بشه: `MoreHoriz` (از `AccountTypeIcon.kt`) یا `Payments`
- **توصیه:** `MoreHoriz` بهتره چون معنای "سایر" رو بهتر می‌رسونه و در جاهای دیگه app استفاده شده

**چک بعد از اجرا:**
```bash
grep -n "ACCOUNT_TYPE_ICONS" app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt
# باید ۰ نتیجه برگردونه (حذف شده)
grep -n "AccountType.icon\|\.icon()" app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt
# باید حداقل ۲ reference باشه
```

**Commit:** `fix(ui): remove duplicate icon mapping, use AccountType.icon() consistently`

---

### باگ #4: یکسان‌سازی DEFAULT_ACCOUNT_COLOR

**مشکل:** `DEFAULT_ACCOUNT_COLOR` در سه مکان تعریف شده. هر سه مقدار `0xFF4CAF50L` هستن ولی منبع واحد نیستن.

**مسیر فایلها:**
- `ui/designsystem/FinancialColors.kt:37` — `const val DEFAULT_ACCOUNT_COLOR = 0xFF4CAF50L`
- `domain/usecase/ManageBackupUseCase.kt:30` — `const val DEFAULT_ACCOUNT_COLOR = 0xFF4CAF50L`
- `rust/hesabyar-core/src/models/mod.rs:116` — `fn default_color() -> i64 { 0xFF4CAF50 }`

**راه‌حل:**
1. مقدار دقیق هر سه رو تأیید کنید (باید یکسان باشن)
2. `ManageBackupUseCase.kt:30` رو حذف کنید و import از `FinancialColors.DEFAULT_ACCOUNT_COLOR` اضافه کنید
3. مقدار Rust نباید تغییر کنه (فقط Kotlin-side duplicate حذف بشه)
> **یادآوری معماری:** Rust منبع تک‌حقیقت منطق تجاری است. این برطرف‌سازی صرفاً حذف یک duplicate طرف Kotlin است — Rust ارجاع اصلی محسوب می‌شود. هیچ‌گاه منطق یا محاسبه‌ جدیدی به سمت Kotlin اضافه نکنید.

**چک بعد از اجرا:**
```bash
grep -rn "DEFAULT_ACCOUNT_COLOR" app/src/main/java/ | grep -v "build/"
# باید فقط FinancialColors.kt و import‌های اون رو برگردونه
```

**Commit:** `fix: remove duplicate DEFAULT_ACCOUNT_COLOR in ManageBackupUseCase`

---

## نکات خاص این فاز

- از چک‌لیست مرکزی: **تصمیم #4** (یکسان‌سازی رنگ) باید قبل از شروع باگ #4 تأیید بشه
- از چک‌لیست مرکزی: **R1** (تطابق Rust/Kotlin) — باگ #4 فقط Kotlin-side duplicate رو حذف می‌کنه، مقدار Rust تغییر نمی‌کنه پس R1 نقض نمی‌شه. یادآوری: این صرفاً یک اصلاح fallback موجود است — راهنمایی برای اضافه کردن منطق جدید در Kotlin نیست.
- هر باگ باید **commit جداگانه** داشته باشه تا rollback آسان باشه
- بعد از هر باگ، `./gradlew test --no-daemon` اجرا بشه

## معیار پذیرش

- [ ] باگ #1: `AccountOverflowMenu` داخل `Box` اطراف `IconButton` رندر بشه (نه در سطح Scaffold)
- [ ] باگ #2: آرشیو کردن با confirmation dialog انجام بشه
- [ ] باگ #3: `ACCOUNT_TYPE_ICONS` map حذف شده و `AccountType.icon()` استفاده بشه
- [ ] باگ #4: `ManageBackupUseCase.kt` دیگه `DEFAULT_ACCOUNT_COLOR` جداگانه نداشته باشه
- [ ] `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → بدون خطا
- [ ] ۴ commit جداگانه روی شاخه
- [ ] با grep تأیید بشه که هیچ reference قدیمی باقی نمونده

## Rollback

هر باگ جداگانه قابل rollback هست:
```bash
git log --oneline  # شماره commit مورد نظر رو پیدا کنید
git revert <commit-hash>
```
یا همه باگ‌ها با هم:
```bash
git diff HEAD~4..HEAD --stat  # تأیید ۴ commit آخر
git reset --hard HEAD~4
```
