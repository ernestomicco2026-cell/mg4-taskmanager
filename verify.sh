#!/bin/sh
# Controlla gli APK appena compilati con gli strumenti ufficiali di Android (aapt2 + apksigner):
# manifest e risorse leggibili, testi tradotti presenti, firma valida, versione attesa.
# Uso: BUILD_TOOLS=$ANDROID_HOME/build-tools/34.0.0 sh verify.sh [versione attesa]
set -e
cd "$(dirname "$0")"
: "${BUILD_TOOLS:?imposta BUILD_TOOLS (cartella build-tools, es. .../build-tools/34.0.0)}"
EXPECTED="$1"

for apk in MG4-TaskManager.apk MG4-TaskManager-system.apk; do
  [ -f "$apk" ] || continue
  echo "== $apk"
  "$BUILD_TOOLS/aapt2" dump badging "$apk" > build/badging.txt
  head -1 build/badging.txt
  if [ -n "$EXPECTED" ]; then
    grep -q "versionName='${EXPECTED#v}'" build/badging.txt || { echo "versionName diversa da $EXPECTED" >&2; exit 1; }
  fi
  "$BUILD_TOOLS/aapt2" dump xmltree --file AndroidManifest.xml "$apk" > build/manifest.txt
  "$BUILD_TOOLS/aapt2" dump xmltree --file res/xml/tm_keys.xml "$apk" > /dev/null
  "$BUILD_TOOLS/aapt2" dump resources "$apk" > build/resources.txt
  # testi del servizio di accessibilità: default (inglese) + 5 lingue
  for cfg in "" it es pt fr de; do
    n=$(grep -cE "^[[:space:]]*\($cfg\) \"" build/resources.txt || true)
    [ "$n" -ge 2 ] || { echo "Mancano i testi per la lingua '${cfg:-default}'" >&2; cat build/resources.txt >&2; exit 1; }
  done
  "$BUILD_TOOLS/apksigner" verify --min-sdk-version 28 --print-certs "$apk" | grep -m1 "Signer #1 certificate DN"
done
echo "APK verificati"
