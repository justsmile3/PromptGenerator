#!/usr/bin/env bash
#
# SD Prompt Studio — POSIX build script (Linux / macOS / CI)
# Mirrors android/build.ps1 without any PowerShell dependency.
#
# Usage:
#   export ANDROID_SDK_ROOT=/path/to/android-sdk
#   bash android/build.sh
#
# Optional environment overrides:
#   BT_VERSION=34.0.0        build-tools version
#   PLATFORM=android-33      compile platform
#   OUT=/path/to/out.apk     output path
#   KEYSTORE=/path/ks        keystore (auto-generated debug one if missing)
#   KEYSTORE_PASSWORD=...    store password   (default: android)
#   KEY_ALIAS=...            key alias        (default: androiddebugkey)
#   KEY_PASSWORD=...         key password     (default: android)
#
set -euo pipefail

PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$PROJ")"

# ---------------------------------------------------------------- config ----
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ]; then
  echo "ERROR: set ANDROID_SDK_ROOT (or ANDROID_HOME) to your Android SDK path" >&2
  exit 1
fi

BT_VERSION="${BT_VERSION:-34.0.0}"
PLATFORM="${PLATFORM:-android-33}"
BT="$SDK/build-tools/$BT_VERSION"
ANDROID_JAR="$SDK/platforms/$PLATFORM/android.jar"

AAPT2="$BT/aapt2"
AAPT="$BT/aapt"
D8="$BT/d8"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"

OUT="${OUT:-$ROOT/PromptGenerator.apk}"
ASSETS="$PROJ/assets"
CLASSES="$PROJ/classes"

KS="${KEYSTORE:-$PROJ/debug.keystore}"
KS_PASS="${KEYSTORE_PASSWORD:-android}"
KS_ALIAS="${KEY_ALIAS:-androiddebugkey}"
KEY_PASS="${KEY_PASSWORD:-android}"

# --------------------------------------------------------------- helpers ----
step() { printf '\n=== %s ===\n' "$1"; }

die() { echo "ERROR: $*" >&2; exit 1; }

# ------------------------------------------------------------ sanity check --
for tool in "$AAPT2" "$AAPT" "$ZIPALIGN" "$APKSIGNER"; do
  [ -x "$tool" ] || die "missing $tool (install build-tools;$BT_VERSION)"
done
[ -f "$ANDROID_JAR" ] || die "missing $ANDROID_JAR (install platforms;$PLATFORM)"
[ -f "$ROOT/src/index.html" ] || die "missing src/index.html"

# ---------------------------------------------------------------- prepare ---
mkdir -p "$ASSETS"
cp "$ROOT/src/index.html" "$ASSETS/index.html"

rm -rf "$CLASSES"
rm -f "$PROJ/compiled.zip" "$PROJ/base.apk" "$PROJ/unsigned.apk" \
      "$PROJ/aligned.apk" "$PROJ/classes.dex" "$PROJ/sources.txt" "$OUT"
mkdir -p "$CLASSES"

# --------------------------------------------------------------- keystore ---
if [ ! -f "$KS" ]; then
  step "0/7 generate debug keystore"
  # PKCS12 keystores ignore -keypass and print a warning; keep it quiet.
  keytool -genkeypair \
    -keystore "$KS" \
    -alias "$KS_ALIAS" \
    -storepass "$KS_PASS" \
    -keypass "$KEY_PASS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
  echo "generated $KS"
fi

# --------------------------------------------------------- 1. compile res ---
step "1/7 compile resources (aapt2 compile)"
"$AAPT2" compile --dir "$PROJ/res" -o "$PROJ/compiled.zip"

# ------------------------------------------------------------ 2. link res ---
step "2/7 link resources + manifest + assets (aapt2 link)"
"$AAPT2" link \
  -o "$PROJ/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$PROJ/AndroidManifest.xml" \
  -A "$ASSETS" \
  "$PROJ/compiled.zip"

# ----------------------------------------------------------- 3. javac -------
step "3/7 compile Java (javac)"
find "$PROJ/src" -name '*.java' > "$PROJ/sources.txt"
javac -encoding UTF-8 --release 8 \
  -classpath "$ANDROID_JAR" \
  -d "$CLASSES" \
  @"$PROJ/sources.txt"

# ------------------------------------------------------------- 4. d8 --------
step "4/7 dex (d8)"
# NOTE: every .class must be dexed, including inner classes such as
# MainActivity$Bridge.class. Passing only the outer class causes
# NoClassDefFoundError at runtime.
CLASS_FILES=()
while IFS= read -r f; do CLASS_FILES+=("$f"); done < <(find "$CLASSES" -name '*.class')
if [ "${#CLASS_FILES[@]}" -eq 0 ]; then
  die "javac produced no .class files"
fi
echo "dexing ${#CLASS_FILES[@]} class file(s)"
"$D8" --lib "$ANDROID_JAR" --min-api 21 --output "$PROJ" "${CLASS_FILES[@]}"

# --------------------------------------------------------- 5. add dex -------
step "5/7 add classes.dex to apk (aapt add)"
cp "$PROJ/base.apk" "$PROJ/unsigned.apk"
( cd "$PROJ" && "$AAPT" add unsigned.apk classes.dex )

# ------------------------------------------------------- 6. zipalign --------
step "6/7 zipalign"
"$ZIPALIGN" -f 4 "$PROJ/unsigned.apk" "$PROJ/aligned.apk"

# --------------------------------------------------------- 7. sign ----------
step "7/7 sign (apksigner)"
"$APKSIGNER" sign \
  --ks "$KS" \
  --ks-pass "pass:$KS_PASS" \
  --ks-key-alias "$KS_ALIAS" \
  --key-pass "pass:$KEY_PASS" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --out "$OUT" \
  "$PROJ/aligned.apk"

# ---------------------------------------------------------- verify ----------
step "verify"
# NOTE: do NOT pipe apksigner into `head`. With `set -o pipefail`, head closing
# the pipe early makes apksigner die from SIGPIPE and the whole build "fails"
# even though signing succeeded.
"$APKSIGNER" verify --print-certs "$OUT" > "$PROJ/verify.txt" 2>&1 || true
head -n 6 "$PROJ/verify.txt" || true
if grep -q "Signer #1 certificate DN" "$PROJ/verify.txt"; then
  echo "signature: OK"
else
  die "signature verification failed - see $PROJ/verify.txt"
fi

echo ""
echo "BUILD OK -> $OUT"
if command -v du >/dev/null 2>&1; then
  echo "size: $(du -h "$OUT" | cut -f1)"
fi
