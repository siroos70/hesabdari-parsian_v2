#!/usr/bin/env sh
# Tests for scripts/detect-changes.sh (release gating logic).
#
# POSIX-compatible: runs with bash, ash/busybox, or dash. Requires git.
# Usage: ./scripts/tests/test-detect-changes.sh
set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DETECT="$HERE/../detect-changes.sh"
TMP=$(mktemp -d)
trap 'cd "$HERE" && rm -rf "$TMP" 2>/dev/null || true' EXIT

pass=0
fail=0
test_id=0

new_repo() {
  test_id=$((test_id + 1))
  REPO="$TMP/repo-$test_id"
  mkdir -p "$REPO/rust/hesabyar-core" "$REPO/gradle" "$REPO/app"
  cd "$REPO"
  # Portable init: -b needs Git >= 2.28; set HEAD explicitly instead.
  git init -q .
  git symbolic-ref HEAD refs/heads/main
  # Isolate from the developer's global config (gpg signing, hooks).
  git config user.email test@example.com
  git config user.name test
  git config commit.gpgsign false
  git config core.hooksPath /dev/null
  printf '[workspace.package]\nversion = "0.7.2"\n' > rust/Cargo.toml
  printf '[package]\nname = "core"\nversion.workspace = true\nedition.workspace = true\ndependencies = []\n' > rust/hesabyar-core/Cargo.toml
  # Optional seeds so extra manifests exist in the BASE commit (otherwise
  # adding them later counts as a version-bearing new-file diff).
  case "${1:-}" in
    nested)
      mkdir -p rust/tools/nested-crate
      printf '[package]\nname = "nested"\nversion = "0.1.0"\ndependencies = []\n' > rust/tools/nested-crate/Cargo.toml
      ;;
    deep)
      mkdir -p rust/a/b/c
      printf '[package]\nname = "deep"\nversion = "0.1.0"\ndependencies = []\n' > rust/a/b/c/Cargo.toml
      ;;
  esac
  echo base > app/MainActivity.kt
  git add -A
  git commit -qm base
}

branch() { git checkout -q -b "$1" main; }

commit_all() {
  git add -A
  git commit -qm "scenario"
}

# check <description> <expected-exit-code> [expected-output-substring]
# The optional substring guards against passing through the wrong code path
# (e.g. a generic release instead of the version-bump branch).
check() {
  name=$1
  want=$2
  want_msg=${3:-}
  got=0
  "$DETECT" main HEAD > "$TMP/out.txt" 2> "$TMP/err.txt" || got=$?
  if [ "$got" != "$want" ]; then
    fail=$((fail + 1))
    printf 'FAIL %-42s want=%s got=%s\n' "$name" "$want" "$got"
    echo '--- stdout ---'; cat "$TMP/out.txt"
    echo '--- stderr ---'; cat "$TMP/err.txt"
    echo '---------------'
    return
  fi
  if [ -n "$want_msg" ] && ! grep -qF -- "$want_msg" "$TMP/out.txt"; then
    fail=$((fail + 1))
    printf 'FAIL %-42s exit=%s but output lacks: %s\n' "$name" "$got" "$want_msg"
    echo '--- stdout ---'; cat "$TMP/out.txt"
    echo '---------------'
    return
  fi
  pass=$((pass + 1))
  printf 'PASS %-42s %s\n' "$name" "$(head -n 1 "$TMP/out.txt")"
}

# 1. Dependency-only bump in an inherited-version member manifest -> skip
new_repo
branch t1
echo 'anyhow = "1"' >> rust/hesabyar-core/Cargo.toml
commit_all
check "member manifest dep-only bump" 1 "SKIP"

# 2. Manual version bump in workspace manifest -> release via bump branch
new_repo
branch t2
sed 's/version = "0.7.2"/version = "0.8.0"/' rust/Cargo.toml > rust/Cargo.toml.new
mv rust/Cargo.toml.new rust/Cargo.toml
commit_all
check "workspace manifest version bump" 0 "Version bump detected"

# 3. Switching an inherited-version member (version.workspace = true) to a
#    local version is NOT a bump: the base manifest has no quoted version
#    key, so there is no old value to change. Releases for inherited members
#    are driven by the root [workspace.package] version (test 2); local-
#    version member bumps are covered by tests 10b/11b.
new_repo
branch t3
printf '[package]\nname = "core"\nversion = "1.0.0"\ndependencies = []\n' > rust/hesabyar-core/Cargo.toml
commit_all
check "inherited member switched to local version" 1 "SKIP"

# 4. Table-form [dependencies.*] version bump is NOT a package bump -> skip
#    (member keeps version.workspace = true; only a dep table is added)
new_repo
branch t4
cat >> rust/hesabyar-core/Cargo.toml <<'EOF'

[dependencies.serde]
version = "2"
EOF
commit_all
check "table-form dependency version bump" 1 "SKIP"

# 5. Gradle catalog change only -> skip
new_repo
branch t5
printf '[versions]\njna = "5.14.0"\n' > gradle/libs.versions.toml
commit_all
check "libs.versions.toml only" 1 "SKIP"

# 5b. Other skip-listed Gradle lockfiles -> skip.
#     Fixtures live under gradle/ so they would otherwise hit the gradle/*
#     include pattern and trigger a release — this makes the test actually
#     exercise the explicit skip entries (a root-level file would skip via
#     the no-match fallback even without them).
new_repo
branch t5b
echo lock > gradle/gradle.lockfile
echo lock > gradle/versions.lock
commit_all
check "gradle lockfile variants" 1 "SKIP"

# 6. Cargo.lock only -> skip
new_repo
branch t6
echo 'lockfile' > rust/Cargo.lock
commit_all
check "Cargo.lock only" 1 "SKIP"

# 7. App code mixed with manifest edit -> release via app-code branch
new_repo
branch t7
echo 'class NewScreen' > app/NewScreen.kt
echo 'logos = "0.14"' >> rust/Cargo.toml
commit_all
check "mixed app code + manifest" 0 "Application code changes"

# 7b. Pure application-code change (no manifest touched) -> release via the
#     app-code branch. Locks in the primary gating path independently of
#     manifest edits.
new_repo
branch t7b
echo 'class Tweaked' > app/MainActivity.kt
commit_all
check "app code only" 0 "Application code changes"

# 8. Unrelated file only (no include match) -> skip
new_repo
branch t8
echo 'docs' > README.md
commit_all
check "unrelated non-app file" 1 "SKIP"

# 9. Non-version edit to the workspace manifest only (the weekly Dependabot
#    case: a table-form entry under [workspace.dependencies]) -> skip
new_repo
branch t9
printf '\n[workspace.dependencies.anyhow]\nversion = "1"\n' >> rust/Cargo.toml
commit_all
check "workspace manifest dep-table edit" 1 "SKIP"

# 10. Nested member manifest: dep-only edit -> skip, then its own
#     version bump -> release via the bump branch
new_repo nested
branch t10
echo 'log = "0.4"' >> rust/tools/nested-crate/Cargo.toml
commit_all
check "nested member dep-only bump" 1 "SKIP"
git checkout -q -b t10b
cat > rust/tools/nested-crate/Cargo.toml <<'EOF'
[package]
name = "nested"
version = "0.2.0"
dependencies = []
log = "0.4"
EOF
commit_all
check "nested member version bump" 0 "Version bump detected in rust/tools/nested-crate/Cargo.toml"

# 11. Deeply nested member manifest: dep-only edit -> skip.
#     In shell case globs `*` also matches `/`, so any depth under rust/
#     is covered by rust/*/Cargo.toml; this test locks that assumption in.
new_repo deep
branch t11
echo 'log = "0.4"' >> rust/a/b/c/Cargo.toml
commit_all
check "deep-nested member dep-only" 1 "SKIP"
git checkout -q -b t11b
cat > rust/a/b/c/Cargo.toml <<'EOF'
[package]
name = "deep"
version = "0.2.0"
dependencies = []
log = "0.4"
EOF
commit_all
check "deep-nested member version bump" 0 "Version bump detected in rust/a/b/c/Cargo.toml"

# 12. Rust source edit without a version bump -> skip BY DESIGN:
#     releases are version-driven (unchanged versionCode cannot publish),
#     so core changes ship by bumping [workspace.package].version.
new_repo
branch t12
mkdir -p rust/hesabyar-core/src
echo 'fn fixed() {}' > rust/hesabyar-core/src/lib.rs
commit_all
check "rust source edit without bump" 1 "SKIP"

# 13. Newly added versioned manifest -> skip BY DESIGN: a new file carrying
#     its own version key is not a bump. Like any other core change it ships
#     when [workspace.package].version moves.
new_repo
branch t13
mkdir -p rust/new-crate
printf '[package]\nname = "new-crate"\nversion = "0.1.0"\ndependencies = []\n' > rust/new-crate/Cargo.toml
commit_all
check "added manifest with version key" 1 "SKIP"

# 14. Branch identical to main (empty diff) -> skip via the empty-diff
#     safeguard, the default barrier against spurious releases.
new_repo
branch t14
check "identical to main (empty diff)" 1 "SKIP"

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
