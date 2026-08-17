[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Phase,

    [switch]$Push
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$python = Join-Path $repoRoot ".venv\Scripts\python.exe"
$execute = Join-Path $repoRoot "scripts\execute.py"

if (-not (Test-Path -LiteralPath $python -PathType Leaf)) {
    [Console]::Error.WriteLine("ERROR: local Python virtual environment not found: $python")
    [Console]::Error.WriteLine("Create it and install development dependencies:")
    [Console]::Error.WriteLine("  python -m venv .venv")
    [Console]::Error.WriteLine("  .\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt")
    exit 1
}

$env:PYTHONUTF8 = "1"
$arguments = @($execute, $Phase)
if ($Push) {
    $arguments += "--push"
}

Push-Location $repoRoot
try {
    & $python @arguments
    $exitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

exit $exitCode
