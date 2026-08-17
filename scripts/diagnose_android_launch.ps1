[CmdletBinding()]
param(
    [string]$Package = "com.todoquest",
    [string]$Activity = ".MainActivity",
    [string]$OutputDir = "app\build\launch-diagnostics",
    [string]$Serial,
    [switch]$SkipBuild,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
. (Join-Path $scriptDir "android_launch_common.ps1")
$outputRoot = if ([System.IO.Path]::IsPathRooted($OutputDir)) {
    $OutputDir
} else {
    Join-Path $repoRoot $OutputDir
}

$blackBrightnessThreshold = 16
$blackPixelRatioFailureThreshold = 0.95
$averageBrightnessFailureThreshold = 24
$sampleTargetPerAxis = 40

function New-SafeFileName {
    param([Parameter(Mandatory = $true)][string]$Value)

    $safe = $Value -replace "[^A-Za-z0-9_-]+", "-"
    $safe = $safe.Trim("-", "_")
    if ([string]::IsNullOrWhiteSpace($safe)) {
        return "unknown"
    }
    return $safe
}

function Invoke-AdbText {
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [string]$OutputFile,
        [switch]$AllowFailure
    )

    $adbArguments = @("-s", $Serial) + $Arguments
    $output = & adb @adbArguments 2>&1
    $exitCode = $LASTEXITCODE
    $text = ($output | Out-String).TrimEnd()

    if ($OutputFile) {
        $text | Set-Content -LiteralPath $OutputFile -Encoding UTF8
    }

    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "adb $($adbArguments -join ' ') failed with exit code $exitCode`n$text"
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Text = $text
    }
}

function Get-AdbProperty {
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $result = Invoke-AdbText -Serial $Serial -Arguments @("shell", "getprop", $Name) -AllowFailure
    if ([string]::IsNullOrWhiteSpace($result.Text)) {
        return "unknown"
    }
    return $result.Text.Trim()
}

function Save-Screenshot {
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$OutputFile
    )

    $quotedOutput = '"' + $OutputFile + '"'
    & cmd /c "adb -s $Serial exec-out screencap -p > $quotedOutput"
    if ($LASTEXITCODE -ne 0) {
        throw "adb -s $Serial exec-out screencap failed with exit code $LASTEXITCODE"
    }
}

function Measure-ScreenshotBrightness {
    param([Parameter(Mandatory = $true)][string]$ImagePath)

    Add-Type -AssemblyName System.Drawing
    $bitmap = [System.Drawing.Bitmap]::FromFile($ImagePath)
    try {
        $left = [int]($bitmap.Width / 4)
        $right = $bitmap.Width - $left
        $top = [int]($bitmap.Height / 4)
        $bottom = $bitmap.Height - $top
        $stepX = [Math]::Max(1, [int](($right - $left) / $sampleTargetPerAxis))
        $stepY = [Math]::Max(1, [int](($bottom - $top) / $sampleTargetPerAxis))

        $sampleCount = 0
        $blackPixelCount = 0
        [long]$brightnessTotal = 0

        for ($y = $top; $y -lt $bottom; $y += $stepY) {
            for ($x = $left; $x -lt $right; $x += $stepX) {
                $pixel = $bitmap.GetPixel($x, $y)
                $brightness = [int](($pixel.R + $pixel.G + $pixel.B) / 3)
                if ($brightness -le $blackBrightnessThreshold) {
                    $blackPixelCount += 1
                }
                $brightnessTotal += $brightness
                $sampleCount += 1
            }
        }

        $blackPixelRatio = $blackPixelCount / [double]$sampleCount
        $averageBrightness = $brightnessTotal / [double]$sampleCount

        return [pscustomobject]@{
            BlackPixelRatio = $blackPixelRatio
            AverageBrightness = $averageBrightness
            IsBlackScreen = (
                $blackPixelRatio -ge $blackPixelRatioFailureThreshold -and
                $averageBrightness -le $averageBrightnessFailureThreshold
            )
        }
    }
    finally {
        $bitmap.Dispose()
    }
}

function Get-ConnectedDeviceSerials {
    $devices = & adb devices 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed`n$(($devices | Out-String).TrimEnd())"
    }

    return $devices |
        Select-String -Pattern "^(\S+)\s+device$" |
        ForEach-Object { $_.Matches[0].Groups[1].Value }
}

function Get-TargetComponentPatterns {
    param(
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][string]$Activity
    )

    $patterns = @("$Package/$Activity")
    if ($Activity.StartsWith(".")) {
        $patterns += "$Package/$Package$Activity"
    }
    return $patterns
}

function Test-PackageInstalled {
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][string]$OutputFile
    )

    $packagePath = Invoke-AdbText -Serial $Serial -Arguments @("shell", "pm", "path", $Package) -OutputFile $OutputFile -AllowFailure
    return [pscustomobject]@{
        IsInstalled = ($packagePath.ExitCode -eq 0 -and $packagePath.Text -match "^package:")
        Text = $packagePath.Text
    }
}

function Get-ForegroundSummary {
    param(
        [string]$WindowText,
        [string]$ActivityText,
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][string]$Activity
    )

    $targetPatterns = @(Get-TargetComponentPatterns -Package $Package -Activity $Activity)
    $foregroundLines = @()
    $diagnosticLines = @()
    if (-not [string]::IsNullOrWhiteSpace($WindowText)) {
        $foregroundLines += $WindowText -split "`r?`n" | Where-Object {
            $_ -match "mCurrentFocus|mFocusedApp|topResumedActivity"
        }
        $diagnosticLines += $WindowText -split "`r?`n" | Where-Object {
            $_ -match [regex]::Escape($Package) -and $_ -match "Window #|mActivityRecord|mToken"
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($ActivityText)) {
        $foregroundLines += $ActivityText -split "`r?`n" | Where-Object {
            $_ -match "mResumedActivity|topResumedActivity|ResumedActivity|mLastPausedActivity"
        }
        $diagnosticLines += $ActivityText -split "`r?`n" | Where-Object {
            $_ -match [regex]::Escape($Package) -and $_ -match "ACTIVITY|Hist|Task"
        }
    }

    $foregroundText = ($foregroundLines | ForEach-Object { $_.Trim() }) -join " | "
    $interestingLines = @($foregroundLines) + @($diagnosticLines)
    $summaryText = ($interestingLines | Select-Object -First 30 | ForEach-Object { $_.Trim() }) -join " | "
    if ([string]::IsNullOrWhiteSpace($summaryText)) {
        $summaryText = "unknown"
    }

    $foregroundOk = $false
    foreach ($pattern in $targetPatterns) {
        if ($foregroundText.Contains($pattern)) {
            $foregroundOk = $true
            break
        }
    }

    return [pscustomobject]@{
        ForegroundOk = $foregroundOk
        Summary = $summaryText
    }
}

function Get-DiagnosticHints {
    param(
        [string]$WindowText,
        [string]$SurfaceFlingerText,
        [string]$LogcatText,
        [Parameter(Mandatory = $true)][string]$Package
    )

    $combinedText = @($WindowText, $SurfaceFlingerText, $LogcatText) -join "`n"
    $hints = @()
    if ($combinedText -match [regex]::Escape("$Package.test")) {
        $hints += "TestPackageWindowOrLayerPresent"
    }
    if ($combinedText -match "PackageUpdateActivity") {
        $hints += "PackageUpdateActivityPresent"
    }
    if ($combinedText -match [regex]::Escape("Splash Screen $Package")) {
        $hints += "SplashScreenLayerPresent"
    }
    if ($combinedText -match "starting_reveal") {
        $hints += "StartingRevealTransitionPresent"
    }
    if ($combinedText -match "hidden by parent or layer flag") {
        $hints += "HiddenByParentOrLayerFlag"
    }
    if ($LogcatText -match "BLASTSyncEngine.*never received commit callback") {
        $hints += "BlastSyncCommitCallbackMissing"
    }

    if ($hints.Count -eq 0) {
        return "none"
    }
    return ($hints | Select-Object -Unique) -join ","
}

function Get-EmulatorHostMetadata {
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$SdkRoot,
        [Parameter(Mandatory = $true)][bool]$IsEmulator
    )

    if (-not $IsEmulator) {
        return [pscustomobject]@{
            AvdName = "not-applicable"
            ConfiguredGpuMode = "not-applicable"
            RuntimeGpuMode = "not-applicable"
            SystemImageRevision = "not-applicable"
        }
    }

    $avdResult = Invoke-AdbText -Serial $Serial -Arguments @("shell", "getprop", "ro.boot.qemu.avd_name") -AllowFailure
    $avdName = $avdResult.Text -split "`r?`n" |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and $_ -ne "OK" } |
        Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($avdName)) {
        $avdResult = Invoke-AdbText -Serial $Serial -Arguments @("emu", "avd", "name") -AllowFailure
        $avdName = $avdResult.Text -split "`r?`n" |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and $_ -ne "OK" } |
            Select-Object -First 1
    }
    if ([string]::IsNullOrWhiteSpace($avdName)) {
        $avdName = "unknown"
    }

    $configuredGpuMode = "unknown"
    $runtimeGpuMode = "unknown"
    $systemImageRevision = "unknown"
    $avdDir = Join-Path $env:USERPROFILE ".android\avd\$avdName.avd"
    $configPath = Join-Path $avdDir "config.ini"
    $runtimeConfigPath = Join-Path $avdDir "hardware-qemu.ini"

    if (Test-Path -LiteralPath $configPath -PathType Leaf) {
        $configLines = Get-Content -LiteralPath $configPath
        $configuredGpuLine = $configLines | Where-Object { $_ -match "^hw\.gpu\.mode=" } | Select-Object -First 1
        if ($configuredGpuLine) {
            $configuredGpuMode = ($configuredGpuLine -split "=", 2)[1].Trim()
        }

        $imageLine = $configLines | Where-Object { $_ -match "^image\.sysdir\.1=" } | Select-Object -First 1
        if ($imageLine) {
            $imageRelativePath = ($imageLine -split "=", 2)[1].Trim().TrimEnd("\", "/")
            $sourceProperties = Join-Path (Join-Path $SdkRoot $imageRelativePath) "source.properties"
            if (Test-Path -LiteralPath $sourceProperties -PathType Leaf) {
                $revisionLine = Get-Content -LiteralPath $sourceProperties |
                    Where-Object { $_ -match "^Pkg\.Revision=" } |
                    Select-Object -First 1
                if ($revisionLine) {
                    $systemImageRevision = ($revisionLine -split "=", 2)[1].Trim()
                }
            }
        }
    }

    if (Test-Path -LiteralPath $runtimeConfigPath -PathType Leaf) {
        $runtimeGpuLine = Get-Content -LiteralPath $runtimeConfigPath |
            Where-Object { $_ -match "^hw\.gpu\.mode\s*=" } |
            Select-Object -First 1
        if ($runtimeGpuLine) {
            $runtimeGpuMode = ($runtimeGpuLine -split "=", 2)[1].Trim()
        }
    }

    return [pscustomobject]@{
        AvdName = $avdName
        ConfiguredGpuMode = $configuredGpuMode
        RuntimeGpuMode = $runtimeGpuMode
        SystemImageRevision = $systemImageRevision
    }
}

New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

$connectedSerials = @(Get-ConnectedDeviceSerials)
if ($connectedSerials.Count -eq 0) {
    [Console]::Error.WriteLine("ERROR: no connected Android devices in 'device' state.")
    exit 1
}

$serials = if ([string]::IsNullOrWhiteSpace($Serial)) {
    $connectedSerials
} else {
    if ($connectedSerials -notcontains $Serial) {
        throw "Requested device '$Serial' is not connected in 'device' state."
    }
    @($Serial)
}

$sdkRoot = Resolve-AndroidSdkRoot
$hostEmulatorVersion = Get-HostEmulatorVersion -SdkRoot $sdkRoot
$debugApkPath = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"

if (-not $SkipBuild -and -not $SkipInstall) {
    Push-Location $repoRoot
    try {
        & .\gradlew.bat assembleDebug
        if ($LASTEXITCODE -ne 0) {
            throw "gradlew assembleDebug failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}
if (-not $SkipInstall) {
    if (-not (Test-Path -LiteralPath $debugApkPath -PathType Leaf)) {
        throw "Debug APK was not found at $debugApkPath. Run without -SkipBuild first."
    }
}

$failedDevices = @()

foreach ($deviceSerial in $serials) {
    $model = Get-AdbProperty -Serial $deviceSerial -Name "ro.product.model"
    $sdk = Get-AdbProperty -Serial $deviceSerial -Name "ro.build.version.sdk"
    $release = Get-AdbProperty -Serial $deviceSerial -Name "ro.build.version.release"
    $fingerprint = Get-AdbProperty -Serial $deviceSerial -Name "ro.build.fingerprint"
    $kernelQemu = Get-AdbProperty -Serial $deviceSerial -Name "ro.kernel.qemu"
    $renderer = Get-AdbProperty -Serial $deviceSerial -Name "debug.hwui.renderer"
    $egl = Get-AdbProperty -Serial $deviceSerial -Name "ro.hardware.egl"
    $emulator = if ($kernelQemu -eq "1") { "true" } elseif ($kernelQemu -eq "0") { "false" } else { "unknown" }
    $emulatorMetadata = Get-EmulatorHostMetadata `
        -Serial $deviceSerial `
        -SdkRoot $sdkRoot `
        -IsEmulator ($emulator -eq "true")
    $deviceDir = Join-Path $outputRoot (New-SafeFileName "$deviceSerial-$model-sdk$sdk")
    New-Item -ItemType Directory -Force -Path $deviceDir | Out-Null

    $deviceInfo = @(
        "serial=$deviceSerial"
        "model=$model"
        "sdk=$sdk"
        "release=$release"
        "fingerprint=$fingerprint"
        "emulator=$emulator"
        "kernelQemu=$kernelQemu"
        "renderer=$renderer"
        "egl=$egl"
        "hostEmulatorVersion=$hostEmulatorVersion"
        "avdName=$($emulatorMetadata.AvdName)"
        "configuredGpuMode=$($emulatorMetadata.ConfiguredGpuMode)"
        "runtimeGpuMode=$($emulatorMetadata.RuntimeGpuMode)"
        "systemImageRevision=$($emulatorMetadata.SystemImageRevision)"
    )
    $deviceInfo | Set-Content -LiteralPath (Join-Path $deviceDir "device.txt") -Encoding UTF8

    Invoke-AdbText -Serial $deviceSerial -Arguments @("shell", "input", "keyevent", "KEYCODE_WAKEUP") -AllowFailure | Out-Null
    Invoke-AdbText -Serial $deviceSerial -Arguments @("shell", "wm", "dismiss-keyguard") -AllowFailure | Out-Null
    $baselineStart = Invoke-AdbText -Serial $deviceSerial -Arguments @(
        "shell",
        "am",
        "start",
        "-W",
        "-a",
        "android.settings.SETTINGS"
    ) -OutputFile (Join-Path $deviceDir "baseline-am-start.txt") -AllowFailure
    Start-Sleep -Seconds 2

    $baselineScreenshotPath = Join-Path $deviceDir "system-baseline.png"
    $baselineScreenshotError = $null
    try {
        Save-Screenshot -Serial $deviceSerial -OutputFile $baselineScreenshotPath
    }
    catch {
        $baselineScreenshotError = $_.Exception.Message
    }

    $baselineBrightness = $null
    if (-not $baselineScreenshotError -and (Test-Path -LiteralPath $baselineScreenshotPath -PathType Leaf)) {
        $baselineBrightness = Measure-ScreenshotBrightness -ImagePath $baselineScreenshotPath
    }
    $baselineCaptured = $baselineBrightness -ne $null
    $baselineIsBlack = $baselineCaptured -and $baselineBrightness.IsBlackScreen
    $baselineScreenshotOk = $baselineCaptured -and -not $baselineIsBlack

    $installResult = [pscustomobject]@{ ExitCode = 0; Text = "SKIPPED: -SkipInstall was specified." }
    if (-not $SkipInstall -and $baselineScreenshotOk) {
        $installResult = Invoke-AdbText -Serial $deviceSerial -Arguments @(
            "install",
            "-r",
            "-t",
            $debugApkPath
        ) -OutputFile (Join-Path $deviceDir "install.txt") -AllowFailure
    } elseif (-not $SkipInstall) {
        $installResult = [pscustomobject]@{ ExitCode = 1; Text = "SKIPPED: system baseline is unavailable or black." }
        $installResult.Text | Set-Content -LiteralPath (Join-Path $deviceDir "install.txt") -Encoding UTF8
    } else {
        $installResult.Text | Set-Content -LiteralPath (Join-Path $deviceDir "install.txt") -Encoding UTF8
    }

    $testPackage = "$Package.test"
    $packageInstall = Test-PackageInstalled -Serial $deviceSerial -Package $Package -OutputFile (Join-Path $deviceDir "package-path.txt")
    $installOk = $packageInstall.IsInstalled

    Invoke-AdbText -Serial $deviceSerial -Arguments @("shell", "am", "force-stop", $Package) -OutputFile (Join-Path $deviceDir "force-stop.txt") -AllowFailure | Out-Null
    Invoke-AdbText -Serial $deviceSerial -Arguments @("shell", "am", "force-stop", $testPackage) -OutputFile (Join-Path $deviceDir "force-stop-test-package.txt") -AllowFailure | Out-Null

    $screenshotPath = Join-Path $deviceDir "screen.png"
    $screenshotError = $null
    $amStart = [pscustomobject]@{ ExitCode = 1; Text = "SKIPPED: package is not installed." }
    $pidResult = [pscustomobject]@{ ExitCode = 1; Text = "" }

    if ($baselineScreenshotOk -and $installOk) {
        $amStart = Invoke-AdbText -Serial $deviceSerial -Arguments @(
            "shell",
            "am",
            "start",
            "-W",
            "-S",
            "-a",
            "android.intent.action.MAIN",
            "-c",
            "android.intent.category.LAUNCHER",
            "-n",
            "$Package/$Activity"
        ) -OutputFile (Join-Path $deviceDir "am-start.txt") -AllowFailure
        Start-Sleep -Seconds 3
        $pidResult = Invoke-AdbText -Serial $deviceSerial -Arguments @("shell", "pidof", $Package) -OutputFile (Join-Path $deviceDir "pid.txt") -AllowFailure

        try {
            Save-Screenshot -Serial $deviceSerial -OutputFile $screenshotPath
        }
        catch {
            $screenshotError = $_.Exception.Message
        }
    }
    else {
        $skipReason = if (-not $baselineScreenshotOk) {
            "SKIPPED: system baseline is unavailable or black."
        } else {
            "SKIPPED: package is not installed."
        }
        $amStart = [pscustomobject]@{ ExitCode = 1; Text = $skipReason }
        $amStart.Text | Set-Content -LiteralPath (Join-Path $deviceDir "am-start.txt") -Encoding UTF8
        $skipReason | Set-Content -LiteralPath (Join-Path $deviceDir "pid.txt") -Encoding UTF8
        $screenshotError = $skipReason
    }

    $windowVisible = Invoke-AdbText -Serial $deviceSerial -Arguments @("shell", "dumpsys", "window", "visible") -OutputFile (Join-Path $deviceDir "window-visible.txt") -AllowFailure
    $activityDump = Invoke-AdbText -Serial $deviceSerial -Arguments @("shell", "dumpsys", "activity", "activities") -OutputFile (Join-Path $deviceDir "activity-activities.txt") -AllowFailure
    $surfaceFlinger = Invoke-AdbText -Serial $deviceSerial -Arguments @("shell", "dumpsys", "SurfaceFlinger", "--layers") -OutputFile (Join-Path $deviceDir "surfaceflinger-layers.txt") -AllowFailure
    $logcat = Invoke-AdbText -Serial $deviceSerial -Arguments @("logcat", "-d", "-t", "500") -OutputFile (Join-Path $deviceDir "logcat.txt") -AllowFailure
    $foreground = Get-ForegroundSummary -WindowText $windowVisible.Text -ActivityText $activityDump.Text -Package $Package -Activity $Activity
    $diagnosticHints = Get-DiagnosticHints -WindowText $windowVisible.Text -SurfaceFlingerText $surfaceFlinger.Text -LogcatText $logcat.Text -Package $Package
    $compositionMetrics = Get-LaunchCompositionMetrics -SurfaceFlingerText $surfaceFlinger.Text -Package $Package

    $brightness = $null
    if (-not $screenshotError -and (Test-Path -LiteralPath $screenshotPath -PathType Leaf)) {
        $brightness = Measure-ScreenshotBrightness -ImagePath $screenshotPath
    }

    $amStartOk = $amStart.Text -match "Status:\s+ok"
    $pidOk = -not [string]::IsNullOrWhiteSpace($pidResult.Text)
    $screenshotOk = $brightness -ne $null -and -not $brightness.IsBlackScreen
    $surfaceFlingerRestricted = $surfaceFlinger.ExitCode -ne 0
    $classification = Get-LaunchFailureClassification `
        -BaselineCaptured $baselineCaptured `
        -BaselineIsBlack $baselineIsBlack `
        -InstallOk $installOk `
        -AmStartOk $amStartOk `
        -PidOk $pidOk `
        -ForegroundOk $foreground.ForegroundOk `
        -ScreenshotOk $screenshotOk
    $passed = $classification.Domain -eq "none"

    if (-not $passed) {
        $failedDevices += $deviceSerial
    }

    $summary = @(
        "result=$(if ($passed) { 'PASS' } else { 'FAIL' })"
        "serial=$deviceSerial"
        "model=$model"
        "sdk=$sdk"
        "release=$release"
        "emulator=$emulator"
        "renderer=$renderer"
        "hostEmulatorVersion=$hostEmulatorVersion"
        "avdName=$($emulatorMetadata.AvdName)"
        "configuredGpuMode=$($emulatorMetadata.ConfiguredGpuMode)"
        "runtimeGpuMode=$($emulatorMetadata.RuntimeGpuMode)"
        "systemImageRevision=$($emulatorMetadata.SystemImageRevision)"
        "installOk=$installOk"
        "failureDomain=$($classification.Domain)"
        "failureReason=$($classification.Reason)"
        "baselineStartOk=$($baselineStart.ExitCode -eq 0)"
        "baselineScreenshotOk=$baselineScreenshotOk"
        "baselineScreenshot=$baselineScreenshotPath"
        "baselineScreenshotError=$baselineScreenshotError"
        "baselineBlackPixelRatio=$(if ($baselineBrightness) { '{0:N4}' -f $baselineBrightness.BlackPixelRatio } else { 'unknown' })"
        "baselineAverageBrightness=$(if ($baselineBrightness) { '{0:N2}' -f $baselineBrightness.AverageBrightness } else { 'unknown' })"
        "amStartOk=$amStartOk"
        "pidOk=$pidOk"
        "foregroundOk=$($foreground.ForegroundOk)"
        "foreground=$($foreground.Summary)"
        "screenshotOk=$screenshotOk"
        "screenshot=$screenshotPath"
        "screenshotError=$screenshotError"
        "blackPixelRatio=$(if ($brightness) { '{0:N4}' -f $brightness.BlackPixelRatio } else { 'unknown' })"
        "averageBrightness=$(if ($brightness) { '{0:N2}' -f $brightness.AverageBrightness } else { 'unknown' })"
        "surfaceFlingerRestricted=$surfaceFlingerRestricted"
        "hwcLayerCount=$($compositionMetrics.HwcLayerCount)"
        "transitionRootCount=$($compositionMetrics.TransitionRootCount)"
        "mainActivityLayerFound=$($compositionMetrics.MainActivityLayerFound)"
        "mainActivityLayerHidden=$($compositionMetrics.MainActivityLayerHidden)"
        "diagnosticHints=$diagnosticHints"
        "surfaceFlinger=$(Join-Path $deviceDir 'surfaceflinger-layers.txt')"
        "windowVisible=$(Join-Path $deviceDir 'window-visible.txt')"
        "activityActivities=$(Join-Path $deviceDir 'activity-activities.txt')"
        "logcat=$(Join-Path $deviceDir 'logcat.txt')"
    )
    $summary | Set-Content -LiteralPath (Join-Path $deviceDir "summary.txt") -Encoding UTF8

    Write-Host "[$deviceSerial] $(if ($passed) { 'PASS' } else { 'FAIL' }) - $deviceDir"
}

if ($failedDevices.Count -gt 0) {
    [Console]::Error.WriteLine("ERROR: launch diagnostics failed for: $($failedDevices -join ', ')")
    exit 1
}

exit 0
