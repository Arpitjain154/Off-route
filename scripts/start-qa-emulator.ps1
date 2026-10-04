$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$env:ANDROID_AVD_HOME=Join-Path $taskRoot 'tmp/avd'
Start-Process -FilePath 'C:/Users/surya/AppData/Local/Android/Sdk/emulator/emulator.exe' -ArgumentList '-avd','idr-api36','-port','5556','-no-window','-no-audio','-no-snapshot','-gpu','swiftshader_indirect' -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskRoot 'artifacts/qa-1.5/emulator.log') -RedirectStandardError (Join-Path $taskRoot 'artifacts/qa-1.5/emulator-error.log')
