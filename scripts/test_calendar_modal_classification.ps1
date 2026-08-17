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

$calendarHierarchy = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<hierarchy rotation="0">
  <node text="" clickable="false" enabled="true" visible-to-user="true" bounds="[0,0][1080,2400]">
    <node text="" clickable="true" enabled="true" visible-to-user="true" bounds="[744,2110][1032,2258]">
      <node text="&#xCD94;&#xAC00;" clickable="false" enabled="true" visible-to-user="true" bounds="[842,2148][934,2220]" />
    </node>
  </node>
</hierarchy>
"@
$addButton = Get-CalendarAddButtonTarget -HierarchyXml $calendarHierarchy
Assert-Equal 1 $addButton.MatchCount "Exactly one visible Korean add button should match."
Assert-Equal $true $addButton.TargetFound "The closest clickable ancestor should be selected."
Assert-Equal 888 $addButton.CenterX "Clickable ancestor center X mismatch."
Assert-Equal 2184 $addButton.CenterY "Clickable ancestor center Y mismatch."

$ambiguousHierarchy = $calendarHierarchy -replace `
    '</node>\s*</node>\s*</hierarchy>',
    '<node text="&#xCD94;&#xAC00;" clickable="true" enabled="true" visible-to-user="true" bounds="[24,24][124,124]" /></node></node></hierarchy>'
$ambiguousTarget = Get-CalendarAddButtonTarget -HierarchyXml $ambiguousHierarchy
Assert-Equal 2 $ambiguousTarget.MatchCount "Two visible Korean add nodes must remain ambiguous."
Assert-Equal $false $ambiguousTarget.TargetFound "An ambiguous hierarchy must not return a tap target."

$modalHierarchy = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<hierarchy rotation="0">
  <node text="&#xD560; &#xC77C; &#xCD94;&#xAC00;" clickable="false" enabled="true" visible-to-user="true" bounds="[80,300][1000,520]">
    <node text="&#xC81C;&#xBAA9;" clickable="false" enabled="true" visible-to-user="true" bounds="[112,560][300,640]" />
  </node>
</hierarchy>
"@
Assert-Equal $true (Test-CalendarModalHierarchy -HierarchyXml $modalHierarchy) "Both Korean modal labels should identify the task editor."
$missingTitleHierarchy = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<hierarchy rotation="0">
  <node text="&#xD560; &#xC77C; &#xCD94;&#xAC00;" clickable="false" enabled="true" visible-to-user="true" bounds="[80,300][1000,520]">
    <node text="&#xBA54;&#xBAA8;" clickable="false" enabled="true" visible-to-user="true" bounds="[112,560][300,640]" />
  </node>
</hierarchy>
"@
$missingTitleResult = Test-CalendarModalHierarchy -HierarchyXml $missingTitleHierarchy
Assert-Equal -Expected $false -Actual $missingTitleResult -Message "A hierarchy without the title field must not identify the task editor."

$hiddenWindowText = @"
WINDOW MANAGER WINDOWS (dumpsys window windows)
  Window #0 Window{111111 u0 com.todoquest/com.todoquest.MainActivity}:
    mAttrs=WM.LayoutParams{ty=BASE_APPLICATION}
    mHasSurface=true isReadyForDisplay()=true
    Surface: shown=true layer=0 alpha=1.0
    mLastHidden=false
    mShownAlpha=1.0
  Window #1 Window{222222 u0 com.todoquest/com.todoquest.MainActivity}:
    mAttrs=WM.LayoutParams{ty=APPLICATION_ATTACHED_DIALOG fl=DIM_BEHIND}
    mHasSurface=true isReadyForDisplay()=true
    mSurfaceControl=Surface(name=VRI-com.todoquest/com.todoquest.MainActivity#77)
    Surface: shown=false layer=0 alpha=0.0
    mLastHidden=true
    mShownAlpha=0.0
"@
$hiddenSurfaceFlingerText = @"
Display 1 HWC layers (top to bottom):
  VRI-com.todoquest/com.todoquest.MainActivity#42
Window Infos:
Layer [77] VRI-com.todoquest/com.todoquest.MainActivity#77
  invisible reason=hidden by parent or layer flag
"@

$hiddenMetrics = Get-CalendarModalCompositionMetrics `
    -WindowText $hiddenWindowText `
    -SurfaceFlingerText $hiddenSurfaceFlingerText `
    -Package "com.todoquest"
Assert-Equal $true $hiddenMetrics.DialogWindowReady "The dialog Window should be ready before composition."
Assert-Equal $false $hiddenMetrics.DialogSurfaceShown "A hidden dialog Surface must not be reported as shown."
Assert-Equal $false $hiddenMetrics.DialogLayerVisible "A dialog layer outside the HWC list must not be visible."

$hiddenClassification = Get-CalendarModalFailureClassification `
    -AddButtonMatchCount 1 `
    -ModalHierarchyFound $true `
    -DialogWindowReady $hiddenMetrics.DialogWindowReady `
    -DialogSurfaceShown $hiddenMetrics.DialogSurfaceShown `
    -DialogLayerVisible $hiddenMetrics.DialogLayerVisible `
    -ScreenshotChanged $false `
    -SurfaceFlingerRestricted $false
Assert-Equal "render" $hiddenClassification.Domain "A created but hidden dialog must be a render failure."
Assert-Equal "DialogCompositionHidden" $hiddenClassification.Reason "Hidden dialog reason mismatch."

$visibleWindowText = $hiddenWindowText `
    -replace "Surface: shown=false layer=0 alpha=0.0", "Surface: shown=true layer=1 alpha=1.0" `
    -replace "mLastHidden=true", "mLastHidden=false" `
    -replace "mShownAlpha=0.0", "mShownAlpha=1.0"
$visibleSurfaceFlingerText = @"
Display 1 HWC layers (top to bottom):
  VRI-com.todoquest/com.todoquest.MainActivity#77
  VRI-com.todoquest/com.todoquest.MainActivity#42
Window Infos:
Layer [77] VRI-com.todoquest/com.todoquest.MainActivity#77
"@

$visibleMetrics = Get-CalendarModalCompositionMetrics `
    -WindowText $visibleWindowText `
    -SurfaceFlingerText $visibleSurfaceFlingerText `
    -Package "com.todoquest"
Assert-Equal $true $visibleMetrics.DialogWindowReady "Visible dialog Window should be ready."
Assert-Equal $true $visibleMetrics.DialogSurfaceShown "Visible dialog Surface should be shown."
Assert-Equal $true $visibleMetrics.DialogLayerVisible "Matching dialog layer should be present in HWC output."

$visibleClassification = Get-CalendarModalFailureClassification `
    -AddButtonMatchCount 1 `
    -ModalHierarchyFound $true `
    -DialogWindowReady $visibleMetrics.DialogWindowReady `
    -DialogSurfaceShown $visibleMetrics.DialogSurfaceShown `
    -DialogLayerVisible $visibleMetrics.DialogLayerVisible `
    -ScreenshotChanged $true `
    -SurfaceFlingerRestricted $false
Assert-Equal "none" $visibleClassification.Domain "A fully composed dialog must succeed."
Assert-Equal "none" $visibleClassification.Reason "Successful dialog reason mismatch."

$missingLayerClassification = Get-CalendarModalFailureClassification `
    -AddButtonMatchCount 1 `
    -ModalHierarchyFound $true `
    -DialogWindowReady $true `
    -DialogSurfaceShown $true `
    -DialogLayerVisible $false `
    -ScreenshotChanged $true `
    -SurfaceFlingerRestricted $false
Assert-Equal "render" $missingLayerClassification.Domain "Accessible SurfaceFlinger must require the dialog HWC layer."
Assert-Equal "DialogCompositionHidden" $missingLayerClassification.Reason "Missing HWC dialog layer reason mismatch."

$restrictedSuccess = Get-CalendarModalFailureClassification `
    -AddButtonMatchCount 1 `
    -ModalHierarchyFound $true `
    -DialogWindowReady $true `
    -DialogSurfaceShown $true `
    -DialogLayerVisible $false `
    -ScreenshotChanged $true `
    -SurfaceFlingerRestricted $true
Assert-Equal "none" $restrictedSuccess.Domain "Restricted devices may use Window and screenshot evidence."
Assert-Equal "none" $restrictedSuccess.Reason "Restricted-device success reason mismatch."

$restrictedUnchanged = Get-CalendarModalFailureClassification `
    -AddButtonMatchCount 1 `
    -ModalHierarchyFound $true `
    -DialogWindowReady $true `
    -DialogSurfaceShown $true `
    -DialogLayerVisible $false `
    -ScreenshotChanged $false `
    -SurfaceFlingerRestricted $true
Assert-Equal "render" $restrictedUnchanged.Domain "Restricted devices must still show screenshot change."
Assert-Equal "DialogCompositionHidden" $restrictedUnchanged.Reason "Unchanged screenshot reason mismatch."

$missingButton = Get-CalendarModalFailureClassification `
    -AddButtonMatchCount 0 `
    -ModalHierarchyFound $false `
    -DialogWindowReady $false `
    -DialogSurfaceShown $false `
    -DialogLayerVisible $false `
    -ScreenshotChanged $false `
    -SurfaceFlingerRestricted $false
Assert-Equal "interaction" $missingButton.Domain "A missing add button must be an interaction failure."
Assert-Equal "AddButtonNotFound" $missingButton.Reason "Missing add button reason mismatch."

$ambiguousButton = Get-CalendarModalFailureClassification `
    -AddButtonMatchCount 2 `
    -ModalHierarchyFound $false `
    -DialogWindowReady $false `
    -DialogSurfaceShown $false `
    -DialogLayerVisible $false `
    -ScreenshotChanged $false `
    -SurfaceFlingerRestricted $false
Assert-Equal "interaction" $ambiguousButton.Domain "Multiple add buttons must be an interaction failure."
Assert-Equal "AddButtonAmbiguous" $ambiguousButton.Reason "Ambiguous add button reason mismatch."

$dialogNotCreated = Get-CalendarModalFailureClassification `
    -AddButtonMatchCount 1 `
    -ModalHierarchyFound $false `
    -DialogWindowReady $false `
    -DialogSurfaceShown $false `
    -DialogLayerVisible $false `
    -ScreenshotChanged $false `
    -SurfaceFlingerRestricted $false
Assert-Equal "interaction" $dialogNotCreated.Domain "A missing post-tap hierarchy must be an interaction failure."
Assert-Equal "DialogNotCreated" $dialogNotCreated.Reason "Missing dialog hierarchy reason mismatch."

Write-Host "Calendar modal classification tests passed."
