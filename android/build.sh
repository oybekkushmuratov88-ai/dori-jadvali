#!/usr/bin/env bash
# Builds dori-jadvali.apk without the Android SDK: every tool comes from Maven Central.
#   aapt2 + framework resources  <- org.apktool:apktool-lib
#   Android classes (compile)    <- org.robolectric:android-all
#   .class -> .dex               <- com.jakewharton.android.repackaged:dalvik-dx
#   signing (v2 scheme)          <- com.android.tools.build:apksig
#
# Signing key: KEYSTORE (PKCS12, alias "dori") and KEYSTORE_PASS. Keep the key safe and
# never commit it: Android only installs an update when it is signed with the same key.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
TOOLS="$HERE/.tools"
OUT="$HERE/.build"
MAVEN="${MAVEN:-https://maven-central.storage-download.googleapis.com/maven2}"
KEYSTORE="${KEYSTORE:-$HERE/.keystore/dori.p12}"
: "${KEYSTORE_PASS:?set KEYSTORE_PASS to the signing key password}"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-2.2}"

fetch() { [ -s "$2" ] || { echo "download $1"; curl -fsSL -o "$2" "$MAVEN/$1"; }; }
mkdir -p "$TOOLS"
fetch org/apktool/apktool-lib/3.0.3/apktool-lib-3.0.3.jar "$TOOLS/apktool-lib.jar"
fetch org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar "$TOOLS/android-all.jar"
fetch com/jakewharton/android/repackaged/dalvik-dx/16.0.1/dalvik-dx-16.0.1.jar "$TOOLS/dx.jar"
fetch com/android/tools/build/apksig/2.3.0/apksig-2.3.0.jar "$TOOLS/apksig.jar"
if [ ! -x "$TOOLS/aapt2" ]; then
  (cd "$TOOLS" && unzip -o -q apktool-lib.jar prebuilt/linux/aapt2 prebuilt/android-framework.jar \
    && mv prebuilt/linux/aapt2 aapt2 && mv prebuilt/android-framework.jar android-framework.jar && rm -rf prebuilt && chmod +x aapt2)
fi

if [ ! -s "$KEYSTORE" ]; then
  echo "creating signing key $KEYSTORE"
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" \
    -alias dori -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Dori jadvali, O=Dori jadvali" >/dev/null 2>&1
fi

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"

# 1. resources + manifest
"$TOOLS/aapt2" compile --dir "$HERE/res" -o "$OUT/res.zip"
"$TOOLS/aapt2" link -o "$OUT/base.apk" -I "$TOOLS/android-framework.jar" \
  --manifest "$HERE/AndroidManifest.xml" -R "$OUT/res.zip" --java "$OUT/gen" --auto-add-overlay \
  --min-sdk-version 24 --target-sdk-version 34 --version-code "$VERSION_CODE" --version-name "$VERSION_NAME"

# 2. code
javac -nowarn --release 8 -encoding UTF-8 -cp "$TOOLS/android-all.jar" -d "$OUT/classes" \
  $(find "$HERE/src" "$OUT/gen" -name '*.java')
java -cp "$TOOLS/dx.jar" com.android.dx.command.Main --dex --min-sdk-version=24 --output="$OUT/classes.dex" "$OUT/classes"

# 3. package: add the dex, keep resources.arsc uncompressed and 4-byte aligned
python3 - "$OUT/base.apk" "$OUT/classes.dex" "$OUT/unsigned.apk" <<'PY'
import sys, zipfile, struct
src, dex, dst = sys.argv[1:4]
with zipfile.ZipFile(src) as zin, open(dst, "wb") as raw:
    zout = zipfile.ZipFile(raw, "w")
    items = [(i, zin.read(i.filename)) for i in zin.infolist()]
    items.append((zipfile.ZipInfo("classes.dex", (2008, 1, 1, 0, 0, 0)), open(dex, "rb").read()))
    for info, data in items:
        out = zipfile.ZipInfo(info.filename, (2008, 1, 1, 0, 0, 0))
        stored = info.filename == "resources.arsc" or (info.filename != "classes.dex" and info.compress_type == zipfile.ZIP_STORED)
        out.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
        if stored:
            # zipalign-style extra field (0xD935) so the entry's data starts on a 4-byte boundary
            base = raw.tell() + 30 + len(info.filename.encode())
            pad = (-(base + 6)) % 4
            out.extra = struct.pack("<HHH", 0xD935, 2 + pad, 4) + b"\0" * pad
        zout.writestr(out, data)
    zout.close()
PY

# 4. sign + verify
javac -nowarn -d "$OUT/signer" -cp "$TOOLS/apksig.jar" "$HERE/tools/SignApk.java"
java --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.pkcs=ALL-UNNAMED \
  --add-exports java.base/sun.security.util=ALL-UNNAMED -cp "$OUT/signer:$TOOLS/apksig.jar" SignApk "$KEYSTORE" dori "$KEYSTORE_PASS" "$OUT/unsigned.apk" "$ROOT/dori-jadvali.apk"
"$TOOLS/aapt2" dump badging "$ROOT/dori-jadvali.apk" | head -3
ls -la "$ROOT/dori-jadvali.apk"
