# YYMC 安卓版

APK 里跑一个只绑 `127.0.0.1` 的中继，WebView 直接加载它，于是页面是同源 http 回环：静态资源、`/rooms` 的 HTTP 反代、`/rooms/p2p` 的 WebSocket 隧道**全在设备上完成**，登录与联机不依赖任何云端中转。

## 下载（Release 直链）

| 版本 | 文件 | 大小 | 直链 |
|---|---|---|---|
| **v1.0.1** | `YYMC.apk` | 106.7 MB | https://github.com/mdy114514mdy/yymc-android/releases/download/v1.0.1/YYMC.apk |
| v1.0.1 | 整页（含更新记录） | — | https://github.com/mdy114514mdy/yymc-android/releases/tag/v1.0.1 |
| v1.0.0 | `YYMC.apk`（旧版） | 106.7 MB | https://github.com/mdy114514mdy/yymc-android/releases/download/v1.0.0/YYMC.apk |

资源随仓库发布（`site/`，148 MB），克隆下来即可从零编译出 APK。

---

## 一、环境要求

| 需要 | 版本 / 说明 |
|---|---|
| JDK | 8 以上，实测 Microsoft OpenJDK **25** |
| Android SDK | `build-tools`（要 `aapt2` / `d8` / `zipalign` / `apksigner`）与任一 `platforms/android-XX/android.jar`，实测 `android-31` |
| 磁盘 | 约 400 MB（`site/` 148 + `assets/` 148 + 中间产物） |

不需要 gradle，也不需要 androidx。缺 `build-tools` 时：

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  --install "platforms;android-31" "build-tools;31.0.0" "build-tools;34.0.0"
```

`31.0.0` 提供 aapt2/zipalign/apksigner；`34.0.0` 只用它的 `lib/d8.jar`（31 的 d8 在 JDK 9+ 上起不来）。

## 二、从零编译

```bash
git clone https://github.com/mdy114514mdy/yymc-android.git
cd yymc-android
# 改 build.sh 顶部三个路径：BT / BT34 / PLAT
bash build.sh
```

脚本自己定位仓库根（`W=$(cd "$(dirname "$0")" && pwd)`），`MIR=$W/site`。

六步流水线：

| 步骤 | 做什么 | 产物 |
|---|---|---|
| 1 | 从 `site/` 收资源到 `assets/site/`（只要 `.m4a`） | `assets/site/` ≈106 MB |
| 2 | `aapt2 link -A assets … -0 m4a` | 含资源的未签名 APK |
| 3 | `javac --release 8` | `build/classes` |
| 4 | `java -cp $BT34/lib/d8.jar com.android.tools.r8.D8` | `build/dex/classes.dex` |
| 5 | Python `zipfile` 以 STORED 追加 `classes.dex` + `zipalign -f 4` | `build/app-aligned.apk` |
| 6 | `apksigner sign` | `YYMC.apk` |

期望输出：`sounds 4849 / 89.2 MB`、`classes: 13`、`classes.dex 19.6 KB`、`v2+v3 通过`、`YYMC.apk 106.7 MB`。

## 三、验证产物

```bash
"$BT/aapt2.exe" dump badging YYMC.apk | head -6
"$BT/apksigner.bat" verify -v YYMC.apk

python - <<'PY'
import zipfile, re
z = zipfile.ZipFile("YYMC.apk")
n = z.namelist()
print("条目:", len(n))                                                   # 4860
print("classes.dex:", "classes.dex" in n)                                # True
print("类数:", len(set(re.findall(rb"Lcn/mdy/yymc/[A-Za-z/]+;", z.read("classes.dex")))))  # 13
print("assets/site:", len([x for x in n if x.startswith("assets/site/")]))                 # 4854
print("testzip:", "OK" if z.testzip() is None else "BAD")
PY
```

## 四、设备内中继（这套 APK 的核心）

| 路径 | 实现 |
|---|---|
| 静态资源 | `AssetHandler` 从 APK 的 `assets/site/` 读，支持 Range（206） |
| `/rooms/api/*` | `RoomsProxy` **自己拼 HTTP/1.1 走裸 socket** 到官方服务并补 `Origin` |
| `/rooms/p2p` | `WsTunnel`：本地替浏览器算 `Sec-WebSocket-Accept`（SHA1(key+GUID)），再用一条带 Origin 的上游连接握手，之后按帧原样搬运 |

为什么不用 `HttpURLConnection`：JDK 与 Android 都把它当 restricted header，**`Origin` 会被静默丢弃**，请求稳定 403。

## 五、不用真机的自测

```bash
javac -nowarn -d rt $(find src -name '*.java')
node mock_upstream.mjs &                                  # 会校验 Origin 的假上游
java -cp rt cn.mdy.yymc.relay.DevMain "$(pwd)/site" 8734 127.0.0.1 9443 plain &
curl -s http://127.0.0.1:8734/rooms/api/live              # {"mock":true,...,"origin":"https://www.bilibili.com"}
```

## 六、更新记录

| 版本 | 变化 |
|---|---|
| **v1.0.1** | **修区块"永久毒化"**（与桌面版同一修复）：被看门狗记进毒名单的区块原本永久跳过 → 永远不建网格、能看穿地面看到洞穴。现改为识别页面停顿 + 退避重试（2/4/8/16/32 s，最多 4 次）+ 建成即清除 + 退避用尽补空块 |
| v1.0.0 | 首个版本：设备内中继（静态 + `/rooms/api` 反代 + `/rooms/p2p` WS 隧道） |

## 七、已知坑

1. `d8.bat` 拼了 `-Djava.ext.dirs`，JDK 9+ 直接拒启 → 改 `java -cp $BT34/lib/d8.jar`。
2. `javac` 生成的 `$` 类（匿名/具名内部类）会让 d8 抛 NPE → 本仓库所有类都是**顶层类**。
3. `HttpURLConnection` 丢 `Origin` → 见上。
4. `classes.dex` 没进 APK 会闪退，日志只有一行 `Duplicate name` 警告 → 用第三节脚本确认。
5. `usesCleartextTraffic` 必须为 true（页面是 `http://127.0.0.1:PORT`）。
6. Android 11+ 侧载需允许「安装未知来源应用」。

---

# YYMC — Android build (English)

An APK that runs a loopback relay bound to `127.0.0.1` and loads it in a WebView. Because the page is served over `http://127.0.0.1:PORT`, it gets a real same-origin http context: static assets, the `/rooms` HTTP reverse proxy and the `/rooms/p2p` WebSocket tunnel **all run on the device** — sign-in and multiplayer never touch a cloud relay.

## Download (release direct link)

| Version | File | Size | Direct link |
|---|---|---|---|
| v1.0.0 | `YYMC.apk` | 106.7 MB | https://github.com/mdy114514mdy/yymc-android/releases/download/v1.0.0/YYMC.apk |
| v1.0.0 | release page | — | https://github.com/mdy114514mdy/yymc-android/releases/tag/v1.0.0 |

## Requirements

JDK 8+ (tested on Microsoft OpenJDK 25), Android SDK with `build-tools` (aapt2, d8, zipalign, apksigner) and any `platforms/android-XX/android.jar` (tested with android-31), ~400 MB free disk. No gradle, no androidx.

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  --install "platforms;android-31" "build-tools;31.0.0" "build-tools;34.0.0"
```

## Build from scratch

```bash
git clone https://github.com/mdy114514mdy/yymc-android.git
cd yymc-android
# edit BT / BT34 / PLAT at the top of build.sh
bash build.sh            # -> YYMC.apk (~106.7 MB)
```

Six steps: collect assets → `aapt2 link -A assets -0 m4a` → `javac --release 8` → `d8` → append `classes.dex` (STORED) + `zipalign -f 4` → `apksigner sign`.

## Verify

`aapt2 dump badging` (minSdk 24, target 31), `apksigner verify -v` (v2/v3), and a zip check expecting **4860 entries, `classes.dex` present, 13 classes, 4854 `assets/site` files, testzip OK**.

## How the on-device relay works

| Path | Implementation |
|---|---|
| static | `AssetHandler` serves `assets/site/` with Range support |
| `/rooms/api/*` | `RoomsProxy` builds raw HTTP/1.1 over a socket and adds `Origin` |
| `/rooms/p2p` | `WsTunnel` computes `Sec-WebSocket-Accept` (SHA1(key+GUID)) locally, handshakes with the upstream over TLS (adding `Origin`), then relays frames verbatim |

`HttpURLConnection` is **not** used on purpose: both the JDK and Android treat `Origin` as a restricted header and silently drop it, which makes every request 403.

## Off-device self test

`mock_upstream.mjs` is a local fake upstream that rejects a wrong `Origin`; `DevMain` is the desktop entry point for the same relay classes (excluded from the APK build). Run both and check `/rooms/api/live` returns `{"mock":true,...,"origin":"https://www.bilibili.com"}`.

## Known pitfalls

1. `d8.bat` passes `-Djava.ext.dirs` and refuses to start on JDK 9+ — call `java -cp <build-tools>/lib/d8.jar` instead.
2. Anonymous/named inner classes compile to `Foo$1`-style names and make `d8` throw NPE — every class here is top-level.
3. Missing `classes.dex` in the APK means an instant crash on launch; only a `Duplicate name` notice hints at it.
4. `usesCleartextTraffic` must be `true`.
5. Sideloading on Android 11+ requires allowing installs from unknown sources.

## Notes

Only AAC (`.m4a`) audio ships. Self-signed keystore — sideload only; replacing the signature requires uninstall + reinstall. No account/recovery tooling in this repository.
