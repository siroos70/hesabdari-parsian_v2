# OpenCodeReview project memory (issue #206 phase 4)

Injected into every OpenCodeReview run via the `rule` input, alongside
`.github/ocr-rules.json`. This is the single editable source of
project-specific facts the reviewer cannot infer from the code alone —
kept separate from the structured per-path rules so it is easy to extend
without touching the rule matching logic.

## Release / dependency conventions (do NOT report as suspicious)

- Version bumps in `Cargo.toml` and Gradle files are **INTENTIONAL release
  triggers** produced by the version-driven release pipeline — never report
  them as suspicious or as "unexpected version changes".
- Lockfile updates under `gradle/` and `Cargo.lock` are automated
  **Dependabot** outputs — intentional; do not report them.
- Dependabot grouping and the `detect-changes.sh` dependency-skip rules are
  deliberate CI design, not regressions.

## Localization

- User-facing strings are expected in **Persian (Farsi)**. Do not report
  mixed Persian/English UI strings as an inconsistency or a bug.

## Reporting discipline

- Report genuine bugs, security issues, and regressions you are **reasonably
  confident** about, with a concrete failure scenario.
- Skip style, documentation nits, naming, and anything ktlint / detekt /
  clippy would already flag.
- Pure refactoring preference is not a bug. Report a maintainability concern
  only when it materially raises the risk of future defects — such findings
  belong in the PR summary, not as inline comments.
- If you are below ~65% confident a finding is a real issue AND cannot name a
  concrete failure scenario, omit it; otherwise report it.
