# Plan 002: Remove Gemini API key from request URLs

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 44dd519..HEAD -- app/src/main/java/io/github/mojri/hesabyar/api/AiProvider.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- Priority: P1
- Effort: M
- Risk: HIGH — changes the AI network path; must not break Gemini/OpenRouter/Custom flows.
- Depends on: none
- Category: security
- Planned at: commit `44dd519`, 2026-07-23
- **Resolution**: The redaction interceptor (added by this plan) was removed. It stripped `?key=` from the actual wire request, breaking Gemini auth. AppLogger already avoids logging the key value (logs `apiKeyLength` only, error logs use `url.substringBefore("?")`), so no interceptor is needed. The key is safe from local logs without one.

## Why this matters

`AiProvider.kt` embeds the Gemini API key as a `?key=` query parameter for both model listing and `generateContent`. That string appears in OkHttp `Request.url`, and any future logging interceptor, reverse-proxy log, or network debugger can capture it. OpenRouter/Custom already use an `Authorization: Bearer` header; Gemini's REST endpoint only supports query-string keys, so the mitigation is to redact before any local logging path and document the exposure surface. If this key has already appeared in logs, it should be rotated.

## Current state

`app/src/main/java/io/github/mojri/hesabyar/api/AiProvider.kt` — singleton object implementing all AI provider HTTP calls.

Lines 34-39 — OkHttp client with no interceptor chain:
```kotlin
  private val client =
    OkHttpClient
      .Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .build()
```

Lines 131-138 — Gemini model list URL includes `?key=$apiKey`:
```kotlin
  private fun fetchGeminiModels(apiKey: String): List<FetchedModel> {
    val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
    val request =
      Request
        .Builder()
        .url(url)
        .get()
        .build()
```

Lines 309-311 — Gemini generateContent URL includes `?key=...`:
```kotlin
    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${config.apiKey}"
    return executePost(url, requestJson, ::parseGeminiResponse, apiKey = null)
```

Lines 71-74 and 397/400 — `AppLogger.d` and error logs already avoid printing the key value, and error logs already strip query params with `url.substringBefore("?")`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Tests | `./gradlew test --no-daemon` | `BUILD SUCCESSFUL`, all tests pass |
| Lint | `./gradlew ktlintCheck detekt --no-daemon` | exit 0 |

## Scope

In scope:
- `app/src/main/java/io/github/mojri/hesabyar/api/AiProvider.kt`
- `app/src/test/java/io/github/mojri/hesabyar/api/AiProviderTest.kt` (create if absent)

Out of scope:
- Switching to a different AI provider SDK or adding server-side proxy infrastructure. Treat proxy migration as a follow-up spike, not this plan.

## Steps

### Step 1: Add a URL-redaction interceptor

Add a private `Interceptor` inside `AiProvider` that strips the `key` query parameter from `request.url` before the request reaches any future logging interceptor. The interceptor must return the original request for execution — the actual network request still carries `?key=` because the Google Generative AI REST API requires it; the point is to prevent the key from appearing in any **local** log/interceptor output.

Target shape (illustrative):
```kotlin
  private val redactionInterceptor = Interceptor { chain ->
    val original = chain.request()
    val redactedUrl = original.url.newBuilder()
      .removeAllQueryParameters("key")
      .build()
    chain.proceed(original.newBuilder().url(redactedUrl).build())
  }
```

Then prepend it to the `client` builder:
```kotlin
  private val client =
    OkHttpClient
      .Builder()
      .addInterceptor(redactionInterceptor)
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .build()
```

**STOP**: If `removeAllQueryParameters` is not available on the project's OkHttp version, stop and report the exact method or version; do not replace it with brittle string manipulation.

**Verify**: `./gradlew test --no-daemon` → all pass, including `AiProviderTest`, `AiProviderLogicTest`, `AiProviderNullBodyTest`.

### Step 2: Add a redaction unit test

In a new or existing `AiProviderTest.kt`, add a test that:
1. Builds a `Request` with `?key=SECRET` in the URL.
2. Passes it through the redaction interceptor.
3. Asserts the outgoing request's URL has no `key` query parameter.

Use the existing test helper pattern in `app/src/test/java/io/github/mojri/hesabyar/api/AiProviderTest.kt`.

**Verify**: `./gradlew test --no-daemon --tests "io.github.mojri.hesabyar.api.AiProviderTest"` → new test passes.

### Step 3: Add a KDoc note about residual exposure

Update the KDoc / object-level comment in `AiProvider.kt` to state: "Gemini REST API requires the API key as a URL query parameter. The key is stripped from local interceptor output, but may still be visible to reverse proxies or network captures. If logs are collected, rotate the key."

**Verify**: `git diff --stat 44dd519..HEAD -- app/src/main/java/io/github/mojri/hesabyar/api/AiProvider.kt` shows only the interceptor, test, and comment change.

### Step 4: Lint

**Verify**: `./gradlew ktlintCheck detekt --no-daemon` → exit 0.

## Test plan

- New test: interceptor strips `key` query parameter.
- Existing tests to re-run: `AiProviderTest`, `AiProviderLogicTest`, `AiProviderNullBodyTest`, `GeminiParserFallbackTest` (to ensure parsing behavior is unaffected).

## Done criteria

- [ ] `AiProvider.client` builder includes the redaction interceptor before `connectTimeout`/`readTimeout`
- [ ] New redaction unit test passes
- [ ] No `AppLogger.d` or `AppLogger.e` call in `AiProvider.kt` logs a URL containing `?key=`
- [ ] `./gradlew test --no-daemon` exits 0
- [ ] `./gradlew ktlintCheck detekt --no-daemon` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

- The code at `AiProvider.kt:34-39` or `:131-138`, `:309-311` doesn't match the excerpts.
- `removeAllQueryParameters` is unavailable on the project's OkHttp version.
- The test fails twice after a reasonable fix attempt.
- Adding the interceptor causes a test failure that cannot be explained by redaction behavior.

## Maintenance notes

- Google's Gemini REST API only accepts API keys via `?key=`. If a future Google SDK or backend proxy becomes available, prefer header-based auth and remove the query param entirely.
- Any OkHttp logging interceptor added later must preserve the redaction order: logging interceptors below the redaction interceptor will see redacted URLs.
- If API keys have been captured in logs before this change, rotate them independently of this plan.
