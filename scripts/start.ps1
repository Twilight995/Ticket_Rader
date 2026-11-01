# 티켓레이더 서버 실행 (팀 노트북용). 저장소 어디서든: powershell -ExecutionPolicy Bypass -File scripts\start.ps1
$root = Split-Path -Parent $PSScriptRoot

if (-not (Test-Path "$root\.env")) {
    Write-Host ".env 파일이 없습니다. .env.example 을 복사해 .env 를 만들고 DB_PASSWORD 를 채워 주세요." -ForegroundColor Yellow
    exit 1
}

$mysql = Get-Service MySQL80 -ErrorAction SilentlyContinue
if ($null -eq $mysql) {
    Write-Host "MySQL80 서비스를 찾지 못했습니다. MySQL 8 이 설치되어 있는지 확인하세요." -ForegroundColor Yellow
} elseif ($mysql.Status -ne 'Running') {
    Write-Host "MySQL80 서비스가 꺼져 있습니다. 관리자 PowerShell 에서 'net start MySQL80' 을 먼저 실행하세요." -ForegroundColor Yellow
    exit 1
}

Set-Location "$root\server"
.\mvnw.cmd -q spring-boot:run
