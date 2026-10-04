param([string]$Suite='native')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
$env:JAVA_HOME='C:/Users/surya/.jdks/jbr-21.0.11'
if($Suite -eq 'native') {
    & .\android\gradlew.bat -p android :app:testDebugUnitTest --max-workers=2 --no-parallel --console=plain *> artifacts/qa-1.5/native-suite.log
} elseif($Suite -eq 'apk') {
    & .\android\gradlew.bat -p android :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=2 --no-parallel --console=plain *> artifacts/qa-1.5/apk-build.log
} elseif($Suite -eq 'edge') {
    & .\android\gradlew.bat -p edge build distZip --max-workers=2 --no-parallel --console=plain *> artifacts/qa-1.5/edge-build.log
} elseif($Suite -eq 'instrumentation') {
    & .\android\gradlew.bat -p android :app:assembleDebugAndroidTest --max-workers=2 --no-parallel --console=plain *> artifacts/qa-1.5/test-build.log
} else {throw 'Unknown QA suite'}
exit $LASTEXITCODE
