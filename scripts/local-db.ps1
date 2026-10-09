param(
    [ValidateSet('Start','Stop','Status')][string]$Action = 'Start',
    [string]$MySqlHome = ''
)
$ErrorActionPreference = 'Stop'
$vaultRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$vaultInstance = [IO.Path]::GetFullPath((Join-Path $vaultRoot '.local/mysql84-test'))
$vaultData = [IO.Path]::GetFullPath((Join-Path $vaultInstance 'data'))
if (-not $vaultData.StartsWith($vaultRoot + [IO.Path]::DirectorySeparatorChar)) { throw 'Unsafe database path' }
if (-not $MySqlHome) {
    $vaultRuntimeFile = Join-Path $vaultRoot '.local/mysql-runtime.txt'
    if (Test-Path -LiteralPath $vaultRuntimeFile) { $MySqlHome = (Get-Content -LiteralPath $vaultRuntimeFile -Raw).Trim() }
}
$vaultListener = Get-NetTCPConnection -LocalPort 33326 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($vaultListener) {
    $vaultProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($vaultListener.OwningProcess)"
    $vaultCommand = ($vaultProcess.CommandLine -replace '\\','/').ToLowerInvariant()
    if (-not $vaultCommand.Contains(($vaultData -replace '\\','/').ToLowerInvariant())) { throw 'Port 33326 belongs to a different instance; no action taken.' }
}
if ($Action -eq 'Status') {
    if ($vaultListener) { Write-Output 'Project MySQL is listening on 127.0.0.1:33326.' }
    else { Write-Output 'Project MySQL is stopped.' }
    exit 0
}
if ($Action -eq 'Start') {
    if ($vaultListener) { Write-Output 'Project MySQL is already running.'; exit 0 }
    if (-not (Test-Path -LiteralPath (Join-Path $vaultData 'auto.cnf'))) { throw 'No initialized project database. Use Docker Compose for a fresh installation.' }
    if (-not $MySqlHome -or -not (Test-Path -LiteralPath (Join-Path $MySqlHome 'bin/mysqld.exe'))) { throw 'Provide -MySqlHome pointing to the MySQL runtime used to initialize this data directory.' }
    $vaultArgs = @('--no-defaults',"--basedir=$($MySqlHome -replace '\\','/')","--datadir=$($vaultData -replace '\\','/')",'--port=33326','--bind-address=127.0.0.1','--mysqlx=OFF','--default-time-zone=+00:00',"--log-error=$(($vaultInstance -replace '\\','/'))/mysql84-error.log", "--pid-file=$(($vaultInstance -replace '\\','/'))/mysql84.pid")
    Start-Process -FilePath (Join-Path $MySqlHome 'bin/mysqld.exe') -ArgumentList $vaultArgs -WindowStyle Hidden | Out-Null
    for ($vaultAttempt=0; $vaultAttempt -lt 20; $vaultAttempt++) {
        if (Get-NetTCPConnection -LocalPort 33326 -State Listen -ErrorAction SilentlyContinue) { Write-Output 'Project MySQL started on 127.0.0.1:33326.'; exit 0 }
        Start-Sleep -Seconds 1
    }
    throw 'MySQL did not become ready; inspect .local/mysql84-test/mysql84-error.log.'
}
if (-not $vaultListener) { Write-Output 'Project MySQL is already stopped.'; exit 0 }
$vaultConfig = Join-Path $vaultRoot '.local/mysql84-root.cnf'
$vaultConfigText = Get-Content -LiteralPath $vaultConfig -Raw
if ($vaultConfigText -notmatch '(?m)^host=127\.0\.0\.1\s*$' -or $vaultConfigText -notmatch '(?m)^port=33326\s*$') { throw 'Refusing to stop a database outside the project port.' }
& (Join-Path $MySqlHome 'bin/mysqladmin.exe') "--defaults-file=$vaultConfig" shutdown
if ($LASTEXITCODE -ne 0) { throw 'Database shutdown failed.' }
Write-Output 'Project MySQL stopped.'
