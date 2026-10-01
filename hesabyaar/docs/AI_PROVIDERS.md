# AI Providers

## Overview

Hesabyar supports multiple AI providers for natural language parsing and budget advice. AI is optional — all core features work offline.

---

## Supported Providers

### Google Gemini

- **Type:** `GEMINI`
- **API:** Google Generative Language API
- **Auth:** API key (Gemini API key)
- **Default model:** `gemini-2.0-flash`

### OpenRouter

- **Type:** `OPENROUTER`
- **API:** OpenAI-compatible chat completions
- **Auth:** Bearer token
- **Models:** Any model available on OpenRouter

### Custom Endpoint

- **Type:** `CUSTOM`
- **API:** OpenAI-compatible chat completions
- **Auth:** Bearer token
- **Base URL:** User-provided endpoint

---

## Configuration

AI configs are stored in `EncryptedSharedPreferences` (AES-256-GCM).

Each config contains:

- `id` — UUID
- `providerType` — GEMINI, OPENROUTER, or CUSTOM
- `apiKey` — API key or token
- `model` — Model identifier
- `baseUrl` — Endpoint URL (OpenRouter/Custom only)
- `label` — User-friendly display name

Multiple configs can be stored. One is marked as active.

---

## Online vs Offline Mode

### Online Mode

Uses the configured AI provider for:

- Smart text parsing (Persian → structured transaction)
- Budget advice generation
- Budget forecast

### Offline Mode

Falls back to local rule-based parsing (now powered by Rust core via UniFFI):

- **Rust NLP Module** — 200+ keyword Persian text analysis (replaces `PersianAmountParser`, `MoneyDetector`, `PersianTextPreprocessor`)
- `GeminiParser.parseSentenceOffline()` — Delegates to Rust for category inference + type detection. The Kotlin `parseSentenceOffline` path is a **permanent** offline NLP parser fallback (per the exception list in ADR-001).
- `BudgetAdvisor.getOfflineAdvice()` — Rule-based financial advice (Rust-backed via UniFFI). The Kotlin-side `getOfflineAdvice` fallback is **temporary** and scheduled for removal (Phase 9 in `plans/2026-08-19-rust-fallback-consolidation-plan.md`), after which a Rust failure will surface a controlled error to the UI.
- `BudgetAdvisor.getOfflineForecast()` — Rule-based forecast (Rust-backed via UniFFI). The Kotlin-side `getOfflineForecast` fallback is **temporary** and scheduled for removal (Phase 10 in `plans/2026-08-19-rust-fallback-consolidation-plan.md`).

---

## Parsing Pipeline

### Online Flow

```
User Text → GeminiParser.parseSentence()
         → AiProvider.generateContent() [API call]
         → JSON response parsed
         → ParsedResult returned
         → User confirms → Saved to Room
```

### Offline Flow

```
User Text → GeminiParser.parseSentenceOffline()
         → RustBridge.parseSentenceOfflineSync() [Rust FFI call]
         → Rust core: normalize → tokenize → infer category → detect type
         → ParsedResult returned
         → User confirms → Saved to Room
```

---

## Rust NLP Module (hesabyar-core)

The offline parsing pipeline was migrated from Kotlin to Rust for performance and accuracy.
The Rust core handles: text preprocessing, amount parsing, category inference, and type detection.

> **Note:** The Kotlin-side `GeminiParser.parseSentenceOffline()` retains a parser implementation as a **permanent** fallback (offline NLP parser is in the ADR-001 exception list). It is NOT scheduled for removal. Other offline capabilities (budget advice, forecast, analytics, dashboard) have **temporary** Kotlin fallbacks scheduled for removal per `plans/2026-08-19-rust-fallback-consolidation-plan.md`.

### Pipeline (Rust)

1. **Normalize** — Convert Arabic/Persian digits to ASCII, remove separators
2. **Tokenize** — Character-by-character scanner producing Number and Unit tokens
3. **Interpret** — Three paths:
   - `interpretWithUnits` — Explicit units (میلیون, هزار, etc.)
   - `interpretShorthand` — Position-based ("5 و 400" = 5M + 400K)
   - `interpretBareLast` — Fallback to last number
4. **Classify** — 200+ keyword matching for category inference and type detection

## Caching Strategy

### Forecast & Advice Cache

- Cached in SharedPreferences (plain text)
- Cache duration: 10 minutes
- Invalidated when data signature changes
- Data signature: `txCount|txTotal|loanCount|instCount|catCount`

### Model Cache

- Cached in EncryptedSharedPreferences
- Duration: 24 hours
- Per-provider cache entries

---

## Security

- API keys stored in EncryptedSharedPreferences (AES-256-GCM)
- Keys never logged in plain text
- Legacy plaintext SharedPreferences migrated and cleared
- No API keys in source code (use .env for build secrets)
