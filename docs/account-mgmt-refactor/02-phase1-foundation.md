# Phase 1: استخراج Domain/Data Layer

## پیش‌نیاز

- **فاز قبلی:** Phase 0 باید کامل شده باشه (۴ باگ فیکس شده)
- **تصمیمات معلق:**
  - تصمیم #1 (یکتایی نام) — باید قبل از نوشتن `AccountValidator` روشن بشه
  - تصمیم #2 (FK Constraint) — باید قبل از نوشتن Room migration (اگر انتخاب بشه) روشن بشه
  - تصمیم #4 (رنگ) — باید قبلاً در Phase 0 تأیید شده باشه

## زمینه

فایل‌های فعلی `Daos.kt`، `HesabyarRepository.kt`، و `HesabyarRepositoryInterface.kt` شامل تمام DAOها و Repositoryهای app هستن. `AccountViewModel` منطق Business رو مستقیماً اجرا می‌کنه بدون UseCase واسط. این فاز لایه Domain رو معرفی می‌کنه و Account CRUD رو از بقیه جدا می‌کنه.

## هدف دقیق این فاز

ایجاد لایه Domain و Data مستقل برای حساب‌ها: `AccountDao` جداگانه، `AccountRepository` interface + impl، ۵ UseCase، و `AccountValidator`. در پایان این فاز، `AccountViewModel` باید از UseCaseها استفاده کنه (نه مستقیماً از Repository).

## فایل‌های درگیر

### فایل‌های جدید (ساخته بشن)
| فایل | توضیح |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/data/account/AccountDao.kt` | استخراج از `Daos.kt` |
| `app/src/main/java/io/github/mojri/hesabyar/data/account/AccountRepository.kt` | interface |
| `app/src/main/java/io/github/mojri/hesabyar/data/account/AccountRepositoryImpl.kt` | پیاده‌سازی |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/account/AddAccountUseCase.kt` | افزودن حساب |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/account/UpdateAccountUseCase.kt` | ویرایش حساب |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/account/DeleteAccountUseCase.kt` | حذف حساب |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/account/ArchiveAccountUseCase.kt` | آرشیو |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/account/GetAccountsUseCase.kt` | لیست حساب‌ها |
| `app/src/main/java/io/github/mojri/hesabyar/domain/validation/AccountValidator.kt` | قوانین اعتبارسنجی |
| `app/src/main/java/io/github/mojri/hesabyar/domain/validation/ValidationResult.kt` | نوع نتیجه اعتبارسنجی |

### فایل‌های ویرایشی
| فایل | تغییر |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/data/Daos.kt` | حذف `AccountDao` (انتقال به فایل جداگانه) |
| `app/src/main/java/io/github/mojri/hesabyar/data/HesabyarRepository.kt` | حذف Account CRUD methods (انتقال به `AccountRepositoryImpl`) |
| `app/src/main/java/io/github/mojri/hesabyar/data/HesabyarRepositoryInterface.kt` | نگه‌داشتن Account-related methods (deprecated تا همه consumers مهاجرت کنن) — دیده‌شده در نکات خاص (خط ۱۵۳) |
| `app/src/main/java/io/github/mojri/hesabyar/di/RepositoryModule.kt` | اضافه کردن `AccountRepository` binding |
| `app/src/main/java/io/github/mojri/hesabyar/di/DatabaseModule.kt` | اضافه کردن `AccountDao` provision |
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountViewModel.kt` | تغییر از Repository به UseCaseها |

### فایل‌های تست جدید
| فایل | توضیح |
|---|---|
| `app/src/test/java/io/github/mojri/hesabyar/domain/validation/AccountValidatorTest.kt` | تست validator |
| `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/account/AddAccountUseCaseTest.kt` | تست UseCase |
| `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/account/DeleteAccountUseCaseTest.kt` | تست UseCase |

## گام‌های اجرا

### گام ۱.۱: استخراج AccountDao

- از `Daos.kt` (خطوط ۱۸۶-۲۱۷) interface `AccountDao` رو به فایل جدید `data/account/AccountDao.kt` منتقل کنید
- اطمینان حاصل کنید که Room annotationها (`@Dao`, `@Query`, `@Insert`, `@Update`, `@Delete`) حفظ شدن
- در `Daos.kt` اصلی، `AccountDao` رو حذف کنید
- در `DatabaseModule.kt` تأیید کنید که `database.accountDao()` هنوز کار می‌کنه (Room DAOs از interfaces ساخته می‌شن)
- **Rollback:** `git checkout HEAD -- app/src/main/java/.../data/Daos.kt`

### گام ۱.۲: ایجاد AccountRepository Interface

- فایل جدید `data/account/AccountRepository.kt` بسازید
- شامل methods:
  ```kotlin
  interface AccountRepository {
    val allAccounts: Flow<List<AccountEntity>>
    suspend fun getActiveAccounts(): List<AccountEntity>
    suspend fun getAllAccounts(): List<AccountEntity>
    suspend fun getAccountById(id: Long): AccountEntity?
    suspend fun insertAccount(account: AccountEntity): Long
    suspend fun updateAccount(account: AccountEntity)
    suspend fun deleteAccount(account: AccountEntity)
    suspend fun getTransactionCountForAccount(accountId: Long): Int
  }
  ```
- **Rollback:** حذف فایل

### گام ۱.۳: پیاده‌سازی AccountRepositoryImpl

- فایل جدید `data/account/AccountRepositoryImpl.kt` بسازید
- `@Inject constructor(private val accountDao: AccountDao)`
- تمام methods رو از `HesabyarRepository.kt` (خطوط ۱۹۶-۲۰۶) منتقل کنید
- در `RepositoryModule.kt`:
  - `AccountRepository` رو bind کنید: `@Binds abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository`
  - یا `@Provides` با `AccountDao` parameter
- **Rollback:** حذف فایل + revert `RepositoryModule.kt`

### گام ۱.۴: ایجاد AccountValidator

- فایل `domain/validation/ValidationResult.kt`:
  ```kotlin
  sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val errors: Map<String, String>) : ValidationResult
  }
  ```
- فایل `domain/validation/AccountValidator.kt`:
  - `fun validate(form: AccountFormState): ValidationResult`
  - قوانین (طبق تصمیم #1):
    - `name`: خالی نباشه، حداکثر ۱۰۰ کاراکتر
    - `type`: معتبر باشه
    - `cardNumber`: اگر پر بود، ۱۶ رقم باشه
    - `iban`: اگر پر بود، regex `^IR\d{24}$`
    - `initialBalance`: `toLongOrNull()` موفق باشه
  - **اگر تصمیم #1 = "سخت‌گیر":** `name` unique check با `getAllAccounts()` اضافه بشه
- **Rollback:** حذف فایل‌ها

### گام ۱.۵: ایجاد UseCaseها

هر UseCase یک فایل جداگانه:

**AddAccountUseCase:**
- `suspend operator fun invoke(form: AccountFormModel): Long`
- Validation → insert → return ID

**UpdateAccountUseCase:**
- `suspend operator fun invoke(account: AccountEntity)`
- `account.copy(updatedAt = System.currentTimeMillis())` → update

**DeleteAccountUseCase:**
- `suspend operator fun invoke(account: AccountEntity)`
- `getTransactionCountForAccount(account.id)` → if 0, delete; else throw exception

**ArchiveAccountUseCase:**
- `suspend operator fun invoke(account: AccountEntity)`
- `account.copy(isArchived = true, updatedAt = System.currentTimeMillis())` → update

**GetAccountsUseCase:**
- `val allAccounts: Flow<List<AccountEntity>>` → repository.allAccounts
- `suspend fun getActiveAccounts(): List<AccountEntity>` → repository.getActiveAccounts()

### گام ۱.۶: به‌روزرسانی AccountViewModel

- `AccountViewModel` رو تغییر بدید تا به‌جای `HesabyarRepositoryInterface` از UseCaseها استفاده کنه
- constructor: `@Inject constructor(private val addAccount: AddAccountUseCase, private val updateAccount: UpdateAccountUseCase, ...)`
- `addAccount()` method: `addAccount(form)` فراخوانی بشه
- `canDeleteAccount()` method: `deleteAccountUseCase.canDelete(accountId)` (یا method جداگانه)
- **مهم:** رفتار بیرونی نباید تغییر کنه — فقط dependency internal عوض شده

### گام ۱.۷: نوشتن تست‌ها

- `AccountValidatorTest`: تست تمام قوانین validation (valid, empty name, duplicate name, invalid IBAN, invalid card number)
- `AddAccountUseCaseTest`: تست با FakeRepository — insert موفق، validation خطا
- `DeleteAccountUseCaseTest`: تست حذف موفق، حذف با تراکنش (باید خطا بده)

## نکات خاص این فاز

- از چک‌لیست مرکزی:
  - **R1** (تطابق Rust/Kotlin): این فاز تغییری در محاسبات ایجاد نمی‌کنه — فقط ساختار عوض می‌شه
  - **R5** (JNI state): اگر هیچ کد Rust لمس نشه، R5 نیاز به اجرا نداره
- **مهم:** `HesabyarRepositoryInterface` باید هنوز Account methods رو داشته باشه چون بقیه ViewModels (Dashboard, Analytics, Backup) هنوز از اون استفاده می‌کنن. فقط `AccountViewModel` به UseCaseها switch می‌شه.
- اگر `HesabyarRepository` interface از account methods خالی بشه، باید تأیید کنید که هیچ caller دیگه‌ای نداره (grep!)

## معیار پذیرش

- [ ] `AccountDao.kt` جداگانه وجود داره و `Daos.kt` دیگه `AccountDao` نداره
- [ ] `AccountRepository` interface و `AccountRepositoryImpl` وجود دارن
- [ ] `AccountValidator` تمام قوانین validation رو پوشش می‌ده
- [ ] `AccountValidatorTest` → همه تستها pass
- [ ] `AddAccountUseCaseTest` → همه تستها pass
- [ ] `DeleteAccountUseCaseTest` → همه تستها pass
- [ ] `AccountViewModel` از UseCaseها استفاده می‌کنه (نه مستقیماً از Repository)
- [ ] `AccountViewModel` methods رفتار قبلی رو حفظ کردن (signature یکسان)
- [ ] DI modules به‌روزرسانی شدن و `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → بدون خطا
- [ ] با grep تأیید بشه: `AccountViewModel` دیگه `repository.insertAccount` مستقیم نداره

## Rollback

```bash
# rollback کل Phase 1:
git log --oneline -10  # Phase 1 commits رو شناسایی کنید
git revert <last-phase-1-commit>..<first-phase-1-commit>

# یا rollback یک گام خاص:
git checkout HEAD -- app/src/main/java/.../data/Daos.kt  # rollback گام ۱.۱
```
