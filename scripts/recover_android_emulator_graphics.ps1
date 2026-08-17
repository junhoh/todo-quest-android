[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)][string]$AvdName,
    [ValidateSet("software", "swangle", "swiftshader", "host", "auto")]
    [string[]]$GpuModes = @("software", "swangle"),
    [switch]$RestartRunningAvd,
    [switch]$LeaveRunning,
    [switch]$SkipBuild,
    [switch]$VerifyCalendarTaskEditor,
    [ValidateRange(30, 900)][int]$BootTimeoutSeconds = 240
)

$ErrorActionPreference = "Stop"

# Some IDE hosts inject both Path and PATH. Start-Process rejects that duplicate
# environment block, so normalize it in this script process before launching AVDs.
$processPath = $env:Path
[Environment]::SetEnvironmentVariable("PATH", $null, "Process")
[Environment]::SetEnvironmentVariable("Path", $processPath, "Process")
$avdHome = Join-Path $env:USERPROFILE ".android\avd"
[Environment]::SetEnvironmentVariable("ANDROID_AVD_HOME", $avdHome, "Process")

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
. (Join-Path $scriptDir "android_launch_common.ps1")

$sdkRoot = Resolve-AndroidSdkRoot
$emulatorPath = Join-Path $sdkRoot "emulator\emulator.exe"
$diagnoseScript = Join-Path $scriptDir "diagnose_android_launch.ps1"
$calendarModalDiagnoseScript = Join-Path $scriptDir "diagnose_calendar_modal.ps1"
$avdConfigPath = Join-Path $avdHome "$AvdName.avd\config.ini"
$outputRoot = Join-Path $repoRoot "app\build\launch-diagnostics\graphics-recovery\$AvdName"

if (-not (Test-Path -LiteralPath $emulatorPath -PathType Leaf)) {
    throw "Android emulator executable was not found at $emulatorPath"
}
if (-not (Test-Path -LiteralPath $avdConfigPath -PathType Leaf)) {
    throw "AVD '$AvdName' was not found at $avdConfigPath"
}
if (-not (Test-Path -LiteralPath $diagnoseScript -PathType Leaf)) {
    throw "Launch diagnostics script was not found at $diagnoseScript"
}
if ($VerifyCalendarTaskEditor -and -not (Test-Path -LiteralPath $calendarModalDiagnoseScript -PathType Leaf)) {
    throw "Calendar modal diagnostics script was not found at $calendarModalDiagnoseScript"
}

New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

function Read-EmulatorConsoleResponse {
    param([Parameter(Mandatory = $true)][System.IO.StreamReader]$Reader)

    $lines = @()
    while ($true) {
        $line = $Reader.ReadLine()
        if ($null -eq $line) {
            break
        }
        if ($line -eq "OK") {
            break
        }
        if ($line.StartsWith("KO:")) {
            throw "Android emulator console rejected the command: $line"
        }
        $lines += $line
    }
    return $lines
}

function Invoke-EmulatorConsoleCommand {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Command,
        [switch]$AllowDisconnect
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $client.ReceiveTimeout = 5000
        $client.SendTimeout = 5000
        $client.Connect("127.0.0.1", $Port)
        $stream = $client.GetStream()
        $reader = [System.IO.StreamReader]::new($stream)
        $writer = [System.IO.StreamWriter]::new($stream)
        $writer.NewLine = "`n"
        $writer.AutoFlush = $true

        $greeting = @(Read-EmulatorConsoleResponse -Reader $reader)
        if (($greeting -join "`n") -match "Authentication required") {
            $tokenPath = Join-Path $env:USERPROFILE ".emulator_console_auth_token"
            if (-not (Test-Path -LiteralPath $tokenPath -PathType Leaf)) {
                throw "Emulator console authentication token was not found at $tokenPath"
            }
            $token = (Get-Content -Raw -LiteralPath $tokenPath).Trim()
            $writer.WriteLine("auth $token")
            Read-EmulatorConsoleResponse -Reader $reader | Out-Null
        }

        $writer.WriteLine($Command)
        try {
            return @(Read-EmulatorConsoleResponse -Reader $reader)
        }
        catch {
            if ($AllowDisconnect -and ($_.Exception.Message -match "transport connection|forcibly closed|Unable to read")) {
                return @()
            }
            throw
        }
    }
    finally {
        $client.Dispose()
    }
}

function Get-RunningEmulatorRecords {
    $output = & adb devices 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed: $(($output | Out-String).Trim())"
    }

    $records = @()
    foreach ($line in $output) {
        if ($line -match "^(emulator-(\d+))\s+(\S+)$") {
            $serial = $Matches[1]
            $port = [int]$Matches[2]
            $state = $Matches[3]
            $name = "unknown"
            try {
                $nameResponse = @(Invoke-EmulatorConsoleCommand -Port $port -Command "avd name")
                $candidate = $nameResponse |
                    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
                    Select-Object -Last 1
                if (-not [string]::IsNullOrWhiteSpace($candidate)) {
                    $name = $candidate.Trim()
                }
            }
            catch {
                $name = "unknown"
            }
            if ($name -eq "unknown" -and $state -eq "device") {
                $bootAvdName = (& adb -s $serial shell getprop ro.boot.qemu.avd_name 2>&1 | Out-String).Trim()
                if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($bootAvdName)) {
                    $name = $bootAvdName
                }
            }

            $records += [pscustomobject]@{
                Serial = $serial
                Port = $port
                State = $state
                AvdName = $name
            }
        }
    }
    return $records
}

function Test-TcpPortOpen {
    param([Parameter(Mandatory = $true)][int]$Port)

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        return $connect.AsyncWaitHandle.WaitOne(250) -and $client.Connected
    }
    finally {
        $client.Dispose()
    }
}

function Get-FreeEmulatorPort {
    foreach ($port in 5554..5682) {
        if ($port % 2 -eq 0 -and -not (Test-TcpPortOpen -Port $port)) {
            return $port
        }
    }
    throw "No free Android emulator console port was found."
}

function Stop-SelectedAvd {
    param([Parameter(Mandatory = $true)][pscustomobject]$Record)

    if ($Record.AvdName -ne $AvdName) {
        throw "Refusing to stop $($Record.Serial): expected '$AvdName', console reported '$($Record.AvdName)'."
    }
    Stop-EmulatorBySerial -Serial $Record.Serial
}

function Wait-ForEmulatorBoot {
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $state = (& adb -s $Serial get-state 2>&1 | Out-String).Trim()
            $stateExitCode = $LASTEXITCODE
            $bootCompleted = ""
            $bootExitCode = 1
            if ($stateExitCode -eq 0 -and $state -eq "device") {
                $bootCompleted = (& adb -s $Serial shell getprop sys.boot_completed 2>&1 | Out-String).Trim()
                $bootExitCode = $LASTEXITCODE
            }
        }
        finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }

        if ($stateExitCode -eq 0 -and $state -eq "device") {
            if ($bootExitCode -eq 0 -and $bootCompleted -eq "1") {
                return
            }
        }
        Start-Sleep -Seconds 2
    }
    throw "Emulator $Serial did not finish booting within $TimeoutSeconds seconds."
}

function Stop-EmulatorBySerial {
    param([Parameter(Mandatory = $true)][string]$Serial)

    if ($Serial -notmatch "^emulator-(\d+)$") {
        throw "Refusing to stop non-emulator serial '$Serial'."
    }
    if (-not $PSCmdlet.ShouldProcess("$Serial ($AvdName)", "Stop Android emulator")) {
        return
    }

    $killOutput = & adb -s $Serial emu kill 2>&1
    if ($LASTEXITCODE -eq 0) {
        return
    }

    $record = [pscustomobject]@{
        Serial = $Serial
        Port = [int]$Matches[1]
        State = "unknown"
        AvdName = $AvdName
    }
    try {
        Invoke-EmulatorConsoleCommand -Port $record.Port -Command "kill" -AllowDisconnect | Out-Null
    }
    catch {
        throw "adb emu kill failed: $(($killOutput | Out-String).Trim()); console fallback failed: $($_.Exception.Message)"
    }
}

$runningTargetRecords = @(Get-RunningEmulatorRecords | Where-Object { $_.AvdName -eq $AvdName })
if ($runningTargetRecords.Count -gt 0 -and -not $RestartRunningAvd) {
    throw "AVD '$AvdName' is already running. Use -RestartRunningAvd for a non-snapshot cold boot."
}
foreach ($record in $runningTargetRecords) {
    Stop-SelectedAvd -Record $record
}
if ($runningTargetRecords.Count -gt 0) {
    Start-Sleep -Seconds 3
}

$attempts = @()
$selectedSerial = $null
$selectedGpuMode = $null

foreach ($gpuMode in $GpuModes) {
    $port = Get-FreeEmulatorPort
    $serial = "emulator-$port"
    $modeOutputDir = Join-Path $outputRoot $gpuMode
    New-Item -ItemType Directory -Force -Path $modeOutputDir | Out-Null
    $recoveryErrorPath = Join-Path $modeOutputDir "recovery-error.txt"
    if (Test-Path -LiteralPath $recoveryErrorPath -PathType Leaf) {
        Remove-Item -LiteralPath $recoveryErrorPath
    }

    $arguments = @(
        "-avd", $AvdName,
        "-port", "$port",
        "-gpu", $gpuMode,
        "-no-snapshot-load",
        "-no-snapshot-save",
        "-no-boot-anim",
        "-no-window"
    )
    $argumentText = $arguments -join " "
    $argumentText | Set-Content -LiteralPath (Join-Path $modeOutputDir "emulator-arguments.txt") -Encoding UTF8

    Write-Host "[$gpuMode] Starting $AvdName as $serial with snapshot loading disabled."
    if (-not $PSCmdlet.ShouldProcess("$AvdName ($gpuMode)", "Start Android emulator")) {
        continue
    }
    $process = Start-Process `
        -FilePath $emulatorPath `
        -ArgumentList $arguments `
        -WindowStyle Hidden `
        -PassThru `
        -RedirectStandardOutput (Join-Path $modeOutputDir "emulator-stdout.txt") `
        -RedirectStandardError (Join-Path $modeOutputDir "emulator-stderr.txt")

    $diagnosticExitCode = 1
    $diagnosticSummary = $null
    $calendarModalExitCode = if ($VerifyCalendarTaskEditor) { 1 } else { 0 }
    $calendarModalSummary = $null
    try {
        Wait-ForEmulatorBoot -Serial $serial -TimeoutSeconds $BootTimeoutSeconds
        & adb -s $serial shell input keyevent KEYCODE_WAKEUP | Out-Null
        & adb -s $serial shell wm dismiss-keyguard | Out-Null

        $diagnoseArguments = @(
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", $diagnoseScript,
            "-Serial", $serial,
            "-OutputDir", $modeOutputDir
        )
        if ($SkipBuild) {
            $diagnoseArguments += "-SkipBuild"
        }
        & powershell @diagnoseArguments
        $diagnosticExitCode = $LASTEXITCODE
        $diagnosticSummary = Get-ChildItem -File -Recurse -Filter "summary.txt" -LiteralPath $modeOutputDir |
            Where-Object { $_.FullName -notlike "*\calendar-modal\*" } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1

        if ($diagnosticExitCode -eq 0 -and $VerifyCalendarTaskEditor) {
            $calendarModalOutputDir = Join-Path $modeOutputDir "calendar-modal"
            $calendarModalArguments = @(
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-File", $calendarModalDiagnoseScript,
                "-Serial", $serial,
                "-OutputDir", $calendarModalOutputDir,
                "-SkipBuild",
                "-SkipInstall"
            )
            if ($LeaveRunning) {
                $calendarModalArguments += "-LeaveRunning"
            }
            & powershell @calendarModalArguments
            $calendarModalExitCode = $LASTEXITCODE
            $calendarModalSummary = Get-ChildItem -File -Recurse -Filter "summary.txt" -LiteralPath $calendarModalOutputDir |
                Sort-Object LastWriteTime -Descending |
                Select-Object -First 1
        }
    }
    catch {
        $_.Exception.Message | Set-Content -LiteralPath $recoveryErrorPath -Encoding UTF8
        $diagnosticExitCode = 1
    }

    $attempt = [pscustomobject]@{
        GpuMode = $gpuMode
        Serial = $serial
        Passed = ($diagnosticExitCode -eq 0 -and $calendarModalExitCode -eq 0)
        LaunchPassed = ($diagnosticExitCode -eq 0)
        CalendarModalPassed = if ($VerifyCalendarTaskEditor) { $calendarModalExitCode -eq 0 } else { $null }
        Summary = if ($diagnosticSummary) { $diagnosticSummary.FullName } else { "unknown" }
        CalendarModalSummary = if ($calendarModalSummary) { $calendarModalSummary.FullName } else { "unknown" }
    }
    $attempts += $attempt

    if ($attempt.Passed) {
        $selectedSerial = $serial
        $selectedGpuMode = $gpuMode
        $successScope = if ($VerifyCalendarTaskEditor) {
            "compositor, app launch, and Calendar task editor diagnostics succeeded"
        } else {
            "compositor and app launch diagnostics succeeded"
        }
        Write-Host "[$gpuMode] PASS - $successScope."
        if (-not $LeaveRunning) {
            Stop-EmulatorBySerial -Serial $serial
        }
        break
    }

    Write-Warning "[$gpuMode] FAIL - see $modeOutputDir"
    $isLastMode = $gpuMode -eq $GpuModes[-1]
    if (-not ($LeaveRunning -and $isLastMode)) {
        try {
            Stop-EmulatorBySerial -Serial $serial
            Start-Sleep -Seconds 3
        }
        catch {
            Write-Warning "Unable to stop failed emulator ${serial}: $($_.Exception.Message)"
        }
    }
}

$recoverySummary = @(
    "avdName=$AvdName"
    "hostEmulatorVersion=$(Get-HostEmulatorVersion -SdkRoot $sdkRoot)"
    "selectedGpuMode=$(if ($selectedGpuMode) { $selectedGpuMode } else { 'none' })"
    "selectedSerial=$(if ($selectedSerial) { $selectedSerial } else { 'none' })"
    "verifyCalendarTaskEditor=$VerifyCalendarTaskEditor"
    "result=$(if ($selectedGpuMode) { 'PASS' } else { 'FAIL' })"
) + ($attempts | ForEach-Object {
    $calendarModalResult = if (-not $VerifyCalendarTaskEditor) { "SKIPPED" } elseif ($_.CalendarModalPassed) { "PASS" } else { "FAIL" }
    "attempt.$($_.GpuMode)=$(if ($_.Passed) { 'PASS' } else { 'FAIL' });serial=$($_.Serial);launch=$(if ($_.LaunchPassed) { 'PASS' } else { 'FAIL' });calendarModal=$calendarModalResult;launchSummary=$($_.Summary);calendarModalSummary=$($_.CalendarModalSummary)"
})
$recoverySummary | Set-Content -LiteralPath (Join-Path $outputRoot "summary.txt") -Encoding UTF8

if (-not $selectedGpuMode) {
    $requiredResult = if ($VerifyCalendarTaskEditor) {
        "a non-black system baseline, app launch, and visible Calendar task editor"
    } else {
        "a non-black system baseline and app launch"
    }
    throw "No graphics backend produced $requiredResult for AVD '$AvdName'."
}

Write-Host "Selected graphics backend: $selectedGpuMode ($selectedSerial)"
