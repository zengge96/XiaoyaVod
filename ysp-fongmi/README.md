# FongMi / OK影视 央视频直播插件（ysp_spider）

让 FongMi / OK影视（TVBox 类播放器）通过 JSON 配置加载 `ysp_spider.jar`，播放央视/卫视共 51 个直播频道，无需改播放器 APK。频道在直播页按「央视」「卫视」两个栏目分组显示。

**jar 本身是双 ABI（arm64-v8a + armeabi-v7a）**，按进程架构自动选 so，32 位和 64 位播放器都能用。分支名里的 `64bit` 只是标记这次最后攻克的场景（64 位手机版 5.0.2 上的 libest.so 闪退），不代表只支持 64 位。

本分支是完整可用的工作快照；`ysp_spider.jar` 已在云手机真实环境（32 位 FongMi 5.1.0 电视版 + 64 位 OK影视手机版 5.0.2）端到端验证通过。

## 目录结构

```
ysp-fongmi/
├── README.md                      本文档
├── build.sh                       从源码重建 ysp_spider.jar
├── ysp_spider.jar                  已验证的最终产物
├── java/                          Java 源码（Spider 实现 + 网络桥）
│   ├── com/github/catvod/spider/Ysp.java
│   └── com/ysp/svc/NetBridge.java
├── stubs/                         FongMi spider 接口 stub（编译用）
│   └── com/github/catvod/crawler/Spider.java
├── native/                        native hook 源码（进程名伪装）
│   ├── cmdhook_armv7.c
│   └── cmdhook_arm64.c
├── smali/                         反编译并 patch 过的关键 smali
│   └── com/ysp/tv/ckey/BssIdentity.smali
├── prebuilt/                      预编译依赖
│   ├── classes3.dex               cKey/BssIdentity/FakeContext 所在的 dex（已 patch）
│   ├── armeabi-v7a/               libckeygenerator.so, libckguard.so
│   └── arm64-v8a/                 libckeygenerator.so, libckguard.so, libstdc++.so, libcmdhook.so
├── config/                        导入用配置
│   ├── config_local.json          本地 jar 路径（file://）
│   └── config_url.json            网络 jar 路径（http）
├── scripts/
│   ├── build_hook_so.sh           重建 native hook so
│   └── ...                        验证/诊断脚本
└── docs/
    └── TECHNICAL.md               详细技术说明（做了什么、为什么）
```

## 快速使用

1. 把 `ysp_spider.jar` 放到手机 `/sdcard/Download/`
2. FongMi / OK影视 里导入 `config/config_local.json`（本地 jar）
   - 如果 `file://` 路径在你那版不生效，改用 `config/config_url.json`
3. 直播列表出现「央视频直播」，点进去即可播放 CCTV1

## 从源码完整重建

环境：JDK 8+、Android SDK（platform android-34、build-tools 34.0.0）、NDK（含 clang-18）。

```bash
export ANDROID_HOME=/path/to/android-sdk
export NDK_PATH=/path/to/android-ndk
./scripts/build_hook_so.sh    # 可选：重建 native hook so（已带预编译 so 可跳过）
./build.sh                    # 重建 ysp_spider.jar
```

产物 md5 应与已验证包一致：`1c700cd3c662a66cf193c4f8c03a9951`（51 频道 SM4 解密 + 启动优化版，央视/卫视分组）。

## 全频道实时代理（解决长播卡死）

CDN 分片 URL 带 token，播放 5-10 分钟后过期返回 404 → 播放器报 `Bad HTTP Status` 卡死。本版所有频道统一走本地代理 `http://127.0.0.1:18089/live/<idx>`：

- 每次 m3u8 请求都实时重新生成 cKey + playurl（`freshPlayurl`），URL 永不过期
- 分片请求按频道索引回源，404 时强制重建 playurl 重试
- m3u8 加 `Cache-Control: no-cache`，避免播放器缓存旧分片列表
- 加密频道分片调 SM4 解密，失败回退原始数据

## 频道分组与启动速度优化

FongMi 的直播分组靠 `#EXTINF` 里的 `group-title="央视"` 属性解析（不是 `央视$CCTV1` 这种 `$` 写法，那会把整串当频道名显示）。频道名只写频道号，分组栏显示「央视」「卫视」两部分。缓存文件升级为 v2 路径以免旧格式缓存污染。

## 启动速度优化

老版本每次冷启动点直播要串行给 51 个频道生成 cKey/playurl、且每频道额外请求一次 m3u8 探测 KEY，实测约 16.5 秒。优化后：

- 加密频道改用 cnlid 白名单判断（`ENC_CNLID`），去掉 51 次 m3u8 探测请求
- 12 线程池并发生成各频道 playurl，实测 LIVE 生成降到约 1.5 秒
- 30 秒 m3u 缓存（`/sdcard/Android/data/com.fongmi.android.tv/files/ysp_live_cache.txt`），期间重启直接命中，秒开

## SM4 加密频道解密

编号 010/011/013/014/015/016（CCTV11/12/14/15/17/4K）的 playurl 返回 `#EXT-X-KEY:METHOD=SM4-CBC` 国密加密流，FongMi 的 iJK 内核不支持。本版本在 jar 内启用本地 HTTP 代理（127.0.0.1:18089）：

- 拉源 m3u8，去掉 `#EXT-X-KEY` 行，把相对 `.ts` 改成绝对 CDN URL
- 每个 `.ts` 分片经代理时用 `CctvDrmSegmentDecryptor.decrypt()`（来自破解版 classes3.dex，含 key 表）做 SM4-CBC 解密后返回明文 TS

注意：SM4-CBC 解密不改变数据长度，不能用「解密前后长度不同」判断是否生效。

注意：`CHANNELS` 表里每项格式是 `{栏目,频道名,cnlid,livepid,program_id}`，`channelPlayurl()` 必须用 `ch[2]/ch[3]/ch[4]` 取 cnlid/pid/program_id；早期用 `ch[1]/ch[2]/ch[3]` 会因整体错位一位导致 `iretcode=14 请求uri格式出错`。

## 验证记录

- 32 位 FongMi 5.1.0 电视版（单频道基线）：GenCKey 682、四帧画面推进、100 秒无 403/重试/崩溃
- 64 位 OK影视手机版 5.0.2（`com.fongmi.android.tv`，arm64，51 频道 SM4 解密版 md5 `1c700cd3...`）：`LIVE channels ok=48/51`，OSD 面板分组栏显示「央视」「卫视」，频道名只写 `CCTV1`…，CCTV1 多帧持续推进；CCTV11（SM4 加密频道）经本地代理解密后出正常 CCTV 11 台标画面，进程存活无崩溃
- 少数频道（如部分卫视）因地区版权返回 iret≠0，jar 自动跳过，不影响其余频道

详细技术原理见 `docs/TECHNICAL.md`。
