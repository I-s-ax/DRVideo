#!/data/data/com.termux/files/usr/bin/bash
# Compila TikTok DL en Termux -> build/TikTokDL.apk
set -e
cd "$(dirname "$0")"

# Herramientas (solo la primera vez)
for t in aapt d8 apksigner javac keytool zip unzip curl; do
  command -v $t >/dev/null || { pkg install -y aapt d8 apksigner openjdk-17 zip unzip curl; break; }
done

JAR="$PREFIX/share/aapt/android.jar"
[ -f "$JAR" ] || JAR=$(find "$PREFIX" -name android.jar 2>/dev/null | head -1)
[ -f "$JAR" ] || { echo "No encontré android.jar (reinstala: pkg install aapt)"; exit 1; }

# Motor VLC (se descarga una vez, ~170 MB, y se guarda en vlc-cache/)
VLC_VER=3.7.7
if [ ! -f "vlc-cache/$VLC_VER/classes.jar" ]; then
  echo "[0/4] Descargando motor VLC $VLC_VER..."
  rm -rf vlc-cache && mkdir -p "vlc-cache/$VLC_VER"
  curl -fL -o vlc-cache/libvlc.aar \
    "https://repo1.maven.org/maven2/org/videolan/android/libvlc-all/$VLC_VER/libvlc-all-$VLC_VER.aar"
  unzip -q vlc-cache/libvlc.aar 'classes.jar' 'jni/arm64-v8a/*' -d "vlc-cache/$VLC_VER"
  rm vlc-cache/libvlc.aar
fi
VLC="vlc-cache/$VLC_VER"

rm -rf build && mkdir -p build/classes build/apk/lib/arm64-v8a
echo "[1/4] Empaquetando manifest..."
aapt package -f -M AndroidManifest.xml -S res -I "$JAR" -F build/base.apk

echo "[2/4] Compilando Java..."
javac --release 8 -classpath "$JAR:$VLC/classes.jar" -d build/classes $(find src -name '*.java') 2>/dev/null \
  || javac -source 8 -target 8 -bootclasspath "$JAR" -classpath "$VLC/classes.jar" -d build/classes $(find src -name '*.java')

echo "[3/4] Convirtiendo a dex..."
d8 --min-api 21 --lib "$JAR" --output build $(find build/classes -name '*.class') "$VLC/classes.jar"
(cd build && zip -q -j base.apk classes.dex)
cp "$VLC"/jni/arm64-v8a/*.so build/apk/lib/arm64-v8a/
(cd build/apk && zip -q -r ../base.apk lib)

echo "[4/4] Firmando..."
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android \
  -keypass android -alias debug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Isa"
apksigner sign --ks debug.keystore --ks-pass pass:android --out build/TikTokDL.apk build/base.apk

cp build/TikTokDL.apk ~/storage/downloads/ 2>/dev/null && echo "Listo: Descargas/TikTokDL.apk" \
  || echo "Listo: $(pwd)/build/TikTokDL.apk (corre termux-setup-storage para copiarlo a Descargas)"
