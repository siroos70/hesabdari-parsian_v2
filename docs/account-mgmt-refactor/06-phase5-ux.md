# Phase 5: بهبودهای UX

## پیش‌نیاز

- **فاز قبلی:** Phase 4 باید کامل شده باشه (Screen بازنویسی شده و state از ViewModel میاد)
- **تصمیمات معلق:** ندارد

## زمینه

بعد از Phase 4، ساختار معماری کامله. این فاز فقط بهبودهای UX اضافه می‌کنه: بازیابی حساب آرشیوشده، undo برای حذف/آرشیو، و بهبود empty state. هیچ‌کدام تغییر معماری نمی‌خوان — فقط feature additions.

## هدف دقیق این فاز

اضافه کردن ۵ قابلیت UX:
1. **Unarchive** — بازیابی حساب آرشیوشده
2. **Undo Delete** — واگردانی حذف از Snackbar
3. **Undo Archive** — واگردانی آرشیو از Snackbar
4. **بهبود Empty State** — توضیح + CTA
5. **Progressive Disclosure** — فیلدهای بانکی فقط برای نوع بانکی

## فایل‌های درگیر

### فایل‌های جدید
| فایل | توضیح |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/account/UnarchiveAccountUseCase.kt` | بازیابی حساب |

### فایل‌های ویرایشی
| فایل | تغییر |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountEvent.kt` | اضافه کردن OnUnarchiveAccount |
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountViewModel.kt` | پردازش undo + unarchive |
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountUiState.kt` | اضافه کردن lastDeletedAccount برای undo |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt` | empty state بهبودیافته + Snackbar undo |
| `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountOverflowMenu.kt` | گزینه "فعال‌سازی مجدد" برای حساب آرشیوشده |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountListCard.kt` | StatusBadge برای حساب آرشیوشده |
| `app/src/main/java/io/github/mojri/hesabyar/ui/components/account/AccountBankFields.kt` | conditional rendering |

## گام‌های اجرا

### گام ۵.۱: Unarchive

- `UnarchiveAccountUseCase`: `suspend fun invoke(account: AccountEntity)` → `account.copy(isArchived = false, updatedAt = now)` → update
- `AccountEvent.OnUnarchiveAccount(account)`
- در `AccountOverflowMenu`: اگر حساب آرشیوشده باشه، گزینه "فعال‌سازی مجدد" نشون داده بشه (به‌جای "آرشیو")
- اضافه کردن `AccountStatusBadge` به `AccountListCard` — فقط اگر `isArchived` true باشه

### گام ۵.۲: Undo Delete

- در `AccountUiState`: اضافه کردن `lastDeletedAccount: AccountEntity? = null`
- بعد از حذف: `lastDeletedAccount = account` + snackbar message = "حساب «{name}» حذف شد" + actionLabel = "واگردانی"
- در Screen: اگر snackbar action "واگردانی" کلیک بشه → `OnUndoDelete` event
- `OnUndoDelete`: `addAccountUseCase(lastDeletedAccount!!)` → `lastDeletedAccount = null`
- **نکته:** برای undo، حساب قبل از حذف کامل باید cache بشه. `lastDeletedAccount` این کار رو می‌کنه.
- **محدودیت:** فقط آخرین حذف قابل undo هست (۵ ثانیه window)

### گام ۵.۳: Undo Archive

- مشابه Undo Delete
- در `AccountUiState`: `lastArchivedAccount: AccountEntity? = null`
- بعد از آرشیو: snackbar + actionLabel = "واگردانی"
- `OnUndoArchive`: `unarchiveAccountUseCase(lastArchivedAccount!!)`

### گام ۵.۴: بهبود Empty State

- استفاده از `EmptyState` component (از `ui/components/EmptyState.kt` — قبلاً وجود داره)
- محتوا:
  - Icon: `Icons.Filled.AccountBalance`
  - Title: "حسابی ثبت نشده است"
  - Description: "حساب‌ها به شما کمک می‌کنند تراکنش‌ها را دسته‌بندی کنید و موجودی هر حساب را جداگانه مدیریت کنید."
  - Action: "ایجاد حساب" → `onEvent(OnAddAccount)`

### گام ۵.۵: Progressive Disclosure فیلدهای بانکی

- در `AccountFormContent`: فیلدهای `AccountBankFields` فقط زمانی نمایش داده بشن که `formState.type == AccountType.BANK`
- برای سایر انواع (CASH_WALLET, SAVINGS_INVESTMENT, OTHER): فیلدهای بانکی مخفی باشن
- با `AnimatedVisibility` یا `if` ساده

### گام ۵.۶: StatusBadge component

- فایل جدید `ui/components/account/AccountStatusBadge.kt`
- نمایش "آرشیو" badge روی حساب‌های آرشیوشده
- فقط در management list نمایش داده بشه (نه dashboard)
- رنگ: `MaterialTheme.colorScheme.surfaceVariant` با `onSurfaceVariant` text

## نکات خاص این فاز

- از چک‌لیست مرکزی:
  - **R2** (اعداد منفی): اگر "موجودی محاسبه‌شده" جدیدی اضافه بشه (مثلاً در `AccountListCard`)، از الگوی LRM و sign/amount separation استفاده بشه
  - **R1** (تطابق Rust/Kotlin): Unarchive حساب باید dashboard sync رو فعال کنه — تأیید کنید که Room Flow بعد از update خودکار emit می‌کنه
- **مهم:** `lastDeletedAccount` و `lastArchivedAccount` باید بعد از Snackbar dismiss پاک بشن
- Undo window: ۵ ثانیه (duration Snackbar)
- اگر کاربر قبل از dismiss Snackbar اپ رو ببنده، undo از بین می‌ره — این قابل قبوله

## معیار پذیرش

- [ ] `UnarchiveAccountUseCase` وجود داره و تست شده
- [ ] `AccountOverflowMenu` گزینه "فعال‌سازی مجدد" برای حساب آرشیوشده داره
- [ ] بعد از حذف، Snackbar با "واگردانی" نمایش داده می‌شه
- [ ] بعد از آرشیو، Snackbar با "واگردانی" نمایش داده می‌شه
- [ ] کلیک "واگردانی" حساب رو برمی‌گردونه
- [ ] Empty state شامل title + description + CTA button هست
- [ ] فیلدهای بانکی فقط برای `AccountType.BANK` نمایش داده می‌شن
- [ ] `AccountStatusBadge` روی حساب‌های آرشیوشده نمایش داده می‌شه
- [ ] `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → بدون خطا
- [ ] Manual QA: آرشیو → بازیابی، حذف → undo، empty state جدید

## Rollback

```bash
git log --oneline -5
git revert <phase-5-commits>
# یا حذف فایل‌های جدید و revert فایل‌های ویرایشی
```
