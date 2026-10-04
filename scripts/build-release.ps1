$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
foreach($releaseVar in 'IDR_KEYSTORE_PATH','IDR_KEYSTORE_PASSWORD','IDR_KEY_ALIAS','IDR_KEY_PASSWORD') {
    if(-not [Environment]::GetEnvironmentVariable($releaseVar)) { throw "Set $releaseVar in your secure build environment" }
}
if(-not (Test-Path -LiteralPath $env:IDR_KEYSTORE_PATH)) { throw 'Release keystore not found' }
if(-not $env:VITE_API_URL -or -not $env:VITE_API_URL.StartsWith('https://')) { throw 'Set VITE_API_URL to the deployed HTTPS API origin' }
npm run build
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
npx cap sync android
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& .\android\gradlew.bat -p android testDebugUnitTest assembleRelease bundleRelease --console=plain
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
Copy-Item -LiteralPath android\app\build\outputs\apk\release\app-release.apk -Destination artifacts\IDR-Navigator-release.apk
Copy-Item -LiteralPath android\app\build\outputs\bundle\release\app-release.aab -Destination artifacts\IDR-Navigator-release.aab
Write-Output 'Release APK and AAB created under artifacts. Verify the signing certificate before distributing.'
