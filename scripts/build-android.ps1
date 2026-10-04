$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (-not $env:JAVA_HOME) {
    $bundledJdk = Join-Path $env:USERPROFILE '.jdks\jbr-21.0.11'
    if (Test-Path -LiteralPath $bundledJdk) { $env:JAVA_HOME = $bundledJdk }
}
npm run build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
npx cap sync android
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& .\android\gradlew.bat -p android testDebugUnitTest assembleDebug --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
New-Item -ItemType Directory -Force -Path artifacts | Out-Null
Copy-Item -LiteralPath android\app\build\outputs\apk\debug\app-debug.apk -Destination artifacts\IDR-Navigator-debug.apk
Write-Output 'Built artifacts/IDR-Navigator-debug.apk'
