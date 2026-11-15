# 6단계 검증표 채우기. 떠 있는 서버에 docs/validation/cases.json 의 가상 글 10건을 보내고, docs/validation.md 에 붙일 표 줄을 찍는다.
#   규칙 엔진만(AI 불필요):  pwsh -File scripts/validate.ps1 -SkipAi    → 기대 추출값을 그대로 POST /api/check
#   AI 포함(할당량 필요):    pwsh -File scripts/validate.ps1            → POST /api/extract(글 또는 이미지) → 그 결과로 POST /api/check
#Requires -Version 7
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [switch]$SkipAi,
    [int]$DelaySeconds = 5  # AI 무료 티어 분당 한도를 넘지 않게 글 사이에 쉰다
)
$ErrorActionPreference = 'Stop'
$dir = "$PSScriptRoot/../docs/validation"
$cases = (Get-Content "$dir/cases.json" -Raw -Encoding utf8 | ConvertFrom-Json).cases
$eventId = (Invoke-RestMethod "$BaseUrl/api/event").data.id

function Format-Extracted($v) {
    if ($null -eq $v) { return '(없음)' }
    $show = { param($x) if ($null -eq $x) { '-' } elseif ($x -is [bool]) { "$x".ToLower() } else { "$x" } }
    ($v.pricePerTicket, $v.seat, $v.quantity, $v.deliveryMethod, $v.prepaymentRequired | ForEach-Object { & $show $_ }) -join ' / '
}

function Invoke-Check($values) {
    $body = @{ eventId = $eventId }
    foreach ($k in 'pricePerTicket', 'seat', 'quantity', 'deliveryMethod', 'prepaymentRequired') { $body[$k] = $values.$k }
    $res = Invoke-RestMethod "$BaseUrl/api/check" -Method Post -ContentType 'application/json; charset=utf-8' `
        -Body ([Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json)))
    @($res.data.findings | ForEach-Object { "$($_.type):$($_.severity)" })
}

# 실패 원인이 섞이지 않게 상태 코드를 같이 적는다 (429 = 분당 한도, 400 = 값 부족, 연결 실패 = 서버 꺼짐)
function Format-Failure($label, $err) {
    $code = $err.Exception.Response.StatusCode.value__
    "$label 실패($(if ($code) { $code } else { '연결 안 됨' }))"
}

$matched = 0
foreach ($c in $cases) {
    $extracted = $c.expected
    $actualExtracted = ''
    if (-not $SkipAi) {
        $form = @{ eventId = $eventId }
        if ($c.image) { $form.image = Get-Item "$dir/$($c.image)" } else { $form.text = $c.text }
        try {
            $data = (Invoke-RestMethod "$BaseUrl/api/extract" -Method Post -Form $form).data
            $extracted = $data.extracted
            $actualExtracted = "$(Format-Extracted $extracted) ($($data.aiStatus))"
        } catch {
            $extracted = $null
            $actualExtracted = Format-Failure '추출 요청' $_
        }
        Start-Sleep -Seconds $DelaySeconds
    }
    $findings = if ($null -eq $extracted) { @('비교 안 함') } else {
        try { Invoke-Check $extracted } catch { @(Format-Failure '비교 요청' $_) }
    }
    $ok = ($findings -join ',') -eq ($c.expectedFindings -join ',')
    if ($ok) { $matched++ }
    $shown = if ($findings.Count) { $findings -join '<br>' } else { '0건' }
    Write-Output "| $($c.id) | $actualExtracted | $shown | $(if ($ok) { '일치' } else { '불일치' }) |"
}
Write-Output "일치 $matched / $($cases.Count)"
