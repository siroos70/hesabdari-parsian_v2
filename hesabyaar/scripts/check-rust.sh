#!/usr/bin/env bash
set -euo pipefail

# check-rust.sh - Read-only static analysis and test runner for Rust workspace.
# Usage: ./scripts/check-rust.sh

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUST_DIR="$REPO_ROOT/rust"

echo "=== Rust Check: Clippy ==="
(cd "$RUST_DIR" && cargo clippy --workspace --all-targets --all-features -- -D warnings)

echo "=== Rust Check: Tests ==="
(cd "$RUST_DIR" && cargo test --workspace --all-features)

echo "=== Rust Check: Complete (PASS) ==="
