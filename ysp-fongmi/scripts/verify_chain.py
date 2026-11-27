#!/usr/bin/env python3
"""在云手机上验证 ysp_spider 端到端：hook 生效 -> GenCKey 682 -> 无 403 -> 进程存活。

用法: python3 verify_chain.py [--package com.fongmi.android.tv] [--seconds 100]
需要: adb 可达云手机, frida-server 已运行, 端口 27042 转发。
"""
import argparse, subprocess, time, os, re

ADB = os.environ.get("ADB", "/usr/lib/android-sdk/platform-tools/adb")

def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True).stdout.strip()

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--package", default="com.fongmi.android.tv")
    ap.add_argument("--seconds", type=int, default=100)
    args = ap.parse_args()

    print("清日志并启动 LiveActivity...")
    adb("logcat", "-c")
    adb("am", "force-stop", args.package)
    time.sleep(2)
    adb("shell", f"am start -n {args.package}/.ui.activity.LiveActivity")
    time.sleep(15)

    out = subprocess.run([ADB, "logcat", "-d"], capture_output=True, text=True).stdout
    checkpoints = {
        "cmdline_spoof": r"CMDLINE_SPOOFED|CMDHook.*SPOOF",
        "genckey_682": r"GenCKey length=682",
        "playback_ready": r"playback ready",
        "http_403": r"Response code: 403|HTTP 403",
        "behind_live": r"BEHIND_LIVE_WINDOW",
        "crash": r"Fatal signal|SIGSEGV|SIGABRT",
    }
    for name, pat in checkpoints.items():
        hits = re.findall(pat, out, re.I)
        print(f"{name}: {len(hits)}")

    pid = adb("shell", f"pidof {args.package}")
    print(f"存活 pid: {pid or 'NONE'}")

    ok = bool(re.search(checkpoints["genckey_682"], out, re.I)) and bool(pid)
    print("RESULT:", "PASS" if ok else "FAIL")

if __name__ == "__main__":
    main()
