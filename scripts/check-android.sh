#!/usr/bin/env bash
set -euo pipefail

# check-android.sh - Read-only static analysis and test runner for Android Kotlin code.
# Usage: ./scripts/check-android.sh

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

echo "=== Android Check: ktlintCheck ==="
./gradlew --no-daemon ktlintCheck

echo "=== Android Check: detekt ==="
./gradlew --no-daemon detekt

echo "=== Android Check: testDebugUnitTest ==="
./gradlew --no-daemon testDebugUnitTest

echo "=== Android Check: lintDebug ==="
./gradlew --no-daemon lintDebug

echo "=== Android Check: Complete (PASS) ==="
