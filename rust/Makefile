# Developer commands for the Rust workspace.
#
# Run from rust/:
#   make lint
# Or from the repository root:
#   make -C rust lint
#
# CI runs fmt-check, lint, and test with the same flags (see
# .github/workflows/rust-lint.yml).

.PHONY: lint lint-fix fmt fmt-check check test bench clean

# Static analysis. Same command as CI. Fails on any warning.
lint:
	cargo clippy --workspace --all-targets --all-features -- -D warnings

# Auto-fix clippy findings where a safe, machine-applicable fix exists.
# Review the diff before you commit.
lint-fix:
	cargo clippy --fix --allow-dirty --workspace --all-targets

# Format all Rust sources in place.
fmt:
	cargo fmt

# Verify formatting only. Changes no files.
fmt-check:
	cargo fmt --check

# Fast type and borrow check. No code generation.
check:
	cargo check --workspace --all-targets

# Unit tests for the whole workspace.
test:
	cargo test --workspace

# Criterion benchmarks for hesabyar-core.
bench:
	cargo bench --package hesabyar-core

# Remove build artifacts.
clean:
	cargo clean
