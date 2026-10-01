# Code Review — حسابیار (Hesabyaar)

**تاریخ بازبینی:** 2026-06-25
**برنچ:** `feature/phase-0-setup`
**محدوده:** کل ماژول `app/` — لایه‌های `data/`, `api/`, `ui/`, `reminder/`

> این سند حاصل بازبینی خودکارد کد فعلی است و پایه‌ی فازهای بهبود پروژه (Phase 1–4) را تشکیل می‌دهد. هر مورد با اولویت و فاز پیشنهادی برچسب‌گذاری شده است.

---

## ۱. خلاصه اجرایی

| بخش | وضعیت کلی | نکات کلیدی |
|------|-----------|------------|
| **معماری پول** | ✅ خوب | همه‌جا `Long` (ریال)، بدون `Float/Double` در لایه دیتا |
| **کوروتین‌ها** | ✅ خوب | بدون `GlobalScope`، استفاده درست از `viewModelScope`/`CoroutineWorker` |
| **امنیت کلیدها** | ✅ خوب | بدون کلید هاردکدشده، ذخیره رمزنگاری‌شده در `EncryptedSharedPreferences` |
| **مهاجرت Room** | ✅ غیرمخرب | مهاجرت صریح، بدون `fallbackToDestructiveMigration` |
| **اتمیک بودن تراکنش‌ها** | 🔴 بحرانی | عملیات مالی چندمرحله‌ای بدون `@Transaction` |
| **دقت محاسبات پارسر** | 🔴 بحرانی | مسیر پارس از `Double` عبور می‌کند |
| **اعتبارسنجی خروجی AI** | 🟡 ضعیف | بدون validate روی type/hour/amount/confidence |
| **وابستگی‌پذیری ViewModel** | 🟡 ضعیف | بدون DI، ViewModel‌ها قابل unit-test نیستند |
| **مستندسازی** | ✅ قوی | ۹ فایل در `docs/` |
| **تست‌ها** | 🟡 متوسط | ۱۴ فایل تست منطق خالص، بدون تست ViewModel/UI |

**نکته:** بنیادها (coroutineها، نوع پول، امنیت کلیدها) محکم است. کار فازهای بعدی بیشتر **سخت‌سازی و قابلیت تست** است تا اصلاح باگ‌های اساسی.

---

## ۲. یافته‌های بحرانی (CRITICAL)

### ۲.۱. اتمیک نبودن عملیات مالی چندمرحله‌ای
**فایل:** `data/HesabyarRepository.kt` (سطرهای ۷۴–۱۱۰، ۱۱۷–۱۲۹، ۱۳۶–۱۶۸)
**فاز پیشنهادی:** Phase 1 (معماری)

چند عملیات که باید اتمیک باشند در فراخوانی‌های مجزا اجرا می‌شوند و در هیچ `@Transaction` یا `db.withTransaction { }` پیچیده نشده‌اند:

- `addPaymentToLoan`: `updateLoan` + `insertPayment` + `insertTransaction` به‌صورت سه فراخوانی جدا. اگر فرآیند وسط کار قطع شود، `remainingAmount` وام کاهش می‌یابد بدون اینکه `PaymentHistory` ثبت شود (یا برعکس).
- `updateInstallment`: علاوه بر مشکل اتمیک نبودن، **هر بار آپدیت قسطِ قبلاً پرداخت‌شده، یک تراکنش تکراری insert می‌کند** (بدون بررسی idempotency).
- `importBackup` / `replaceAllFromBackup`: `deleteAll` سپس loop-insert. یک crash وسط loop دیتابیس را نیمه‌خالی رها می‌کند بدون rollback.

> هیچ استفاده‌ای از `@Transaction` / `withTransaction` در کل کدبیس وجود ندارد.

**اقدام:**
```kotlin
// افزودن به AppDatabase یا Repository
suspend fun <R> withTransaction(block: suspend () -> R): R =
    db.withTransaction { block() }
```
و پیچیدن تمام عملیات چندمرحله‌ای مالی و backup/restore در آن.

---

### ۲.۲. دقت محاسبات پول در پارسر (Double → Long)
**فایل:** `api/PersianAmountParser.kt:6`, `api/GeminiParser.kt:75,253,303,490`
**فاز پیشنهادی:** Phase 2 (AI)

با وجود اینکه مدل دیتا کاملاً `Long` است، مسیر پارس از `Double` عبور می‌کند که برای پول ناامن است:

- `Token.Number(val value: Double)` — باید `Long` باشد.
- `interpretWithUnits`: `total += (currentNum * token.type.multiplier).toLong()` — ضرب `Double × Long → Double` سپس truncate.
- `parseSentenceOffline`: `var amountToman = 0.0` سپس `(amountToman * 1000).toLong()`.
- `parseJsonResult`: `(json.optDouble("amount", 0.0) * 1000).toLong()`.

خطر: `.toLong()` روی `Double` برای مقادیر بزرگ دقت را از دست می‌دهد (مثلاً ۱۹ رقم). ضریب‌های واحد (`UnitType.multiplier`) از قبل `Long` هستند، پس حذف کامل `Double` از `Token.Number` ممکن است.

**اقدام:** تبدیل `Token.Number` به `Long` و حذف تمام `.toDouble()/.toLong()` در مسیر پارس.

---

## ۳. یافته‌های با اولویت بالا (HIGH)

### ۳.۱. نبود Foreign Key و Index
**فایل:** `data/Entities.kt`, `data/Daos.kt`
**فاز پیشنهادی:** Phase 1 (معماری)

هیچ `ForeignKey` و هیچ `indices` در هیچ Entity تعریف نشده:

- رابطه‌های منطقی بدون اعمال: `Transaction.categoryId → Category.id`, `Transaction.installmentId → Installment.id`, `PaymentHistory.loanId → Loan.id`.
- **پیامد:** حذف `Category`/`Loan`/`Installment` می‌تواند ردیف‌های یتیم در `Transaction`/`PaymentHistory` ایجاد کند و crash در UI هنگام lookup null.
- ستون‌های پراستفاده بدون index: `payment_history.loanId` (اجرای per-loan در Flow)، `transactions.date` (range scan `WHERE date BETWEEN`).

**اقدام:** افزودن `ForeignKey` با `onDelete` مناسب + `Index` روی حداقل `loanId`, `categoryId`, `date`. این نیازمند مهاجرت Room جدید (version 4) است.

---

### ۳.۲. اعتبارسنجی ضعیف خروجی AI
**فایل:** `api/GeminiParser.kt:69–91` (`parseJsonResult`)
**فاز پیشنهادی:** Phase 2 (AI)

خروجی مدل با `optString`/`optDouble` و مقادیر پیش‌فرض خوانده می‌شود و بی‌چون‌وچرا پذیرفته می‌شود:

- `type` اعتبارسنجی نمی‌شود که یکی از مقادیر مجاز enum باشد (هر رشته‌ای پذیرفته می‌شود، پیش‌فرض `"EXPENSE"`).
- بدون بررسی بازه روی `hour`/`minute` (مدل می‌تواند `hour=99` برگرداند).
- بدون بررسی `amount >= 0` یا معقول بودن `daysFromNow`/`dateOffsetDays`.
- `confidence` پارس می‌شود اما هیچ‌جا برای رد نتایج کم‌اطمینان استفاده نمی‌شود.

**اقدام:** افزودن تابع `validateParsedResult()` طبق Task 2-1 پلن.

---

### ۳.۳. ViewModel‌ها قابل unit-test نیستند (نبود DI)
**فایل:** همه‌ی ۹ ViewModel در `ui/`
**فاز پیشنهادی:** Phase 1 (معماری)

هر ViewModel وابستگی‌هایش را داخلی می‌سازد:
```kotlin
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = HesabyarRepository(database.transactionDao(), …)
```
به همین دلیل تست‌ها فقط روی helper‌های منطق خالص متمرکزند، نه ViewModel‌ها. `InstallmentReminderWorker` هم مستقیماً `AppDatabase.getDatabase(applicationContext)` را صدا می‌زند.

**اقدام:** معرفی Hilt (Task 1-2) و تزریق Repository (به‌عنوان interface) در سازنده ViewModel‌ها و Worker‌ها.

---

## ۴. یافته‌های با اولویت متوسط (MEDIUM)

### ۴.۱. `exportSchema = false` در Room
**فایل:** `data/AppDatabase.kt:10–14`
**فاز پیشنهادی:** Phase 1

با `exportSchema = false` هیچ `schema/` JSON برای اعتبارسنجی مهاجرت‌ها تولید نمی‌شود. با توجه به DDL دستی سنگین در مهاجرت‌ها (از جمله rename ستون `category TEXT → categoryId INTEGER`)، این شکننده است.

**اقدام:** `exportSchema = true` و commit کردن schema‌های تولیدشده.

---

### ۴.۲. دوگانگی مفهوم «ماهانه»
**فایل:** `ui/DashboardViewModel.kt:39` در برابر `ui/AnalyticsViewModel`
**فاز پیشنهادی:** Phase 1

`DashboardViewModel` درآمد/هزینه «ماهانه» را به‌عنوان پنجره‌ی غلتان ۳۰ روزه محاسبه می‌کند:
```kotlin
val oneMonthAgo = now - (30L * 24L * 60L * 60L * 1000L)
```
اما `AnalyticsViewModel` به‌درستی بر اساس ماه جلالی گروه‌بندی می‌کند. دو صفحه روی مفهوم «این ماه» اختلاف خواهند داشت.

**اقدام:** استفاده از `JalaliCalendarHelper` در `DashboardViewModel` نیز.

---

### ۴.۳. خرابی abstraction پروایدر AI
**فایل:** `api/AiProvider.kt`
**فاز پیشنهادی:** Phase 2

`AiProvider` یک `object` (singleton) است نه interface. انتخاب پروایدر یک `when` روی enum است — افزودن پروایدر یعنی ویرایش `when` (نقض open/closed). نام `GeminiParser` هم گمراه‌کننده است چون با هر پروایدر پیکربندی‌شده‌ای کار می‌کند.

همچنین منطق مشاوره بودجه دو بار پیاده‌سازی شده: `GeminiParser.getBudgetAdvice` و `BudgetAdvisor.getBudgetAdvice`.

**اقدام:** استخراج `interface AiProvider` (Task 1-1 / 2-2) و یکپارچه‌سازی منطق بودجه.

---

### ۴.۴. تکرار literal روز-به-میلی‌ثانیه و اعداد جادویی
**فایل:** `DashboardViewModel.kt:39`, `ReportsScreen.kt:55,131`, `AiProviderConfig.kt:44`, `AiAssistantViewModel.kt:118`, `InstallmentReminderWorker.kt:36`
**فاز پیشنهادی:** Phase 1

`24*60*60*1000` حداقل در ۵ جای کد با سبک‌های مختلف تکرار شده. `-7` (پنجره‌ی ۷ روزه سررسید گذشته) هم هاردکد است.

**اقدام:** استخراج ثابت‌های نام‌گذاری‌شده یا استفاده از `kotlin.time.Duration`/`TimeUnit`.

---

### ۴.۵. فایل‌های UI غول‌پیکر و رشته‌های فارسی inline
**فایل:** `ui/screens/DashboardScreen.kt` (۱۸۴۸ خط), `SettingsScreen.kt` (۱۴۳۹), `SmartAssistantScreen.kt` (۱۳۱۷)
**فاز پیشنهادی:** Phase 1 / 3

این فایل‌ها شامل `CATEGORY_ICONS_MAP` (۳۰ ورودی)، `formatToman`، `formatPersianDate` و همه‌ی composable‌های کارت/دیالوگ هستند که باید به `ui/components/` و `ui/util/` منتقل شوند. هیچ استفاده‌ای از `strings.xml` نمی‌شود — رشته‌های فارسی همه‌جا inline‌اند.

**اقدام:** تفکیک composable‌ها، استخراج formatters/icon map، و انتقال رشته‌های کاربر-رو به `res/values-fa/strings.xml`.

---

### ۴.۶. نرمال‌سازی ناهمگون ارقام فارسی
**فایل:** `api/GeminiParser.kt`, `api/PersianAmountParser.kt`
**فاز پیشنهادی:** Phase 2

`PersianAmountParser.normalizeText` و `GeminiParser.toArabicDigits` هر دو نرمال‌سازی می‌کنند، اما `parseJsonResult`, `parseSentenceOffline`, `extractJalaliDaysFromNow`, و `inferExpenseCategory` روی متن **خام** (نارمال‌نشده) کار می‌کنند که پارس را شکننده می‌کند.

**اقدام:** نرمال‌سازی یک‌بار در ورودی، نه به‌صورت موردی در هر تابع.

---

## ۵. یافته‌های با اولویت پایین (LOW)

| # | مسئله | فایل | فاز |
|---|-------|------|-----|
| ۵.۱ | مقادیر جادویی `categoryId = ... ?: 1L` (دو بار) و رنگ‌های کپی‌شده بین `MIGRATION_2_3` و `Category.DEFAULTS` (دو منبع حقیقت) | `HesabyarRepository.kt:93,102,121,124` | P1 |
| ۵.۲ | Worker‌های یادآوری همیشه `Result.success()` برمی‌گردانند حتی در صورت خطا — بدون `Result.retry()`/backoff | `reminder/InstallmentReminderWorker.kt` | P1 |
| ۵.۳ | PII (توضیحات کامل تراکنش و `personName`) بدون فیلتر به LLM می‌رود | `api/GeminiParser.kt` | P2 |
| ۵.۴ | retry/backoff روی خطاهای شبکه وجود ندارد؛ خطاها بی‌صدا به offline fallback می‌روند | `api/AiProvider.kt` | P2 |
| ۵.۵ | Vazirmatn فقط از Google Fonts downloadable لود می‌شود — بدون fallback `.ttf` باندل‌شده (خرابی روی دستگاه بدون Play Services یا آفلاین در اولین لانچ) | `ui/theme/Type.kt:12–19` | P3 |
| ۵.۶ | notification request code‌ها شکننده‌اند: `installmentId.toInt()` و `(loanId + 10000).toInt()` می‌توانند برای ID‌های بزرگ برخورد/overflow کنند | `reminder/NotificationHelper.kt:55,115,135` | P3 |
| ۵.۷ | `isMinifyEnabled = false` در release (ریسک امنیتی/حجم) | `app/build.gradle.kts:42` | P4 |
| ۵.۸ | جاوا ۱۱ فعال (`sourceCompatibility = VERSION_11`) — نسخه‌های جدید کتابخانه معمولاً ۱۷ را هدف می‌گیرند | `app/build.gradle.kts:50–53` | P0/P1 |
| ۵.۹ | default category‌ها stringly-typed هستند (`type` بدون enum یا CHECK constraint) | `data/Entities.kt` | P1 |
| ۵.۱۰ | initializerهای `System.currentTimeMillis()` روی Entity‌ها، آن‌ها را non-deterministic می‌کند | `data/Entities.kt:43,56,76` | P1 |

---

## ۶. کیفیت تست

**موجود:** ۱۴ فایل تست در `app/src/test/`:
```
AmountQuickFillTest, OfflineParserTest, BudgetAdvisorTest, JalaliCalendarTest,
AiConfigTest, RepositoryLogicTest, ExcelExporterTest, BackupValidationTest,
AiCacheTest, AnalyticsTest, ReminderTest, CategoryTest, TransactionTest,
LoanInstallmentTest
```

| نکته | وضعیت |
|------|-------|
| پوشش منطق خالص | ✅ خوب (Jalali, parser, repository logic, budget advisor, backup) |
| تست ViewModel | ❌ отсутствует (به‌خاطر نبود DI — §3.3) |
| تست UI (Compose) | ❌ отсутствует (androidTest خالی است) |
| Coverage report | ❌ отсутствует |
| Instrumentation test | ❌ `app/src/androidTest/` خالی |

**هدف Phase 4:** coverage > ۸۰٪، افزودن تست ViewModel (پس از Hilt) و تست Compose UI.

---

## ۷. نقشه راه اصلاح به فازها

| فاز | موارد مرتبط از این بازبینی |
|-----|----------------------------|
| **Phase 0** | (این سند) + `.editorconfig`, ktlint, CI/CD |
| **Phase 1** | §2.1 اتمیک بودن، §3.1 FK/Index، §3.3 DI، §4.1 exportSchema، §4.2 ماهانه، §4.4 ثابت‌ها، §4.5 تفکیک UI، §5.1, §5.2, §5.8, §5.9, §5.10 |
| **Phase 2** | §2.2 دقت پارسر، §3.2 اعتبارسنجی AI، §4.3 abstraction، §4.6 نرمال‌سازی، §5.3, §5.4 |
| **Phase 3** | §4.5 strings.xml، §5.5 فونت، §5.6 notification code |
| **Phase 4** | §5.7 minify، coverage تست، انتشار |

---

## ۸. نقاط قوت کد (حفظ شوند)

- ✅ **مدل پول یکپارچه** — `Long` (ریال) در همه‌ی لایه‌ها؛ مهاجرت تاریخی هم اشتباه `Double` قدیمی را اصلاح کرده.
- ✅ **concurrency ساختاریافته** — بدون `GlobalScope`، `viewModelScope`/`CoroutineWorker`/`unique work` به‌درستی استفاده شده.
- ✅ **امنیت کلیدها** — `EncryptedSharedPreferences`، فقط طول کلید لاگ می‌شود، sentinel guard موجود.
- ✅ **مهاجرت غیرمخرب** — مهاجرت صریح، بدون `fallbackToDestructiveMigration` (درست برای اپ مالی).
- ✅ **degradation درست** — هر caller خطای AI را به fallback آفلاین هدایت می‌کند.
- ✅ **مستندسازی غنی** — ۹ فایل `docs/` + README + AGENTS.md.
- ✅ **RTL و تقویم جلالی** — RTL app-wide، Vazirmatn، استفاده از helper جلالی در analytics.

---

*این سند مرجع زنده‌ای است و در پایان هر فاز باید به‌روزرسانی شود.*

---

## ۹. معیارهای معماری Rust-First

**سیاست منطق تجاری:** هسته Rust (`rust/hesabyar-core`) تنها مکان پیاده‌سازی منطق تجاری، محاسبات، قوانین، اعتبارسنجی، و تبدیلات داده‌های جدید است. هر درخواست Pull که شامل منطق تجاری، محاسبه، یا اعتبارسنجی جدید مستقیماً در Kotlin باشد باید در بازبینی علامت زده شود و به سمت Rust هدایت شود. لیست پنج استثنای دائمی (تقویم جلالی، قالب‌بندی ارز، پارسر NLP آفلاین، تحلیل/اعتبارسنجی JSON بکاپ، اعتبارسنجی مشاوره AI) در [سیاست منطق تجاری (Rust-first)](architecture/ADR-001-rust-sole-implementation.md) مرجع شود. این استثناها دائمی هستند و به عنوان fallbackهای کاتلینی حفظ می‌شوند؛ در غیر این صورت، این پیاده‌سازی‌ها باید از بین بروند. برنامه حذف تدریجی fallbackهای غیر-استثنا فقط در `../plans/2026-08-19-rust-fallback-consolidation-plan.md` مرجع شود؛ fallbackهای استثنای دائمی تحت این برنامه قرار نمی‌گیرند.
