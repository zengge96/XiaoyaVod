#!/usr/bin/env bash
# 重建 native hook so（armv7 + arm64）
# 依赖: NDK clang（用系统 clang-18 直接指定 --target，避免 NDK wrapper 在本机架构上的问题）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${NDK_PATH:-/opt/android-sdk/ndk/28.2.13676358}"
SYSROOT="$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
CLANG="${CLANG_BIN:-/usr/bin/clang-18}"
LLD="${LLD_BIN:-/usr/bin/ld.lld-18}"
BUILTINS="$NDK/lib/clang/19/lib/linux"

mkdir -p /tmp/ysp_armld
ln -sf "$BUILTINS/libclang_rt.builtins-arm-android.a" /tmp/ysp_armld/libgcc.a 2>/dev/null || true

echo "=== armv7 hook so ==="
"$CLANG" --target=armv7a-linux-androideabi24 \
  --sysroot="$SYSROOT" -isystem "$SYSROOT/usr/include/arm-linux-androideabi" \
  -fuse-ld="$LLD" -L/tmp/ysp_armld -L"$SYSROOT/usr/lib/arm-linux-androideabi/24" \
  -shared -fPIC -O2 -o "$ROOT/prebuilt/armeabi-v7a/libcmdhook.so" \
  "$ROOT/native/cmdhook_armv7.c" -llog

echo "=== arm64 hook so ==="
"$CLANG" --target=aarch64-linux-android24 \
  --sysroot="$SYSROOT" -isystem "$SYSROOT/usr/include/aarch64-linux-android" \
  -fuse-ld="$LLD" -L"$SYSROOT/usr/lib/aarch64-linux-android/21" \
  -shared -fPIC -O2 -o "$ROOT/prebuilt/arm64-v8a/libcmdhook.so" \
  "$ROOT/native/cmdhook_arm64.c" -llog

file "$ROOT/prebuilt/armeabi-v7a/libcmdhook.so" "$ROOT/prebuilt/arm64-v8a/libcmdhook.so"
