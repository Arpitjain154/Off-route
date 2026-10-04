$ErrorActionPreference = 'Stop'
$demoRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $demoRoot
function Test-DemoPort($port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1', $port); return $true } catch { return $false } finally { $client.Dispose() }
}
if (-not (Test-DemoPort 27017)) {
    Start-Process -FilePath 'node.exe' -ArgumentList 'scripts/local-mongo.mjs' -WorkingDirectory $demoRoot -WindowStyle Hidden -RedirectStandardOutput 'artifacts/demo-db.log' -RedirectStandardError 'artifacts/demo-db-error.log'
    for ($i=0; $i -lt 30 -and -not (Test-DemoPort 27017); $i++) { Start-Sleep -Seconds 1 }
}
if (-not (Test-DemoPort 3001)) {
    Start-Process -FilePath 'node.exe' -ArgumentList 'server/src/index.js' -WorkingDirectory $demoRoot -WindowStyle Hidden -RedirectStandardOutput 'artifacts/demo-api.log' -RedirectStandardError 'artifacts/demo-api-error.log'
}
if (-not (Test-DemoPort 5173)) {
    Start-Process -FilePath 'node.exe' -ArgumentList 'node_modules/vite/bin/vite.js --host 0.0.0.0 --port 5173' -WorkingDirectory $demoRoot -WindowStyle Hidden -RedirectStandardOutput 'artifacts/demo-web.log' -RedirectStandardError 'artifacts/demo-web-error.log'
}
Write-Output 'Demo: http://localhost:5173 — database/API/web logs in artifacts/demo-*.log'
