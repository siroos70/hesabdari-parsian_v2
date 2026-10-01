# چک‌لیست ریفکتور مدیریت حساب‌ها

> **شروع:** ۲۰۲۶-۰۷-۳۱  
> **آخرین به‌روزرسانی:** ۲۰۲۶-۰۷-۳۱  
> **شاخه:** `feature/multi-account-wallet`

---

## وضعیت فازها

- [ ] **Phase 0:** رفع باگ‌های واقعی (بدون تغییر معماری)
- [ ] **Phase 1:** استخراج Domain/Data layer (Dao, Repository, UseCase, Validator)
- [ ] **Phase 2:** معماری State (Event, UiState, ViewModel refactor)
- [ ] **Phase 3:** استخراج کامپوننت‌های UI
- [ ] **Phase 4:** بازنویسی Screen به shell + دیالوگ‌ها
- [ ] **Phase 5:** بهبودهای UX (Undo، Unarchive، Progressive disclosure)
- [ ] **Phase 6:** تست‌ها و Accessibility

---

## تصمیمات معلق

> هر تصمیم باید **قبل از فاز وابسته** روشن بشه. وضعیت هر تصمیم با ✅ (روشن) یا ⏳ (معلق) مشخص می‌شه.

### ۱. یکتایی نام حساب
- **وضعیت:** ⏳ معلق
- **سوال:** آیا نام تکراری حساب مجاز باشه یا validation سخت‌گیر اعمال بشه؟
- **گزینه‌ها:**
  - الف) Validation سخت‌گیر: insert/update رد بشه اگر نام تکراری وجود داشته باشه
  - B) Warning غیرمسدودکننده: پیام هشدار نشون داده بشه ولی عملیات انجام بشه
  - ج) بدون محدودیت: وضعیت فعلی
- **وابسته به:** Phase 1 (`AccountValidator`)
- **تاثیر:** اگر الف یا ب انتخاب بشه، `AccountValidator` باید `getAllAccounts()` رو بخونه

### ۲. FK Constraint برای حذف حساب
- **وضعیت:** ⏳ معلق
- **سوال:** حذف حساب دارای تراکنش: فقط app-level check (`canDeleteAccount`) بمونه یا FK constraint واقعی (`onDelete=RESTRICT`) اضافه بشه؟
- **گزینه‌ها:**
  - الف) فقط app-level check (وضعیت فعلی) — ساده‌تر، ولی backup/restore ممکنه data integrity رو بشکنه
  - ب) FK constraint `onDelete=RESTRICT` — ایمن‌تر، ولی Room migration نیاز داره
- **وابسته به:** Phase 1 (Room schema)
- **تاثیر:** اگر ب انتخاب بشه، Room migration لازمه و `HesabyarRepository.replaceAllFromBackup` باید order حذف رو رعایت کنه

### ۳. ابزارهای تست
- **وضعیت:** ⏳ معلق
- **سوال:** آیا Roborazzi و Detekt واقعاً در پروژه نصب/پیکربندی شدن؟
- **اقدام:** قبل از Phase 6 با grep در `build.gradle.kts` تأیید بشه:
  ```bash
  grep -rn "roborazzi\|detekt" app/build.gradle.kts build.gradle.kts
  ```
- **وابسته به:** Phase 6

### ۴. DEFAULT_ACCOUNT_COLOR یکسان‌سازی
- **وضعیت:** ⏳ معلق
- **سوال:** سه نسخه `DEFAULT_ACCOUNT_COLOR` باید یکسان باشن — تأیید مقدار دقیق:
  - `FinancialColors.kt:37` → `0xFF4CAF50L`
  - `ManageBackupUseCase.kt:30` → `0xFF4CAF50L`
  - Rust `default_color()` → `0xFF4CAF50`
- **اقدام تأیید:**
  ```bash
  grep -n "DEFAULT_ACCOUNT_COLOR\|default_color" app/src/main/java/.../FinancialColors.kt app/src/main/java/.../ManageBackupUseCase.kt rust/hesabyar-core/src/models/mod.rs
  ```
- **وابسته به:** Phase 0 یا Phase 1

---

## ریسک‌های خاص این پروژه

> این موارد باید **در هر فاز** چک بشن — نه فقط فاز مربوطه.

### R1. تطابق Rust vs Kotlin Fallback
- [ ] هرجا محاسبه‌ی موجودی (initial + محاسبه‌شده) نمایش داده می‌شه، مسیر Rust و Kotlin fallback مقایسه بشن
- [ ] مخصوصاً فیلتر حساب آرشیوشده در هر دو مسیر یکسان عمل کنه
- [ ] **تست تأیید:** `./gradlew test --tests "io.github.mojri.hesabyar.GetDashboardDataUseCaseTest.rustAndKotlinFallbackProduceSameResultWithFixedNow" --no-daemon`
- [ ] **قانون جدید:** این چک‌لیست صرفاً برای کد fallback موجود اعمال می‌شود. هیچ‌گاه منطق جدید یا محاسبه‌ای به سمت طرف Kotlin fallback اضافه نکنید — فقط در Rust. برنامه حذف fallbackهای غیردائم در `plans/2026-08-19-rust-fallback-consolidation-plan.md` مرجع شود.

### R2. نمایش اعداد منفی (RTL/BIDI)
- [ ] هرجا مبلغ منفی نمایش داده می‌شه (خصوصاً کامپوننت جدید «موجودی محاسبه‌شده» در Phase 5)، از الگوی تست‌شده‌ی موجود استفاده بشه:
  - LRM (`\u200E`) قبل از عدد
  - جداسازی sign و amount در Text جداگانه (طبق `CurrencyFormatter.formatSignedParts`)
  - `LocalLayoutDirection.Ltr` روی کانتینر عددی
- [ ] **رجوع کنید به:** `CurrencyFormatter.kt:87` (الگوی LRM) و `AccountBalanceCard.kt` (الگوی sign/amount separation)

### R3. تأیید تست‌ها
- [ ] ادعای "تست شد" agent در هر فاز باید با **عدد تست، خروجی دقیقِ دستور اجرا، و در صورت لزوم اسکرین‌شات** تأیید بشه
- [ ] دستور اجرای تست: `./gradlew test --rerun-tasks --no-daemon` (نه `./gradlew test` ساده — چون build cache ممکنه نتایج قدیمی رو برگردونه)

### R4. تأیید "انجام شد در همه‌جا"
- [ ] ادعای "کامل انجام شد در همه‌جا" باید با **grep مستقل** چک بشه قبل از قبول
- [ ] مثال: اگر قراره `ACCOUNT_TYPE_ICONS` حذف بشه، grep کنید که هیچ reference‌ای باقی نمونده

### R5. Rust JNI State Leakage
- [ ] اگر هر فازی کد Rust رو لمس کنه (حتی فقط mappers)، قبل از merge با `./gradlew clean test --no-daemon` تأیید بشه
- [ ] `forkEvery = 1` در `build.gradle.kts` باید فعال باشه

---

## Log تکمیل فازها

> بعد از هر فاز پر بشه.

### Phase 0
- **تاریخ:**
- **خلاصه تغییرات:**
- **نتیجه تست:**
- **لینک گزارش تأیید:**
- **تعداد commit:**

### Phase 1
- **تاریخ:**
- **خلاصه تغییرات:**
- **نتیجه تست:**
- **لینک گزارش تأیید:**

### Phase 2
- **تاریخ:**
- **خلاصه تغییرات:**
- **نتیجه تست:**
- **لینک گزارش تأیید:**

### Phase 3
- **تاریخ:**
- **خلاصه تغییرات:**
- **نتیجه تست:**
- **لینک گزارش تأیید:**

### Phase 4
- **تاریخ:**
- **خلاصه تغییرات:**
- **نتیجه تست:**
- **لینک گزارش تأیید:**

### Phase 5
- **تاریخ:**
- **خلاصه تغییرات:**
- **نتیجه تست:**
- **لینک گزارش تأیید:**

### Phase 6
- **تاریخ:**
- **خلاصه تغییرات:**
- **نتیجه تست:**
- **لینک گزارش تأیید:**
