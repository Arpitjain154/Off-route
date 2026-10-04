$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
& .venv-ml/Scripts/python.exe scripts/prepare-mp-map.py
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
node --max-old-space-size=8192 scripts/build-mp-tiles.mjs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$env:IDR_BUILD_STATE_GRAPH = '1'
try {
    & .\android\gradlew.bat -p android :app:testDebugUnitTest --tests '*StateGraphBuildTest' --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally { Remove-Item Env:IDR_BUILD_STATE_GRAPH }
New-Item -ItemType Directory -Force android/app/src/main/assets/maps/mp-vehicle-v1 | Out-Null
Get-ChildItem -LiteralPath android/app/build/mp-vehicle-v1 -File | Where-Object { $_.Name -ne 'gh.lock' } | Copy-Item -Destination android/app/src/main/assets/maps/mp-vehicle-v1
Write-Output 'Statewide tiles and verified road graph prepared. Rebuild Android to package them.'
