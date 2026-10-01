#!/usr/bin/env bash
set -euo pipefail

# check-rust-bridge.sh - Run isolated Rust-bridge JVM tests.
# Usage: ./scripts/check-rust-bridge.sh

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

echo "=== Rust Bridge Check: testDebugUnitTestRust ==="
./gradlew --no-daemon testDebugUnitTestRust

echo "=== Rust Bridge Check: Complete (PASS) ==="
