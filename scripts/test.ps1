param([string]$EnvFile = '.local/test.env')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
$vaultRoot = Split-Path -Parent $PSScriptRoot
$vaultEnvPath = Join-Path $vaultRoot $EnvFile
if (Test-Path -LiteralPath $vaultEnvPath) {
    Get-Content -LiteralPath $vaultEnvPath | ForEach-Object {
        if ($_ -and -not $_.StartsWith('#')) {
            $vaultParts = $_ -split '=', 2
            if ($vaultParts.Length -eq 2) { [Environment]::SetEnvironmentVariable($vaultParts[0].Trim(),$vaultParts[1],'Process') }
        }
    }
}
Push-Location $vaultRoot
try {
    & mvn.cmd -B -ntp test
    if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
} finally { Pop-Location }
