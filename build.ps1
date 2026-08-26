# Build script for HeyBox Liquid Glass LSPosed module (libxposed api 102)
#
# Prerequisites (adjust $ToolRoot below to where you put them):
#   1. JDK 17+ on PATH (javac, jar, keytool, java)
#   2. Android platform android-34  ->  https://dl.google.com/android/repository/platform-34-ext7_r03.zip
#   3. Android build-tools 34       ->  https://dl.google.com/android/repository/build-tools_r34-windows.zip
#   4. libxposed api 102 AAR        ->  https://repo1.maven.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar
#   5. QWEA0 renderer main AAR      ->  https://jitpack.io/com/github/QWEA0/liquidglass/90f4ea28e3/liquidglass-90f4ea28e3.aar
#   6. kotlin-stdlib 2.1.21 jar     ->  https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.1.21/kotlin-stdlib-2.1.21.jar
#
# Expected layout under $ToolRoot:
#   android\platform\android-34\android.jar
#   android\buildtools\android-14\           (aapt2.exe, d8.bat, zipalign.exe, lib\apksigner.jar)
#   xapi\aar\classes.jar                     (classes.jar extracted from the api AAR)
#   qwea0\main\aar\classes.jar + jni\        (extracted from the QWEA0 main AAR)
#   qwea0\kotlin-stdlib.jar
$ErrorActionPreference = 'Stop'

$proj     = $PSScriptRoot
$ToolRoot = "D:\working\heybox\tools"   # <-- adjust me
$tools    = $ToolRoot
$apiJar   = Join-Path $tools 'xapi\aar\classes.jar'          # io.github.libxposed:api:102
$plat     = Join-Path $tools 'android\platform\android-34\android.jar'
$bt       = Join-Path $tools 'android\buildtools\android-14'
$out      = Join-Path $proj 'build'

Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$out\classes", "$out\dex" | Out-Null

Write-Host '[1/7] javac'
$sources = Get-ChildItem -Recurse -Filter '*.java' (Join-Path $proj 'src') | ForEach-Object { $_.FullName }
$qwea0 = Join-Path $ToolRoot 'qwea0'
& javac -encoding UTF-8 --release 11 -Xlint:-options `
    -classpath "$plat;$apiJar;$qwea0\main\aar\classes.jar;$qwea0\kotlin-stdlib.jar" `
    -d "$out\classes" `
    $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '[2/7] jar + d8 (dex, merged with QWEA0 renderer + kotlin-stdlib)'
$clsJar = "$out\classes.jar"
if (Test-Path $clsJar) { Remove-Item $clsJar }
Push-Location "$out\classes"
& jar cf $clsJar .
Pop-Location
$qwea0 = Join-Path $ToolRoot 'qwea0'
& (Join-Path $bt 'd8.bat') --release --lib $plat --min-api 26 `
    --output "$out\dex" `
    $clsJar `
    (Join-Path $qwea0 'main\aar\classes.jar') `
    (Join-Path $qwea0 'kotlin-stdlib.jar')
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '[3/7] aapt2 compile/link'
& (Join-Path $bt 'aapt2.exe') compile --dir (Join-Path $proj 'res') -o "$out\res.zip"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }
& (Join-Path $bt 'aapt2.exe') link `
    -o "$out\base.apk" `
    -I $plat `
    --manifest (Join-Path $proj 'AndroidManifest.xml') `
    "$out\res.zip"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '[4/7] inject classes.dex + lib + META-INF + META-INF/xposed'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Copy-Item "$out\base.apk" "$out\unsigned.apk" -Force
$zip = [System.IO.Compression.ZipFile]::Open("$out\unsigned.apk", 'Update')
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, "$out\dex\classes.dex", 'classes.dex') | Out-Null
    Get-ChildItem -Recurse (Join-Path $proj 'META-INF') | ForEach-Object {
        if (-not $_.PSIsContainer) {
            $rel = $_.FullName.Substring($proj.Length + 1).Replace('\', '/')
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $zip, $_.FullName, $rel) | Out-Null
        }
    }
    # native libs for the QWEA0 renderer classic pipeline
    $jniRoot = Join-Path $qwea0 'main\aar\jni'
    Get-ChildItem -Recurse $jniRoot -Filter '*.so' | ForEach-Object {
        $abi = Split-Path (Split-Path $_.FullName -Parent) -Leaf
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip, $_.FullName, "lib/$abi/$($_.Name)",
            [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $zip.Dispose()
}

Write-Host '[5/7] zipalign'
& (Join-Path $bt 'zipalign.exe') -f -p 4 "$out\unsigned.apk" "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

Write-Host '[6/7] sign'
$ks = Join-Path $proj 'debug.keystore'
if (-not (Test-Path $ks)) {
    & keytool -genkeypair -keystore $ks -storepass android -keypass android `
        -alias androiddebugkey -keyalg RSA -validity 10000 `
        -dname 'CN=Android Debug,O=Android,C=US'
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}
# Release APK output: ../release/  (bump the version in the filename on release)
$finalApk = Join-Path $proj '..\release\HeyBoxLiquidGlass-v1.0.2.apk'
& java -cp (Join-Path $bt 'lib\apksigner.jar') com.android.apksigner.ApkSignerTool sign `
    --ks $ks --ks-pass pass:android --key-pass pass:android `
    --out $finalApk "$out\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

& java -cp (Join-Path $bt 'lib\apksigner.jar') com.android.apksigner.ApkSignerTool verify --print-certs $finalApk
Get-Item $finalApk | Select-Object FullName, Length

































