[CmdletBinding()]
param(
    [string]$Package = "com.todoquest",
    [string]$Activity = ".MainActivity",
    [string]$OutputDir = "app\build\launch-diagnostics\calendar-modal",
    [string]$Serial,
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$LeaveRunning
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
$remoteHierarchyPath = "/data/local/tmp/todoquest-calendar-modal.xml"
$minimumScreenChangeRatio = 0.10
$screenChangeNoiseMargin = 0.08
$channelDifferenceThreshold = 12
$maximumSearchSwipes = 5
$modalWaitMilliseconds = 5000
$pollIntervalMilliseconds = 200
$sampleTargetPerAxis = 160

function New-SafeFileName {
    param([Parameter(Mandatory = $true)][string]$Value)

    $safe = ($Value -replace "[^A-Za-z0-9_-]+", "-").Trim("-", "_")
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

function Get-ConnectedDeviceSerials {
    $devices = & adb devices 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed`n$(($devices | Out-String).TrimEnd())"
    }
    return $devices |
        Select-String -Pattern "^(\S+)\s+device$" |
        ForEach-Object { $_.Matches[0].Groups[1].Value }
}

function Test-PackageInstalled {
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$Package
    )

    $result = Invoke-AdbText -Serial $Serial -Arguments @("shell", "pm", "path", $Package) -AllowFailure
    return $result.ExitCode -eq 0 -and $result.Text -match "^package:"
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

function Measure-ScreenshotDifferenceRatio {
    param(
        [Parameter(Mandatory = $true)][string]$BeforePath,
        [Parameter(Mandatory = $true)][string]$AfterPath
    )

    Add-Type -AssemblyName System.Drawing
    $before = [System.Drawing.Bitmap]::FromFile($BeforePath)
    $after = [System.Drawing.Bitmap]::FromFile($AfterPath)
    try {
        if ($before.Width -ne $after.Width -or $before.Height -ne $after.Height) {
            throw "Screenshot size changed from $($before.Width)x$($before.Height) to $($after.Width)x$($after.Height)."
        }

        $left = [int]($before.Width / 4)
        $right = $before.Width - $left
        $top = [int]($before.Height / 4)
        $bottom = $before.Height - $top
        $stepX = [Math]::Max(1, [int](($right - $left) / $sampleTargetPerAxis))
        $stepY = [Math]::Max(1, [int](($bottom - $top) / $sampleTargetPerAxis))
        $sampleCount = 0
        $changedCount = 0

        for ($y = $top; $y -lt $bottom; $y += $stepY) {
            for ($x = $left; $x -lt $right; $x += $stepX) {
                $beforePixel = $before.GetPixel($x, $y)
                $afterPixel = $after.GetPixel($x, $y)
                if (
                    [Math]::Abs([int]$beforePixel.R - [int]$afterPixel.R) -ge $channelDifferenceThreshold -or
                    [Math]::Abs([int]$beforePixel.G - [int]$afterPixel.G) -ge $channelDifferenceThreshold -or
                    [Math]::Abs([int]$beforePixel.B - [int]$afterPixel.B) -ge $channelDifferenceThreshold
                ) {
                    $changedCount += 1
                }
                $sampleCount += 1
            }
        }
        return $changedCount / [double]$sampleCount
    }
    finally {
        $before.Dispose()
        $after.Dispose()
    }
}

function Save-UiHierarchy {
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$OutputFile
    )

    $dump = Invoke-AdbText -Serial $Serial -Arguments @(
        "shell", "uiautomator", "dump", "--compressed", $remoteHierarchyPath
    ) -AllowFailure
    if ($dump.ExitCode -ne 0) {
        $dump.Text | Set-Content -LiteralPath "$OutputFile.error.txt" -Encoding UTF8
        return $false
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $pullOutput = & adb -s $Serial pull $remoteHierarchyPath $OutputFile 2>&1
        $pullExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($pullExitCode -ne 0 -or -not (Test-Path -LiteralPath $OutputFile -PathType Leaf)) {
        ($pullOutput | Out-String).TrimEnd() | Set-Content -LiteralPath "$OutputFile.error.txt" -Encoding UTF8
        return $false
    }
    return $true
}

function Get-DisplaySize {
    param([Parameter(Mandatory = $true)][string]$Serial)

    $result = Invoke-AdbText -Serial $Serial -Arguments @("shell", "wm", "size")
    $matches = [regex]::Matches($result.Text, "(?m)(?:Physical|Override) size:\s*(?<width>\d+)x(?<height>\d+)")
    if ($matches.Count -eq 0) {
        throw "Unable to parse display size from: $($result.Text)"
    }
    $match = $matches[$matches.Count - 1]
    return [pscustomobject]@{
        Width = [int]$match.Groups["width"].Value
        Height = [int]$match.Groups["height"].Value
    }
}

function Save-CompositionArtifacts {
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$DeviceDir,
        [Parameter(Mandatory = $true)][string]$Prefix
    )

    $window = Invoke-AdbText -Serial $Serial -Arguments @(
        "shell", "dumpsys", "window", "windows"
    ) -OutputFile (Join-Path $DeviceDir "$Prefix-window-windows.txt") -AllowFailure
    $surfaceFlinger = Invoke-AdbText -Serial $Serial -Arguments @(
        "shell", "dumpsys", "SurfaceFlinger", "--layers"
    ) -OutputFile (Join-Path $DeviceDir "$Prefix-surfaceflinger-layers.txt") -AllowFailure
    Invoke-AdbText -Serial $Serial -Arguments @(
        "logcat", "-d", "-t", "1000"
    ) -OutputFile (Join-Path $DeviceDir "$Prefix-logcat.txt") -AllowFailure | Out-Null

    return [pscustomobject]@{
        WindowText = $window.Text
        SurfaceFlingerText = $surfaceFlinger.Text
        SurfaceFlingerExitCode = $surfaceFlinger.ExitCode
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

$debugApkPath = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipBuild) {
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
if (-not $SkipInstall -and -not (Test-Path -LiteralPath $debugApkPath -PathType Leaf)) {
    throw "Debug APK was not found at $debugApkPath. Run without -SkipBuild first."
}

$failedDevices = @()
foreach ($deviceSerial in $serials) {
    $deviceDir = Join-Path $outputRoot (New-SafeFileName $deviceSerial)
    New-Item -ItemType Directory -Force -Path $deviceDir | Out-Null
    $diagnosticErrorPath = Join-Path $deviceDir "diagnostic-error.txt"
    if (Test-Path -LiteralPath $diagnosticErrorPath -PathType Leaf) {
        Remove-Item -LiteralPath $diagnosticErrorPath
    }

    $passed = $false
    $summaryWritten = $false
    $addButtonMatchCount = 0
    $modalHierarchyFound = $false
    $dialogWindowReady = $false
    $dialogSurfaceShown = $false
    $dialogLayerVisible = $false
    $screenChangedPixelRatio = 0.0
    try {
        Invoke-AdbText -Serial $deviceSerial -Arguments @("shell", "input", "keyevent", "KEYCODE_WAKEUP") -AllowFailure | Out-Null
        Invoke-AdbText -Serial $deviceSerial -Arguments @("shell", "wm", "dismiss-keyguard") -AllowFailure | Out-Null
        if (-not $SkipInstall) {
            $install = Invoke-AdbText -Serial $deviceSerial -Arguments @(
                "install", "-r", "-t", $debugApkPath
            ) -OutputFile (Join-Path $deviceDir "install.txt") -AllowFailure
            if ($install.ExitCode -ne 0) {
                throw "Debug APK installation failed: $($install.Text)"
            }
        }
        if (-not (Test-PackageInstalled -Serial $deviceSerial -Package $Package)) {
            throw "Package '$Package' is not installed on $deviceSerial."
        }

        Invoke-AdbText -Serial $deviceSerial -Arguments @("shell", "am", "force-stop", $Package) -AllowFailure | Out-Null
        Invoke-AdbText -Serial $deviceSerial -Arguments @("shell", "am", "force-stop", "$Package.test") -AllowFailure | Out-Null
        Invoke-AdbText -Serial $deviceSerial -Arguments @("logcat", "-c") -AllowFailure | Out-Null
        $amStart = Invoke-AdbText -Serial $deviceSerial -Arguments @(
            "shell", "am", "start", "-W", "-S",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.LAUNCHER",
            "-n", "$Package/$Activity"
        ) -OutputFile (Join-Path $deviceDir "am-start.txt") -AllowFailure
        if ($amStart.ExitCode -ne 0 -or $amStart.Text -notmatch "Status:\s+ok") {
            throw "Clean app launch failed: $($amStart.Text)"
        }
        Start-Sleep -Milliseconds 1500

        $displaySize = Get-DisplaySize -Serial $deviceSerial
        $addButtonTarget = $null
        $swipeCount = 0
        $beforeUiPath = Join-Path $deviceDir "before-ui.xml"
        for ($attempt = 0; $attempt -le $maximumSearchSwipes; $attempt += 1) {
            $searchUiPath = Join-Path $deviceDir ("before-ui-search-{0}.xml" -f $attempt)
            if (Save-UiHierarchy -Serial $deviceSerial -OutputFile $searchUiPath) {
                Copy-Item -LiteralPath $searchUiPath -Destination $beforeUiPath -Force
                $hierarchyText = Get-Content -Raw -Encoding UTF8 -LiteralPath $searchUiPath
                $candidate = Get-CalendarAddButtonTarget -HierarchyXml $hierarchyText
                $addButtonMatchCount = $candidate.MatchCount
                if ($candidate.TargetFound) {
                    $addButtonTarget = $candidate
                    break
                }
                if ($candidate.MatchCount -gt 1) {
                    break
                }
            }

            if ($attempt -lt $maximumSearchSwipes) {
                $swipeX = [int]($displaySize.Width / 2)
                $swipeStartY = [int]($displaySize.Height * 0.78)
                $swipeEndY = [int]($displaySize.Height * 0.35)
                Invoke-AdbText -Serial $deviceSerial -Arguments @(
                    "shell", "input", "touchscreen", "-d", "0", "swipe",
                    "$swipeX", "$swipeStartY", "$swipeX", "$swipeEndY", "300"
                ) -AllowFailure | Out-Null
                $swipeCount += 1
                Start-Sleep -Milliseconds 350
            }
        }

        $beforeFirstScreenshotPath = Join-Path $deviceDir "before-first.png"
        $beforeScreenshotPath = Join-Path $deviceDir "before.png"
        $afterScreenshotPath = Join-Path $deviceDir "after.png"
        Save-Screenshot -Serial $deviceSerial -OutputFile $beforeFirstScreenshotPath
        Start-Sleep -Milliseconds 250
        Save-Screenshot -Serial $deviceSerial -OutputFile $beforeScreenshotPath
        $preClickNoiseRatio = Measure-ScreenshotDifferenceRatio `
            -BeforePath $beforeFirstScreenshotPath `
            -AfterPath $beforeScreenshotPath
        $requiredScreenChangeRatio = [Math]::Max(
            $minimumScreenChangeRatio,
            $preClickNoiseRatio + $screenChangeNoiseMargin
        )
        $beforeComposition = Save-CompositionArtifacts `
            -Serial $deviceSerial `
            -DeviceDir $deviceDir `
            -Prefix "before"

        $afterUiPath = Join-Path $deviceDir "after-ui.xml"
        if ($addButtonTarget) {
            $tap = Invoke-AdbText -Serial $deviceSerial -Arguments @(
                "shell", "input", "touchscreen", "-d", "0", "tap",
                "$($addButtonTarget.CenterX)", "$($addButtonTarget.CenterY)"
            ) -OutputFile (Join-Path $deviceDir "tap.txt") -AllowFailure

            $deadline = [DateTime]::UtcNow.AddMilliseconds($modalWaitMilliseconds)
            $poll = 0
            $candidateScreenshotPath = Join-Path $deviceDir "after-candidate.png"
            do {
                Start-Sleep -Milliseconds $pollIntervalMilliseconds
                $pollUiPath = Join-Path $deviceDir ("after-ui-poll-{0:D2}.xml" -f $poll)
                if (Save-UiHierarchy -Serial $deviceSerial -OutputFile $pollUiPath) {
                    Copy-Item -LiteralPath $pollUiPath -Destination $afterUiPath -Force
                    $afterHierarchyText = Get-Content -Raw -Encoding UTF8 -LiteralPath $pollUiPath
                    if (Test-CalendarModalHierarchy -HierarchyXml $afterHierarchyText) {
                        $modalHierarchyFound = $true
                    }
                }

                Save-Screenshot -Serial $deviceSerial -OutputFile $candidateScreenshotPath
                $candidateRatio = Measure-ScreenshotDifferenceRatio `
                    -BeforePath $beforeScreenshotPath `
                    -AfterPath $candidateScreenshotPath
                if ($poll -eq 0 -or $candidateRatio -gt $screenChangedPixelRatio) {
                    $screenChangedPixelRatio = $candidateRatio
                    Copy-Item -LiteralPath $candidateScreenshotPath -Destination $afterScreenshotPath -Force
                }
                $poll += 1
            } while (
                [DateTime]::UtcNow -lt $deadline -and
                (-not $modalHierarchyFound -or $screenChangedPixelRatio -lt $requiredScreenChangeRatio)
            )
        } else {
            "SKIPPED: no unambiguous clickable Korean add node was found." |
                Set-Content -LiteralPath (Join-Path $deviceDir "tap.txt") -Encoding UTF8
            if (Save-UiHierarchy -Serial $deviceSerial -OutputFile $afterUiPath) {
                $afterHierarchyText = Get-Content -Raw -Encoding UTF8 -LiteralPath $afterUiPath
                $modalHierarchyFound = Test-CalendarModalHierarchy -HierarchyXml $afterHierarchyText
            }
            Save-Screenshot -Serial $deviceSerial -OutputFile $afterScreenshotPath
            $screenChangedPixelRatio = Measure-ScreenshotDifferenceRatio `
                -BeforePath $beforeScreenshotPath `
                -AfterPath $afterScreenshotPath
        }

        $afterComposition = Save-CompositionArtifacts `
            -Serial $deviceSerial `
            -DeviceDir $deviceDir `
            -Prefix "after"
        $compositionMetrics = Get-CalendarModalCompositionMetrics `
            -WindowText $afterComposition.WindowText `
            -SurfaceFlingerText $afterComposition.SurfaceFlingerText `
            -Package $Package
        $dialogWindowReady = $compositionMetrics.DialogWindowReady
        $dialogSurfaceShown = $compositionMetrics.DialogSurfaceShown
        $dialogLayerVisible = $compositionMetrics.DialogLayerVisible
        $classificationCount = if ($addButtonTarget) { $addButtonMatchCount } elseif ($addButtonMatchCount -gt 1) { $addButtonMatchCount } else { 0 }
        $classification = Get-CalendarModalFailureClassification `
            -AddButtonMatchCount $classificationCount `
            -ModalHierarchyFound $modalHierarchyFound `
            -DialogWindowReady $dialogWindowReady `
            -DialogSurfaceShown $dialogSurfaceShown `
            -DialogLayerVisible $dialogLayerVisible `
            -ScreenshotChanged ($screenChangedPixelRatio -ge $requiredScreenChangeRatio) `
            -SurfaceFlingerRestricted $compositionMetrics.SurfaceFlingerRestricted
        $passed = $classification.Domain -eq "none"

        $summary = @(
            "result=$(if ($passed) { 'PASS' } else { 'FAIL' })"
            "failureDomain=$($classification.Domain)"
            "failureReason=$($classification.Reason)"
            "serial=$deviceSerial"
            "addButtonMatchCount=$addButtonMatchCount"
            "addButtonTargetFound=$($null -ne $addButtonTarget)"
            "searchSwipeCount=$swipeCount"
            "modalHierarchyFound=$modalHierarchyFound"
            "dialogWindowReady=$dialogWindowReady"
            "dialogSurfaceShown=$dialogSurfaceShown"
            "dialogLayerVisible=$dialogLayerVisible"
            "surfaceFlingerRestricted=$($compositionMetrics.SurfaceFlingerRestricted)"
            "screenChangedPixelRatio=$('{0:F6}' -f $screenChangedPixelRatio)"
            "preClickNoisePixelRatio=$('{0:F6}' -f $preClickNoiseRatio)"
            "screenChangeRequiredRatio=$('{0:F6}' -f $requiredScreenChangeRatio)"
            "beforeScreenshot=$beforeScreenshotPath"
            "afterScreenshot=$afterScreenshotPath"
            "beforeUi=$beforeUiPath"
            "afterUi=$afterUiPath"
            "beforeWindow=$(Join-Path $deviceDir 'before-window-windows.txt')"
            "afterWindow=$(Join-Path $deviceDir 'after-window-windows.txt')"
            "beforeSurfaceFlinger=$(Join-Path $deviceDir 'before-surfaceflinger-layers.txt')"
            "afterSurfaceFlinger=$(Join-Path $deviceDir 'after-surfaceflinger-layers.txt')"
            "beforeLogcat=$(Join-Path $deviceDir 'before-logcat.txt')"
            "afterLogcat=$(Join-Path $deviceDir 'after-logcat.txt')"
        )
        $summary | Set-Content -LiteralPath (Join-Path $deviceDir "summary.txt") -Encoding UTF8
        $summaryWritten = $true
    }
    catch {
        $errorMessage = $_.Exception.Message
        $errorMessage | Set-Content -LiteralPath $diagnosticErrorPath -Encoding UTF8
        if (-not $summaryWritten) {
            @(
                "result=FAIL"
                "failureDomain=diagnostic"
                "failureReason=UnexpectedDiagnosticError"
                "serial=$deviceSerial"
                "addButtonMatchCount=$addButtonMatchCount"
                "modalHierarchyFound=$modalHierarchyFound"
                "dialogWindowReady=$dialogWindowReady"
                "dialogSurfaceShown=$dialogSurfaceShown"
                "dialogLayerVisible=$dialogLayerVisible"
                "screenChangedPixelRatio=$('{0:F6}' -f $screenChangedPixelRatio)"
                "error=$errorMessage"
            ) | Set-Content -LiteralPath (Join-Path $deviceDir "summary.txt") -Encoding UTF8
        }
    }
    finally {
        Invoke-AdbText -Serial $deviceSerial -Arguments @(
            "shell", "rm", "-f", $remoteHierarchyPath
        ) -AllowFailure | Out-Null
        if (-not $passed -or -not $LeaveRunning) {
            Invoke-AdbText -Serial $deviceSerial -Arguments @(
                "shell", "am", "force-stop", $Package
            ) -AllowFailure | Out-Null
        }
    }

    if (-not $passed) {
        $failedDevices += $deviceSerial
    }
    Write-Host "[$deviceSerial] $(if ($passed) { 'PASS' } else { 'FAIL' }) - $deviceDir"
}

if ($failedDevices.Count -gt 0) {
    [Console]::Error.WriteLine("ERROR: Calendar modal diagnostics failed for: $($failedDevices -join ', ')")
    exit 1
}
exit 0
