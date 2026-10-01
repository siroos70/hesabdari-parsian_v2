#!/usr/bin/env bash
#
# Regression tests for the issue auto-close pattern.
#
# The pattern lives in .github/workflows/ai-issue-os.yml. This script
# extracts it from the workflow file and runs it. The workflow YAML
# cannot run under unit test, and the regex must not be duplicated
# here. Extraction keeps one single source of truth.
#
# Usage: scripts/test-issue-autoclose.sh   (run from repo root or anywhere)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="${REPO_ROOT}/.github/workflows/ai-issue-os.yml"

if [ ! -f "$WORKFLOW" ]; then
  echo "FAIL: workflow not found: ${WORKFLOW}" >&2
  exit 1
fi

# Extract the patterns from the workflow step.
LOOSE_CHAIN=$(sed -n "s/^.*LOOSE_CHAIN='\(.*\)'$/\1/p" "$WORKFLOW")
PER_NUM_TEMPLATE=$(sed -n "s/^.*PER_NUM_TEMPLATE='\(.*\)'$/\1/p" "$WORKFLOW")

if [ -z "$LOOSE_CHAIN" ] || [ -z "$PER_NUM_TEMPLATE" ]; then
  echo "FAIL: could not extract chain patterns from ${WORKFLOW}" >&2
  exit 1
fi

# Decide whether a commit message closes a given issue number.
# This mirrors both filters of the auto_close_resolved job in order:
# first the LOOSE_CHAIN history filter (git log --grep runs it against
# each raw message line, case-insensitive), then the per-number check
# on the newline-flattened message.
issue_closed_by() {
  local msg="$1" num="$2"
  local flat pattern
  printf '%s\n' "$msg" | grep -qiE "${LOOSE_CHAIN}" || return 1
  flat=$(printf '%s' "$msg" | tr '\n' ' ')
  printf '%s' "$flat" | grep -qE '#[0-9]+' || return 1
  pattern="${PER_NUM_TEMPLATE//\$\{NUM\}/${num}}"
  printf '%s\n' "$flat" | grep -qiE "${pattern}"
}

PASS=0
FAIL=0

check() {
  local expect="$1" num="$2" msg="$3"
  local got label status
  if issue_closed_by "$msg" "$num"; then got=CLOSE; else got=SKIP; fi
  if [ "$got" = "$expect" ]; then
    status=PASS
    PASS=$((PASS + 1))
  else
    status=FAIL
    FAIL=$((FAIL + 1))
  fi
  printf '%s  expect=%-5s got=%-5s  #%-*s %s\n' \
    "$status" "$expect" "$got" 4 "$num" "$msg"
}

echo "=== Positive: plain GitHub-style closing forms (target #40) ==="
check CLOSE 40 "fix #40"
check CLOSE 40 "fixes #40"
check CLOSE 40 "fixed #40"
check CLOSE 40 "close #40"
check CLOSE 40 "closes #40"
check CLOSE 40 "closed #40"
check CLOSE 40 "resolve #40"
check CLOSE 40 "resolves #40"
check CLOSE 40 "resolved #40"
check CLOSE 40 "patch #40"
check CLOSE 40 "patches #40"
check CLOSE 40 "patched #40"

echo
echo "=== Positive: casing, position, and list forms ==="
check CLOSE 40 "Fix #40"
check CLOSE 40 "feat: add X (fixes #40)"
check CLOSE 40 "fixes #40, update docs"
check CLOSE 40 "resolves issue #40"
check CLOSE 36 "resolve issues #36, #21, #20, #19"
check CLOSE 21 "resolve issues #36, #21, #20, #19"
check CLOSE 19 "resolve issues #36, #21, #20, #19"
check SKIP 40 "resolve issues #36, #21, #20, #19"
check CLOSE 40 "resolves #12, #40"
check CLOSE 40 "fixes #12, closes #40"

echo
echo "=== Negative: verb embedded inside another word ==="
check SKIP 40 "unfixed #40"
check SKIP 40 "discloses #40"
check SKIP 40 "hotfixes #40"
check SKIP 40 "prefixes #40"

echo
echo "=== Negative: letters right after the issue number ==="
check SKIP 40 "fix #40abc"
check SKIP 40 "fixes #40x"

echo
echo "=== Negative: partial issue-number matching ==="
check SKIP 40 "fix #400"
check SKIP 400 "fix #40"

echo
echo "=== Negative: mention without a closing verb ==="
check SKIP 40 "see #40 for context"
check SKIP 40 "updated README, discussed approach in #40"
check SKIP 40 "improved logging related to #40"
check SKIP 40 "reworked module, refs #40"
check SKIP 40 "issue #40 is a duplicate of #41"
check SKIP 40 "fixes #36, see also #40"

echo
echo "=== Multi-line message bodies ==="
check CLOSE 40 "$(printf 'feat: rework parser\n\nfixes #40')"
check SKIP 40 "$(printf 'chore: cleanup\n\nsee #40')"

echo
echo "=== Result: ${PASS} passed, ${FAIL} failed ==="
[ "$FAIL" -eq 0 ]
