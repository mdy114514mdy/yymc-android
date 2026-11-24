# YYMC 安卓版

APK 里跑一个只绑 `127.0.0.1` 的中继，WebView 直接加载它，于是页面是同源 http 回环：静态资源、`/rooms` 的 HTTP 反代、`/rooms/p2p` 的 WebSocket 隧道**全在设备上完成**，登录与联机不依赖任何云端中转。

Release 里的 `YYMC.apk` 约 107 MB（内含游戏本体 48.5 MB + 4849 个 `.m4a` 共 89 MB）。

## 用法

安装后打开即可（横屏、全屏、常亮）。首次启动会在本机 `127.0.0.1:8720` 起中继（被占用自动 +1）。

## 构建

不依赖 gradle / androidx，直接调用 SDK 工具。需要 JDK，以及 Android SDK 的 `build-tools`（`aapt2`、`d8`、`zipalign`、`apksigner`）和任一 `platforms/android-XX/android.jar`。

```bash
# 先按需改 build.sh 顶部的 BT / BT34 / PLAT 路径
bash build.sh
```

流水线：`aapt2 link`（把 `assets/site/` 打进 APK）→ `javac --release 8` → `d8` → 把 `classes.dex` 以 STORED 方式写回 APK 根 → `zipalign -f 4` → `apksigner sign`。

两个 JDK 25 上的坑已在脚本里绕开：`d8.bat` 会拼 `-Djava.ext.dirs`（JDK 9+ 拒绝启动），改直接 `java -cp lib/d8.jar`；`javac` 25 生成的匿名/具名内部类（`Foo$1`、`Foo$Bar`）会让 `d8` 抛 `NullPointerException`，所以本仓库所有类都是**顶层类**。

## 结构

```
src/cn/mdy/yymc/MainActivity.java     WebView + 启动中继 + 全屏横屏
src/cn/mdy/yymc/AndroidAssets.java    AssetManager → AssetSource
src/cn/mdy/yymc/relay/
  Relay.java        监听 127.0.0.1，accept 起线程
  Acceptor.java     监听循环
  Conn.java         读请求头并分发
  HttpIn.java       按行读 + 定长读 + 交出缓冲残留
  AssetHandler.java 出静态文件（含 Range / 206）
  RoomsProxy.java   裸 socket 反代 HTTP（不能自动带 Origin 的坑见下）
  WsTunnel.java     本地算 Sec-WebSocket-Accept，双向搬帧
  Pump.java         单向搬运
  Upstream.java     上游地址（默认官方，可指到本地 mock）
  Mime.java / AssetSource.java / FileAssets.java / DevMain.java
```

中继核心是纯 `java.*`，所以能在电脑上先跑通再塞进 APK。

## 自测（不需要真机）

```bash
javac -d rt $(find src -name '*.java')
java -cp rt cn.mdy.yymc.relay.DevMain <mirror目录> 8734 127.0.0.1 9443 plain &
node mock_upstream.mjs &
curl -s http://127.0.0.1:8734/rooms/api/live   # mock 会校验 Origin，错了返回 403
```

`DevMain` 是桌面入口，`FileAssets` 用文件系统代替 `AssetManager`；两者在打包 APK 时被 `build.sh` 排除。

## 一个必须知道的坑

`HttpURLConnection`（JDK 与 Android 都是）把 **Origin 当成 restricted header 静默丢弃**，用它做反代请求会稳定 403。所以 `RoomsProxy` 自己拼 HTTP/1.1 走裸 socket，顺带处理 `Content-Length` / `Transfer-Encoding: chunked`。

## 需要自备

仓库不含游戏本体与音效。构建前准备 `assets/site/` 下的 `index.html`、`game/OpusMC.v6.html`、`game/js/chunk_worker.js`、`game/sounds/sfx/**/*.m4a`（`build.sh` 里有自动收集步骤，路径指向本地 mirror 目录）。

## 说明

- 签名用的是自带 keystore 的自签名，仅供侧载；上架请换自己的正式签名（换签名必须卸载重装）。
- 不包含任何账号/识别码相关工具。
