#!/usr/bin/env bash
# 从仓库内源码完整重建 ysp_spider.jar（FongMi/OK影视 央视频直播插件）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

echo "=== 环境要求 ==="
echo "JDK 8+ (javac), Android SDK build-tools (d8), NDK clang-18 (可选, 重建 hook so 用)"
: "${ANDROID_HOME:?请设置 ANDROID_HOME}"
ANDROID_JAR="${ANDROID_HOME}/platforms/android-34/android.jar"
D8="${ANDROID_HOME}/build-tools/34.0.0/d8"

echo "=== 1. 编译 Java ==="
rm -rf build/yclass && mkdir -p build/yclass
javac -source 8 -target 8 -cp "$ANDROID_JAR:stubs" -d build/yclass \
  stubs/com/github/catvod/crawler/Spider.java \
  java/com/ysp/svc/NetBridge.java \
  java/com/github/catvod/spider/Ysp.java

echo "=== 2. d8 合并 dex（预编译的 cKey/BssIdentity dex + 本次 Java） ==="
rm -rf build/finaldex && mkdir -p build/finaldex
"$D8" --output build/finaldex/ \
  prebuilt/classes3.dex \
  build/yclass/com/github/catvod/crawler/Spider.class \
  build/yclass/com/ysp/svc/NetBridge.class \
  build/yclass/com/github/catvod/spider/Ysp.class

echo "=== 3. 打包 jar（含 v7/arm64 两套 so） ==="
rm -rf build/jarout && mkdir -p build/jarout/lib/armeabi-v7a build/jarout/lib/arm64-v8a
cp build/finaldex/classes.dex build/jarout/
cp prebuilt/armeabi-v7a/*.so build/jarout/lib/armeabi-v7a/
cp prebuilt/arm64-v8a/*.so build/jarout/lib/arm64-v8a/
( cd build/jarout && zip -q -0 ../../ysp_spider.jar classes.dex lib/armeabi-v7a/*.so lib/arm64-v8a/*.so )

echo "=== 完成 ==="
echo "产物: $ROOT/ysp_spider.jar"
md5sum "$ROOT/ysp_spider.jar"
