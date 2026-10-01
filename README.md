# حسابیار (Hesabyar)

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Platform: Android">
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Language: Kotlin">
  <img src="https://img.shields.io/badge/Rust-E74C3C?logo=rust&logoColor=white" alt="Language: Rust">
  <img src="https://img.shields.io/badge/Calendar-Jalali-4CAF50" alt="Calendar: Jalali">
  <img src="https://img.shields.io/badge/License-MIT-blue" alt="License: MIT">
</p>

<p align="center">
  <a href="https://app.codspeed.io/mcuteangel/hesabyaar?utm_source=badge"><img src="https://img.shields.io/endpoint?url=https://codspeed.io/badge.json" alt="CodSpeed" /></a>
  <a href="https://codecov.io/gh/mcuteangel/hesabyaar"><img src="https://codecov.io/gh/mcuteangel/hesabyaar/graph/badge.svg" alt="Codecov" /></a>
  <a href="https://coveralls.io/github/mcuteangel/hesabyaar?branch=main"><img src="https://coveralls.io/repos/github/mcuteangel/hesabyaar/badge.svg?branch=main" alt="Coverage Status" /></a>
  <a href="https://app.deepsource.com/gh/mcuteangel/hesabyaar/"><img src="https://app.deepsource.com/gh/mcuteangel/hesabyaar.svg/?label=code+coverage&show_trend=true&token=ILOs6PrgoKPamuLY2r208RwZ" alt="DeepSource Coverage" /></a>
  <a href="https://qlty.sh/gh/mcuteangel/projects/hesabyaar"><img src="https://qlty.sh/gh/mcuteangel/projects/hesabyaar/coverage.svg" alt="Qlty Coverage" /></a>
  <a href="https://app.codacy.com/gh/mcuteangel/hesabyaar/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_coverage"><img src="https://app.codacy.com/project/badge/Coverage/67b7ee17a65a4c1da1e268e8fa16df1f" alt="Codacy Coverage" /></a>
  <a href="https://qlty.sh/gh/mcuteangel/projects/hesabyaar"><img src="https://qlty.sh/gh/mcuteangel/projects/hesabyaar/maintainability.svg" alt="Qlty Maintainability" /></a>
  <a href="https://app.codacy.com/gh/mcuteangel/hesabyaar/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade"><img src="https://app.codacy.com/project/badge/Grade/67b7ee17a65a4c1da1e268e8fa16df1f" alt="Codacy Grade" /></a>
  <a href="https://deepscan.io/dashboard#view=project&tid=30741&pid=32476&bid=1076614"><img src="https://deepscan.io/api/teams/30741/projects/32476/branches/1076614/badge/grade.svg" alt="DeepScan grade" /></a>
  <a href="https://app.deepsource.com/gh/mcuteangel/hesabyaar/"><img src="https://app.deepsource.com/gh/mcuteangel/hesabyaar.svg/?label=active+issues&show_trend=true&token=ILOs6PrgoKPamuLY2r208RwZ" alt="DeepSource Active Issues" /></a>
  <a href="https://app.deepsource.com/gh/mcuteangel/hesabyaar/"><img src="https://app.deepsource.com/gh/mcuteangel/hesabyaar.svg/?label=resolved+issues&show_trend=true&token=ILOs6PrgoKPamuLY2r208RwZ" alt="DeepSource Resolved Issues" /></a>
  <a href="https://www.codefactor.io/repository/github/mcuteangel/hesabyaar"><img src="https://www.codefactor.io/repository/github/mcuteangel/hesabyaar/badge" alt="CodeFactor" /></a>
</p>

<p align="center">
  <b>اولین دستیار مالی شخصی با اولویت زبان فارسی برای اندروید</b>
</p>

<p align="center">
  مدیریت هوشمند امور مالی با رابط کاربری مدرن و امکانات پیشرفته
</p>

---

## 📋 مقدمه

**حسابیار** یک برنامه اندرویدی برای مدیریت امور مالی شخصی است که با تمرکز بر نیازهای کاربران فارسی‌زبان طراحی شده است. این برنامه با ارائه رابط کاربری ساده و در عین حال قدرتمند، به شما کمک می‌کند تا امور مالی خود را به صورت حرفه‌ای مدیریت کنید.

### هسته مشترک (Rust Core)

از نسخه 0.1.1 به بعد، منطق محاسبه‌ی مالی، پردازش زبان طبیعی فارسی، تبدیل تقویم جلالی/میلادی، فرمت‌سازی پول، و الگوریتم‌های تحلیلی به یک **هسته مشترک Rust** (`hesabyar-core`) استخراج شده‌اند. این لایه:
- کدهای داغ JVM (تجزیه مقدار، تبدیل تاریخ) را با **UniFFI** به اندروید (Kotlin) و دسکتاپ (Rust) منتقل می‌کند
- امنیت حافظه و عملکرد بالا بدون کد `unsafe`
- بنچمارک‌های Criterion برای رصد رگرسیون‌های عملکردی

## ✨ ویژگی‌های کلیدی

### 💰 مدیریت تراکنش‌ها
- ثبت درآمد و هزینه با دسته‌بندی‌های سفارشی
- پشتیبانی انحصاری از واحدهای تومان و ریال
- ثبت سریع تراکنش با استفاده از زبان طبیعی فارسی
- ویرایش و حذف آسان تراکنش‌ها

### 🏦 پیگیری وام و بدهی
- مدیریت کامل بدهی‌ها و اعتبارات با افراد
- ثبت وام‌های دریافتی و پرداختی
- یادآوری خودکار سررسید اقساط
- مشاهده تراز مالی با هر شخص

### 📅 مدیریت اقساط
- ثبت اقساط با تاریخ سررسید
- یادآوری خودکار قبل از سررسید
- مشاهده لیست کامل اقساط فعال
- علامت‌گذاری اقساط پرداخت شده

### 🤖 هوش مصنوعی
- **تجزیه و تحلیل هوشمند**: دریافت بینش‌های مالی و توصیه‌های هوشمند
- **ورودی زبان طبیعی**: ثبت تراکنش با استفاده از جملات فارسی طبیعی
- **مشاهده الگوهای مالی**: شناسایی عادات مالی و ارائه پیشنهادها بهبود

### 📊 داشبورد تحلیلی
- مشاهده هزینه‌ها و درآمدها به صورت ماهانه
- تجزیه و تحلیل بر اساس دسته‌بندی
- مشاهده خلاصه بدهی‌ها و اعتبارات
- نمودارهای گرافیکی برای درک بهتر وضعیت مالی

### 💾 پشتیبان‌گیری و بازیابی
- پشتیبان‌گیری کامل از داده‌ها در قالب JSON
- دو حالت بازیابی: جایگزینی کامل یا ادغام با داده‌های موجود
- ذخیره در حافظه داخلی یا فضای ابری

### 📤 صادرات به اکسل
- صادرات گزارش‌ها مالی به فرمت .xlsx
- سفارشی‌سازی گزارش‌ها صادر شده
- مناسب برای تحلیل‌های پیشرفته در اکسل

### 🌐 ویژگی‌های دیگر
- **آفلاین‌محور**: تمام ویژگی‌های اصلی بدون نیاز به اینترنت کار می‌کنند
- **تقویم جلالی**: پشتیبانی کامل از تقویم شمسی
- **رابط کاربری مدرن**: طراحی با Jetpack Compose و Material 3
- **امنیت**: حفاظت از داده‌ها با رمزنگاری

---

## 🛠 تکنولوژی‌های مورد استفاده

| دسته | تکنولوژی |
|------|------------|
| **زبان برنامه‌نویسی** | Kotlin, **Rust** |
| **رابط کاربری** | Jetpack Compose, Material 3 |
| **پایگاه داده** | Room Database (SQLite) |
| **ناوبری** | Navigation Compose |
| **همزمانی** | Kotlin Coroutines + Flow |
| **عملیات پس‌زمینه** | WorkManager |
| **شبکه** | OkHttp + Retrofit |
| **هوش مصنوعی** | Firebase AI, OpenRouter, Custom AI providers |
| **تست** | Robolectric, Roborazzi |
| **بنچمارک** | CodSpeed, JMH, **Criterion (Rust)** |
| **بایندینگ بومی (Native)** | **UniFFI** (Kotlin ↔ Rust) |
| **رمزنگاری** | AES-256-GCM (Rust `aes-gcm`) |

---

## 🏗 معماری هسته Rust (hesabyar-core)

```text
rust/
├── Cargo.toml                 # Workspace root
├── hesabyar-core/             # کریت اصلی منطق تجاری
│   ├── Cargo.toml
│   ├── build.rs               # کد تولید UniFFI scaffolding
│   ├── src/
│   │   ├── lib.rs             # Re-exportهای عمومی API
│   │   ├── calendar.rs        # تبدیل تقویم جلالی ↔ میلادی
│   │   ├── currency.rs        # فرمت‌سازی ریال/تومان
│   │   ├── analytics.rs       # آنالیتیکس تراکنش‌ها
│   │   ├── dashboard.rs       # محاسبه داده‌های داشبورد
│   │   ├── models/            # ساختارهای دامنه
│   │   ├── parser/            # ماژول پردازش زبان طبیعی فارسی
│   │   │   ├── mod.rs
│   │   │   ├── nlp.rs
│   │   │   ├── text_preprocessor.rs
│   │   │   ├── money_detector.rs
│   │   │   └── amount.rs
│   │   ├── advisory/          # مشاوره مالی آفلاین
│   │   │   ├── mod.rs
│   │   │   └── budget.rs
│   │   ├── ffi/               # لایه UniFFI (20+ تابع صادرشده)
│   │   │   └── mod.rs
│   │   ├── validation.rs      # اعتبارسنجی موجودیت‌ها
│   │   ├── search.rs          # جستجوی متن کامل
│   │   ├── crypto.rs          # رمزنگاری AES-256-GCM
│   │   ├── ai_validation.rs   # اعتبارسنجی خروجی AI
│   │   └── excel.rs           # تولید گزارش اکسل
│   ├── tests/
│   │   └── golden/            # تست‌های طلایی (JSON)
│   └── benches/
│       └── parser_bench.rs    # بنچمارک‌های Criterion
└── uniffi-gen/                # باینری تولید بایندینگ Kotlin
    ├── Cargo.toml
    └── src/main.rs
```

### ویژگی‌های معماری Rust

| ویژگی | توضیح |
|----------|---------|
| **نوع پول** | `i64` (ریال) — هم‌خوانا با `Long` در Room |
| **تاریخ** | `i64` (timestamp UTC) + توابع تبدیل جلالی |
| **بایندینگ** | UniFFI — تولید خودکار Kotlin (توسط `uniffi-gen`)، بدون JNI دستی |
| **امنیت حافظه** | کد `unsafe` نداریم، تضمین توسط سیستم نوع Rust |
| **سریال‌سازی** | Serde برای JSON بک‌آپ |
| **بنچمارک** | Criterion + flamegraph برای تحلیل عملکرد |

---

## 📦 ساختار پروژه

```text
app/src/main/java/io/github/mojri/hesabyar/
├── api/                    # ارائه‌دهندگان هوش مصنوعی، مفسرها، مشاور بودجه
│   ├── ai/                 # پیاده‌سازی‌های مختلف AI
│   ├── parser/             # مفسرهای ورودی زبان طبیعی
│   └── advisor/            # مشاور هوشمند مالی
│
├── data/                   # لایه داده
│   ├── db/                 # موجودیت‌ها، DAOها، پایگاه داده Room
│   ├── repository/         # مخازن داده
│   ├── backup/             # پشتیبان‌گیری و بازیابی
│   └── export/             # صادرات به اکسل
│
├── reminder/               # یادآوری‌ها و اعلان‌ها
│   ├── worker/             # کارگران WorkManager
│   └── notification/       # کمک‌کنندگان اعلان
│
└── ui/                     # لایه رابط کاربری
    ├── screens/            # صفحات Compose
    ├── viewmodel/          # ViewModelها
    ├── theme/              # تم و استایل‌ها
    └── components/         # کامپوننت‌های قابل استفاده مجدد
```

---

## 🚀 شروع به کار

### پیش‌نیازها
- Android Studio (آخرین نسخه)
- JDK 21+
- Git
- **Rust toolchain 1.75+** (برای هسته Rust)
- **Android NDK** (برای cross-compilation Rust)

### راه‌اندازی محیط

1. مخزن را کلون کنید:
   ```bash
   git clone https://github.com/mcuteangel/hesabyaar.git
   cd hesabyaar
   ```

2. زیرماژول‌های git را initialize کنید:
   ```bash
   git submodule update --init --recursive
   ```

3. فایل `.env` را از روی `.env.example` کپی کنید:
   ```bash
   cp .env.example .env
   ```

4. کلید API هوش مصنوعی را تنظیم کنید (اختیاری):
   ```env
   GEMINI_API_KEY=your_api_key_here
   ```

5. برای buildهای release، اطلاعات امضای دیجیتال را در `.env` تنظیم کنید.

6. **Rust toolchain و targets اندروید را نصب کنید:**
   ```bash
   rustup default stable
   rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
   cargo install cargo-ndk
   ```

---

## 🔨 ساخت و اجرا

### ساخت هسته Rust (الزامی برای اولین بار و پس از تغییرات Rust)

```bash
# Build همه ABIهای اندروید
./gradlew :app:assembleRust --no-daemon

# یا مستقیم با cargo
cd rust && cargo build --target aarch64-linux-android --release
```

### تولید بایندینگ‌های Kotlin (UniFFI)

```bash
./gradlew :app:generateRustBindings --no-daemon
```

### ساخت نسخه Debug
```bash
./gradlew installDebug
```

### اجرا بر روی دستگاه
```bash
./gradlew installDebug
```

### ساخت APK
```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

---

## 🧪 تست

### اجرای تمام تست‌ها
```bash
./gradlew test --no-daemon
```

### اجرای تست یک کلاس خاص
```bash
./gradlew test --tests "io.github.mojri.hesabyar.OfflineParserTest" --no-daemon
```

### تست‌های Rust Core
```bash
cd rust/hesabyar-core
cargo test
cargo test -- --nocapture
```

### بنچمارک‌های Rust
```bash
cd rust/hesabyar-core
cargo bench
```

### آنالیز کد
```bash
./gradlew lint --no-daemon
./gradlew ktlintCheck detekt --no-daemon
```

### 📊 پوشش کد (Code Coverage)

وضعیت و ساختار پوشش آزمون‌ها به‌صورت زنده توسط **Codecov** تحلیل می‌شود.

<p align="center">
  <a href="https://codecov.io/gh/mcuteangel/hesabyaar">
    <img src="https://codecov.io/gh/mcuteangel/hesabyaar/graphs/sunburst.svg" alt="Codecov Sunburst Graph" width="280" />
  </a>
</p>

---

## 📈 بنچمارک

پروژه از بنچمارک‌های عملکردی با استفاده از **CodSpeed، JMH (JVM)** و **Criterion (Rust)** استفاده می‌کند. بنچمارک‌های JVM در build مستقل `benchmarks/` و بنچمارک‌های Rust در `rust/hesabyar-core/benches/` قرار دارند.

### اجرای بنچمارک‌های JVM
```bash
cd benchmarks
./gradlew jmh --no-daemon
```

### اجرای بنچمارک‌های Rust
```bash
cd rust/hesabyar-core
cargo bench
```

بنچمارک‌ها به صورت خودکار در CI بر روی هر Pull Request اجرا می‌شوند.

---

## 📝 مستندات

- [معماری پروژه](docs/architecture/ARCHITECTURE.md) - جزئیات معماری و پیاده‌سازی
- [سیاست منطق تجاری (Rust-first)](docs/architecture/ADR-001-rust-sole-implementation.md) - تصمیم معماری: منطق تجاری فقط در Rust
- [راهنمای ساخت و انتشار](docs/BUILD_RELEASE.md) - راهنمای توسعه‌دهندگان
- [CHANGELOG](CHANGELOG.md) - تاریخچه تغییرات
- [AGENTS](AGENTS.md) - راهنمای استفاده از عامل‌های هوش مصنوعی

---

## 🤝 مشارکت

ما از مشارکت‌های جامعه استقبال می‌کنیم! برای مشارکت:

1. مخزن را Fork کنید
2. یک شاخه جدید ایجاد کنید (`git checkout -b feature/amazing-feature`)
3. تغییرات خود را Commit کنید (`git commit -m 'feat: add amazing feature'`)
4. به مخزن اصلی Push کنید (`git push origin feature/amazing-feature`)
5. یک Pull Request ایجاد کنید

### راهنمایی‌های مشارکت
- از پیغام‌های Commit معنی‌دار استفاده کنید
- کد خود را تست کنید
- از استانداردهای کدگذاری پروژه پیروی کنید
- مستندات را به‌روز نگه دارید

### 📐 راهنمای معماری

برای جزئیات کامل دربارهٔ سیاست Rust-first و لیست استثناهای Kotlin، به [`سیاست منطق تجاری (Rust-first)`](docs/architecture/ADR-001-rust-sole-implementation.md) مراجعه کنید.

---

## 📄 مجوز

این پروژه تحت مجوز **MIT License** منتشر شده است. متن کامل مجوز در فایل [LICENSE](LICENSE) قرار دارد.

---

## 🙏 تشکر و قدردانی

- از تمام کاربران و مشارکت‌کنندگان برای حمایتشان سپاسگزاریم
- از جامعه اندرویدی ایران برای بازخوردهای ارزشمند
- از توسعه‌دهندگان کتابخانه‌های اوپن سورس که این پروژه را ممکن ساخته‌اند

---

## 📞 تماس

برای سؤال‌ها، پیشنهادها یا گزارش مشکلات، لطفاً از طریق [GitHub Issues](https://github.com/mcuteangel/hesabyaar/issues) اقدام کنید.

---

<p align="center">
  ساخته شده با ❤️ برای جامعه فارسی‌زبان
</p>
