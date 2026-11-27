# 技术说明：相比官方/原版都做了什么

## 1. 目标与约束

目标：在不修改 FongMi / OK影视 APK 的前提下，仅靠一个 JSON 配置加载我们自己的 `ysp_spider.jar`，让播放器能稳定播放 CCTV1 直播。

约束：so 必须运行在用户手机（播放器进程）内；不能改播放器 APK；最终 jar 要能被 FongMi 的 spider 加载器识别。

## 2. FongMi 直播 spider 机制

FongMi 5.x 支持 `Spider.liveContent(String)`（`com.github.catvod.crawler.Spider`）。配置里一条 `lives` 项：

```json
{"name":"央视频直播","type":3,"api":"csp_Ysp","jar":"...ysp_spider.jar","ua":"qqlive","playerType":2}
```

FongMi 下载 jar → DexClassLoader 加载 → 找 `com.github.catvod.spider.Ysp`（`csp_` 前缀）→ 调 `init(context)` → 播放时调 `liveContent(url)` 返回 m3u8 播放列表。

`java/` 里的 `Ysp.java` 就是这个 spider；`stubs/` 是编译用的接口 stub。

## 3. cKey：唯一门槛

央视频 liveinfo 接口要求一个动态 `cKey` 参数。实测结论：

- 官方 App 生成的 cKey 长度 **682**，请求 liveinfo 拿到的 playurl 能返回 HTTP 200 真实 m3u8；
- 非官方身份生成的 cKey 长度不同（586/618/660/671），liveinfo 虽返回 `iretcode=0` 和 playurl，但 CDN 对该 playurl 一律 HTTP 403；
- 因此“让 native 在播放器进程里产出 682 字节的等价 cKey”是全部工作的核心。

cKey 由 `libckeygenerator.so` 的 `CKeyFacade.GenCKey` 生成。`prebuilt/classes3.dex` 包含从央视频 APK 提取的 `CKey`/`CKeyGuard`/`BssIdentity`/`FakeContext` 等类（已 patch，见下）。

## 4. 三项关键改造

### 4.1 改包名 + FakeContext 伪装官方身份

`classes3.dex` 里的 `FakeContext`（`com.ysp.tv.FakeContext`）继承 `ContextWrapper`，覆写 `getPackageName()` 等返回官方包名 `com.cctv.yangshipin.app.androidp`，让 native 从 Context 读到的身份是官方的。`CKey.init()` 内部会用 FakeContext 包装传入的 context。

### 4.2 BssIdentity 三槽固化（native .bss 身份槽）

反编译 `libckeygenerator.so` 可知 native 读 `.bss` 段几处身份字符串来决定 cKey。`smali/com/ysp/tv/ckey/BssIdentity.smali` 是 patch 过的版本：用 `Unsafe` 持续把三个槽写回官方值（watchdog 每 60ms）：

- Application 类名（决定 cKey 长度）
- 官方包名
- 身份 hash

原版槽表只有 5 项，缺后两项；本版扩到 8 项并修正寄存器/偏移。ARM64 和 ARMv7 的 `.bss` 基址不同（`0x5cd80` / `0x32c70`），槽偏移分别处理。

### 4.3 native 进程名伪装（hook read）

即便有 FakeContext，native 还会读 `/proc/self/cmdline` 拿进程名来生成 guard。非官方进程名会导致 guard 不同、cKey 不等价。

`native/cmdhook_*.c` 是一个小型 ELF GOT hook：

- 用 `dlsym` 拿 `read`/`fread`，读 `/proc/self/maps` 找到 `libckeygenerator.so` 和 `libckguard.so` 的加载基址；
- 计算两个 so 的 `read@LIBC`/`fread@LIBC` GOT 项偏移（见源码常量），把 GOT 改成自己的 `my_read`/`my_fread`；
- `my_read`/`my_fread` 用 `readlink(/proc/self/fd/N)` 判断 fd 是否指向 cmdline，只有是才把读到的真实包名替换成官方包名；
- 只 hook 这两个 so 的 GOT，不动全局 libc，避免误伤其它线程；
- 由 `Ysp.init()` 通过 JNI `installCmdHook(pkgName)` 把真实包名传进 native 做动态匹配（不写死），并用 SIGSEGV/SIGABRT 信号处理器把崩溃栈写到 `/sdcard/TV` 或 app 私有目录（诊断用）。

## 4.4 SM4-CBC 加密频道解密（本地代理）

编号 010/011/013/014/015/016（CCTV11/12/14/15/17/4K）的 playurl 返回 m3u8 带：

```
#EXT-X-KEY:METHOD=SM4-CBC,VIDEOFORMAT="H.264",KEYFORMAT="urn:uuid:3d5e6d35-...",URI="data:text/plain;base64,..."
```

FongMi 的 iJK 内核只认 NONE/AES-128/SAMPLE-AES 系列，不认国密 SM4-CBC，直接黑屏/绿屏。key 表和 `Sm4Cbc`/`CctvDrmSegmentDecryptor` 实现在央视频破解版的 classes3.dex 里（`com.ysp.tv.player`），已随 jar 打包。

做法（纯插件侧，不改 APK）：

1. `channelPlayurl()` 拿到 playurl 后，先请求一次 m3u8，检测到 `#EXT-X-KEY` 就改返回本地代理地址 `http://127.0.0.1:18089/<key>.m3u8`；
2. jar 内 `ServerSocket` 起本地 HTTP 服务：收到 m3u8 请求时拉源、去掉 `#EXT-X-KEY` 行、把相对 `.ts` 改成绝对 CDN URL，分片仍走代理；
3. 收到 `.ts` 请求时拉加密分片，调 `CctvDrmSegmentDecryptor.INSTANCE.decrypt(raw, tsName)` 做 SM4-CBC 解密，返回明文 TS。

**关键判据**：SM4-CBC 解密不改变数据长度，不能用「解密前后长度不同」判断是否生效，要看实际画面（CCTV 11 台标 + 节目内容）。

## 5. 踩过的坑（都是实测）

1. **`System.loadLibrary` 找不到 so**：FongMi 的 DexClassLoader 只在 `/system/lib` 找，`getLoaded()` 永远 false。改从 jar 资源解压 so 到 cache 目录再 `System.load(绝对路径)`。
3. **缺合成类**：官方 dex 反编译时 `MainActivity$$ExternalSyntheticApiModelOutline0` 缺失导致 `NoClassDefFoundError`；`is64BitProcess` 改成直接返回 false（32 位）。
3. **guard 模式**：`CKeyGuard` 默认 `SYNTH_ORIG` 产出 88 字节 guard；必须切到 `NATIVE` 模式才有 104 字节（等价 cKey 所需）。
4. **cKey 长度判据**：长度一致是必要不充分条件；必须端到端看 playurl 是否 200、画面是否推进。
5. **进程位数差异**：32 位 FongMi 走 `armeabi-v7a` so；64 位 OK影视手机版走 `arm64-v8a` so。两套 GOT 偏移、`.bss` 基址都不同，必须分别处理。
6. **arm64 `libstdc++.so` 依赖**：arm64 `libckeygenerator.so` 依赖 `libstdc++.so`，新 Android 常移除系统副本；已随 jar 打包并在 init 里先加载。
7. **`libest.so` 闪退（最终根因）**：某版诊断包把 `libest.so` 一起加载，它的 `JNI_OnLoad` 里 `RegisterNatives` 传 null class，Android 11+ ART 直接 SIGABRT。tombstone 铁证：
   ```
   Abort message: 'JNI DETECTED ERROR IN APPLICATION: java_class == null in call to RegisterNatives'
   #06 libest.so (JNI_OnLoad+264)
   #21 com.github.catvod.spider.Ysp.init
   ```
   最终版彻底不加载 est.so（cKey 生成不需要它）。

## 6. 端到端验证方法

判定标准（缺一不可）：

1. 日志出现 `CMDLINE_SPOOFED` + `GenCKey length=682`；
2. playurl 请求 HTTP 200（不是 403）；
3. 100 秒以上长时：只有 1 次 GenCKey、零 `BEHIND_LIVE_WINDOW`、零 `403`、零自动重试、零崩溃；
4. 四帧截图逐张目检：画面持续推进、无错误弹窗。

## 7. 交付形态

- `ysp_spider.jar`：含 v7/arm64 两套 so + classes.dex + SM4 解密 dex + 启动优化，md5 `5cbe8a18f5cd5370970c31c11b55f108`
- `config/config_local.json`：本地导入；`config/config_url.json`：网络导入
- 全部源码、so、smali、构建脚本都在本目录，可完整重建
