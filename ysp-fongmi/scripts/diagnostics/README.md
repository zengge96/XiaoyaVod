# 诊断/逆向脚本

这些是定位问题过程中用过的脚本，保留以便复现分析。

## 用途

- `dump_genckey_args.js` / `dump_gen_inputs.js` — Frida hook `GenCKey`/`generate` 打印 12 个入参
- `test671_full.py` / `test682_full.py` — 在官方/改包进程里抓 682/671 cKey 并请求 playurl 验证 200/403
- `fake_cmdline_*.js` — Frida 伪造 `/proc/self/cmdline`（open/read hook），证明进程名伪装能产出 682
- `probe_libc.js` — 探测 libc `read`/`open` 是 ARM 还是 Thumb 指令（决定 inline hook 方案）
- `scan_guard_*.js` / `scan_ranges_proc.js` — 扫 native so 的 bss 身份槽和 GOT 偏移
- `patch_procbuf.js` — 直接改 so 内存里的进程名缓冲（验证无效，因为每次 read 重读）
- `dump_slots_runtime.js` — dump BssIdentity 槽表运行时类型（定位 Pair cast 异常）
- `verify_chain.py`（上级目录）— 端到端验证包装

## 运行

需要 frida 16.x、frida-server 在云手机运行、adb 端口 27042 转发。脚本里默认包名/路径按当时云手机环境写，按需修改。
