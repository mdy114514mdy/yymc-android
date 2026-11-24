#!/usr/bin/env bash
# 手工构建 YYMC.apk（不依赖 gradle / androidx）
set -e
BT=/e/android-sdk/build-tools/31.0.0          # aapt2 / zipalign / apksigner
BT34=/e/android-sdk/build-tools/34.0.0        # 新版 d8（31 的 d8 不认 JDK 25 编出的类）
PLAT=/e/android-sdk/platforms/android-31/android.jar
W="$(cd "$(dirname "$0")" && pwd)"   # 脚本所在目录＝仓库根
MIR="$W/site"          # 仓库自带的资源目录
cd "$W"

echo "== 1/6 收集站点资源（只要 v6 + m4a）"
rm -rf assets build YYMC.apk
mkdir -p assets/site/game/js
cp "$MIR/index.html" assets/site/index.html
cp "$MIR/favicon.svg" assets/site/ 2>/dev/null || true
cp "$MIR/game/OpusMC.v6.html" assets/site/game/
cp "$MIR/game/js/chunk_worker.js" assets/site/game/js/
cp "$MIR/game/favicon.ico" assets/site/game/ 2>/dev/null || true
YYMC_W="$W" python - <<'PY'
import os, shutil
src = os.path.join(os.environ["YYMC_W"], "site", "game", "sounds", "sfx")
dst = os.path.join(os.environ["YYMC_W"], "assets", "site", "game", "sounds", "sfx")
n = sz = 0
for root, dirs, files in os.walk(src):
    for f in files:
        if not f.lower().endswith(".m4a"):
            continue
        s = os.path.join(root, f)
        d = os.path.join(dst, os.path.relpath(s, src))
        os.makedirs(os.path.dirname(d), exist_ok=True)
        shutil.copyfile(s, d)
        n += 1; sz += os.path.getsize(s)
print("   sounds %d / %.1f MB" % (n, sz/1048576.0))
PY

echo "== 2/6 aapt2 link"
mkdir -p build
"$BT/aapt2.exe" link -o build/app-unsigned.apk -I "$PLAT" \
  --manifest AndroidManifest.xml -A assets \
  --min-sdk-version 24 --target-sdk-version 31 \
  --version-code 1 --version-name 1.0 -0 m4a
ls -l build/app-unsigned.apk | awk '{printf "   apk(含 assets) %.1f MB\n", $5/1048576}'

echo "== 3/6 javac"
mkdir -p build/classes
javac -nowarn --release 8 -classpath "$PLAT" -d build/classes $(find src -name '*.java' ! -name 'DevMain.java' ! -name 'FileAssets.java') 2>&1 | grep -v "已过时\|详细信息\|注:" || true
echo "   classes: $(find build/classes -name '*.class' | wc -l)"

echo "== 4/6 d8 -> classes.dex"
mkdir -p build/dex
java -cp "$BT34/lib/d8.jar" com.android.tools.r8.D8 --min-api 24 --lib "$PLAT" \
  --output build/dex $(find build/classes -name '*.class')
ls -l build/dex/classes.dex | awk '{printf "   classes.dex %.1f KB\n", $5/1024}'

echo "== 5/6 写入 dex + 对齐"
YYMC_W="$W" python - <<'PY'
import os, zipfile
w = os.environ["YYMC_W"]
apk = os.path.join(w, "build", "app-unsigned.apk")
dex = os.path.join(w, "build", "dex", "classes.dex")
with zipfile.ZipFile(apk, "a", zipfile.ZIP_STORED) as z:
    if "classes.dex" in z.namelist():
        raise SystemExit("classes.dex already present")
    z.write(dex, "classes.dex")
print("   classes.dex 已写入 APK 根")
PY
"$BT/zipalign.exe" -f 4 build/app-unsigned.apk build/app-aligned.apk

echo "== 6/6 签名"
[ -f yymc.keystore ] || keytool -genkeypair -keystore yymc.keystore -storepass android -keypass android \
  -alias yymc -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=YYMC, OU=mirror, O=mdy, C=CN" 2>/dev/null
"$BT/apksigner.bat" sign --ks yymc.keystore --ks-pass pass:android --key-pass pass:android \
  --min-sdk-version 24 --out YYMC.apk build/app-aligned.apk
"$BT/apksigner.bat" verify -v YYMC.apk | head -5
ls -l YYMC.apk | awk '{printf "YYMC.apk %.1f MB\n", $5/1048576}'
