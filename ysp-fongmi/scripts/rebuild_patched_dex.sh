#!/usr/bin/env bash
# 从 smali/ 重建 prebuilt/classes3.dex（当修改 BssIdentity.smali 后用）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "需要完整反编译 smali 树（本仓库只跟踪了 patch 过的 BssIdentity.smali）"
echo "步骤："
echo "  1) 从央视频 armeabi-v7a APK 提取 classes3.dex"
echo "  2) baksmali d classes3.dex -o smali_full/"
echo "  3) 用 smali/com/ysp/tv/ckey/BssIdentity.smali 覆盖 smali_full 同名文件"
echo "  4) apktool b 或 smali a 重建 dex → prebuilt/classes3.dex"
echo "  5) ./build.sh 重建 jar"

if [ $# -eq 2 ]; then
  SRC_DEX="$1"; OUT_DEX="$2"
  BAKSMALI="${BAKSMALI:-/root/work/XiaoyaVod/jar/3rd/baksmali-2.5.2.jar}"
  APKTOOL="${APKTOOL:-/tmp/apktool.jar}"
  TMP=$(mktemp -d)
  java -jar "$BAKSMALI" d "$SRC_DEX" -o "$TMP/smali"
  cp "$ROOT/smali/com/ysp/tv/ckey/BssIdentity.smali" "$TMP/smali/com/ysp/tv/ckey/BssIdentity.smali"
  mkdir -p "$TMP/apk/smali"
  cp -r "$TMP/smali"/* "$TMP/apk/smali/"
  printf '%s\n' '<?xml version="1.0" encoding="utf-8"?>' '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.patch"><application/></manifest>' > "$TMP/apk/AndroidManifest.xml"
  java -jar "$APKTOOL" b "$TMP/apk" -o "$TMP/out.apk" >/dev/null
  cp "$TMP/build/apk/classes.dex" "$OUT_DEX"
  echo "rebuilt: $OUT_DEX"
  rm -rf "$TMP"
fi
