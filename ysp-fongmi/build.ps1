# 从仓库内源码重建 ysp_spider.jar（供 GitHub Actions windows-latest 使用）
# 不动 Java 版本：沿用 CI 的 JDK 11，javac 用 -source 8 -target 8 保持老系统兼容
$ErrorActionPreference = "Stop"
$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ROOT

$ANDROID_HOME = $env:ANDROID_HOME
if (-not $ANDROID_HOME) { throw "ANDROID_HOME 未设置" }
$ANDROID_JAR = Join-Path $ANDROID_HOME "platforms\android-34\android.jar"
$D8 = Join-Path $ANDROID_HOME "build-tools\34.0.0\d8.bat"
if (-not (Test-Path $ANDROID_JAR)) { throw "缺少 $ANDROID_JAR" }
if (-not (Test-Path $D8)) { throw "缺少 $D8" }

Write-Host "=== 1. 编译 Java（JDK 11, source/target 8） ==="
Remove-Item -Recurse -Force build\yclass -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path build\yclass | Out-Null
& javac -source 8 -target 8 -cp "$ANDROID_JAR;stubs" -d build\yclass `
  "stubs\com\github\catvod\crawler\Spider.java" `
  "stubs\com\ysp\tv\player\CctvDrmSegmentDecryptor.java" `
  "java\com\ysp\svc\NetBridge.java" `
  "java\com\github\catvod\spider\Ysp.java"
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }

Write-Host "=== 2. d8 合并 dex ==="
Remove-Item -Recurse -Force build\finaldex -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path build\finaldex | Out-Null
$yspClasses = Get-ChildItem "build\yclass\com\github\catvod\spider\Ysp*.class" | ForEach-Object { $_.FullName }
& $D8 --output build\finaldex\ `
  "prebuilt\classes3.dex" `
  "build\yclass\com\github\catvod\crawler\Spider.class" `
  "build\yclass\com\ysp\svc\NetBridge.class" `
  $yspClasses
if ($LASTEXITCODE -ne 0) { throw "d8 失败" }

Write-Host "=== 3. 打包 jar（7z store, 含 v7/arm64 so） ==="
Remove-Item -Recurse -Force build\jarout -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path build\jarout\lib\armeabi-v7a, build\jarout\lib\arm64-v8a | Out-Null
Copy-Item "build\finaldex\classes.dex" "build\jarout\classes.dex"
Copy-Item "prebuilt\armeabi-v7a\*.so" "build\jarout\lib\armeabi-v7a\"
Copy-Item "prebuilt\arm64-v8a\*.so" "build\jarout\lib\arm64-v8a\"
Remove-Item -Force ysp_spider.jar -ErrorAction SilentlyContinue
Push-Location build\jarout
$sevenZip = "C:\Program Files\7-Zip\7z.exe"
if (-not (Test-Path $sevenZip)) { $sevenZip = "7z" }
& $sevenZip a -tzip -mx=0 -y ..\..\ysp_spider.jar classes.dex "lib\armeabi-v7a\*.so" "lib\arm64-v8a\*.so" | Out-Null
Pop-Location
if (-not (Test-Path ysp_spider.jar)) { throw "ysp_spider.jar 打包失败" }

Write-Host "=== 4. 生成 md5 ==="
$md5 = (Get-FileHash -Algorithm MD5 ysp_spider.jar).Hash.ToLower()
Set-Content -Path ysp_spider.jar.md5 -Value $md5 -NoNewline

Write-Host "完成: ysp_spider.jar md5=$md5"
