# ======================================================
# SMACian - LOCAL PostgreSQL starter (testing only)
# Double-click or run:  .\local-db-start.ps1
# Starts the portable Postgres 16 on localhost:5432 (no install needed).
# Data lives in .runtime\pgdata (git-ignored). Re-run after every reboot.
# NEVER commit this file.
# ======================================================

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$BinDir = Join-Path $Root ".runtime\pgsql\pgsql\bin"
$Ctl = Join-Path $BinDir "pg_ctl.exe"
$ReadyExe = Join-Path $BinDir "pg_isready.exe"
$Data = Join-Path $Root ".runtime\pgdata"
$Log = Join-Path $Root ".runtime\pgdata.log"

& $Ctl status -D $Data 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "Postgres is already running on localhost:5432" -ForegroundColor Green
} else {
    Write-Host "Starting local Postgres (unclean shutdowns need WAL recovery - can take ~1 min)..." -ForegroundColor Yellow
    $Args = @(
        "start",
        "-D", $Data,
        "-l", $Log,
        "-o", "-p 5432 -c listen_addresses=localhost"
    )
    Start-Process -FilePath $Ctl -ArgumentList $Args -WindowStyle Hidden
    # Poll instead of a fixed sleep: recovery time varies, and a second
    # pg_ctl start while one is already recovering can crash the server,
    # so never launch twice - just wait for readiness here.
    $Up = $false
    for ($i = 0; $i -lt 24; $i++) {
        Start-Sleep -Seconds 5
        & $ReadyExe -h localhost -p 5432 2>$null
        if ($LASTEXITCODE -eq 0) { $Up = $true; break }
    }
    if (-not $Up) {
        Write-Host "Postgres did not become ready in 2 minutes. Check .runtime\pgdata.log" -ForegroundColor Red
    }
}
