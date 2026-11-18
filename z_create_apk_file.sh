#!/usr/bin/env bash
# ==============================================================================
# ViralKit — Create & Export Android APK File
# Application ID : com.aistudio.viralkit.qrmxkp
# Version        : 1.0 (versionCode 1)
# Output APK     : app/build/outputs/apk/debug/app-debug.apk
# Cached APK     : .build-outputs/app-debug.apk
# Latest Size    : 29,919,209 bytes (~28.5 MB)
# Latest SHA-256 : 097d144b87b59d7a3ed0115bd655a56af3ae16a43147afd1b629fa3f4e7d335e
# ==============================================================================
set -euo pipefail

echo "1. Removing old APK build outputs..."
rm -rf app/build/outputs/apk/debug
mkdir -p .build-outputs

echo "2. Building fresh ViralKit Debug APK..."
gradle :app:assembleDebug

APK_SOURCE="app/build/outputs/apk/debug/app-debug.apk"
APK_COPY=".build-outputs/app-debug.apk"

if [ -f "$APK_SOURCE" ]; then
  cp -f "$APK_SOURCE" "$APK_COPY"
  SIZE_BYTES=$(wc -c < "$APK_SOURCE" | tr -d ' ')
  SHA256_HASH=$(sha256sum "$APK_SOURCE" | awk '{print $1}')
  echo "=============================================================================="
  echo "APK Created Successfully!"
  echo "  File   : $APK_SOURCE"
  echo "  Copy   : $APK_COPY"
  echo "  Size   : ${SIZE_BYTES} bytes"
  echo "  SHA256 : ${SHA256_HASH}"
  echo "=============================================================================="
else
  echo "Error: APK was not generated at $APK_SOURCE" >&2
  exit 1
fi
