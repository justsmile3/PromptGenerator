$ErrorActionPreference = "Stop"
# javac 的"注: ...已过时的 API"提示走 stderr，不要让它中断构建；
# 真正的失败由下面的 $LASTEXITCODE 判断。
$PSNativeCommandUseErrorActionPreference = $false

$SDK        = "C:\Users\AA\AppData\Local\Android\Sdk"
$BT         = "$SDK\build-tools\34.0.0"
$AAPT2      = "$BT\aapt2.exe"
$AAPT       = "$BT\aapt.exe"
$D8         = "$BT\d8.bat"
$ZIPALIGN   = "$BT\zipalign.exe"
$APKSIGNER  = "$BT\apksigner.bat"
$ANDROID_JAR= "$SDK\platforms\android-33\android.jar"
$JDK        = "C:\Users\AA\AppData\Local\Temp\jdk17\jdk-17.0.20.1+1"
$KEYSTORE   = "C:\Users\AA\.android\debug.keystore"
$PROJ       = "C:\Users\AA\Documents\deepseek-harness\default-workspace\apk-build"
$OUT        = "C:\Users\AA\Documents\deepseek-harness\default-workspace\PromptGenerator.apk"

# make sure tools use the working JDK (not the broken Oracle shim on PATH)
$env:JAVA_HOME = $JDK
$env:PATH      = "$JDK\bin;$env:PATH"

Set-Location $PROJ

function Step($name) { Write-Host ""; Write-Host "=== $name ===" -ForegroundColor Cyan }

Step "1/7 compile resources (aapt2 compile)"
& $AAPT2 compile --dir "$PROJ\res" -o "$PROJ\compiled.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Step "2/7 link resources + manifest + assets (aapt2 link)"
& $AAPT2 link -o "$PROJ\base.apk" -I $ANDROID_JAR --manifest "$PROJ\AndroidManifest.xml" -A "$PROJ\assets" "$PROJ\compiled.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Step "3/7 compile Java (javac)"
New-Item -ItemType Directory -Force -Path "$PROJ\classes" | Out-Null
& "$JDK\bin\javac.exe" -encoding UTF-8 --release 8 -classpath $ANDROID_JAR -d "$PROJ\classes" "$PROJ\src\com\promptgen\app\MainActivity.java"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Step "4/7 dex (d8)"
$classFiles = (Get-ChildItem "$PROJ\classes" -Recurse -Filter *.class).FullName
Write-Host ("dexing " + $classFiles.Count + " class file(s)")
& $D8 --lib $ANDROID_JAR --min-api 21 --output "$PROJ" $classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Step "5/7 add classes.dex to apk (aapt add)"
& $AAPT add "$PROJ\base.apk" "classes.dex"
if ($LASTEXITCODE -ne 0) { throw "aapt add failed" }

Step "6/7 zipalign"
& $ZIPALIGN -f 4 "$PROJ\base.apk" "$PROJ\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Step "7/7 sign (apksigner)"
& $APKSIGNER sign --ks $KEYSTORE --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out $OUT "$PROJ\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Step "verify"
& $APKSIGNER verify --verbose $OUT

Write-Host ""
Write-Host "BUILD OK -> $OUT" -ForegroundColor Green
if (Test-Path $OUT) { Write-Host ("size: " + [math]::Round((Get-Item $OUT).Length/1MB,2) + " MB") }
