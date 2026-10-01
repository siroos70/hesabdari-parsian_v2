#!/usr/bin/env sh
# Tests for scripts/generate-release-notes.sh (Gemini release notes & resilient fallback).
#
# POSIX-compatible: runs with bash, ash/busybox, or dash. Requires git.
# Usage: ./scripts/tests/test-generate-release-notes.sh
set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GENERATE="$HERE/../generate-release-notes.sh"
TMP=$(mktemp -d)
trap 'cd "$HERE" && rm -rf "$TMP" 2>/dev/null || true' EXIT

pass=0
fail=0
test_id=0

new_repo() {
  test_id=$((test_id + 1))
  REPO="$TMP/repo-$test_id"
  mkdir -p "$REPO"
  cd "$REPO"
  git init -q .
  git symbolic-ref HEAD refs/heads/main
  git config user.email test@example.com
  git config user.name test
  git config commit.gpgsign false
  git config core.hooksPath /dev/null
  echo "init" > README.md
  git add -A
  git commit -qm "initial commit"
}

# --- Test 1: Fallback when GEMINI_API_KEY is unset ---
new_repo
git checkout -q -b feat-branch main
echo "feature" > feat.txt; git add feat.txt; git commit -qm "feat: add export feature"
echo "bugfix" > fix.txt; git add fix.txt; git commit -qm "fix: resolve sync crash"
echo "chore" > chore.txt; git add chore.txt; git commit -qm "perf: speed up query"

stdout_file="$TMP/t1_stdout.md"
stderr_file="$TMP/t1_stderr.log"

set +e
GEMINI_API_KEY="" bash "$GENERATE" "0.8.0" "main" "HEAD" > "$stdout_file" 2> "$stderr_file"
t1_exit=$?
set -e

if [ "$t1_exit" -eq 0 ]; then
  echo "PASS generator exits with status 0 on missing key"
  pass=$((pass + 1))
else
  echo "FAIL generator exited with non-zero status $t1_exit"
  fail=$((fail + 1))
fi

stdout_content=$(cat "$stdout_file")
stderr_content=$(cat "$stderr_file")

case "$stdout_content" in
  *"نسخه 0.8.0 حساب‌یار منتشر شد."*"### امکانات جدید"*"* feat: add export feature"*"### رفع مشکلات"*"* fix: resolve sync crash"*"### بهبودها و تغییرات"*"* perf: speed up query"*)
    echo "PASS fallback generates structured Persian notes"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL fallback output structure invalid: $stdout_content"
    fail=$((fail + 1))
    ;;
esac

# Assert commit short-hash suffix was stripped
case "$stdout_content" in
  *"feat: add export feature ("*)
    echo "FAIL commit hash was not stripped: $stdout_content"
    fail=$((fail + 1))
    ;;
  *)
    echo "PASS commit hash stripped from fallback bullet"
    pass=$((pass + 1))
    ;;
esac

# Assert stdout does NOT contain WARNING lines
case "$stdout_content" in
  *"WARNING:"*)
    echo "FAIL stdout contains WARNING (should be in stderr): $stdout_content"
    fail=$((fail + 1))
    ;;
  *)
    echo "PASS stdout does not contain WARNING logs"
    pass=$((pass + 1))
    ;;
esac

# Assert stderr contains the warning
case "$stderr_content" in
  *"WARNING:"*)
    echo "PASS stderr contains warning"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL stderr missing expected warning: $stderr_content"
    fail=$((fail + 1))
    ;;
esac

# --- Test 2: Resilient fallback when API call fails (mocked curl) ---
new_repo
git checkout -q -b fail-branch main
echo "feature" > feat.txt; git add feat.txt; git commit -qm "feat: new dashboard"

mock_bin="$TMP/mock_bin"
mkdir -p "$mock_bin"
cat << 'EOF' > "$mock_bin/curl"
#!/usr/bin/env sh
# Mock curl returning HTTP 503 to simulate transient service overload deterministically
printf '{"error": {"code": 503, "message": "The model is overloaded. Please try again later."}}\n503'
EOF
chmod +x "$mock_bin/curl"

t2_stdout="$TMP/t2_stdout.md"
t2_stderr="$TMP/t2_stderr.log"

set +e
PATH="$mock_bin:$PATH" GEMINI_API_KEY="test-key" GEMINI_MODEL="gemini-2.0-flash" \
  bash "$GENERATE" "0.8.0" "main" "HEAD" > "$t2_stdout" 2> "$t2_stderr"
t2_exit=$?
set -e

if [ "$t2_exit" -eq 0 ]; then
  echo "PASS generator exits with status 0 on API fallback"
  pass=$((pass + 1))
else
  echo "FAIL generator exited with non-zero status $t2_exit"
  fail=$((fail + 1))
fi

t2_out=$(cat "$t2_stdout")
t2_err=$(cat "$t2_stderr")

case "$t2_out" in
  *"نسخه 0.8.0 حساب‌یار منتشر شد."*"### امکانات جدید"*"* feat: new dashboard"*)
    echo "PASS API failure triggers resilient fallback"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL API failure fallback invalid: $t2_out"
    fail=$((fail + 1))
    ;;
esac

case "$t2_out" in
  *"WARNING:"*)
    echo "FAIL stdout on API failure contains WARNING: $t2_out"
    fail=$((fail + 1))
    ;;
  *)
    echo "PASS stdout clean on API failure"
    pass=$((pass + 1))
    ;;
esac

case "$t2_err" in
  *"WARNING: All Gemini API attempts failed"*|*"Attempting release note generation"*)
    echo "PASS stderr records retry and fallback diagnostics"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL stderr missing expected diagnostics: $t2_err"
    fail=$((fail + 1))
    ;;
esac

# --- Test 3: Merge commits are excluded from fallback notes ---
new_repo
git checkout -q -b merge-branch main
echo "feature" > feat.txt; git add feat.txt; git commit -qm "feat: bank loan module"
git commit --allow-empty -qm "Merge pull request #100 from user/branch"

t3_stdout="$TMP/t3_stdout.md"
t3_stderr="$TMP/t3_stderr.log"

set +e
GEMINI_API_KEY="" bash "$GENERATE" "0.8.0" "main" "HEAD" > "$t3_stdout" 2> "$t3_stderr"
t3_exit=$?
set -e

if [ "$t3_exit" -eq 0 ]; then
  echo "PASS generator exits with status 0 on merge commit test"
  pass=$((pass + 1))
else
  echo "FAIL generator exited with non-zero status $t3_exit"
  fail=$((fail + 1))
fi

t3_out=$(cat "$t3_stdout")

case "$t3_out" in
  *"Merge pull request"*)
    echo "FAIL fallback included merge commit: $t3_out"
    fail=$((fail + 1))
    ;;
  *"* feat: bank loan module"*)
    echo "PASS merge commits excluded from fallback notes"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL expected feat commit in fallback notes: $t3_out"
    fail=$((fail + 1))
    ;;
esac

# --- Test 4: Successful API response output ---
new_repo
git checkout -q -b api-success-branch main
echo "feature" > feat.txt; git add feat.txt; git commit -qm "feat: new AI features"

mock_bin_success="$TMP/mock_bin_success"
mkdir -p "$mock_bin_success"
cat << 'EOF' > "$mock_bin_success/curl"
#!/usr/bin/env sh
printf '{"candidates": [{"content": {"parts": [{"text": "نسخه 0.8.0 حساب‌یار با موفقیت تولید شد."}]}}]}\n200'
EOF
chmod +x "$mock_bin_success/curl"

t4_stdout="$TMP/t4_stdout.md"
t4_stderr="$TMP/t4_stderr.log"

set +e
PATH="$mock_bin_success:$PATH" GEMINI_API_KEY="test-valid-key" bash "$GENERATE" "0.8.0" "main" "HEAD" > "$t4_stdout" 2> "$t4_stderr"
t4_exit=$?
set -e

if [ "$t4_exit" -eq 0 ]; then
  echo "PASS generator exits with status 0 on successful API response"
  pass=$((pass + 1))
else
  echo "FAIL generator failed on successful API response with exit $t4_exit"
  fail=$((fail + 1))
fi

t4_out=$(cat "$t4_stdout")
t4_err=$(cat "$t4_stderr")

if [ "$t4_out" = "نسخه 0.8.0 حساب‌یار با موفقیت تولید شد." ]; then
  echo "PASS valid AI response emitted cleanly to stdout"
  pass=$((pass + 1))
else
  echo "FAIL unexpected stdout on successful API response: $t4_out"
  fail=$((fail + 1))
fi

case "$t4_err" in
  *"Attempting release note generation with Gemini model"*)
    echo "PASS stderr records model attempt in success test"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL stderr missing model attempt: $t4_err"
    fail=$((fail + 1))
    ;;
esac

# --- Test 5: Model fallback when primary model returns empty content ---
new_repo
git checkout -q -b model-fallback-branch main
echo "feature" > feat.txt; git add feat.txt; git commit -qm "feat: resilient models"

mock_bin_fb="$TMP/mock_bin_fb"
mkdir -p "$mock_bin_fb"
cat << 'EOF' > "$mock_bin_fb/curl"
#!/usr/bin/env sh
case "$*" in
  *"primary-test-model"*)
    # Simulate blocked or empty candidate from primary model
    printf '{"candidates": [{"content": {"parts": []}}]}\n200'
    ;;
  *)
    # Fallback model succeeds
    printf '{"candidates": [{"content": {"parts": [{"text": "یادداشت مدل جایگزین"}]}}]}\n200'
    ;;
esac
EOF
chmod +x "$mock_bin_fb/curl"

t5_stdout="$TMP/t5_stdout.md"
t5_stderr="$TMP/t5_stderr.log"

set +e
PATH="$mock_bin_fb:$PATH" GEMINI_API_KEY="test-key" GEMINI_MODEL="primary-test-model" \
  bash "$GENERATE" "0.8.0" "main" "HEAD" > "$t5_stdout" 2> "$t5_stderr"
t5_exit=$?
set -e

if [ "$t5_exit" -eq 0 ]; then
  echo "PASS generator exits with status 0 on model fallback"
  pass=$((pass + 1))
else
  echo "FAIL generator failed on model fallback with exit $t5_exit"
  fail=$((fail + 1))
fi

t5_out=$(cat "$t5_stdout")
t5_err=$(cat "$t5_stderr")

if [ "$t5_out" = "یادداشت مدل جایگزین" ]; then
  echo "PASS model fallback produces output when primary model candidate is empty"
  pass=$((pass + 1))
else
  echo "FAIL unexpected stdout on model fallback: $t5_out"
  fail=$((fail + 1))
fi

case "$t5_err" in
  *"contained no extractable text, trying next model"*)
    echo "PASS stderr records fallback model transition"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL stderr missing fallback transition notice: $t5_err"
    fail=$((fail + 1))
    ;;
esac

echo ""
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
