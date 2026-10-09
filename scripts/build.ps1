param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
$vaultRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $vaultRoot 'frontend')
try {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
} finally { Pop-Location }
$vaultStatic = [IO.Path]::GetFullPath((Join-Path $vaultRoot 'backend/src/main/resources/static'))
$vaultExpected = [IO.Path]::GetFullPath((Join-Path $vaultRoot 'backend/src/main/resources/static'))
if ($vaultStatic -ne $vaultExpected -or -not $vaultStatic.StartsWith([IO.Path]::GetFullPath($vaultRoot) + [IO.Path]::DirectorySeparatorChar)) { throw 'Unsafe build output path' }
if (Test-Path -LiteralPath $vaultStatic) { Remove-Item -LiteralPath $vaultStatic -Recurse -Force }
New-Item -ItemType Directory -Path $vaultStatic -Force | Out-Null
Copy-Item -Path (Join-Path $vaultRoot 'frontend/dist/*') -Destination $vaultStatic -Recurse
Push-Location $vaultRoot
try {
    $vaultMavenArgs = @('-B', '-ntp', '-pl', 'backend', '-am', 'package')
    if ($SkipTests) { $vaultMavenArgs += '-DskipTests' }
    & mvn.cmd @vaultMavenArgs
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' }
} finally { Pop-Location }
Write-Output 'Built backend/target/media-vault-0.1.0.jar (includes the web UI).'
