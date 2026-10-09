param([string]$EnvFile = '.local/dev.env')
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
$vaultJar = Join-Path $vaultRoot 'backend/target/media-vault-0.1.0.jar'
if (-not (Test-Path -LiteralPath $vaultJar)) { throw 'Run scripts/build.ps1 first.' }
Push-Location $vaultRoot
try { & java -jar $vaultJar } finally { Pop-Location }
