#!/usr/bin/env bash
set -euo pipefail

# bump-version.sh - Reads VERSION, applies semver bump, writes new VERSION.
# Also prepends to CHANGELOG.md (newest entries first).
#
# Usage: ./scripts/bump-version.sh <bump_type>
# Example: ./scripts/bump-version.sh minor

BUMP_TYPE="${1:?Usage: bump-version.sh <major|minor|patch>}"
VERSION_FILE="${VERSION_FILE:-VERSION}"
CHANGELOG_FILE="${CHANGELOG_FILE:-CHANGELOG.md}"
DATE=$(date +%Y-%m-%d)

if [ ! -f "$VERSION_FILE" ]; then
  echo "ERROR: $VERSION_FILE not found"
  exit 1
fi

current=$(tr -d '[:space:]' < "$VERSION_FILE")

# Validate VERSION format
if ! echo "$current" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "ERROR: $VERSION_FILE contains invalid format: '$current' (expected MAJOR.MINOR.PATCH)"
  exit 1
fi

IFS='.' read -r major minor patch <<< "$current"

case "$BUMP_TYPE" in
  major)
    major=$((major + 1))
    minor=0
    patch=0
    ;;
  minor)
    minor=$((minor + 1))
    patch=0
    ;;
  patch)
    patch=$((patch + 1))
    ;;
  *)
    echo "ERROR: Invalid bump type: $BUMP_TYPE (must be major, minor, or patch)"
    exit 1
    ;;
esac

new_version="${major}.${minor}.${patch}"
echo "$new_version" > "$VERSION_FILE"
echo "Version bumped: $current -> $new_version" >&2

# Generate changelog entry
if [ -f "$CHANGELOG_FILE" ]; then
  existing=$(cat "$CHANGELOG_FILE")
else
  existing=""
fi

# Create changelog entry
entry="## [${new_version}] - ${DATE}

### Changed
- Release version ${new_version}
"

# Prepend new entry after header
header="# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

"

if echo "$existing" | grep -q "^# Changelog"; then
  # Extract body: skip the 7-line header block
  body=$(echo "$existing" | tail -n +8)
  echo "${header}${entry}${body}" > "$CHANGELOG_FILE"
else
  echo "${header}${entry}${existing}" > "$CHANGELOG_FILE"
fi

echo "CHANGELOG.md updated" >&2
echo "$new_version"
