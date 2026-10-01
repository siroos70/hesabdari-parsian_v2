# Build & Release Instructions

## Prerequisites

- Android Studio Hedgehog (2023.1) or later
- JDK 11+
- Android SDK 36
- Gradle 8.x

---

## Environment Setup

1. Copy `.env.example` to `.env`
2. Edit `.env` with your values:

```bash
# Required for AI features (optional for core app)
GEMINI_API_KEY=your_api_key_here

# Required for release builds only
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

---

## Debug Build

```bash
# Install debug APK on connected device
./gradlew installDebug

# Build debug APK without installing
./gradlew assembleDebug
```

Debug builds:
- Use debug signing config
- Include debuggable flag
- Use `.env` for GEMINI_API_KEY only

---

## Release Build

### First-time Setup

```bash
# Generate release keystore (requires KEYSTORE_PASSWORD and KEY_PASSWORD in .env)
./gradlew generateKeystore

# Verify signing configuration
./gradlew checkSigningConfig
```

### Build Release APK

```bash
# Per-ABI APKs (arm-v7a, arm-v8a, x86_64) + a universal APK for direct distribution
./gradlew assembleRelease -PenableAbiSplits=true

# Universal AAB (no ABI split — required, see note below)
./gradlew bundleRelease -PenableAbiSplits=false
```

> **Why two separate commands with different flags?**
> The AAB must be built **without** ABI splits. When `splits.abi` is enabled,
> AGP emits one shrunk-resources file per ABI and `buildReleasePreBundle` fails
> with "Multiple shrunk-resources files found" (issuetracker.google.com/402800800).
> The AAB already carries every ABI, so Play splits it on delivery. The APKs,
> by contrast, are shipped directly (e.g. GitHub release), so per-ABI splits let
> users grab the smallest build for their device.
>
> The `enableAbiSplits` property controls this:
> - `true`  → per-ABI APKs + universal APK.
> - `false` → single universal build (used for the AAB).
> - unset   → defaults to NDK presence, but **any bundle task** (e.g.
>   `bundleRelease`) forces splits OFF regardless of the NDK. A blank value
>   (e.g. `-PenableAbiSplits=`) is ignored and falls back to the default.

Release builds:
- Signed with release keystore
- Minified (when enabled)
- Not debuggable
- Requires all signing credentials in `.env`

---

## Testing

```bash
# Run all unit tests
./gradlew test

# Run single test class
./gradlew test --tests "io.github.mojri.hesabyar.OfflineParserTest"

# Run lint checks
./gradlew lint
```

> **⚠️ Important:** The Rust native library uses global mutable JNI state. To get
> reliable test results (especially before merging Rust-related changes), always use
> one of these cache-busting approaches instead of plain `./gradlew test`:
>
> ```bash
> # Option A: clean build (recommended before merge)
> ./gradlew clean test --no-daemon
>
> # Option B: rerun all tasks without cleaning
> ./gradlew test --rerun-tasks --no-daemon
> ```
>
> A plain `./gradlew test` may report false passes from Gradle's incremental cache.

---

## Build Variants

| Variant    | Signing       | Debuggable | Minified |
|------------|---------------|------------|----------|
| debug      | Debug key     | Yes        | No       |
| release    | Release key   | No         | No*      |

*Minification can be enabled by setting `isMinifyEnabled = true` in `build.gradle.kts`.

---

## Secrets Management

- `.env` file is git-ignored (never committed)
- `.env.example` contains placeholder values
- Secrets Gradle Plugin maps `.env` → `BuildConfig` fields
- Keystore file (`my-upload-key.jks`) is git-ignored

---

## Troubleshooting

### Kotlin Daemon Issues

If Kotlin daemon fails to connect:
```bash
./gradlew --stop
./gradlew assembleDebug --rerun-tasks
```

### Missing .env

Build will use `.env.example` defaults. AI features will be unavailable.

### Keystore Not Found

Run `./gradlew generateKeystore` first. Requires `KEYSTORE_PASSWORD` and `KEY_PASSWORD` in `.env`.
