[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Serial,

    [switch]$AllowPhysicalDevice,

    [ValidateRange(15, 90)]
    [int]$TimeoutSeconds = 90,

    [string]$OutputDir = "app\build\reminder-diagnostics"
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$packageName = "com.todoquest"
$testPackageName = "com.todoquest.test"
$runner = "$testPackageName/com.todoquest.app.TodoQuestTestRunner"
$fixtureClass = "com.todoquest.notification.ReminderBackgroundSmokeFixtureTest"
$debugApk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$testApk = Join-Path $repoRoot "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
$safeSerial = ($Serial -replace "[^A-Za-z0-9_.-]+", "-").Trim("-", ".")
if ([string]::IsNullOrWhiteSpace($safeSerial)) {
    $safeSerial = "unknown"
}
$outputRoot = if ([System.IO.Path]::IsPathRooted($OutputDir)) {
    Join-Path $OutputDir $safeSerial
} else {
    Join-Path (Join-Path $repoRoot $OutputDir) $safeSerial
}

function Invoke-AdbText {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [string]$OutputFile,
        [switch]$AllowFailure
    )

    $adbArguments = @("-s", $Serial) + $Arguments
    $output = & adb @adbArguments 2>&1
    $exitCode = $LASTEXITCODE
    $resultText = ($output | Out-String).TrimEnd()
    if ($OutputFile) {
        $resultText | Set-Content -LiteralPath $OutputFile -Encoding UTF8
    }
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "adb $($adbArguments -join ' ') failed with exit code $exitCode`n$resultText"
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Text = $resultText
    }
}

function Get-ConnectedDeviceState {
    $devices = & adb devices 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed`n$(($devices | Out-String).TrimEnd())"
    }
    foreach ($line in $devices) {
        if ($line -match "^(?<serial>\S+)\s+(?<state>\S+)(?:\s|$)") {
            if ($Matches.serial -eq $Serial) {
                return $Matches.state
            }
        }
    }
    return $null
}

function Get-TargetAlarmDeliveryCount {
    param([Parameter(Mandatory = $true)][string]$AlarmDump)

    $match = [regex]::Match(
        $AlarmDump,
        "(?m)^\s*u\d+a\d+:$([regex]::Escape($packageName))\s+.*?running,\s+" +
            "(?<wakeups>\d+)\s+wakeups:"
    )
    if (-not $match.Success) {
        return 0L
    }
    return [long]$match.Groups["wakeups"].Value
}

function Get-TargetFailureLines {
    param([Parameter(Mandatory = $true)][string]$LogcatText)

    $lines = $LogcatText -split "`r?`n"
    return @($lines | Where-Object {
        ($_ -match "\sE\s+TodoQuestReminder(?:\s|:)") -or
        ($_ -match "TodoQuestReminder.*Reminder delivery failed") -or
        ($_ -match "FATAL EXCEPTION" -and $_ -match [regex]::Escape($packageName)) -or
        ($_ -match "Process:\s+$([regex]::Escape($packageName))(?:,|\s|$)")
    })
}

function Write-Summary {
    param(
        [Parameter(Mandatory = $true)][string]$Result,
        [Parameter(Mandatory = $true)][string]$Reason,
        [hashtable]$Values = @{}
    )

    $lines = @(
        "result=$Result"
        "reason=$Reason"
        "serial=$Serial"
        "allowPhysicalDevice=$($AllowPhysicalDevice.IsPresent)"
        "timeoutSeconds=$TimeoutSeconds"
        "outputDir=$outputRoot"
    )
    foreach ($key in ($Values.Keys | Sort-Object)) {
        $lines += "$key=$($Values[$key])"
    }
    $lines | Set-Content -LiteralPath (Join-Path $outputRoot "summary.txt") -Encoding UTF8
}

New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

$summaryValues = @{
    dataReset = "false"
    dndModified = "false"
    notificationChannelModified = "false"
    usedForceStop = "false"
}

try {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        throw "BLOCKED: adb is not available. Install is not attempted."
    }
    if (-not (Test-Path -LiteralPath $debugApk -PathType Leaf)) {
        throw "BLOCKED: debug APK is missing: $debugApk"
    }
    if (-not (Test-Path -LiteralPath $testApk -PathType Leaf)) {
        throw "BLOCKED: androidTest APK is missing: $testApk"
    }

    $deviceState = Get-ConnectedDeviceState
    if ($deviceState -ne "device") {
        throw "BLOCKED: '$Serial' is not connected in device state. Current state: $deviceState"
    }

    $kernelQemu = (Invoke-AdbText -Arguments @("shell", "getprop", "ro.kernel.qemu")).Text.Trim()
    $isEmulator = $Serial.StartsWith("emulator-", [System.StringComparison]::OrdinalIgnoreCase) -and
        $kernelQemu -eq "1"
    $summaryValues.isEmulator = $isEmulator.ToString().ToLowerInvariant()
    $summaryValues.kernelQemu = $kernelQemu
    if (-not $isEmulator -and -not $AllowPhysicalDevice) {
        throw "BLOCKED: '$Serial' is not an emulator. Re-run with -AllowPhysicalDevice only after explicitly approving installation and permission changes on that device."
    }

    $sdk = (Invoke-AdbText -Arguments @("shell", "getprop", "ro.build.version.sdk")).Text.Trim()
    $model = (Invoke-AdbText -Arguments @("shell", "getprop", "ro.product.model")).Text.Trim()
    $summaryValues.sdk = $sdk
    $summaryValues.model = $model
    @(
        "serial=$Serial"
        "model=$model"
        "sdk=$sdk"
        "kernelQemu=$kernelQemu"
        "isEmulator=$isEmulator"
    ) | Set-Content -LiteralPath (Join-Path $outputRoot "device.txt") -Encoding UTF8

    Invoke-AdbText -Arguments @("install", "-r", "-t", $debugApk) -OutputFile (
        Join-Path $outputRoot "install-debug.txt"
    ) | Out-Null
    Invoke-AdbText -Arguments @("install", "-r", "-t", $testApk) -OutputFile (
        Join-Path $outputRoot "install-android-test.txt"
    ) | Out-Null

    Invoke-AdbText -Arguments @("shell", "appops", "get", $packageName, "POST_NOTIFICATION") `
        -OutputFile (Join-Path $outputRoot "notification-capability-before.txt") -AllowFailure | Out-Null
    Invoke-AdbText -Arguments @("shell", "appops", "get", $packageName, "SCHEDULE_EXACT_ALARM") `
        -OutputFile (Join-Path $outputRoot "exact-alarm-capability-before.txt") -AllowFailure | Out-Null
    Invoke-AdbText -Arguments @("shell", "settings", "get", "global", "zen_mode") `
        -OutputFile (Join-Path $outputRoot "dnd-state.txt") -AllowFailure | Out-Null
    Invoke-AdbText -Arguments @("shell", "dumpsys", "notification", "--noredact") `
        -OutputFile (Join-Path $outputRoot "notification-before.txt") -AllowFailure | Out-Null

    $sdkInt = [int]$sdk
    if ($sdkInt -ge 33) {
        Invoke-AdbText -Arguments @(
            "shell",
            "pm",
            "grant",
            $packageName,
            "android.permission.POST_NOTIFICATIONS"
        ) -OutputFile (Join-Path $outputRoot "prepare-notification-permission.txt") | Out-Null
    } else {
        "SKIPPED: POST_NOTIFICATIONS runtime permission is not used below API 33." |
            Set-Content -LiteralPath (Join-Path $outputRoot "prepare-notification-permission.txt") -Encoding UTF8
    }
    if ($sdkInt -ge 31) {
        Invoke-AdbText -Arguments @(
            "shell",
            "appops",
            "set",
            $packageName,
            "SCHEDULE_EXACT_ALARM",
            "allow"
        ) -OutputFile (Join-Path $outputRoot "prepare-exact-alarm-access.txt") | Out-Null
    } else {
        "SKIPPED: exact-alarm special access is not used below API 31." |
            Set-Content -LiteralPath (Join-Path $outputRoot "prepare-exact-alarm-access.txt") -Encoding UTF8
    }

    Invoke-AdbText -Arguments @("shell", "appops", "get", $packageName, "POST_NOTIFICATION") `
        -OutputFile (Join-Path $outputRoot "notification-capability-after.txt") -AllowFailure | Out-Null
    Invoke-AdbText -Arguments @("shell", "appops", "get", $packageName, "SCHEDULE_EXACT_ALARM") `
        -OutputFile (Join-Path $outputRoot "exact-alarm-capability-after.txt") -AllowFailure | Out-Null

    Invoke-AdbText -Arguments @("logcat", "-c") | Out-Null
    $instrumentation = Invoke-AdbText -Arguments @(
        "shell",
        "am",
        "instrument",
        "-w",
        "-r",
        "-e",
        "class",
        $fixtureClass,
        $runner
    ) -OutputFile (Join-Path $outputRoot "instrumentation.txt") -AllowFailure
    if (
        $instrumentation.ExitCode -ne 0 -or
        $instrumentation.Text -notmatch "OK \(1 test\)" -or
        $instrumentation.Text -match "FAILURES!!!|INSTRUMENTATION_FAILED"
    ) {
        throw "BLOCKED: reminder fixture could not prepare notification/exact-alarm capability or seed a scheduled plan. See instrumentation.txt."
    }

    $fixtureLog = Invoke-AdbText -Arguments @(
        "logcat",
        "-d",
        "-v",
        "brief",
        "-s",
        "TodoQuestReminderSmoke:I",
        "*:S"
    ) -OutputFile (Join-Path $outputRoot "fixture-log.txt") -AllowFailure
    $fixtureMatch = [regex]::Match(
        $fixtureLog.Text,
        "taskId=(?<taskId>\d+)\s+occurrence=(?<occurrence>\d{4}-\d{2}-\d{2})\s+" +
            "triggerAt=(?<triggerAt>\S+)\s+delaySeconds=(?<delaySeconds>\d+)"
    )
    if (-not $fixtureMatch.Success) {
        throw "ERROR: the fixture did not emit a parseable TodoQuestReminderSmoke marker."
    }

    $taskId = [long]$fixtureMatch.Groups["taskId"].Value
    $occurrence = $fixtureMatch.Groups["occurrence"].Value
    $triggerAtText = $fixtureMatch.Groups["triggerAt"].Value
    $delaySeconds = [long]$fixtureMatch.Groups["delaySeconds"].Value
    $triggerAt = [DateTimeOffset]::Parse(
        $triggerAtText,
        [System.Globalization.CultureInfo]::InvariantCulture,
        [System.Globalization.DateTimeStyles]::RoundtripKind
    )
    $triggerEpochMillis = $triggerAt.ToUnixTimeMilliseconds()
    $occurrenceDate = [DateTime]::ParseExact(
        $occurrence,
        "yyyy-MM-dd",
        [System.Globalization.CultureInfo]::InvariantCulture
    )
    $epochDay = [long]$occurrenceDate.Date.Subtract([DateTime]::new(1970, 1, 1)).TotalDays
    $occurrenceUri = "todoquest://reminder/$taskId/$epochDay"
    $notificationTag = "reminder:${taskId}:$epochDay"
    $summaryValues.taskId = $taskId
    $summaryValues.occurrence = $occurrence
    $summaryValues.epochDay = $epochDay
    $summaryValues.triggerAt = $triggerAtText
    $summaryValues.triggerEpochMillis = $triggerEpochMillis
    $summaryValues.seedDelaySeconds = $delaySeconds
    $summaryValues.occurrenceUri = $occurrenceUri
    $summaryValues.notificationTag = $notificationTag

    $scheduledAlarm = Invoke-AdbText -Arguments @("shell", "dumpsys", "alarm") `
        -OutputFile (Join-Path $outputRoot "alarm-scheduled.txt") -AllowFailure
    $scheduledAlarmFound = (
        $scheduledAlarm.Text -match [regex]::Escape(
            "*walarm*:com.todoquest.action.DELIVER_REMINDER"
        ) -and
        $scheduledAlarm.Text -match "(?m)^\s*RTC_WAKEUP.*origWhen\s+$triggerEpochMillis\s+.*\b$([regex]::Escape($packageName))\b"
    )
    if (-not $scheduledAlarmFound) {
        throw "ERROR: dumpsys alarm does not contain the seeded occurrence action and trigger epoch before process stop."
    }
    $alarmDeliveriesBefore = Get-TargetAlarmDeliveryCount -AlarmDump $scheduledAlarm.Text
    $summaryValues.alarmDeliveriesBefore = $alarmDeliveriesBefore
    $notificationBefore = Get-Content -Raw -Encoding UTF8 (
        Join-Path $outputRoot "notification-before.txt"
    )
    if ($notificationBefore -match [regex]::Escape($notificationTag)) {
        throw "BLOCKED: an active notification already uses target tag '$notificationTag'; use a clean disposable emulator to avoid a false positive."
    }

    Invoke-AdbText -Arguments @("shell", "cmd", "activity", "stop-app", $packageName) `
        -OutputFile (Join-Path $outputRoot "stop-app.txt") | Out-Null
    $pidAfterStop = Invoke-AdbText -Arguments @("shell", "pidof", $packageName) `
        -OutputFile (Join-Path $outputRoot "pid-after-stop.txt") -AllowFailure
    if (-not [string]::IsNullOrWhiteSpace($pidAfterStop.Text)) {
        throw "ERROR: target process is still running after cmd activity stop-app."
    }

    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    $notificationFound = $false
    $alarmHistoryFound = $false
    $alarmDeliveriesAfter = $alarmDeliveriesBefore
    $processAbsentBeforeTrigger = $true
    $pollCount = 0
    while ([DateTimeOffset]::UtcNow -lt $deadline) {
        $pollCount += 1
        $alarmDump = Invoke-AdbText -Arguments @("shell", "dumpsys", "alarm") -AllowFailure
        $notificationDump = Invoke-AdbText -Arguments @(
            "shell",
            "dumpsys",
            "notification",
            "--noredact"
        ) -AllowFailure
        $targetPid = Invoke-AdbText -Arguments @("shell", "pidof", $packageName) -AllowFailure

        $alarmDump.Text | Set-Content -LiteralPath (Join-Path $outputRoot "alarm-latest.txt") -Encoding UTF8
        $notificationDump.Text | Set-Content -LiteralPath (
            Join-Path $outputRoot "notification-latest.txt"
        ) -Encoding UTF8
        $targetPid.Text | Set-Content -LiteralPath (
            Join-Path $outputRoot "pid-latest.txt"
        ) -Encoding UTF8

        if (
            [DateTimeOffset]::UtcNow -lt $triggerAt -and
            -not [string]::IsNullOrWhiteSpace($targetPid.Text)
        ) {
            $processAbsentBeforeTrigger = $false
        }
        $alarmDeliveriesAfter = Get-TargetAlarmDeliveryCount -AlarmDump $alarmDump.Text
        $alarmHistoryFound = (
            [DateTimeOffset]::UtcNow -ge $triggerAt -and
            $alarmDeliveriesAfter -gt $alarmDeliveriesBefore -and
            $alarmDump.Text -match [regex]::Escape(
                "*walarm*:com.todoquest.action.DELIVER_REMINDER"
            )
        )
        $notificationFound = (
            $notificationDump.Text -match [regex]::Escape("pkg=$packageName") -and
            $notificationDump.Text -match [regex]::Escape("tag=$notificationTag")
        )
        if ($alarmHistoryFound -and $notificationFound) {
            break
        }
        Start-Sleep -Seconds 2
    }

    $finalAlarm = Invoke-AdbText -Arguments @("shell", "dumpsys", "alarm") `
        -OutputFile (Join-Path $outputRoot "alarm-final.txt") -AllowFailure
    $finalNotification = Invoke-AdbText -Arguments @(
        "shell",
        "dumpsys",
        "notification",
        "--noredact"
    ) -OutputFile (Join-Path $outputRoot "notification-final.txt") -AllowFailure
    $finalLogcat = Invoke-AdbText -Arguments @("logcat", "-d", "-v", "threadtime") `
        -OutputFile (Join-Path $outputRoot "logcat-final.txt") -AllowFailure
    $crashLog = Invoke-AdbText -Arguments @("logcat", "-b", "crash", "-d", "-v", "threadtime") `
        -OutputFile (Join-Path $outputRoot "logcat-crash.txt") -AllowFailure
    $failureLines = @(
        Get-TargetFailureLines -LogcatText ($finalLogcat.Text + "`n" + $crashLog.Text)
    )
    ($failureLines -join [Environment]::NewLine) | Set-Content -LiteralPath (
        Join-Path $outputRoot "target-errors.txt"
    ) -Encoding UTF8

    $summaryValues.pollCount = $pollCount
    $summaryValues.processAbsentBeforeTrigger = $processAbsentBeforeTrigger.ToString().ToLowerInvariant()
    $summaryValues.scheduledOccurrenceFound = $scheduledAlarmFound.ToString().ToLowerInvariant()
    $summaryValues.alarmDeliveryHistoryFound = $alarmHistoryFound.ToString().ToLowerInvariant()
    $summaryValues.alarmDeliveriesAfter = $alarmDeliveriesAfter
    $summaryValues.activeOccurrenceNotificationFound = $notificationFound.ToString().ToLowerInvariant()
    $summaryValues.targetErrorCount = $failureLines.Count
    $summaryValues.finishedAt = [DateTimeOffset]::Now.ToString("o")

    if (-not $processAbsentBeforeTrigger) {
        throw "ERROR: target process restarted before the exact trigger time."
    }
    if (-not $alarmHistoryFound) {
        throw "ERROR: dumpsys alarm did not record com.todoquest in App Alarm history within $TimeoutSeconds seconds."
    }
    if (-not $notificationFound) {
        throw "ERROR: active notification tag '$notificationTag' was not found within $TimeoutSeconds seconds."
    }
    if ($failureLines.Count -gt 0) {
        throw "ERROR: TodoQuestReminder error or target crash was recorded. See target-errors.txt."
    }

    Write-Summary -Result "PASS" -Reason "none" -Values $summaryValues
    Write-Host "[$Serial] PASS - $outputRoot"
    exit 0
} catch {
    $message = $_.Exception.Message
    $result = if ($message.StartsWith("BLOCKED:")) { "BLOCKED" } else { "FAIL" }
    $summaryValues.finishedAt = [DateTimeOffset]::Now.ToString("o")
    Write-Summary -Result $result -Reason ($message -replace "`r?`n", " | ") -Values $summaryValues
    [Console]::Error.WriteLine("[$Serial] $result - $message")
    [Console]::Error.WriteLine("Diagnostics: $outputRoot")
    exit 1
}
