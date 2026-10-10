#!/bin/sh
# Compila le due edizioni di MG4-TaskManager senza Android Studio/Gradle.
#
# Serve: JDK, Python 3 con Pillow, android.jar (API 28) e un dexer:
#   - D8  (build-tools moderni)   -> D8=/percorso/build-tools/x.y.z/d8
#   - dx.jar (dexer AOSP storico) -> DX_JAR=/percorso/dx.jar
# Versione: file VERSION, oppure VERSION_NAME=1.2.3 (lo imposta la build di GitHub dal tag).
#
# Produce (lo stesso APK firmato in due modi diversi):
#   MG4-TaskManager.apk         firma propria (tm.keystore) - modalità ADB/base
#   MG4-TaskManager-system.apk  firma platform AOSP (signing/platform.keystore) - modalità sistema
# L'edizione di sistema si salta se la chiave platform non è presente.
set -e
cd "$(dirname "$0")"
: "${ANDROID_JAR:?imposta ANDROID_JAR (android.jar di API 28)}"
KEYSTORE="${KEYSTORE:-tm.keystore}"
KEYSTORE_PASS="${KEYSTORE_PASS:-taskmanager}"
PLATFORM_KEYSTORE="${PLATFORM_KEYSTORE:-signing/platform.keystore}"
PLATFORM_PASS="${PLATFORM_PASS:-platform}"

rm -rf build && mkdir -p build/classes
javac -source 8 -target 8 -encoding UTF-8 -Xlint:-options -bootclasspath "$ANDROID_JAR" \
      -d build/classes $(find src -name "*.java")

if [ -n "$D8" ]; then
  (cd build/classes && jar cf ../classes.jar .)
  "$D8" --release --min-api 28 --lib "$ANDROID_JAR" --output build build/classes.jar
elif [ -n "$DX_JAR" ]; then
  java -cp "$DX_JAR" com.android.dx.command.Main --dex --min-sdk-version=28 --output=build/classes.dex build/classes
else
  echo "Imposta D8 oppure DX_JAR" >&2; exit 1
fi

python3 build_apk.py build

sign() { # <apk> <keystore> <storepass> <alias>
  cp build/unsigned.apk "$1"
  jarsigner -keystore "$2" -storepass "$3" -sigalg SHA256withRSA -digestalg SHA-256 "$1" "$4"
  jarsigner -verify "$1" >/dev/null && echo "Creato $1"
}

# Edizione normale: firma propria stabile (gli aggiornamenti si installano sopra solo con la stessa chiave).
sign MG4-TaskManager.apk "$KEYSTORE" "$KEYSTORE_PASS" tm

# Edizione di sistema: firma con la chiave platform dell'auto (chiave pubblica AOSP).
# Se manca il keystore ma ci sono i PEM/PK8 pubblici in signing/, lo genera al volo.
if [ ! -f "$PLATFORM_KEYSTORE" ] && [ -f signing/platform.pk8 ] && [ -f signing/platform.x509.pem ]; then
  openssl pkcs8 -inform DER -nocrypt -in signing/platform.pk8 -out signing/platform.key
  openssl pkcs12 -export -in signing/platform.x509.pem -inkey signing/platform.key \
                 -name platform -out signing/platform.p12 -passout pass:"$PLATFORM_PASS"
  keytool -importkeystore -srckeystore signing/platform.p12 -srcstoretype PKCS12 -srcstorepass "$PLATFORM_PASS" \
          -destkeystore "$PLATFORM_KEYSTORE" -deststoretype JKS -deststorepass "$PLATFORM_PASS" \
          -destkeypass "$PLATFORM_PASS" -noprompt 2>/dev/null
  rm -f signing/platform.key signing/platform.p12
fi
if [ -f "$PLATFORM_KEYSTORE" ]; then
  sign MG4-TaskManager-system.apk "$PLATFORM_KEYSTORE" "$PLATFORM_PASS" platform
else
  echo "Chiave platform assente ($PLATFORM_KEYSTORE): edizione di sistema saltata"
fi
