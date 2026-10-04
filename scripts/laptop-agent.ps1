[CmdletBinding()]
param(
    [ValidateSet('Menu','Setup','Login','Check','Run','Scheduled','Status','PreparePhone','PhoneTest','Trial','FieldTest','Approve','Publish','Discard','InstallSchedule','RemoveSchedule','Test')]
    [string]$Action = 'Menu'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$agentHome = Join-Path $projectRoot 'artifacts\laptop-agent'
$pythonExe = Join-Path $projectRoot '.venv-ml\Scripts\python.exe'
$taskName = 'OFF-ROUTE Laptop Training Agent'
$env:PYTHONPATH = Join-Path $projectRoot 'ml'
$env:TF_CPP_MIN_LOG_LEVEL = '2'
Set-Location -LiteralPath $projectRoot

function Invoke-Agent {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments)
    if (-not (Test-Path -LiteralPath $pythonExe)) { throw 'Choose Setup first to prepare Python.' }
    & $pythonExe -m laptop_agent @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'The agent stopped. Read the explanation directly above; no model was automatically published.' }
}

function Setup-Agent {
    New-Item -ItemType Directory -Force -Path $agentHome | Out-Null
    if (-not (Test-Path -LiteralPath $pythonExe)) {
        if (Get-Command py -ErrorAction SilentlyContinue) {
            & py -3.12 -m venv (Join-Path $projectRoot '.venv-ml')
        } else {
            $bundledPython = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
            if (-not (Test-Path -LiteralPath $bundledPython)) { throw 'Python 3.12 is needed. Install it from https://www.python.org/downloads/windows/, then run Setup again.' }
            & $bundledPython -m venv (Join-Path $projectRoot '.venv-ml')
        }
        if ($LASTEXITCODE -ne 0) { throw 'Python environment setup failed.' }
    }
    & $pythonExe -c 'import numpy, requests, tensorflow'
    if ($LASTEXITCODE -ne 0) {
        & $pythonExe -m pip install 'tensorflow==2.20.0' 'numpy>=1.26,<3' 'requests>=2.32,<3'
        if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed. Check your internet connection.' }
    }
    Write-Host 'Python is ready. Sign in using the account inside the phone app.'
    Invoke-Agent @('login')
    Install-AgentSchedule
    Invoke-Agent @('run')
}

function Install-AgentSchedule {
    if (-not (Test-Path -LiteralPath (Join-Path $agentHome 'login.dpapi'))) { throw 'Choose Login first, so unattended checks can authenticate.' }
    $scriptFile = Join-Path $PSScriptRoot 'laptop-agent.ps1'
    $arguments = '-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $scriptFile + '" -Action Scheduled'
    $taskAction = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arguments -WorkingDirectory $projectRoot
    $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(2) -RepetitionInterval (New-TimeSpan -Hours 1)
    $settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -StartWhenAvailable -RunOnlyIfNetworkAvailable -ExecutionTimeLimit (New-TimeSpan -Hours 4)
    # WakeToRun is deliberately false; no stored password or elevated privileges.
    $settings.WakeToRun = $false
    $principal = New-ScheduledTaskPrincipal -UserId ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
    Register-ScheduledTask -TaskName $taskName -Action $taskAction -Trigger $trigger -Settings $settings -Principal $principal -Description 'Download completed OFF-ROUTE trips and train candidates. Never approve or publish automatically.' -Force | Out-Null
    $task = Get-ScheduledTask -TaskName $taskName
    if ($task.Settings.WakeToRun) { throw 'Unexpected wake setting. Review Task Scheduler.' }
    Write-Host 'Scheduled while you are signed in and the laptop is awake and on AC power. It checks every 6 hours, slowing to 24/72 hours after repeated rejected candidates.'
}

function Prepare-Phone {
    if (-not $env:JAVA_HOME) {
        $jdk = Join-Path $env:USERPROFILE '.jdks\jbr-21.0.11'
        if (Test-Path -LiteralPath $jdk) { $env:JAVA_HOME = $jdk }
    }
    & (Join-Path $projectRoot 'android\gradlew.bat') -p (Join-Path $projectRoot 'android') testDebugUnitTest assembleDebugAndroidTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android test preparation failed; see output above.' }
    Write-Host 'Phone test APK is ready. Your installed navigation app was not replaced.'
}

function Pick-File([string]$Title, [string]$Filter) {
    Add-Type -AssemblyName System.Windows.Forms
    $dialog = New-Object System.Windows.Forms.OpenFileDialog
    $dialog.Title = $Title; $dialog.Filter = $Filter
    $dialog.InitialDirectory = $agentHome
    try {
        if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { throw 'File selection cancelled.' }
        return $dialog.FileName
    } finally { $dialog.Dispose() }
}

function Do-Action([string]$Choice) {
    switch ($Choice) {
        'Setup' { Setup-Agent }
        'Login' { Invoke-Agent @('login') }
        'Check' { Invoke-Agent @('check') }
        'Run' { Invoke-Agent @('run') }
        'Status' { Invoke-Agent @('status') }
        'PreparePhone' { Prepare-Phone }
        'PhoneTest' { Prepare-Phone; Invoke-Agent @('phone-test') }
        'Trial' {
            Invoke-Agent @('prepare-trial')
            if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $env:USERPROFILE '.jdks\jbr-21.0.11' }
            & (Join-Path $projectRoot 'android\gradlew.bat') -p (Join-Path $projectRoot 'android') :app:assembleLaptopTrial --console=plain
            if ($LASTEXITCODE -ne 0) { throw 'Candidate Test app build failed.' }
            Invoke-Agent @('install-trial')
        }
        'FieldTest' {
            Invoke-Agent @('import-trial')
            $videoFile = Pick-File 'Choose its matching screen recording' 'Video (*.mp4;*.webm;*.mkv)|*.mp4;*.webm;*.mkv'
            Invoke-Agent @('field-test','--video',$videoFile)
        }
        'Approve' { Invoke-Agent @('approve') }
        'Publish' { Invoke-Agent @('publish') }
        'Discard' { Invoke-Agent @('discard') }
        'InstallSchedule' { Install-AgentSchedule }
        'RemoveSchedule' { Unregister-ScheduledTask -TaskName $taskName -Confirm:$false; Write-Host 'Schedule removed. All data and models are retained.' }
        'Test' {
            & $pythonExe -m unittest discover -s (Join-Path $projectRoot 'ml') -p 'test_laptop_agent*.py' -v
            if ($LASTEXITCODE -ne 0) { throw 'Verification failed.' }
        }
    }
}

if ($Action -eq 'Scheduled') {
    New-Item -ItemType Directory -Force -Path (Join-Path $agentHome 'logs') | Out-Null
    $logFile = Join-Path $agentHome ('logs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log')
    & $pythonExe -m laptop_agent run --scheduled *> $logFile
    exit $LASTEXITCODE
}
if ($Action -ne 'Menu') { Do-Action $Action; exit 0 }
do {
    Write-Host "`nOFF-ROUTE LAPTOP TRAINING AGENT"
    Write-Host '1 Setup: login, enable schedule, check data'
    Write-Host '2 Login again'
    Write-Host '3 Verify completed uploads and download'
    Write-Host '4 Check data and train if ready'
    Write-Host '5 Show status and quality report'
    Write-Host '6 Test candidate inference on the connected phone'
    Write-Host '7 Import Candidate Test trip and review its screen recording'
    Write-Host '8 Approve candidate'
    Write-Host '9 Publish approved candidate'
    Write-Host '10 Discard candidate and wait for new data'
    Write-Host '11 Enable schedule'
    Write-Host '12 Remove schedule'
    Write-Host '13 Open reports folder'
    Write-Host '14 Run automated checks'
    Write-Host '15 Install the separate Candidate Test app for a controlled trip'
    Write-Host '0 Exit'
    $choice = Read-Host 'Choose a number'
    try {
        $choices = @{'1'='Setup';'2'='Login';'3'='Check';'4'='Run';'5'='Status';'6'='PhoneTest';'7'='FieldTest';'8'='Approve';'9'='Publish';'10'='Discard';'11'='InstallSchedule';'12'='RemoveSchedule';'14'='Test';'15'='Trial'}
        if ($choice -eq '13') { New-Item -ItemType Directory -Force -Path $agentHome | Out-Null; Start-Process explorer.exe -ArgumentList ('"' + $agentHome + '"') }
        elseif ($choices.ContainsKey($choice)) { Do-Action $choices[$choice] }
    } catch { Write-Host $_.Exception.Message -ForegroundColor Red }
} while ($choice -ne '0')
