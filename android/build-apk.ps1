param(
    [string]$Sdk = (Join-Path $env:LOCALAPPDATA 'Android/Sdk'),
    [string]$Jdk = $env:JAVA_HOME,
    [switch]$PublicBeta
)

$ErrorActionPreference = 'Stop'
$parent = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$fromPublicRepo = Test-Path -LiteralPath (Join-Path $parent 'index.html')
if ($fromPublicRepo) {
    $PublicBeta = $true
    $sourcePage = Join-Path $parent 'index.html'
    $appIcon = Join-Path $parent 'assets/yike-icon.png'
    $outputDirectory = Join-Path $parent 'release'
} else {
    $workspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
    $sourcePage = Join-Path $workspace 'work/yike-timer/dist/index.html'
    $appIcon = Join-Path $workspace 'outputs/一刻-图标.png'
    $outputDirectory = Join-Path $workspace 'outputs'
}
if (-not $Jdk) {
    $javacCommand = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($javacCommand) { $Jdk = Split-Path -Parent (Split-Path -Parent $javacCommand.Source) }
}
if (-not $Jdk) { throw 'Set JAVA_HOME or pass -Jdk to a JDK installation.' }
if ($PublicBeta -and (-not $env:YIKE_SIGNING_STORE -or -not $env:YIKE_SIGNING_PASSWORD)) {
    throw 'Set YIKE_SIGNING_STORE and YIKE_SIGNING_PASSWORD for a signed public build.'
}
$java = Join-Path $Jdk 'bin/java.exe'
$javac = Join-Path $Jdk 'bin/javac.exe'
$keytool = Join-Path $Jdk 'bin/keytool.exe'
$buildTools = Join-Path $Sdk 'build-tools/36.1.0'
$androidJar = Join-Path $Sdk 'platforms/android-34/android.jar'
$aapt = Join-Path $buildTools 'aapt2.exe'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$d8 = Join-Path $buildTools 'lib/d8.jar'
$signer = Join-Path $buildTools 'lib/apksigner.jar'
foreach ($required in @($java, $javac, $keytool, $aapt, $zipalign, $androidJar, $d8, $signer)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing Android build dependency: $required" }
}

function Invoke-BuildTool {
    param([string]$File, [string[]]$Arguments)
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Build tool exited with $LASTEXITCODE`: $File" }
}

$build = Join-Path $PSScriptRoot ('build/' + [guid]::NewGuid().ToString('N'))
$assets = Join-Path $build 'assets'
$resources = Join-Path $build 'res'
$launcher = Join-Path $resources 'mipmap-nodpi'
$classes = Join-Path $build 'classes'
$generated = Join-Path $build 'generated'
$dex = Join-Path $build 'dex'
foreach ($folder in @($assets, $launcher, (Join-Path $resources 'values'), $classes, $generated, $dex, $outputDirectory)) {
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
}
$stagedPage = Join-Path $assets 'index.html'
Copy-Item -LiteralPath $sourcePage -Destination $stagedPage
Copy-Item -LiteralPath $appIcon -Destination (Join-Path $launcher 'yike_launcher.png')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'res/values/app.xml') -Destination (Join-Path $resources 'values/app.xml')
$drawable = New-Item -ItemType Directory -Force -Path (Join-Path $resources 'drawable')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'res/drawable/ic_timer_status.xml') -Destination $drawable.FullName
$raw = New-Item -ItemType Directory -Force -Path (Join-Path $resources 'raw')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'res/raw/break_bell.wav') -Destination $raw.FullName
$manifest = Join-Path $PSScriptRoot 'AndroidManifest.xml'
if ($PublicBeta) {
    [xml]$manifestXml = Get-Content -LiteralPath $manifest -Raw
    $manifestXml.manifest.SetAttribute('package', 'io.github.woshibiantai06.yikefocus')
    $manifestXml.manifest.application.GetAttributeNode('debuggable', 'http://schemas.android.com/apk/res/android').Value = 'false'
    $manifest = Join-Path $build 'AndroidManifest.xml'
    $manifestXml.Save($manifest)
}

$resourceArchive = Join-Path $build 'resources.zip'
$unsigned = Join-Path $build 'unsigned.apk'
$aligned = Join-Path $build 'aligned.apk'
$signed = Join-Path $build 'yike-preview.apk'
Invoke-BuildTool $aapt @('compile', '--dir', $resources, '-o', $resourceArchive)
Invoke-BuildTool $aapt @('link', '-I', $androidJar, '--manifest', $manifest, '--custom-package', 'app.yike.focus',
    '-A', $assets, '--java', $generated, '-o', $unsigned, $resourceArchive)
$javaSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -File -Filter '*.java' | ForEach-Object FullName)
Invoke-BuildTool $javac (@('--release', '8', '-encoding', 'UTF-8', '-classpath', $androidJar, '-d', $classes,
    (Join-Path $generated 'app/yike/focus/R.java')) + $javaSources)
$classFiles = @(Get-ChildItem -LiteralPath $classes -Filter '*.class' -Recurse | ForEach-Object FullName)
Invoke-BuildTool $java (@('-cp', $d8, 'com.android.tools.r8.D8', '--lib', $androidJar, '--min-api', '26', '--output', $dex) + $classFiles)

Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = [System.IO.Compression.ZipFile]::Open($unsigned, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apk, (Join-Path $dex 'classes.dex'), 'classes.dex') | Out-Null
} finally { $apk.Dispose() }
Invoke-BuildTool $zipalign @('-f', '4', $unsigned, $aligned)

if ($PublicBeta) {
    $signingStore = (Get-Item -LiteralPath $env:YIKE_SIGNING_STORE).FullName
    $keyAlias = 'yike-release'
    $storePassword = 'env:YIKE_SIGNING_PASSWORD'
} else {
    $signingStore = Join-Path $PSScriptRoot 'yike-preview-key.p12'
    $keyAlias = 'yike-preview'
    $storePassword = 'pass:android'
    if (-not (Test-Path -LiteralPath $signingStore)) {
        Invoke-BuildTool $keytool @('-genkeypair', '-keystore', $signingStore, '-storetype', 'PKCS12', '-alias', $keyAlias,
            '-storepass', 'android', '-keypass', 'android', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
            '-dname', 'CN=Yike Preview,O=Local Development')
    }
}
Invoke-BuildTool $java @('-jar', $signer, 'sign', '--ks', $signingStore, '--ks-key-alias', $keyAlias,
    '--ks-pass', $storePassword, '--out', $signed, $aligned)
Invoke-BuildTool $java @('-jar', $signer, 'verify', '--verbose', $signed)
Invoke-BuildTool $zipalign @('-c', '4', $signed)

$apk = [System.IO.Compression.ZipFile]::OpenRead($signed)
try {
    $page = $apk.GetEntry('assets/index.html').Open()
    try { $embeddedHash = (Get-FileHash -InputStream $page).Hash } finally { $page.Dispose() }
    if ($embeddedHash -ne (Get-FileHash -LiteralPath $stagedPage).Hash) { throw 'The Android app does not contain the desktop source page.' }
} finally { $apk.Dispose() }
$apkName = if ($PublicBeta) { 'Yike-Focus-Android.apk' } else { '一刻-安卓版.apk' }
$pageName = if ($PublicBeta) { 'Yike-Focus-Desktop.html' } else { '一刻-番茄钟.html' }
Copy-Item -LiteralPath $signed -Destination (Join-Path $outputDirectory $apkName)
Copy-Item -LiteralPath $stagedPage -Destination (Join-Path $outputDirectory $pageName)
Write-Output 'Verified Android package and matching desktop page.'
