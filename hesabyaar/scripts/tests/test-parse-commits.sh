#!/usr/bin/env sh
# Tests for scripts/parse-commits.sh (conventional commit parsing & version bump type).
#
# POSIX-compatible: runs with bash, ash/busybox, or dash. Requires git.
# Usage: ./scripts/tests/test-parse-commits.sh
set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PARSE="$HERE/../parse-commits.sh"
TMP=$(mktemp -d)
trap 'cd "$HERE" && rm -rf "$TMP" 2>/dev/null || true' EXIT

pass=0
fail=0
test_id=0

# Detect available Python interpreter
PYTHON_CMD=""
if python3 --version >/dev/null 2>&1; then
  PYTHON_CMD="python3"
elif py -3 --version >/dev/null 2>&1; then
  PYTHON_CMD="py -3"
fi

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

parse_json_field() {
  json_input="$1"
  field_name="$2"
  res=""
  if command -v jq >/dev/null 2>&1; then
    res=$(printf '%s' "$json_input" | jq -r --arg f "$field_name" '.[$f]')
  elif [ -n "$PYTHON_CMD" ]; then
    res=$(printf '%s' "$json_input" | $PYTHON_CMD -c "import sys, json; sys.stdout.write(json.load(sys.stdin).get('$field_name', ''))")
  else
    raw=$(printf '%s' "$json_input" | sed -n 's/.*"'"$field_name"'":"\([^"]*\)".*/\1/p')
    res=$(printf '%b' "$raw")
  fi
  printf '%s' "$res" | tr -d '\r'
}

check() {
  name=$1
  want_bump=$2
  want_summary_sub=$3

  set +e
  output=$(bash "$PARSE" main HEAD)
  exit_status=$?
  set -e

  if [ "$exit_status" -ne 0 ]; then
    echo "FAIL $name (parse-commits exited with status $exit_status)"
    fail=$((fail + 1))
    return
  fi

  got_bump=$(parse_json_field "$output" "bump_type")
  got_summary=$(parse_json_field "$output" "summary")

  if [ "$got_bump" != "$want_bump" ]; then
    echo "FAIL $name: expected bump_type='$want_bump', got '$got_bump'"
    fail=$((fail + 1))
    return
  fi

  case "$got_summary" in
    *"$want_summary_sub"*)
      echo "PASS $name (bump=$got_bump)"
      pass=$((pass + 1))
      ;;
    *)
      echo "FAIL $name: expected summary to contain '$want_summary_sub', got: $got_summary"
      fail=$((fail + 1))
      ;;
  esac
}

# --- Test 1: feat commit gives minor bump and bulleted summary ---
new_repo
git checkout -q -b feat-branch main
echo "feature" > feat.txt
git add feat.txt
git commit -qm "feat: add personal loan ledger"
check "feat commit yields minor bump" "minor" "- feat: add personal loan ledger"

# --- Test 2: fix commit gives patch bump ---
new_repo
git checkout -q -b fix-branch main
echo "fix" > fix.txt
git add fix.txt
git commit -qm "fix: correct loan interest calculation"
check "fix commit yields patch bump" "patch" "- fix: correct loan interest calculation"

# --- Test 3: breaking change in title yields major bump ---
new_repo
git checkout -q -b break-branch main
echo "break" > break.txt
git add break.txt
git commit -qm "feat!: redesign database schema"
check "breaking change title yields major bump" "major" "- feat!: redesign database schema"

# --- Test 4: breaking change in body yields major bump ---
new_repo
git checkout -q -b break-body-branch main
echo "break" > break.txt
git add break.txt
git commit -qm "refactor: reorganize database models" -m "BREAKING CHANGE: drops legacy v1 table"
check "breaking change body yields major bump" "major" "- refactor: reorganize database models"

# --- Test 5: merge commits (real 2-parent merges) are excluded from summary ---
new_repo
git checkout -q -b feat-branch main
echo "work" > work.txt
git add work.txt
git commit -qm "feat: add export feature"
# Create a side branch and make a real 2-parent merge commit
git checkout -q -b side-branch main
echo "side" > side.txt
git add side.txt
git commit -qm "chore: side work"
git checkout -q feat-branch
git merge -q --no-ff -m "Merge branch 'side-branch' into feat-branch" side-branch

output=$(bash "$PARSE" main HEAD)
summary=$(parse_json_field "$output" "summary")

case "$summary" in
  *"Merge branch"*|*"Merge pull request"*)
    echo "FAIL merge commit was included in summary: $summary"
    fail=$((fail + 1))
    ;;
  *"- feat: add export feature"*)
    echo "PASS merge commits excluded from summary"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL expected summary to contain feat commit: $summary"
    fail=$((fail + 1))
    ;;
esac

# --- Test 6: bullet format preservation with exact newline assertion ---
new_repo
git checkout -q -b multi-branch main
echo "1" > 1.txt; git add 1.txt; git commit -qm "feat: first feature"
echo "2" > 2.txt; git add 2.txt; git commit -qm "fix: second fix"
output=$(bash "$PARSE" main HEAD)
summary=$(parse_json_field "$output" "summary")

expected_summary="- fix: second fix
- feat: first feature"

if [ "$summary" = "$expected_summary" ]; then
  echo "PASS multi-commit bullet list preserved with exact newlines"
  pass=$((pass + 1))
else
  echo "FAIL multi-commit bullet list not preserved: $summary"
  fail=$((fail + 1))
fi

echo ""
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
