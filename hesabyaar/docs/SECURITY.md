# Security Improvements

## API Key Storage

### Before

- API keys stored in plain `SharedPreferences`
- readable by any app with root access

### After

- API keys stored in `EncryptedSharedPreferences`
- AES-256-GCM encryption for values
- AES-256-SIV for key encryption
- Automatic migration from legacy plain prefs

**Implementation:** `AiProviderConfig.kt` → `AiConfigManager`

---

## Build Secrets

- `.env` file for build-time secrets (API keys, keystore passwords)
- `.env` is git-ignored — never committed to repository
- `.env.example` provides placeholder template
- Secrets Gradle Plugin maps `.env` → `BuildConfig` fields
- Keystore file (`my-upload-key.jks`) is git-ignored

---

## Data Storage

### Room Database

- SQLite database stored in app-private directory
- Not encrypted (future enhancement: SQLCipher)
- Access restricted to app process only

### Backups

- JSON format, plain text
- Stored in user-selected location via SAF
- Future enhancement: encryption with user password

---

## Network Security

- All API calls use HTTPS
- OkHttp configured with 30s connect / 60s read timeouts
- No certificate pinning (future enhancement)
- No sensitive data in URL parameters (API key in header for OpenRouter/Custom)

---

## What's NOT Encrypted

1. **Room database** — SQLite file on disk (accessible with root)
2. **Backup files** — Plain JSON (user responsibility)
3. **SharedPreferences** — App settings, reminder config (non-sensitive)

---

## Future Security Enhancements

- [ ] SQLCipher for database encryption
- [ ] Encrypted backup format
- [ ] Certificate pinning
- [ ] Biometric authentication
- [ ] ProGuard/R8 minification and obfuscation
- [ ] Root detection
