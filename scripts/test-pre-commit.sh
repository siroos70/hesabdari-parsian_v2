#!/usr/bin/env bash
#
# Regression harness for the Hesabyar pre-commit hook (Rust gate).
#
# The script clones this repository into a scratch directory. It installs the
# real hook through core.hooksPath and drives real `git commit` runs. Only the
# Kotlin gates are stubbed: a fake `gradlew` exits 0 at once, except in case U,
# where a failing stub proves a Kotlin failure blocks every Rust gate. cargo fmt
# and cargo clippy run for real against the cloned rust/ workspace.
#
# Assertion model:
#   - Failure cases assert the residual index (the commit was aborted).
#   - Success cases assert the created commit (BASE..HEAD) plus an empty
#     residual index, because a passing hook lets git create the commit.
#   - Expected worktree/index bytes are always derived from the actual base
#     files (copy + append). No expected content is rebuilt from shell
#     strings, so command-substitution newline stripping cannot corrupt a
#     comparison.
#   - Probes are inserted before the #[cfg(test)] attribute of currency.rs,
#     with end-of-line style matched to the file. This keeps them compiled,
#     avoids clippy::items_after_test_module, and gives cargo fmt zero churn.
#
# Matrix letters match the review regression matrix:
#   A normal staged Rust commit          K staged Cargo.toml + unstaged Cargo.toml
#   B partially staged Rust file         L unstaged Cargo.lock corruption
#   C staged + unrelated unstaged Rust   M cargo fmt failure
#   D staged invalid + valid worktree    N cargo clippy failure
#   E staged valid + invalid worktree    O restoration after fmt failure
#   F staged unformatted + formatted wt  P Kotlin-only commit
#   G staged formatted + unstaged edits  Q missing rust/ directory
#   H untracked Rust file                R unusual filename (spaces/brackets)
#   I staged Rust deletion               S tab in filename
#   J unstaged Rust deletion             T newline in filename (best effort)
#   U failing Kotlin gate blocks all Rust gates (cargo probe proves it)
#   V unstaged +x mode change survives       W +x restored after clippy failure
#   X unstaged 740/750/710 modes survive     Y exact modes restored after failure
#   Z broken stat aborts before materialization (checkout-index probe proves it)
#   E0 static check: no destructive git commands in the hook source
#   E1 static check: full-mode capture/restore wired into the hook source
#   E2 static check: mode capture precedes index materialization
#
# Usage:
#   scripts/test-pre-commit.sh
# Environment:
#   BASE_COMMIT  commit to test against (default: current HEAD)
#   KEEP_WORK=1  keep the scratch directory for debugging

set -uo pipefail

SRC=$(git rev-parse --show-toplevel) || exit 1
BASE=${BASE_COMMIT:-$(git -C "$SRC" rev-parse HEAD)}
WORK=$(mktemp -d "${TMPDIR:-/tmp}/hesabyar-hooktest.XXXXXX")
CLONE="$WORK/repo"
HOOKS="$WORK/hooks"
LOG="$WORK/hook.log"
EXP="$WORK/exp.bin"
WTX="$WORK/wtx.bin"
EXP_FMT="$WORK/exp_fmt.bin"

PASS=0
FAIL=0
SKIP=0
RC=0
# Commit-range base for expect_commit_exactly. Cases that create setup
# commits (beyond BASE) repoint this at their pre-probe parent commit.
DIFF_BASE=""

cleanup() {
  if [[ ${KEEP_WORK:-0} != 1 ]]; then
    rm -rf "$WORK"
  else
    echo "Scratch directory kept: $WORK"
  fi
}
trap cleanup EXIT

die() { echo "FATAL: $1" >&2; exit 1; }

command -v cargo >/dev/null 2>&1 || die "cargo not on PATH"
[[ -f "$SRC/scripts/pre-commit" ]] || die "scripts/pre-commit not found in $SRC"

echo "Base commit : $BASE"
echo "Scratch dir : $WORK"

RS="rust/hesabyar-core/src/currency.rs"
CARGO_TOML="rust/Cargo.toml"
CARGO_LOCK="rust/Cargo.lock"
CARRIER="carrier_probe.txt"

# Build the scratch clone. autocrlf is disabled so checkout is byte-exact and
# every comparison below is free of line-ending conversion noise on Windows.
git -c core.autocrlf=false clone -q --no-hardlinks "$SRC" "$CLONE" || die "clone failed"
git_clone() { git -C "$CLONE" "$@"; }
git_clone config core.autocrlf false
git_clone config user.email probe@hesabyar.local
git_clone config user.name "Hook Probe"
git_clone checkout -q -B probe "$BASE" || die "cannot check out $BASE"
[[ -f "$CLONE/$RS" ]] || die "probe target $RS missing"
[[ -f "$CLONE/$CARGO_LOCK" ]] || die "tracked Cargo.lock missing"

mkdir -p "$HOOKS"
cp "$SRC/scripts/pre-commit" "$HOOKS/pre-commit"
chmod +x "$HOOKS/pre-commit"
git_clone config core.hooksPath "$HOOKS"
export CARGO_TARGET_DIR="$WORK/target"

# Cargo probe for case U. A logging cargo shim goes first on PATH for that one
# case. A Kotlin gate failure must abort the hook before any cargo call, so the
# probe log must never appear.
CARGO_SHIM_DIR="$WORK/cargo-shim"
CARGO_PROBE_LOG="$WORK/cargo-invoked.log"
mkdir -p "$CARGO_SHIM_DIR"
printf '#!/usr/bin/env bash\necho "cargo $*" >> "%s"\nexit 0\n' "$CARGO_PROBE_LOG" \
  > "$CARGO_SHIM_DIR/cargo"
chmod +x "$CARGO_SHIM_DIR/cargo"

# Broken-stat shim for case Z. Prepended to PATH for that one case it makes
# every `stat` invocation fail, so the hook's mode capture cannot succeed.
STAT_SHIM_DIR="$WORK/stat-shim"
mkdir -p "$STAT_SHIM_DIR"
printf '#!/usr/bin/env bash\nexit 1\n' > "$STAT_SHIM_DIR/stat"
chmod +x "$STAT_SHIM_DIR/stat"

# git wrapper for case Z. It logs every invocation and then runs the real
# git binary. The mode-capture failure must abort BEFORE any worktree
# materialization, so this log must never contain checkout-index. The
# wrapper also proves it was engaged: run_hook's own `git commit` must
# appear in it.
GIT_SHIM_DIR="$WORK/git-shim"
GIT_PROBE_LOG="$WORK/git-invocations.log"
REAL_GIT=$(command -v git) || die "git not on PATH"
mkdir -p "$GIT_SHIM_DIR"
printf '#!/usr/bin/env bash\necho "git $*" >> "%s"\nexec "%s" "$@"\n' \
  "$GIT_PROBE_LOG" "$REAL_GIT" > "$GIT_SHIM_DIR/git"
chmod +x "$GIT_SHIM_DIR/git"

reset_clone() {
  git_clone reset -q --hard "$BASE" || die "reset failed"
  git_clone clean -qfdx || die "clean failed"
  printf '#!/usr/bin/env bash\nexit 0\n' > "$CLONE/gradlew"
  chmod +x "$CLONE/gradlew"
  refresh_split
  DIFF_BASE=""
}

run_hook() {
  ( cd "$CLONE" && printf 'n\n' | git commit --allow-empty -q -m "hook probe" ) \
    > "$LOG" 2>&1
  RC=$?
}

# --- assertion helpers -------------------------------------------------------

pass() { PASS=$((PASS + 1)); echo "  PASS: $1"; }
fail() { FAIL=$((FAIL + 1)); echo "  FAIL: $1"; }
skip() { SKIP=$((SKIP + 1)); echo "  SKIP: $1"; }

expect_rc() { # expect_rc <0|nonzero> <desc>
  local want="$1" desc="$2"
  if [[ $want == 0 && $RC -eq 0 ]] || [[ $want == nonzero && $RC -ne 0 ]]; then
    pass "$desc (rc=$RC)"
  else
    fail "$desc (wanted rc=$want, got rc=$RC)"
    sed 's/^/      | /' "$LOG" | tail -15
  fi
}

assert_idx_file() { # repo path, expected-bytes file
  local want_oid got_oid
  want_oid=$(git_clone hash-object --path="$1" "$2")
  got_oid=$(git_clone ls-files -s -- "$1" | awk '{print $2}')
  if [[ "$got_oid" == "$want_oid" ]]; then
    pass "index content matches candidate: $1"
  else
    fail "index content differs from candidate: $1"
  fi
}

assert_wt_file() { # repo path, expected-bytes file (byte-exact cmp)
  if cmp -s "$CLONE/$1" "$2"; then
    pass "worktree preserved byte-exact: $1"
  else
    fail "worktree content differs: $1"
  fi
}

assert_absent_wt() {
  if [[ ! -e "$CLONE/$1" ]]; then
    pass "absent in worktree as before: $1"
  else
    fail "unexpectedly present in worktree: $1"
  fi
}

assert_present_wt() {
  if [[ -e "$CLONE/$1" ]]; then
    pass "present in worktree: $1"
  else
    fail "missing from worktree: $1"
  fi
}

assert_idx_is_base() {
  local base_oid got_oid
  base_oid=$(git -C "$SRC" rev-parse -q --verify "$BASE:$1") || base_oid=""
  got_oid=$(git_clone ls-files -s -- "$1" | awk '{print $2}')
  if [[ -n "$base_oid" && "$got_oid" == "$base_oid" ]]; then
    pass "index unchanged from base: $1"
  else
    fail "index changed from base: $1 (idx=$got_oid base=$base_oid)"
  fi
}

contains_staged() {
  git_clone diff --cached -z --name-only | grep -zqx -F -- "$1"
}

expect_commit_exactly() { # probe commit holds exactly these paths vs DIFF_BASE
  local want got n=$#
  want=$(printf '%s\n' "$@" | LC_ALL=C sort)
  got=$(git_clone diff -z --name-only "${DIFF_BASE:-$BASE}..HEAD" \
    | LC_ALL=C sort -z | tr '\0' '\n')
  if [[ "$got" == "$want" ]]; then
    pass "commit contains exactly the $n intended path(s)"
  else
    fail "commit path set mismatch"
    echo "      wanted: $(echo "$want" | tr '\n' ' ')"
    echo "      got:    $(echo "$got" | tr '\n' ' ')"
  fi
}

commit_deletion_present() {
  git_clone diff -z --name-only --diff-filter=D "${DIFF_BASE:-$BASE}..HEAD" \
    | grep -zqx -F -- "$1"
}

assert_nothing_staged() {
  if [[ -z "$(git_clone diff --cached --name-only)" ]]; then
    pass "nothing left staged after the successful commit"
  else
    fail "residual staged paths after commit: $(git_clone diff --cached --name-only | tr '\n' ' ')"
  fi
}

expect_staged_exactly() { # failure path: aborted commit keeps this index set
  local want got n=$#
  want=$(printf '%s\n' "$@" | LC_ALL=C sort)
  got=$(git_clone diff --cached -z --name-only | LC_ALL=C sort -z | tr '\0' '\n')
  if [[ "$got" == "$want" ]]; then
    pass "staged set is exactly the $n intended path(s)"
  else
    fail "staged set mismatch"
    echo "      wanted: $(echo "$want" | tr '\n' ' ')"
    echo "      got:    $(echo "$got" | tr '\n' ' ')"
  fi
}

assert_log_contains() {
  if grep -qF -- "$1" "$LOG"; then
    pass "hook output mentions: $1"
  else
    fail "hook output missing: $1"
  fi
}

stage_carrier() {
  printf 'probe carrier %s\n' "$1" > "$CLONE/$CARRIER"
  git_clone add "$CARRIER"
}

setup_commit_no_verify() {
  git_clone add "$1"
  git_clone commit -q --no-verify -m "setup: add $1"
}

# --- Rust probe plumbing -------------------------------------------------------
#
# currency.rs carries the probes. refresh_split splits the base file around
# its #[cfg(test)] attribute. Probes are inserted before that attribute so
# clippy::items_after_test_module can never fire. Probe end-of-line style is
# matched to the file so cargo fmt sees no churn.

RS_HEAD="$WORK/rs.head"
RS_TAIL="$WORK/rs.tail"
RS_MID="$WORK/rs.mid"

refresh_split() {
  local line
  line=$(grep -nm1 -E '^#\[cfg\(test\)\]$|^mod tests\b' "$CLONE/$RS" | cut -d: -f1)
  [[ -n "${line:-}" ]] || die "cannot locate test module in $RS"
  head -n $((line - 1)) "$CLONE/$RS" > "$RS_HEAD"
  tail -n +"$line" "$CLONE/$RS" > "$RS_TAIL"
}

rs_probe_eol_matched() { # probe text -> $RS_MID with the file's EOL style
  if grep -q $'\r' "$RS_HEAD" 2>/dev/null; then
    printf '%s' "$1" | sed 's/$/\r/' > "$RS_MID"
  else
    printf '%s' "$1" > "$RS_MID"
  fi
}

# Build the full candidate (head + probe + tail) into $EXP and mirror it into
# the clone file. Caller stages afterwards.
mk_rs_candidate() { # probe-lf-text
  rs_probe_eol_matched "$1"
  { cat "$RS_HEAD" "$RS_MID" "$RS_TAIL"; } > "$EXP"
  cp "$EXP" "$CLONE/$RS"
}

# Replace the clone file with an alternative full candidate in $WTX.
mk_rs_alt_worktree() { # probe-lf-text
  rs_probe_eol_matched "$1"
  { cat "$RS_HEAD" "$RS_MID" "$RS_TAIL"; } > "$WTX"
  cp "$WTX" "$CLONE/$RS"
}

# Append literal extra bytes to both $EXP-derived $WTX and the clone file.
add_rs_unstaged_tail() { # raw extra text with trailing newline
  cp "$EXP" "$WTX"
  printf '%s' "$1" >> "$WTX"
  printf '%s' "$1" >> "$CLONE/$RS"
}

P_A=$'pub fn hook_matrix_probe_a() -> i32 {\n    7\n}\n'
P_ALT=$'pub fn hook_matrix_probe_alt() -> i32 {\n    9\n}\n'
P_E=$'pub fn hook_matrix_probe_e() -> i32 {\n    5\n}\n'
P_F_UNF=$'pub   fn   hook_matrix_probe_f()->i32{7}\n'
P_F_STABLE=$'pub fn hook_matrix_probe_f() -> i32 {\n    7\n}\n'
P_G=$'pub fn hook_matrix_probe_g() -> i32 {\n    3\n}\n'
P_BAD_CLIPPY=$'pub fn hook_matrix_bad() -> i32 {\n    let unused_probe_value = 7;\n    3\n}\n'
P_BAD_N=$'pub fn hook_matrix_bad_n() -> i32 {\n    let unused_probe_value_n = 9;\n    4\n}\n'
P_PARSE=$'fn broken( {\n'

# --- matrix cases -------------------------------------------------------------

case_e0_static_no_destructive_ops() {
  echo "=== E0: hook source has no destructive git commands ==="
  reset_clone
  if grep -nE 'git (reset|stash|clean|checkout)([^-]|$)' "$SRC/scripts/pre-commit"; then
    fail "destructive git command found in hook source"
  else
    pass "no git reset/stash/clean/checkout in hook source (checkout-index allowed)"
  fi
}

case_a() {
  echo "=== A: normal staged Rust commit ==="
  reset_clone
  mk_rs_candidate "$P_A"
  git_clone add "$RS"
  stage_carrier a
  run_hook
  expect_rc 0 "hook passes on valid formatted staged Rust"
  assert_log_contains "cargo clippy passed"
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_b() {
  echo "=== B: partially staged Rust file ==="
  reset_clone
  mk_rs_candidate "$P_A"
  git_clone add "$RS"
  stage_carrier b
  add_rs_unstaged_tail '// b-extra unstaged edit
'
  run_hook
  expect_rc 0 "hook passes with partial stage"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_c() {
  echo "=== C: staged Rust edit + unrelated unstaged Rust edit ==="
  reset_clone
  local other="rust/hesabyar-core/src/calendar.rs"
  local other_exp="$WORK/other.bin"
  mk_rs_candidate "$P_A"
  git_clone add "$RS"
  stage_carrier c
  cp "$CLONE/$other" "$other_exp"
  printf '// c-unrelated unstaged comment\n' >> "$CLONE/$other"
  printf '// c-unrelated unstaged comment\n' >> "$other_exp"
  run_hook
  expect_rc 0 "hook passes"
  assert_idx_file "$RS" "$EXP"
  assert_idx_is_base "$other"
  assert_wt_file "$other" "$other_exp"
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_d() {
  echo "=== D: staged Clippy-invalid + valid unstaged worktree ==="
  reset_clone
  mk_rs_candidate "$P_BAD_CLIPPY"
  git_clone add "$RS"
  mk_rs_alt_worktree "$P_ALT"
  stage_carrier d
  run_hook
  expect_rc nonzero "clippy sees staged (invalid) content, not worktree"
  assert_log_contains "cargo clippy failed"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  expect_staged_exactly "$CARRIER" "$RS"
}

case_e() {
  echo "=== E: staged valid + Clippy-invalid unstaged worktree ==="
  reset_clone
  mk_rs_candidate "$P_E"
  git_clone add "$RS"
  stage_carrier e
  add_rs_unstaged_tail "$P_BAD_CLIPPY"
  run_hook
  expect_rc 0 "unstaged lint violation does not block the commit"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_f() {
  echo "=== F: staged unformatted + pre-formatted worktree ==="
  reset_clone
  mk_rs_candidate "$P_F_UNF"
  git_clone add "$RS"
  stage_carrier f
  mk_rs_alt_worktree "$P_F_STABLE"
  printf '// f-worktree extra comment\n' >> "$WTX"
  printf '// f-worktree extra comment\n' >> "$CLONE/$RS"
  run_hook
  expect_rc 0 "fmt normalizes the staged content"
  rs_probe_eol_matched "$P_F_STABLE"
  { cat "$RS_HEAD" "$RS_MID" "$RS_TAIL"; } > "$EXP_FMT"
  assert_idx_file "$RS" "$EXP_FMT"
  assert_wt_file "$RS" "$WTX"
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_g() {
  echo "=== G: staged formatted + additional unstaged same-file edits ==="
  reset_clone
  mk_rs_candidate "$P_G"
  git_clone add "$RS"
  stage_carrier g
  add_rs_unstaged_tail '// g-unstaged comment
'
  run_hook
  expect_rc 0 "hook passes; no formatting delta so no re-stage"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_h() {
  echo "=== H: untracked Rust file survives and stays unstaged ==="
  reset_clone
  local u="rust/hesabyar-core/src/orphan_u.rs"
  printf '%s' "$P_A" > "$CLONE/$u"
  stage_carrier h
  run_hook
  expect_rc 0 "hook passes with untracked orphan module present"
  assert_log_contains "No staged Rust sources to format"
  assert_log_contains "cargo clippy passed"
  assert_present_wt "$u"
  contains_staged "$u" && fail "untracked file became staged: $u" \
    || pass "untracked file stayed unstaged"
  expect_commit_exactly "$CARRIER"
  assert_nothing_staged
}

case_i() {
  echo "=== I: staged Rust deletion reaches clippy without breaking fmt ==="
  reset_clone
  local o="rust/hesabyar-core/src/orphan_i.rs"
  printf '%s' "$P_A" > "$CLONE/$o"
  setup_commit_no_verify "$o"
  DIFF_BASE=$(git_clone rev-parse HEAD)
  git_clone rm -q "$o"
  stage_carrier i
  run_hook
  expect_rc 0 "staged deletion excluded from cargo fmt path list"
  assert_log_contains "cargo clippy passed"
  grep -qF "does not exist" "$LOG" && fail "rustfmt saw the deleted path" \
    || pass "rustfmt never received the deleted path"
  commit_deletion_present "$o" && pass "deletion recorded in the commit" \
    || fail "deletion missing from the commit"
  assert_absent_wt "$o"
  expect_commit_exactly "$CARRIER" "$o"
  assert_nothing_staged
}

case_j() {
  echo "=== J: unstaged deletion of tracked Rust file ==="
  reset_clone
  rm "$CLONE/$RS"
  stage_carrier j
  run_hook
  expect_rc 0 "absent dirty file handled without cp failure"
  assert_idx_is_base "$RS"
  assert_absent_wt "$RS"
  expect_commit_exactly "$CARRIER"
  assert_nothing_staged
}

case_k() {
  echo "=== K: staged Cargo.toml + broken unstaged Cargo.toml ==="
  reset_clone
  cp "$CLONE/$CARGO_TOML" "$EXP"
  printf '# k-probe staged\n' >> "$EXP"
  cp "$EXP" "$WTX"
  printf 'broken-k-probe = = =\n' >> "$WTX"
  cp "$EXP" "$CLONE/$CARGO_TOML"
  git_clone add "$CARGO_TOML"
  cp "$WTX" "$CLONE/$CARGO_TOML"
  stage_carrier k
  run_hook
  expect_rc 0 "clippy parsed the index manifest, not the broken worktree copy"
  assert_idx_file "$CARGO_TOML" "$EXP"
  assert_wt_file "$CARGO_TOML" "$WTX"
  expect_commit_exactly "$CARRIER" "$CARGO_TOML"
  assert_nothing_staged
}

case_l() {
  echo "=== L: corrupted unstaged Cargo.lock ==="
  reset_clone
  cp "$CLONE/$CARGO_LOCK" "$WTX"
  printf '[[package]]\nname = "probe-lock-corruption"\n' >> "$WTX"
  cp "$WTX" "$CLONE/$CARGO_LOCK"
  stage_carrier l
  run_hook
  expect_rc 0 "lockfile materialized from the index for validation"
  assert_idx_is_base "$CARGO_LOCK"
  assert_wt_file "$CARGO_LOCK" "$WTX"
  expect_commit_exactly "$CARRIER"
  assert_nothing_staged
}

case_m() {
  echo "=== M: cargo fmt fails on staged syntax error ==="
  reset_clone
  mk_rs_candidate "$P_PARSE"
  git_clone add "$RS"
  stage_carrier m
  add_rs_unstaged_tail '// m-extra unstaged comment
'
  run_hook
  expect_rc nonzero "parse error in staged content fails cargo fmt"
  assert_log_contains "cargo fmt failed"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  expect_staged_exactly "$CARRIER" "$RS"
}

case_n() {
  echo "=== N: cargo clippy fails on staged lint ==="
  reset_clone
  mk_rs_candidate "$P_BAD_N"
  git_clone add "$RS"
  stage_carrier n
  run_hook
  expect_rc nonzero "-D warnings denies the staged lint"
  assert_log_contains "cargo clippy failed"
  assert_idx_file "$RS" "$EXP"
  expect_staged_exactly "$CARRIER" "$RS"
}

case_o() {
  echo "=== O: full restoration after fmt failure (multi-input + untracked) ==="
  reset_clone
  mk_rs_candidate "$P_PARSE"
  git_clone add "$RS"
  add_rs_unstaged_tail '// o-extra unstaged
'
  local toml_exp="$WORK/o_toml_idx.bin" toml_wt="$WORK/o_toml_wt.bin"
  cp "$CLONE/$CARGO_TOML" "$toml_exp"
  printf '# o-probe staged\n' >> "$toml_exp"
  cp "$toml_exp" "$toml_wt"
  printf 'broken-o-probe = = =\n' >> "$toml_wt"
  cp "$toml_exp" "$CLONE/$CARGO_TOML"
  git_clone add "$CARGO_TOML"
  cp "$toml_wt" "$CLONE/$CARGO_TOML"
  local u="rust/hesabyar-core/src/orphan_o.rs"
  printf '%s' "$P_A" > "$CLONE/$u"
  stage_carrier o
  run_hook
  expect_rc nonzero "fmt failure aborts the commit"
  assert_wt_file "$RS" "$WTX"
  assert_wt_file "$CARGO_TOML" "$toml_wt"
  assert_idx_file "$CARGO_TOML" "$toml_exp"
  assert_present_wt "$u"
  expect_staged_exactly "$CARRIER" "$RS" "$CARGO_TOML"
}

case_p() {
  echo "=== P: Kotlin-only commit skips Rust fmt but keeps clippy gate ==="
  reset_clone
  stage_carrier p
  run_hook
  expect_rc 0 "Kotlin-only commit passes"
  assert_log_contains "No staged Rust sources to format"
  assert_log_contains "cargo clippy passed"
  local dirty_rust
  dirty_rust=$(git_clone status --porcelain -- rust/)
  [[ -z "$dirty_rust" ]] && pass "rust/ untouched" \
    || fail "rust/ modified by Kotlin-only run: $dirty_rust"
  expect_commit_exactly "$CARRIER"
  assert_nothing_staged
}

case_q() {
  echo "=== Q: missing rust/ directory hard-fails with hint ==="
  reset_clone
  mv "$CLONE/rust" "$CLONE/rust_hidden_probe"
  stage_carrier q
  run_hook
  mv "$CLONE/rust_hidden_probe" "$CLONE/rust"
  expect_rc nonzero "missing workspace fails fast"
  assert_log_contains "rust/"
  assert_log_contains "not found"
}

case_r() {
  echo "=== R: unusual filename (spaces, brackets, parens) ==="
  reset_clone
  local name="odd name (v1) [ok].rs"
  local p="rust/hesabyar-core/src/$name"
  printf '%s' "$P_A" > "$CLONE/$p"
  stage_carrier r1
  run_hook
  expect_rc 0 "untracked odd-named file passes and survives"
  assert_present_wt "$p"
  contains_staged "$p" && fail "odd-named untracked became staged" \
    || pass "odd-named untracked stayed unstaged"
  expect_commit_exactly "$CARRIER"
  reset_clone
  printf '%s' "$P_A" > "$CLONE/$p"
  setup_commit_no_verify "$p"
  printf '%s' "$P_ALT" > "$CLONE/$p"
  git_clone add "$p"
  cp "$CLONE/$p" "$EXP"
  stage_carrier r2
  run_hook
  expect_rc 0 "tracked odd-named file stages cleanly"
  assert_idx_file "$p" "$EXP"
  assert_wt_file "$p" "$EXP"
  expect_commit_exactly "$CARRIER" "$p"
  assert_nothing_staged
}

run_odd_name_case() { # $1 label, $2 raw filename
  reset_clone
  local p="rust/hesabyar-core/src/$2"
  if ! mkdir -p "$(dirname "$CLONE/$p")" 2>/dev/null \
     || ! printf '%s' "$P_A" > "$CLONE/$p" 2>/dev/null || [[ ! -f "$CLONE/$p" ]]; then
    skip "$1: filesystem refuses this filename on Windows"
    return 0
  fi
  printf '%s' "$P_A" > "$EXP"
  stage_carrier "$1"
  run_hook
  expect_rc 0 "$1: hook passes"
  assert_present_wt "$p"
  assert_wt_file "$p" "$EXP"
  contains_staged "$p" && fail "$1: untracked became staged" \
    || pass "$1: untracked stayed unstaged"
  expect_commit_exactly "$CARRIER"
  rm -f "$CLONE/$p"
  return 0
}

case_s() {
  echo "=== S: tab in filename ==="
  run_odd_name_case "tab-name" "$(printf 'tab\tname.rs')"
}

case_t() {
  echo "=== T: newline in filename (best effort) ==="
  run_odd_name_case "newline-name" "$(printf 'nl\nname.rs')"
}

case_u() {
  echo "=== U: failing Kotlin gate blocks every Rust gate ==="
  reset_clone
  printf '#!/usr/bin/env bash\nexit 1\n' > "$CLONE/gradlew"
  chmod +x "$CLONE/gradlew"
  rm -f "$CARGO_PROBE_LOG"
  mk_rs_candidate "$P_A"
  git_clone add "$RS"
  stage_carrier u
  local saved_path=$PATH
  export PATH="$CARGO_SHIM_DIR:$PATH"
  run_hook
  export PATH=$saved_path
  expect_rc nonzero "commit aborted by the Kotlin gate failure"
  assert_log_contains "ktlintFormat failed"
  grep -qF "[4/5]" "$LOG" && fail "hook reached the Rust gates after a Kotlin failure" \
    || pass "hook stopped before the Rust gates"
  if [[ -f "$CARGO_PROBE_LOG" ]]; then
    fail "cargo ran despite the Kotlin gate failure: $(cat "$CARGO_PROBE_LOG")"
  else
    pass "cargo never executed (probe log untouched)"
  fi
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$EXP"
  expect_staged_exactly "$CARRIER" "$RS"
}

# Can this host represent a chmod on a .rs file that bash and Git can see?
# NTFS under Git Bash cannot: chmod there is a silent no-op, so cases V and W
# report SKIP instead of faking the scenario.
host_represents_chmod() {
  local pf="$WORK/mode-capability.rs"
  : > "$pf"
  chmod +x "$pf"
  if [[ ! -x "$pf" ]]; then
    rm -f "$pf"
    return 1
  fi
  rm -f "$pf"
  return 0
}

# Print a path's permission bits as octal digits, or an empty string when no
# stat variant can read them. GNU stat accepts -c; BSD and macOS accept -f.
read_mode() {
  local m
  if m=$(stat -c '%a' -- "$1" 2>/dev/null) && [[ -n "$m" ]]; then
    printf '%s' "$m"
  elif m=$(stat -f '%Lp' -- "$1" 2>/dev/null) && [[ -n "$m" ]]; then
    printf '%s' "$m"
  fi
}

# Can this host represent a nonstandard permission mode such as 0740 on a
# file that bash can read back? NTFS under Git Bash cannot: chmod there is a
# silent no-op and stat reads back the old mode. Cases X and Y report SKIP
# instead of faking the scenario.
host_represents_full_modes() {
  local pf="$WORK/mode-capability-full.rs" m
  : > "$pf"
  chmod 740 "$pf" 2>/dev/null || { rm -f "$pf"; return 1; }
  m=$(read_mode "$pf")
  rm -f "$pf"
  [[ "$m" == "740" ]]
}

case_v() {
  echo "=== V: unstaged worktree mode change survives materialization ==="
  host_represents_chmod || { skip "V: host cannot represent chmod on .rs files"; return 0; }
  git_clone config core.fileMode true
  reset_clone
  mk_rs_candidate "$P_A"
  git_clone add "$RS"
  stage_carrier v
  chmod +x "$CLONE/$RS"
  cp "$EXP" "$WTX"
  run_hook
  expect_rc 0 "hook passes with an unstaged mode change present"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  [[ -x "$CLONE/$RS" ]] && pass "original worktree mode (+x) restored" \
    || fail "worktree executable bit lost by materialization"
  local idx_mode
  idx_mode=$(git_clone ls-files -s -- "$RS" | awk '{print $1}')
  [[ "$idx_mode" == "100644" ]] && pass "index mode untouched (100644)" \
    || fail "index mode changed: $idx_mode"
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_w() {
  echo "=== W: worktree mode restored even when clippy fails ==="
  host_represents_chmod || { skip "W: host cannot represent chmod on .rs files"; return 0; }
  git_clone config core.fileMode true
  reset_clone
  mk_rs_candidate "$P_BAD_N"
  git_clone add "$RS"
  stage_carrier w
  chmod +x "$CLONE/$RS"
  cp "$EXP" "$WTX"
  run_hook
  expect_rc nonzero "clippy failure aborts the commit"
  assert_log_contains "cargo clippy failed"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  [[ -x "$CLONE/$RS" ]] && pass "mode restored through the failure path" \
    || fail "mode lost on the clippy failure path"
  expect_staged_exactly "$CARRIER" "$RS"
}

# Shared body for cases X and Y: stage one candidate, leave a DIFFERENT
# unstaged worktree version behind, and stamp a nonstandard permission mode
# on it. This is stricter than V/W: restore must reproduce every bit, not
# just an execute class, and the committed bytes must be the staged
# candidate rather than the unstaged worktree version.
run_full_mode_scenario() { # <probe-text> <clippy-must-fail:0|1> <mode>
  local probe="$1" want_fail="$2" want_mode="$3" got_mode idx_mode head_oid exp_oid
  reset_clone
  mk_rs_candidate "$probe"
  git_clone add "$RS"
  stage_carrier x
  mk_rs_alt_worktree "$P_ALT"
  chmod "$want_mode" "$CLONE/$RS"
  run_hook
  if [[ $want_fail == 1 ]]; then
    expect_rc nonzero "clippy failure aborts the commit (mode $want_mode)"
    assert_log_contains "cargo clippy failed"
    expect_staged_exactly "$CARRIER" "$RS"
  else
    expect_rc 0 "hook passes with unstaged edit and mode $want_mode present"
    expect_commit_exactly "$CARRIER" "$RS"
    assert_nothing_staged
    head_oid=$(git_clone rev-parse "HEAD:$RS")
    exp_oid=$(git_clone hash-object --path="$RS" "$EXP")
    [[ "$head_oid" == "$exp_oid" ]] \
      && pass "commit recorded the staged candidate, not the worktree copy" \
      || fail "commit recorded wrong Rust content"
  fi
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  got_mode=$(read_mode "$CLONE/$RS")
  [[ "$got_mode" == "$want_mode" ]] \
    && pass "exact worktree mode $want_mode restored (got $got_mode)" \
    || fail "worktree mode wrong: wanted $want_mode, got $got_mode"
  idx_mode=$(git_clone ls-files -s -- "$RS" | awk '{print $1}')
  [[ "$idx_mode" == "100644" ]] && pass "index mode untouched (100644)" \
    || fail "index mode changed: $idx_mode"
}

case_x() {
  echo "=== X: unstaged nonstandard modes survive materialization ==="
  host_represents_full_modes || { skip "X: host cannot represent nonstandard modes"; return 0; }
  git_clone config core.fileMode true
  local m
  for m in 740 750 710; do
    echo "  --- mode $m ---"
    run_full_mode_scenario "$P_A" 0 "$m"
  done
}

case_y() {
  echo "=== Y: exact nonstandard modes restored even when clippy fails ==="
  host_represents_full_modes || { skip "Y: host cannot represent nonstandard modes"; return 0; }
  git_clone config core.fileMode true
  local m
  for m in 740 750 710; do
    echo "  --- mode $m ---"
    run_full_mode_scenario "$P_BAD_N" 1 "$m"
  done
}

case_z() {
  echo "=== Z: unusable stat aborts the hook before materialization ==="
  reset_clone
  mk_rs_candidate "$P_A"
  git_clone add "$RS"
  stage_carrier z
  mk_rs_alt_worktree "$P_ALT"
  local mode_before mode_after
  mode_before=$(read_mode "$CLONE/$RS")
  rm -f "$CARGO_PROBE_LOG" "$GIT_PROBE_LOG"
  local saved_path=$PATH
  export PATH="$STAT_SHIM_DIR:$GIT_SHIM_DIR:$PATH"
  run_hook
  export PATH=$saved_path
  expect_rc nonzero "hook aborts when no stat can capture a mode"
  assert_log_contains "Cannot capture file mode"
  grep -qF "[4/5]" "$LOG" && fail "hook reached the Rust gates despite mode-capture failure" \
    || pass "hook stopped before cargo fmt materialized the tree"
  if [[ -f "$CARGO_PROBE_LOG" ]]; then
    fail "cargo ran despite the mode-capture failure: $(cat "$CARGO_PROBE_LOG")"
  else
    pass "cargo never executed (probe log untouched)"
  fi
  # The git wrapper must be engaged (its own `git commit` is logged) and it
  # must never have logged checkout-index. Final-state checks alone cannot
  # tell a clean abort from a transient materialization plus restore.
  if grep -q "^git commit" "$GIT_PROBE_LOG" 2>/dev/null; then
    pass "git wrapper probe engaged during the aborted run"
  else
    fail "git wrapper probe never logged an invocation (instrumentation broken)"
  fi
  if grep -q "checkout-index" "$GIT_PROBE_LOG" 2>/dev/null; then
    fail "checkout-index ran despite the mode-capture failure: $(grep checkout-index "$GIT_PROBE_LOG" | head -1)"
  else
    pass "git checkout-index never invoked before the abort"
  fi
  expect_staged_exactly "$CARRIER" "$RS"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  mode_after=$(read_mode "$CLONE/$RS")
  [[ "$mode_after" == "$mode_before" ]] && pass "worktree mode untouched by the aborted run" \
    || fail "worktree mode changed: wanted $mode_before, got $mode_after"
}

case_e1_static_mode_restore_wiring() {
  echo "=== E1: hook source wires full-mode capture and restore ==="
  reset_clone
  local src="$SRC/scripts/pre-commit"
  if grep -qF 'declare -A RUST_DIRTY_MODES' "$src" \
     && grep -qF 'if ! mode=$(capture_file_mode "$f"); then' "$src" \
     && grep -qF "stat -c '%a'" "$src" \
     && grep -qF "stat -f '%Lp'" "$src" \
     && grep -qF 'chmod "$m" "$root/$f"' "$src"; then
    pass "full-mode capture/restore calls present in hook source"
  else
    fail "full-mode capture/restore wiring missing from hook source"
  fi
}

case_e2_static_capture_before_materialization() {
  echo "=== E2: mode capture is checked before index materialization ==="
  reset_clone
  local src="$SRC/scripts/pre-commit" cap_ln mat_ln
  cap_ln=$(grep -nF 'capture_file_mode "$f"' "$src" | head -1 | cut -d: -f1)
  mat_ln=$(grep -nF 'git checkout-index -f' "$src" | head -1 | cut -d: -f1)
  if [[ -n "$cap_ln" && -n "$mat_ln" && "$cap_ln" -lt "$mat_ln" ]] \
     && grep -qF 'Cannot capture file mode' "$src"; then
    pass "capture (line $cap_ln) precedes checkout-index (line $mat_ln) and failure aborts"
  else
    fail "capture/materialization order wrong (cap=$cap_ln mat=$mat_ln) or abort message missing"
  fi
}

# --- runner -------------------------------------------------------------------

CASES=(e0_static_no_destructive_ops a b c d e f g h i j k l m n o p q r s t u v w x y z e1_static_mode_restore_wiring e2_static_capture_before_materialization)

for c in "${CASES[@]}"; do
  "case_$c"
done

echo ""
echo "==========================================="
echo "  Results: $PASS passed, $FAIL failed, $SKIP skipped"
echo "==========================================="
if [[ $FAIL -gt 0 ]]; then
  echo "Harness FAILED. Scratch dir: $WORK"
  exit 1
fi
echo "All hook regression cases passed."
