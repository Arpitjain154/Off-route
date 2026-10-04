param([string]$TestClass='com.idr.navigator.InertialWorkflowTest')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
$qaAdb='C:/Users/surya/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$qaSerial='emulator-5556'
$hardware=& $qaAdb -s $qaSerial shell getprop ro.hardware
if($hardware -notmatch 'ranchu|goldfish'){throw 'Controlled navigation inputs require the emulator'}
& $qaAdb -s $qaSerial install -r android/app/build/outputs/apk/debug/app-debug.apk
if($LASTEXITCODE -ne 0){throw 'App install failed'}
& $qaAdb -s $qaSerial install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if($LASTEXITCODE -ne 0){throw 'Test install failed'}
$log='artifacts/qa-1.5/'+($TestClass.Split('.')[-1])+'.log'
& $qaAdb -s $qaSerial shell am instrument -w -r -e class $TestClass com.idr.navigator.test/androidx.test.runner.AndroidJUnitRunner *> $log
Get-Content -LiteralPath $log
if(-not (Select-String -LiteralPath $log -Pattern 'OK \(' -Quiet)){throw 'Android test did not pass; inspect saved output'}
