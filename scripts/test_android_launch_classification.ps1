$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptDir "android_launch_common.ps1")

function Assert-Equal {
    param(
        [Parameter(Mandatory = $true)]$Expected,
        [Parameter(Mandatory = $true)]$Actual,
        [Parameter(Mandatory = $true)][string]$Message
    )

    if ($Expected -ne $Actual) {
        throw "$Message Expected=<$Expected> Actual=<$Actual>"
    }
}

$environmentFailure = Get-LaunchFailureClassification `
    -BaselineCaptured $true `
    -BaselineIsBlack $true `
    -InstallOk $true `
    -AmStartOk $true `
    -PidOk $true `
    -ForegroundOk $true `
    -ScreenshotOk $false
Assert-Equal "environment" $environmentFailure.Domain "Black system baseline must be an environment failure."
Assert-Equal "EnvironmentCompositorBlack" $environmentFailure.Reason "Environment failure reason mismatch."

$renderFailure = Get-LaunchFailureClassification `
    -BaselineCaptured $true `
    -BaselineIsBlack $false `
    -InstallOk $true `
    -AmStartOk $true `
    -PidOk $true `
    -ForegroundOk $true `
    -ScreenshotOk $false
Assert-Equal "render" $renderFailure.Domain "App-only black frame must be a render failure."
Assert-Equal "AppCompositionBlack" $renderFailure.Reason "Render failure reason mismatch."

$success = Get-LaunchFailureClassification `
    -BaselineCaptured $true `
    -BaselineIsBlack $false `
    -InstallOk $true `
    -AmStartOk $true `
    -PidOk $true `
    -ForegroundOk $true `
    -ScreenshotOk $true
Assert-Equal "none" $success.Domain "Successful launch must not have a failure domain."
Assert-Equal "none" $success.Reason "Successful launch must not have a failure reason."

$surfaceText = @"
Display 1 HWC layers (top to bottom):
  VRI-com.todoquest/com.todoquest.MainActivity#42
Window Infos:
Transition Root: Task=8#51
Layer [42] VRI-com.todoquest/com.todoquest.MainActivity#42
  invisible reason=hidden by parent or layer flag
"@
$metrics = Get-LaunchCompositionMetrics -SurfaceFlingerText $surfaceText -Package "com.todoquest"
Assert-Equal 1 $metrics.HwcLayerCount "HWC layer count mismatch."
Assert-Equal 1 $metrics.TransitionRootCount "Transition root count mismatch."
Assert-Equal $true $metrics.MainActivityLayerFound "MainActivity layer should be found."
Assert-Equal $true $metrics.MainActivityLayerHidden "MainActivity hidden state should be detected."

Write-Host "Android launch classification tests passed."
