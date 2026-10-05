param([string]$Output='artifacts/eval/final2/edge-tests')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
New-Item -ItemType Directory -Force -Path $Output | Out-Null
$jdk=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin'}else{'C:/Users/surya/.jdks/jbr-21.0.11/bin'}
$cache=Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'
$jars=@(Get-ChildItem "$cache/org.ejml" -Recurse -Filter *.jar; Get-ChildItem "$cache/junit/junit/4.13.2" -Recurse -Filter *.jar; Get-ChildItem "$cache/org.hamcrest/hamcrest-core" -Recurse -Filter *.jar) | Where-Object { $_.Name -notmatch 'sources|javadoc' }
$engineJar=Get-ChildItem edge/build/libs/idr-edge-engine-*.jar | Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1
if(-not $engineJar){throw 'Build the edge JAR first.'}
$cp=$engineJar.FullName+';'+($jars.FullName -join ';')
$existing=@('AiSpeedAdvisorTest','InertialNavigationTest','LiveNavigationTest','LiveEskfIntegrationTest','VehicleConstraintRegressionTest','StationaryOutageTest')
$sources=@($existing | ForEach-Object { "android/app/src/test/java/com/idr/navigator/engine/$_.java" })+@('edge/tests/com/idr/navigator/engine/IovnbdCsvRunnerTest.java')
& "$jdk/javac.exe" -cp $cp -d $Output @sources *> "$Output/javac.log"
if($LASTEXITCODE -ne 0){Get-Content "$Output/javac.log";exit $LASTEXITCODE}
& "$jdk/java.exe" -Xmx512m -cp ($Output+';'+$cp) org.junit.runner.JUnitCore @($existing | ForEach-Object { "com.idr.navigator.engine.$_" }) com.idr.navigator.engine.IovnbdCsvRunnerTest *> "$Output/tests.log"
$testExit=$LASTEXITCODE
Get-Content "$Output/tests.log"
exit $testExit
