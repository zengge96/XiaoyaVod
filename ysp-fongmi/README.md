# FongMi / OK影视 央视频直播插件（ysp_spider）

让 FongMi / OK影视（TVBox 类播放器）通过 JSON 配置加载 `ysp_spider.jar`，直接播放 CCTV1 直播，无需改播放器 APK。

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

产物 md5 应与已验证包一致：`bc55fc2035c97c9a61289029653b4210`

## 验证记录

- 32 位 FongMi 5.1.0 电视版：GenCKey 682、四帧画面推进、100 秒无 403/重试/崩溃
- 64 位 OK影视手机版 5.0.2（`com.fongmi.android.tv`，arm64）：GenCKey 682、m3u8 200、100 秒稳定、四帧 CCTV1《三餐四季 江西篇》推进

详细技术原理见 `docs/TECHNICAL.md`。
