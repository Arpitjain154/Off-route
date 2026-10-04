$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
foreach($relative in @('dist','android/app/src/main/assets/public')) {
    $target=[IO.Path]::GetFullPath((Join-Path $taskRoot $relative))
    $assets=[IO.Path]::GetFullPath((Join-Path $target 'assets'))
    if(-not $assets.StartsWith($taskRoot+[IO.Path]::DirectorySeparatorChar) -or (Split-Path $assets -Leaf) -ne 'assets'){throw 'Invalid assets target'}
    if(Test-Path -LiteralPath $assets){Remove-Item -LiteralPath $assets -Recurse -Force}
    Copy-Item -LiteralPath (Join-Path $taskRoot 'tmp/navigation-ui/assets') -Destination $assets -Recurse
    Copy-Item -LiteralPath (Join-Path $taskRoot 'tmp/navigation-ui/index.html') -Destination (Join-Path $target 'index.html')
}
