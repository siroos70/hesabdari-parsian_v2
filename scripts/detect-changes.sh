#!/bin/sh
# POSIX sh compatible (also invoked as `bash scripts/detect-changes.sh` in CI).
set -eu
# dash/BSD sh lack `set -o pipefail`; enable it only where supported.
(set -o pipefail) 2>/dev/null && set -o pipefail || true

# detect-changes.sh - Determines whether application code changed.
# Exit code 0 = release needed, exit code 1 = skip release.
#
# Usage: ./scripts/detect-changes.sh <base_ref> <head_ref>
# Example: ./scripts/detect-changes.sh origin/main HEAD

BASE_REF="${1:?Usage: detect-changes.sh <base_ref> <head_ref>}"
HEAD_REF="${2:-HEAD}"

# Handle zero SHA (initial push) - treat as all files changed
if echo "$BASE_REF" | grep -qE '^0+$'; then
  echo "RELEASE_NEEDED: Initial push detected (zero SHA base)"
  exit 0
fi

changed_files=$(git diff --name-only "$BASE_REF"..."$HEAD_REF" 2>/dev/null || \
                git diff --name-only "$BASE_REF".."$HEAD_REF" 2>/dev/null || \
                git diff --name-only "$BASE_REF" "$HEAD_REF" 2>/dev/null || \
                echo "")

if [ -z "$changed_files" ]; then
  echo "SKIP: No changed files detected between $BASE_REF and $HEAD_REF"
  exit 1
fi

CHANGED_LIST=$(mktemp "${TMPDIR:-/tmp}/detect-changes.XXXXXX")
trap 'rm -f "$CHANGED_LIST"' EXIT
printf '%s\n' "$changed_files" > "$CHANGED_LIST"

# Print the package version declared under [package] or [workspace.package]
# in a Cargo manifest. Dependency tables such as [dependencies.foo] also use
# `version =` keys, so the section must be tracked explicitly.
# Uses POSIX [[:space:]] classes (a literal \t inside a bracket expression is
# not portable across awk implementations).
rust_manifest_version() {
  # NOTE: no "--" here. After "--", git treats arguments as pathspecs rather
  # than object specs, so 'git show -- rev:path' returns empty with exit 0
  # and every version lookup would silently miss. The combined "$1:$2" form
  # can never start with a hyphen anyway (it always begins with the ref).
  git show "$1:$2" 2>/dev/null | awk -F'"' '
    /^[[:space:]]*\[/ {
      # Only exact [package]/[workspace.package] select the target section;
      # the flag resets only on a DIFFERENT top-level table (no "."), so
      # sub-tables like [package.metadata] do not clear it prematurely.
      sec = $0
      gsub(/^[[:space:]]*\[[[:space:]]*|[[:space:]]*\][[:space:]]*$/, "", sec)
      if (sec == "package" || sec == "workspace.package") in_pkg = 1
      else if (index(sec, ".") == 0) in_pkg = 0
    }
    in_pkg && $0 ~ /^[[:space:]]*version[[:space:]]*=[[:space:]]*"/ { print $2; exit }
  ' || true
}

# Check if any application files changed
has_app_changes=false
while IFS= read -r file; do
  [ -z "$file" ] && continue

  # Dependency manifests/lockfiles never trigger a release on their own.
  # Dependabot bumps land here weekly (grouped); releasing per dependency
  # update would flood the releases page. App code touching these same
  # files alongside other sources still triggers a release below.
  #
  # NOTE: this also means Rust source edits (*.rs) and manifest deletions do
  # NOT gate a release by themselves. Releases are intentionally version-
  # driven: bumping [workspace.package].version is what ships a core change,
  # because an artifact with an unchanged versionCode cannot be published.
  case "$file" in
    gradle/libs.versions.toml|gradle/libs.versions.toml.lock|gradle.lockfile|versions.lock|*/gradle.lockfile|*/versions.lock)
      continue
      ;;
    # Workspace root and every member manifest at ANY nesting depth:
    # POSIX case globs are not pathname-restricted, so `*` also matches `/`
    # and rust/*/Cargo.toml covers rust/a/b/c/Cargo.toml too (locked in by
    # test 11). Matches every tracked Rust manifest so dependency edits
    # never fall through to the *.toml include pattern below.
    rust/Cargo.toml|rust/*/Cargo.toml|rust/Cargo.lock)
      # A manual version bump in a Rust manifest is an application change,
      # not a dependency edit: force a release so the artifact carries the
      # new core version. Pure dependency edits (including table-form
      # [dependencies.*] version bumps) stay skipped.
      # Members declaring `version.workspace = true` inherit their version
      # from [workspace.package] in the root manifest, so they have no local
      # version key — this check correctly finds nothing to compare there,
      # and bumps to the inherited value are caught via rust/Cargo.toml.
      old_ver=$(rust_manifest_version "$BASE_REF" "$file")
      new_ver=$(rust_manifest_version "$HEAD_REF" "$file")
      # Only a genuine version CHANGE is a release trigger: both refs must
      # carry a [package]/[workspace.package] version and differ. A newly
      # added manifest (old_ver empty) is not a bump by itself — like any
      # other core change it ships when [workspace.package].version moves.
      if [ -n "$old_ver" ] && [ -n "$new_ver" ] && [ "$old_ver" != "$new_ver" ]; then
        echo "RELEASE_NEEDED: Version bump detected in $file ($old_ver -> $new_ver)"
        exit 0
      fi
      continue
      ;;
  esac

  # Check if this file matches any include pattern
  case "$file" in
    app/*|src/*|*.kt|*.kts|*.java|*.xml|*.toml)
      has_app_changes=true
      break
      ;;
    build.gradle.kts|build.gradle|settings.gradle.kts|settings.gradle|gradle.properties|proguard-rules.pro)
      has_app_changes=true
      break
      ;;
    gradle/*)
      has_app_changes=true
      break
      ;;
  esac
done < "$CHANGED_LIST"

if [ "$has_app_changes" = false ]; then
  echo "SKIP: No application code changes detected"
  echo "Changed files:"
  echo "$changed_files" | head -20
  exit 1
fi

echo "RELEASE_NEEDED: Application code changes detected"
echo "Changed files:"
echo "$changed_files" | head -30
exit 0
