# YYMC 安卓版

APK 里跑一个只绑 `127.0.0.1` 的中继，WebView 直接加载它，于是页面是同源 http 回环：静态资源、`/rooms` 的 HTTP 反代、`/rooms/p2p` 的 WebSocket 隧道**全在设备上完成**，登录与联机不依赖任何云端中转。

Release 里的 `YYMC.apk` 约 107 MB（内含游戏本体 48.5 MB + 4849 个 `.m4a` 共 89 MB）。

资源随仓库发布（`site/`，148 MB），克隆下来即可从零编译出 APK。

---

## 一、环境要求

| 需要 | 版本 / 说明 |
|---|---|
| 操作系统 | Windows / macOS / Linux 均可（脚本是 bash；Windows 下用 Git Bash 或 WSL） |
| JDK | **JDK 8 以上**，实测 Microsoft OpenJDK **25**（见下方“已知坑”第 1 条） |
| Android SDK | `build-tools`（要 `aapt2`/`d8`/`zipalign`/`apksigner`）与任一 `platforms/android-XX/android.jar`，实测 `android-31` |
| 磁盘 | 约 400 MB（`site/` 148 + `assets/` 148 + 中间产物） |

不需要 gradle，也不需要 androidx —— 全部是 SDK 自带命令。

### 装缺失的 SDK 组件

如果 `build-tools` 是空的（比如只装了 platform-tools），用 `cmdline-tools` 里的 sdkmanager 补：

```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  --install "platforms;android-31" "build-tools;31.0.0" "build-tools;34.0.0"
```

- `31.0.0` 提供 `aapt2` / `zipalign` / `apksigner`
- `34.0.0` 只用它的 `lib/d8.jar`（31 的 d8 在 JDK 9+ 上跑不起来，见“已知坑”第 1 条）

---

## 二、从零编译

```bash
git clone https://github.com/mdy114514mdy/yymc-android.git
cd yymc-android

# 1) 改 build.sh 顶部三个路径，指向你自己的 SDK
#    BT=/path/to/build-tools/31.0.0
#    BT34=/path/to/build-tools/34.0.0
#    PLAT=/path/to/platforms/android-31/android.jar
#  （脚本其余部分会自己定位仓库根，不用改 W / MIR）

# 2) 构建
bash build.sh
```

成功后会打印：

```
== 1/6 收集站点资源（只要 v6 + m4a）
   sounds 4849 / 89.2 MB
== 2/6 aapt2 link
   apk(含 assets) 106.3 MB
== 3/6 javac
   classes: 13
== 4/6 d8 -> classes.dex
   classes.dex 19.6 KB
== 5/6 写入 dex + 对齐
   classes.dex 已写入 APK 根
== 6/6 签名
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
YYMC.apk 106.7 MB
```

产物：仓库根目录的 `YYMC.apk`（以及中间目录 `assets/`、`build/`，均已被 .gitignore）。

### 六步流水线各自在做什么

| 步骤 | 命令 | 产物 |
|---|---|---|
| 1 收集资源 | 从 `site/` 复制到 `assets/site/`（只要 `.m4a`） | `assets/site/` 106.3 MB |
| 2 打资源包 | `aapt2 link -o build/app-unsigned.apk -I $PLAT --manifest AndroidManifest.xml -A assets --min-sdk-version 24 --target-sdk-version 31 --version-code 1 --version-name 1.0 -0 m4a` | 含 assets 的未签名 APK |
| 3 编译 Java | `javac -nowarn --release 8 -classpath $PLAT -d build/classes $(find src -name '*.java')` | `build/classes/**/*.class` |
| 4 转 dex | `java -cp $BT34/lib/d8.jar com.android.tools.r8.D8 --min-api 24 --lib $PLAT --output build/dex ...` | `build/dex/classes.dex` |
| 5 写回 + 对齐 | Python `zipfile` 以 **STORED** 方式把 `classes.dex` 追加到 APK 根；`zipalign -f 4` | `build/app-aligned.apk` |
| 6 签名 | `apksigner sign --ks yymc.keystore …` | `YYMC.apk` |

`-0 m4a` 让音效不压缩（本来就是 AAC），省掉几分钟的无效 deflate。

### 换签名 / 首次签名

`build.sh` 在 `yymc.keystore` 不存在时用 `keytool` 现生成一把（store/key 密码都是 `android`，别名 `yymc`）。想用自己的正式签名，改 `apksigner` 那两个参数即可：

```bash
keytool -genkeypair -keystore my.keystore -storepass <pass> -keypass <pass> \
  -alias yymc -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=YYMC"
"$BT/apksigner.bat" sign --ks my.keystore --ks-pass pass:<pass> --key-pass pass:<pass> \
  --min-sdk-version 24 --out YYMC.apk build/app-aligned.apk
```

> 换签名必须**卸载重装**（Android 不允许同包名不同签名覆盖安装）。

---

## 三、验证产物

```bash
BT=/path/to/build-tools/31.0.0

# 清单与最低版本
"$BT/aapt2.exe" dump badging YYMC.apk | head -6

# 签名
"$BT/apksigner.bat" verify -v YYMC.apk

# 内容自检：条目数、classes.dex 在不在、assets 数量
python - <<'PY'
import zipfile, re
z = zipfile.ZipFile("YYMC.apk")
names = z.namelist()
print("条目:", len(names))
print("classes.dex:", "classes.dex" in names)
print("类数:", len(set(re.findall(rb"Lcn/mdy/yymc/[A-Za-z/]+;", z.read("classes.dex")))))
print("assets/site:", len([n for n in names if n.startswith("assets/site/")]))
print("testzip:", "OK" if z.testzip() is None else "BAD")
PY
```

期望：条目 **4860**、`classes.dex` 在、类数 **13**、`assets/site` **4854**、`testzip: OK`。

装到手机上的观察点：打开 → 出现镜像的加载提示 → 游戏标题画面（说明 assets 读取与 WebView 回环都通）；进世界按暂停 →「开房间」出邀请码（说明 `/rooms/p2p` 的 WS 隧道通）。

---

## 四、不用真机的自测（推荐先跑这个）

中继核心是纯 `java.*`，桌面可以跑同一份 class：

```bash
# 1) 编译中继（含桌面入口 DevMain / FileAssets）
javac -nowarn -d rt $(find src -name '*.java')

# 2) 起一个本地假上游（会校验 Origin，模拟官方服务的白名单行为）
node mock_upstream.mjs &

# 3) 让中继指向这个假上游（最后参数 plain = 不加密）
java -cp rt cn.mdy.yymc.relay.DevMain "$(pwd)/site" 8734 127.0.0.1 9443 plain &

# 4) 验三条路
curl -s http://127.0.0.1:8734/rooms/api/live        # 应返回 {"mock":true,...,"origin":"https://www.bilibili.com"}
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8734/                       # 200
curl -s -r 0-1023 -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8734/game/OpusMC.v6.html   # 206

node -e "const w=new WebSocket('ws://127.0.0.1:8734/rooms/p2p');w.onopen=()=>w.send(JSON.stringify({t:'host',code:'MOCK01',name:'x'}));w.onmessage=e=>{console.log(e.data.slice(0,160));process.exit(0)};setTimeout(()=>process.exit(0),6000)"
```

看到 `{"t":"hosted",...}` 就说明握手、accept 计算、帧搬运都对。`DevMain`/`FileAssets` 在打包 APK 时被 `build.sh` 排除，不会进 dex。

---

## 五、目录结构

```
site/                                  随仓库发布的资源（148 MB）
AndroidManifest.xml                    minSdk 24 / target 31 / 横屏全屏 / usesCleartextTraffic
build.sh                               六步流水线
mock_upstream.mjs                      本地假上游（自测用）
src/cn/mdy/yymc/MainActivity.java      WebView + 启动中继 + 全屏常亮
src/cn/mdy/yymc/AndroidAssets.java     AssetManager → AssetSource
src/cn/mdy/yymc/relay/
  Relay.java        监听 127.0.0.1:8720（被占自动 +1），accept 起线程
  Acceptor.java     监听循环
  Conn.java         读请求头并按路径分发
  HttpIn.java       按行读 / 定长读 / 交出缓冲残留
  AssetHandler.java 出静态文件（含 Range → 206）
  RoomsProxy.java   裸 socket 反代 HTTP（补 Origin）
  WsTunnel.java     本地算 Sec-WebSocket-Accept，双向搬帧
  Pump.java         单向搬运
  Upstream.java     上游地址（默认官方，可指到本地 mock）
  AssetSource.java / FileAssets.java / Mime.java / DevMain.java
```

---

## 六、已知坑（都是实测踩过的）

1. **`d8.bat` 在 JDK 9+ 上直接起不来**：它内部拼了 `-Djava.ext.dirs`，已被移除。所以 `build.sh` 不走 `.bat`，改成 `java -cp $BT34/lib/d8.jar com.android.tools.r8.D8 …`。换 build-tools 版本也不解决（31 和 34 一样）。
2. **`javac` 生成的 `$` 类会让 d8 崩**：匿名内部类（`Foo$1`）与具名内部类（`Foo$Bar`）都会触发 `NullPointerException: Cannot invoke "String.length()"`。所以本仓库所有类都是**顶层类**；改代码后可以用 `find build/classes -name '*$*'` 检查（应为 0）。
3. **`HttpURLConnection` 会静默丢掉 `Origin`**（JDK 与 Android 都把它当 restricted header）。用它做反代会一直 403，上游日志里根本看不到 Origin。所以 `RoomsProxy` 自己拼 HTTP/1.1 走裸 socket，顺带处理 `Content-Length` 与 `Transfer-Encoding: chunked`。
4. **`classes.dex` 没进 APK 会闪退**：`build.sh` 第 5 步是把 dex 追加到 APK 根，若这一步路径写错（比如写死成别的目录）会静默产出一个没有 dex 的包，日志只多一行 `Duplicate name` 警告。用第三节的自检脚本确认 `classes.dex: True`。
5. **`usesCleartextTraffic` 必须为 true**：页面是 `http://127.0.0.1:PORT`，Android 9+ 默认禁止明文。
6. **端口**：默认 8720，被占用自动往后找；真机上若装了两个副本会各占一个端口。
7. **模拟器**：本项目不依赖模拟器。某些环境里 emulator 与 system image 版本不匹配会启动即退，不影响构建与真机安装。
8. **Android 11+** 侧载需要允许「安装未知来源应用」。

---

## 七、说明

- 音效只收 AAC（`.m4a`）：所有现代浏览器都能解；`.ogg` 仅旧浏览器兜底。
- 自带 keystore 是自签名，仅供侧载；上架应用商店需换正式签名。
- 本仓库不包含任何账号/识别码相关的工具或脚本。
