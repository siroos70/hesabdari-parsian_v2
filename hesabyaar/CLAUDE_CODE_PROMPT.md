# اجرا با Claude Code روی لپ‌تاپ (Android SDK لازم است)

در پوشه‌ی همین پروژه `claude` را اجرا کن و این متن را بده:

```
این پروژه (حسابیار، Kotlin + Rust) باید به APK ریلیز امضاشده‌ی «HesabYaar-Iran-v1.0.0-release.apk» تبدیل شود.
1) JDK 17، Android SDK، NDK 27.0.12077973، rustup + targetهای اندروید + cargo-ndk 3.5.2 را اگر نیست نصب کن.
2) ./gradlew :app:generateAndFixBindings && ./gradlew :app:compileRustCore
3) ./gradlew testDebugUnitTest  (خطاها را خودت اصلاح کن، از حذف تست یا قابلیت خودداری کن)
4) با KEYSTORE_PASSWORD / KEY_PASSWORD / KEY_ALIAS در محیط: ./gradlew generateKeystore
5) ./gradlew assembleRelease -PenableAbiSplits=false
6) apksigner verify و aapt2 dump badging روی APK؛ اگر emulator یا گوشی هست نصب و اجرا کن.
7) APK را با نام HesabYaar-Iran-v1.0.0-release.apk در ریشه‌ی پروژه بگذار.
تا موفق نشدن Build متوقف نشو؛ هر خطا را علت‌یابی و رفع کن.
```
