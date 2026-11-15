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

# 화면(React)을 빌드해 두면 서버가 http://localhost:8080 에서 화면도 함께 제공한다(pom.xml 의 copy-client-dist).
Set-Location "$root\client"
if (-not (Test-Path "node_modules")) { npm install }
npm run build
if ($LASTEXITCODE -ne 0) {
    Write-Host "화면 빌드에 실패했습니다. 예전에 빌드한 화면이 있으면 그 화면으로, 없으면 API 서버만 띄웁니다." -ForegroundColor Yellow
}

Set-Location "$root\server"
.\mvnw.cmd -q spring-boot:run
