function Get-LaunchFailureClassification {
    param(
        [Parameter(Mandatory = $true)][bool]$BaselineCaptured,
        [Parameter(Mandatory = $true)][bool]$BaselineIsBlack,
        [Parameter(Mandatory = $true)][bool]$InstallOk,
        [Parameter(Mandatory = $true)][bool]$AmStartOk,
        [Parameter(Mandatory = $true)][bool]$PidOk,
        [Parameter(Mandatory = $true)][bool]$ForegroundOk,
        [Parameter(Mandatory = $true)][bool]$ScreenshotOk
    )

    if (-not $BaselineCaptured) {
        return [pscustomobject]@{ Domain = "environment"; Reason = "SystemBaselineCaptureFailed" }
    }
    if ($BaselineIsBlack) {
        return [pscustomobject]@{ Domain = "environment"; Reason = "EnvironmentCompositorBlack" }
    }
    if (-not $InstallOk) {
        return [pscustomobject]@{ Domain = "launch"; Reason = "PackageNotInstalled" }
    }
    if (-not $AmStartOk) {
        return [pscustomobject]@{ Domain = "launch"; Reason = "ActivityStartFailed" }
    }
    if (-not $PidOk) {
        return [pscustomobject]@{ Domain = "launch"; Reason = "ProcessNotRunning" }
    }
    if (-not $ForegroundOk) {
        return [pscustomobject]@{ Domain = "launch"; Reason = "TargetActivityNotForeground" }
    }
    if (-not $ScreenshotOk) {
        return [pscustomobject]@{ Domain = "render"; Reason = "AppCompositionBlack" }
    }
    return [pscustomobject]@{ Domain = "none"; Reason = "none" }
}

function Get-LaunchCompositionMetrics {
    param(
        [string]$SurfaceFlingerText,
        [Parameter(Mandatory = $true)][string]$Package
    )

    if ([string]::IsNullOrWhiteSpace($SurfaceFlingerText)) {
        return [pscustomobject]@{
            HwcLayerCount = 0
            TransitionRootCount = 0
            MainActivityLayerFound = $false
            MainActivityLayerHidden = $false
        }
    }

    $hwcSection = ""
    $hwcMatch = [regex]::Match(
        $SurfaceFlingerText,
        "(?ms)Display\s+\S+\s+HWC layers \(top to bottom\):(.*?)(?:\r?\nWindow Infos:|\z)"
    )
    if ($hwcMatch.Success) {
        $hwcSection = $hwcMatch.Groups[1].Value
    }

    $packagePattern = [regex]::Escape($Package)
    $mainActivityPattern = "VRI-$packagePattern/$packagePattern\.MainActivity#\d+"
    $mainActivityLayerFound = [regex]::IsMatch($SurfaceFlingerText, $mainActivityPattern)
    $mainActivityLayerHidden = [regex]::IsMatch(
        $SurfaceFlingerText,
        "(?ms)$mainActivityPattern\s+invisible reason=hidden by parent or layer flag"
    )

    return [pscustomobject]@{
        HwcLayerCount = [regex]::Matches($hwcSection, "(?m)^\s+VRI-").Count
        TransitionRootCount = [regex]::Matches($SurfaceFlingerText, "Transition Root:").Count
        MainActivityLayerFound = $mainActivityLayerFound
        MainActivityLayerHidden = $mainActivityLayerHidden
    }
}

function Get-CalendarModalCompositionMetrics {
    param(
        [Parameter(Mandatory = $true)][string]$WindowText,
        [Parameter(Mandatory = $true)][string]$SurfaceFlingerText,
        [Parameter(Mandatory = $true)][string]$Package
    )

    $surfaceFlingerRestricted = [string]::IsNullOrWhiteSpace($SurfaceFlingerText) -or -not [regex]::IsMatch(
        $SurfaceFlingerText,
        "Display\s+\S+\s+HWC layers \(top to bottom\):"
    )
    $hwcSection = ""
    if (-not $surfaceFlingerRestricted) {
        $hwcMatch = [regex]::Match(
            $SurfaceFlingerText,
            "(?ms)Display\s+\S+\s+HWC layers \(top to bottom\):(.*?)(?:\r?\nWindow Infos:|\z)"
        )
        if ($hwcMatch.Success) {
            $hwcSection = $hwcMatch.Groups[1].Value
        }
    }

    $packagePattern = [regex]::Escape($Package)
    $dialogWindows = @()
    $windowMatches = [regex]::Matches(
        $WindowText,
        "(?ms)^\s*Window #\d+ Window\{.*?(?=^\s*Window #\d+ Window\{|\z)"
    )
    foreach ($windowMatch in $windowMatches) {
        $windowBlock = $windowMatch.Value
        $belongsToPackage = [regex]::IsMatch(
            $windowBlock,
            "(?m)\bpackage=$packagePattern(?:\s|$)"
        ) -or [regex]::IsMatch(
            $windowBlock,
            "(?m)^\s*Window #\d+ Window\{[^\r\n]*\b$packagePattern(?:/|\s)"
        )
        $isBaseApplication = [regex]::IsMatch($windowBlock, "\bty=BASE_APPLICATION\b")
        $isApplicationWindow = [regex]::IsMatch($windowBlock, "\bty=APPLICATION(?:_ATTACHED_DIALOG|_PANEL)?\b")
        if (-not $belongsToPackage -or $isBaseApplication -or -not $isApplicationWindow) {
            continue
        }

        $windowReady = [regex]::IsMatch($windowBlock, "\bmHasSurface=true\b") -and
            [regex]::IsMatch($windowBlock, "\bisReadyForDisplay\(\)=true\b")
        $surfaceShown = [regex]::IsMatch($windowBlock, "Surface:\s+shown=true\b") -and
            -not [regex]::IsMatch($windowBlock, "\bmLastHidden=true\b")

        $shownAlphaMatch = [regex]::Match($windowBlock, "\bmShownAlpha=(?<alpha>[-+]?\d+(?:\.\d+)?)")
        if ($shownAlphaMatch.Success) {
            $shownAlpha = [double]::Parse(
                $shownAlphaMatch.Groups["alpha"].Value,
                [System.Globalization.CultureInfo]::InvariantCulture
            )
            $surfaceShown = $surfaceShown -and $shownAlpha -gt 0.0
        }

        $surfaceName = ""
        $surfaceMatch = [regex]::Match($windowBlock, "mSurface(?:Control)?=Surface\(name=(?<name>[^)]+)\)")
        if ($surfaceMatch.Success) {
            $surfaceName = $surfaceMatch.Groups["name"].Value
        }

        $layerVisible = -not [string]::IsNullOrWhiteSpace($surfaceName) -and
            [regex]::IsMatch($hwcSection, [regex]::Escape($surfaceName))
        $dialogWindows += [pscustomobject]@{
            Ready = $windowReady
            SurfaceShown = $surfaceShown
            LayerVisible = $layerVisible
        }
    }

    return [pscustomobject]@{
        DialogWindowFound = $dialogWindows.Count -gt 0
        DialogWindowReady = @($dialogWindows | Where-Object { $_.Ready }).Count -gt 0
        DialogSurfaceShown = @($dialogWindows | Where-Object { $_.SurfaceShown }).Count -gt 0
        DialogLayerVisible = @($dialogWindows | Where-Object { $_.LayerVisible }).Count -gt 0
        SurfaceFlingerRestricted = $surfaceFlingerRestricted
    }
}

function Get-CalendarModalFailureClassification {
    param(
        [Parameter(Mandatory = $true)][int]$AddButtonMatchCount,
        [Parameter(Mandatory = $true)][bool]$ModalHierarchyFound,
        [Parameter(Mandatory = $true)][bool]$DialogWindowReady,
        [Parameter(Mandatory = $true)][bool]$DialogSurfaceShown,
        [Parameter(Mandatory = $true)][bool]$DialogLayerVisible,
        [Parameter(Mandatory = $true)][bool]$ScreenshotChanged,
        [Parameter(Mandatory = $true)][bool]$SurfaceFlingerRestricted
    )

    if ($AddButtonMatchCount -le 0) {
        return [pscustomobject]@{ Domain = "interaction"; Reason = "AddButtonNotFound" }
    }
    if ($AddButtonMatchCount -gt 1) {
        return [pscustomobject]@{ Domain = "interaction"; Reason = "AddButtonAmbiguous" }
    }
    if (-not $ModalHierarchyFound) {
        return [pscustomobject]@{ Domain = "interaction"; Reason = "DialogNotCreated" }
    }
    if (-not $DialogWindowReady) {
        return [pscustomobject]@{ Domain = "render"; Reason = "DialogWindowNotReady" }
    }
    if (-not $DialogSurfaceShown -or (-not $SurfaceFlingerRestricted -and -not $DialogLayerVisible)) {
        return [pscustomobject]@{ Domain = "render"; Reason = "DialogCompositionHidden" }
    }
    if (-not $ScreenshotChanged) {
        return [pscustomobject]@{ Domain = "render"; Reason = "DialogCompositionHidden" }
    }
    return [pscustomobject]@{ Domain = "none"; Reason = "none" }
}

function Get-CalendarAddButtonTarget {
    param([Parameter(Mandatory = $true)][string]$HierarchyXml)

    try {
        [xml]$document = $HierarchyXml
    }
    catch {
        return [pscustomobject]@{
            MatchCount = 0
            TargetFound = $false
            CenterX = 0
            CenterY = 0
        }
    }

    $addText = [string]::Concat([char]0xCD94, [char]0xAC00)
    $matchingNodes = @($document.SelectNodes("//node[@text='$addText']") | Where-Object {
        $_.GetAttribute("visible-to-user") -ne "false" -and
            $_.GetAttribute("enabled") -ne "false"
    })
    if ($matchingNodes.Count -ne 1) {
        return [pscustomobject]@{
            MatchCount = $matchingNodes.Count
            TargetFound = $false
            CenterX = 0
            CenterY = 0
        }
    }

    $clickableNode = $matchingNodes[0]
    while ($clickableNode -and $clickableNode.Name -eq "node") {
        $isClickable = $clickableNode.GetAttribute("clickable") -eq "true"
        $isVisible = $clickableNode.GetAttribute("visible-to-user") -ne "false"
        $isEnabled = $clickableNode.GetAttribute("enabled") -ne "false"
        if ($isClickable -and $isVisible -and $isEnabled) {
            break
        }
        $clickableNode = $clickableNode.ParentNode
    }

    if (-not $clickableNode -or $clickableNode.Name -ne "node") {
        return [pscustomobject]@{
            MatchCount = 1
            TargetFound = $false
            CenterX = 0
            CenterY = 0
        }
    }

    $boundsMatch = [regex]::Match(
        $clickableNode.GetAttribute("bounds"),
        "^\[(?<left>-?\d+),(?<top>-?\d+)\]\[(?<right>-?\d+),(?<bottom>-?\d+)\]$"
    )
    if (-not $boundsMatch.Success) {
        return [pscustomobject]@{
            MatchCount = 1
            TargetFound = $false
            CenterX = 0
            CenterY = 0
        }
    }

    $left = [int]$boundsMatch.Groups["left"].Value
    $top = [int]$boundsMatch.Groups["top"].Value
    $right = [int]$boundsMatch.Groups["right"].Value
    $bottom = [int]$boundsMatch.Groups["bottom"].Value
    if ($right -le $left -or $bottom -le $top) {
        return [pscustomobject]@{
            MatchCount = 1
            TargetFound = $false
            CenterX = 0
            CenterY = 0
        }
    }

    return [pscustomobject]@{
        MatchCount = 1
        TargetFound = $true
        CenterX = [int](($left + $right) / 2)
        CenterY = [int](($top + $bottom) / 2)
    }
}

function Test-CalendarModalHierarchy {
    param([Parameter(Mandatory = $true)][string]$HierarchyXml)

    try {
        [xml]$document = $HierarchyXml
    }
    catch {
        return $false
    }

    $taskEditorTitle = [string]::Concat(
        [char]0xD560, " ", [char]0xC77C, " ", [char]0xCD94, [char]0xAC00
    )
    $titleField = [string]::Concat([char]0xC81C, [char]0xBAA9)
    foreach ($requiredText in @($taskEditorTitle, $titleField)) {
        $visibleMatches = @($document.SelectNodes("//node[@text='$requiredText']") | Where-Object {
            $_.GetAttribute("visible-to-user") -ne "false"
        })
        if ($visibleMatches.Count -eq 0) {
            return $false
        }
    }
    return $true
}

function Resolve-AndroidSdkRoot {
    $candidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk")
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate -PathType Container) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "Android SDK root was not found. Check ANDROID_SDK_ROOT or ANDROID_HOME."
}

function Get-HostEmulatorVersion {
    param([Parameter(Mandatory = $true)][string]$SdkRoot)

    $emulatorPath = Join-Path $SdkRoot "emulator\emulator.exe"
    if (-not (Test-Path -LiteralPath $emulatorPath -PathType Leaf)) {
        return "unknown"
    }

    $versionOutput = & $emulatorPath -version 2>&1
    $versionLine = $versionOutput | Where-Object { $_ -match "Android emulator version" } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($versionLine)) {
        return "unknown"
    }
    return $versionLine.Trim()
}
