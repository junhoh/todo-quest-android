[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Suggest", "Create", "List", "Show", "Validate", "Sync", "MigrateLegacy")]
    [string]$Command,

    [string]$Slug,
    [string[]]$Step,
    [string[]]$Path,
    [string[]]$Area,
    [string[]]$Kind,
    [string[]]$Tag,
    [string[]]$Status,
    [string]$Selector,
    [switch]$Strict,
    [switch]$Check,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$python = Join-Path $repoRoot ".venv\Scripts\python.exe"
$manager = Join-Path $repoRoot "scripts\phase_manager.py"

if (-not (Test-Path -LiteralPath $python -PathType Leaf)) {
    [Console]::Error.WriteLine("ERROR: local Python virtual environment not found: $python")
    [Console]::Error.WriteLine("Create it and install development dependencies:")
    [Console]::Error.WriteLine("  python -m venv .venv")
    [Console]::Error.WriteLine("  .\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt")
    exit 1
}

$commandName = switch ($Command) {
    "MigrateLegacy" { "migrate-legacy" }
    default { $Command.ToLowerInvariant() }
}
$arguments = @($manager, $commandName)

if ($Slug) {
    $arguments += @("--slug", $Slug)
}
foreach ($value in $Step) {
    $arguments += @("--step", $value)
}
foreach ($value in $Path) {
    $arguments += @("--path", $value)
}
foreach ($value in $Area) {
    $arguments += @("--area", $value)
}
foreach ($value in $Kind) {
    $arguments += @("--kind", $value)
}
foreach ($value in $Tag) {
    $arguments += @("--tag", $value)
}
foreach ($value in $Status) {
    $arguments += @("--status", $value)
}
if ($Selector) {
    $arguments += $Selector
}
if ($Strict) {
    $arguments += "--strict"
}
if ($Check) {
    $arguments += "--check"
}
if ($DryRun) {
    $arguments += "--dry-run"
}

$env:PYTHONUTF8 = "1"
Push-Location $repoRoot
try {
    & $python @arguments
    $exitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

exit $exitCode
