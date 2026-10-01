#!/usr/bin/env bash
set -euo pipefail

# inject-version.sh - Updates versionCode and versionName in app/build.gradle.kts.
#
# Usage: ./scripts/inject-version.sh
# Reads VERSION file and updates the Gradle build file.

VERSION_FILE="${VERSION_FILE:-VERSION}"
BUILD_FILE="${BUILD_FILE:-app/build.gradle.kts}"

if [ ! -f "$VERSION_FILE" ]; then
  echo "ERROR: $VERSION_FILE not found"
  exit 1
fi

if [ ! -f "$BUILD_FILE" ]; then
  echo "ERROR: $BUILD_FILE not found"
  exit 1
fi

version=$(cat "$VERSION_FILE" | tr -d '[:space:]')
IFS='.' read -r major minor patch <<< "$version"
version_code=$((major * 10000 + minor * 100 + patch))

echo "Injecting version: $version (code: $version_code)"

# Update versionName
if grep -q 'this.versionName = appVersionName' "$BUILD_FILE"; then
  echo "versionName already references external variable - no injection needed"
else
  # Replace hardcoded versionName
  sed -i.bak "s/versionName = \"[^\"]*\"/versionName = \"$version\"/" "$BUILD_FILE"
  rm -f "${BUILD_FILE}.bak"
  echo "Updated versionName to $version"
fi

# Update versionCode
if grep -q 'this.versionCode = appVersionCode' "$BUILD_FILE"; then
  echo "versionCode already references external variable - no injection needed"
else
  sed -i.bak "s/versionCode = [0-9]*/versionCode = $version_code/" "$BUILD_FILE"
  rm -f "${BUILD_FILE}.bak"
  echo "Updated versionCode to $version_code"
fi

echo "Version injection complete"
