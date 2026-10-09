$ErrorActionPreference = 'Stop'
$vaultRoot = Split-Path -Parent $PSScriptRoot
$vaultBundled = Join-Path $vaultRoot '.tools/jdk21'
if (Test-Path -LiteralPath $vaultBundled) {
    $vaultJdk = Get-ChildItem -LiteralPath $vaultBundled -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') } | Select-Object -First 1
    if ($vaultJdk) { $env:JAVA_HOME = $vaultJdk.FullName }
}
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    throw 'Set JAVA_HOME to a JDK 21+ directory. Do not use the Java 8 entry in PATH.'
}
$env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$env:Path"
$vaultJavaVersion = & java --version
if ($LASTEXITCODE -ne 0 -or $vaultJavaVersion[0] -notmatch '(?:openjdk|java) (\d+)' -or [int]$Matches[1] -lt 21) { throw 'Java 21+ is required.' }
