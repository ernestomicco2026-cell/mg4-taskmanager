#!/bin/sh
# Compila MG4-TaskManager.apk senza Android Studio/Gradle.
#
# Serve: JDK, Python 3 con Pillow, android.jar (API 28) e un dexer:
#   - D8  (build-tools moderni)   -> D8=/percorso/build-tools/x.y.z/d8
#   - dx.jar (dexer AOSP storico) -> DX_JAR=/percorso/dx.jar
# Firma: KEYSTORE / KEYSTORE_PASS (predefiniti: tm.keystore / taskmanager)
# Versione: file VERSION, oppure VERSION_NAME=1.2.3 (lo imposta la build di GitHub dal tag).
set -e
cd "$(dirname "$0")"
: "${ANDROID_JAR:?imposta ANDROID_JAR (android.jar di API 28)}"
KEYSTORE="${KEYSTORE:-tm.keystore}"
KEYSTORE_PASS="${KEYSTORE_PASS:-taskmanager}"

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
cp build/unsigned.apk MG4-TaskManager.apk
# Usare sempre la stessa chiave: altrimenti gli aggiornamenti non si installano sopra la versione precedente.
jarsigner -keystore "$KEYSTORE" -storepass "$KEYSTORE_PASS" -sigalg SHA256withRSA -digestalg SHA-256 \
          MG4-TaskManager.apk tm
jarsigner -verify MG4-TaskManager.apk
echo "Creato MG4-TaskManager.apk"
