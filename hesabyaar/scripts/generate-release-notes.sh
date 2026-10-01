#!/usr/bin/env bash
set -euo pipefail

# generate-release-notes.sh - Uses Gemini to generate human-friendly release notes.
#
# Usage: ./scripts/generate-release-notes.sh <version> <base_ref> <head_ref>
# Requires GEMINI_API_KEY environment variable.

VERSION="${1:?Usage: generate-release-notes.sh <version> <base_ref> <head_ref>}"
BASE_REF="${2:?Usage: generate-release-notes.sh <version> <base_ref> <head_ref>}"
HEAD_REF="${3:-HEAD}"
GEMINI_API_KEY="${GEMINI_API_KEY:-}"
GEMINI_MODEL="${GEMINI_MODEL:-gemini-2.0-flash}"

# Collect commit messages
commits=$(git log --pretty=format:"- %s (%h)" "$BASE_REF".."$HEAD_REF" 2>/dev/null || \
          git log --pretty=format:"- %s (%h)" "$BASE_REF"..."$HEAD_REF" 2>/dev/null || echo "- Release $VERSION")

# Collect changed files
changed_files=$(git diff --name-only "$BASE_REF".."$HEAD_REF" 2>/dev/null || echo "")

generate_fallback_notes() {
  local feat_lines=""
  local fix_lines=""
  local other_lines=""
  local feat_count=0
  local fix_count=0
  local other_count=0
  local max_bullets=5

  while IFS= read -r line; do
    [ -z "$line" ] && continue
    local msg="${line#- }"
    case "$msg" in
      "Merge "*) continue ;;
    esac
    # Strip trailing commit short-hash suffix like " (8fa7dc0)"
    msg="${msg% (*)}"

    case "$msg" in
      feat*|Feat*)
        if [ "$feat_count" -lt "$max_bullets" ]; then
          feat_lines="${feat_lines}* ${msg}"$'\n'
          feat_count=$((feat_count + 1))
        fi
        ;;
      fix*|Fix*)
        if [ "$fix_count" -lt "$max_bullets" ]; then
          fix_lines="${fix_lines}* ${msg}"$'\n'
          fix_count=$((fix_count + 1))
        fi
        ;;
      *)
        if [ "$other_count" -lt "$max_bullets" ]; then
          other_lines="${other_lines}* ${msg}"$'\n'
          other_count=$((other_count + 1))
        fi
        ;;
    esac
  done <<< "$commits"

  echo "نسخه ${VERSION} حساب‌یار منتشر شد."
  echo ""
  if [ -n "$feat_lines" ]; then
    echo "### امکانات جدید"
    printf '%s' "$feat_lines"
    echo ""
  fi
  if [ -n "$fix_lines" ]; then
    echo "### رفع مشکلات"
    printf '%s' "$fix_lines"
    echo ""
  fi
  if [ -n "$other_lines" ]; then
    echo "### بهبودها و تغییرات"
    printf '%s' "$other_lines"
  fi
}

if [ -z "$GEMINI_API_KEY" ]; then
  echo "WARNING: GEMINI_API_KEY not set, falling back to structured commit notes" >&2
  generate_fallback_notes
  exit 0
fi

# Detect Python interpreter
PYTHON_CMD=""
if python3 --version >/dev/null 2>&1; then
  PYTHON_CMD="python3"
elif py -3 --version >/dev/null 2>&1; then
  PYTHON_CMD="py -3"
fi

# Build the prompt
prompt="Generate release notes for version $VERSION of an Android personal finance app called Hesabyar.

Changes in this release:
${commits}

Changed files:
${changed_files}

Requirements:
- Write in Persian (Farsi) since this is a Persian-first app
- Use simple, user-friendly language
- Group changes into categories: Features (امکانات جدید), Fixes (رفع مشکلات), Improvements (بهبودها)
- Keep it concise (3-5 bullet points per category max)
- Start with a brief summary line
- Use markdown format
- Do not include version number in the title (just the content)
- If there are no changes in a category, omit that category entirely"

# Build request payload into a temporary file safely
payload_file=$(mktemp)
trap 'rm -f "$payload_file"' EXIT

if [ -n "$PYTHON_CMD" ]; then
  $PYTHON_CMD -c '
import json, sys
prompt = sys.stdin.read()
payload = {
    "contents": [{"parts": [{"text": prompt}]}],
    "generationConfig": {
        "temperature": 0.3,
        "maxOutputTokens": 1024
    }
}
with open(sys.argv[1], "w", encoding="utf-8") as f:
    json.dump(payload, f)
' "$payload_file" <<< "$prompt"
elif command -v jq >/dev/null 2>&1; then
  jq -n --arg prompt "$prompt" '{
    contents: [{parts: [{text: $prompt}]}],
    generationConfig: {temperature: 0.3, maxOutputTokens: 1024}
  }' > "$payload_file"
else
  escaped_prompt=$(printf '%s' "$prompt" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g' | tr '\n' ' ' | sed 's/ $//' | sed 's/^/"/;s/$/"/')
  cat << EOF > "$payload_file"
{
  "contents": [{
    "parts": [{"text": ${escaped_prompt}}]
  }],
  "generationConfig": {
    "temperature": 0.3,
    "maxOutputTokens": 1024
  }
}
EOF
fi

call_gemini_api() {
  local model="$1"
  local max_attempts="${2:-2}"
  local attempt=1
  local delay=2

  while [ "$attempt" -le "$max_attempts" ]; do
    local response
    response=$(curl -s -w "\n%{http_code}" --connect-timeout 10 --max-time 35 \
      "https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent" \
      -H "Content-Type: application/json" \
      -H "x-goog-api-key: $GEMINI_API_KEY" \
      -d @"$payload_file" 2>/dev/null || echo "")

    local http_code
    http_code=$(echo "$response" | tail -n1)
    local body
    body=$(echo "$response" | head -n -1)

    if [ "$http_code" = "200" ] && [ -n "$body" ]; then
      echo "$body"
      return 0
    fi

    echo "Attempt $attempt with model $model returned HTTP $http_code" >&2
    if [ -n "$body" ]; then
      echo "Response body: $body" >&2
    fi

    case "$http_code" in
      429|500|502|503|504|000|"")
        if [ "$attempt" -lt "$max_attempts" ]; then
          echo "Retrying in ${delay}s..." >&2
          sleep "$delay"
          delay=$((delay * 2))
        fi
        ;;
      *)
        return 1
        ;;
    esac

    attempt=$((attempt + 1))
  done

  return 1
}

extract_text_script='
import sys, json
try:
    data = json.load(sys.stdin)
    for c in data.get("candidates", []):
        for p in (c.get("content") or {}).get("parts", []):
            t = (p.get("text") or "").strip()
            if t:
                print(t)
                sys.exit(0)
except Exception:
    pass
'

extract_gemini_text() {
  local json_str="$1"
  local parsed=""
  if [ -n "$PYTHON_CMD" ]; then
    parsed=$(echo "$json_str" | $PYTHON_CMD -c "$extract_text_script" 2>/dev/null || echo "")
  fi
  if [ -z "$parsed" ] && command -v jq >/dev/null 2>&1; then
    parsed=$(echo "$json_str" | jq -r '.candidates[0].content.parts[0].text // empty' 2>/dev/null || echo "")
  fi
  echo "$parsed"
}

# Determine candidate models to try
models_to_try=("$GEMINI_MODEL")
if [ "$GEMINI_MODEL" != "gemini-2.0-flash" ]; then
  models_to_try+=("gemini-2.0-flash")
fi
if [ "$GEMINI_MODEL" != "gemini-1.5-flash" ]; then
  models_to_try+=("gemini-1.5-flash")
fi

notes=""
for i in "${!models_to_try[@]}"; do
  model_cand="${models_to_try[$i]}"
  echo "Attempting release note generation with Gemini model: $model_cand" >&2
  # Primary model gets 2 attempts (1 retry), subsequent fallback models get 1 attempt each
  attempts=1
  if [ "$i" -eq 0 ]; then
    attempts=2
  fi
  if gemini_body=$(call_gemini_api "$model_cand" "$attempts"); then
    notes=$(extract_gemini_text "$gemini_body")
    if [ -n "$notes" ]; then
      break
    fi
    echo "Response from model $model_cand contained no extractable text, trying next model" >&2
  fi
done

if [ -z "$notes" ]; then
  if [ -z "$PYTHON_CMD" ] && ! command -v jq >/dev/null 2>&1; then
    echo "WARNING: A JSON parser (python3 or jq) is needed to extract AI response; falling back to structured commit notes" >&2
  else
    echo "WARNING: All Gemini API attempts failed or returned empty content, falling back to structured commit notes" >&2
  fi
  generate_fallback_notes
  exit 0
fi

echo "$notes"
