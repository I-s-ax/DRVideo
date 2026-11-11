#!/data/data/com.termux/files/usr/bin/bash
# Compila TikTok DL en Termux -> build/TikTokDL.apk
set -e
cd "$(dirname "$0")"

# Herramientas (solo la primera vez)
for t in aapt d8 apksigner javac keytool zip; do
  command -v $t >/dev/null || { pkg install -y aapt d8 apksigner openjdk-17 zip; break; }
done

JAR="$PREFIX/share/aapt/android.jar"
[ -f "$JAR" ] || JAR=$(find "$PREFIX" -name android.jar 2>/dev/null | head -1)
[ -f "$JAR" ] || { echo "No encontré android.jar (reinstala: pkg install aapt)"; exit 1; }

rm -rf build && mkdir -p build/classes
echo "[1/4] Empaquetando manifest..."
aapt package -f -M AndroidManifest.xml -I "$JAR" -F build/base.apk

echo "[2/4] Compilando Java..."
javac --release 8 -classpath "$JAR" -d build/classes $(find src -name '*.java') 2>/dev/null \
  || javac -source 8 -target 8 -bootclasspath "$JAR" -d build/classes $(find src -name '*.java')

echo "[3/4] Convirtiendo a dex..."
d8 --min-api 21 --lib "$JAR" --output build $(find build/classes -name '*.class')
(cd build && zip -q -j base.apk classes.dex)

echo "[4/4] Firmando..."
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android \
  -keypass android -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Isa"
apksigner sign --ks debug.keystore --ks-pass pass:android --out build/TikTokDL.apk build/base.apk

cp build/TikTokDL.apk ~/storage/downloads/ 2>/dev/null && echo "Listo: Descargas/TikTokDL.apk" \
  || echo "Listo: $(pwd)/build/TikTokDL.apk (corre termux-setup-storage para copiarlo a Descargas)"
