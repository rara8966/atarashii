# 一次性脚本：从客户给的合并 docx 中提取指定表格，通过后端 API 写入到指定项目类型下。
# 默认导入"表4.0.6-1 ~ 表4.0.6-4"到 highway 类型（公路工程）。
#
# 用法：
#   .\scripts\import-highway-tables.ps1
#   .\scripts\import-highway-tables.ps1 -TypeKey highway -Targets '表4.0.6-1','表4.0.6-2'
#
# 幂等：replaceByTitle=true，重复执行不会产生重复条目。

param(
    [string]$DocxPath = "$env:USERPROFILE\Downloads\建设用地指标（各工程核心表格合并）.docx",
    [string]$ApiBase  = 'http://127.0.0.1:8080',
    [string]$TypeKey  = 'highway',
    [string[]]$Targets = @('表4.0.6-1','表4.0.6-2','表4.0.6-3','表4.0.6-4')
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $DocxPath)) { Write-Host "FILE NOT FOUND: $DocxPath" -ForegroundColor Red; exit 1 }
Write-Host "Reading: $DocxPath" -ForegroundColor Cyan

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($DocxPath)
try {
    $entry  = $zip.GetEntry('word/document.xml')
    $reader = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)
    $xml    = $reader.ReadToEnd()
    $reader.Dispose()
} finally { $zip.Dispose() }

$xml  = $xml -replace '</w:p>','[PARABREAK]' -replace '</w:tr>','[ROWBREAK]' -replace '</w:tc>','[CELLBREAK]'
$text = $xml -replace '<[^>]+>','' -replace '\[PARABREAK\]',"`n" -replace '\[ROWBREAK\]',"`n" -replace '\[CELLBREAK\]',"`t"
$text = $text -replace '&lt;','<' -replace '&gt;','>' -replace '&amp;','&' -replace '&quot;','"' -replace '&apos;',"'"
$lines = $text -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ }
Write-Host "  extracted $($lines.Count) non-empty lines" -ForegroundColor Gray

$starts = [ordered]@{}
for ($i = 0; $i -lt $lines.Count; $i++) {
    foreach ($t in $Targets) {
        if (-not $starts.Contains($t) -and $lines[$i].StartsWith($t)) { $starts[$t] = $i }
    }
}
$missing = $Targets | Where-Object { -not $starts.Contains($_) }
if ($missing.Count -gt 0) { Write-Host "WARN missing: $($missing -join ', ')" -ForegroundColor Yellow }

$titlePattern = '^(第[一二三四五六七八九十]+[篇章节]|表\s*\d+(\.\d+)*(-\d+)?\s|附录)'
$sorted = $starts.GetEnumerator() | Sort-Object { $_.Value }
$items = @()
foreach ($e in $sorted) {
    $tname = $e.Key; $start = $e.Value; $end = $lines.Count
    for ($j = $start + 1; $j -lt $lines.Count; $j++) {
        if ($lines[$j] -match $titlePattern -and -not $lines[$j].StartsWith($tname)) { $end = $j; break }
    }
    $items += [ordered]@{
        chapterTitle = $lines[$start]
        content      = ($lines[$start..($end - 1)]) -join "`n"
        sourceFile   = [System.IO.Path]::GetFileName($DocxPath)
    }
    Write-Host "  + $($lines[$start])  ($($end - $start) lines)" -ForegroundColor Green
}

if ($items.Count -eq 0) { Write-Host "no items extracted, abort" -ForegroundColor Red; exit 1 }

Write-Host "Checking backend $ApiBase ..." -ForegroundColor Cyan
try {
    $types = Invoke-RestMethod -Uri "$ApiBase/api/standards/types" -Method GET -TimeoutSec 5
} catch {
    Write-Host "backend not reachable: $($_.Exception.Message)" -ForegroundColor Red; exit 1
}
$target = $types | Where-Object { $_.typeKey -eq $TypeKey }
if (-not $target) { Write-Host "type $TypeKey not found" -ForegroundColor Red; exit 1 }
Write-Host "  $TypeKey ($($target.label)) before: $($target.standardCount) items" -ForegroundColor Gray

$json = $items | ConvertTo-Json -Depth 5
$body = [System.Text.Encoding]::UTF8.GetBytes($json)
Write-Host "POST $ApiBase/api/standards/types/$TypeKey/items?replaceByTitle=true ..." -ForegroundColor Cyan

try {
    $resp = Invoke-RestMethod -Uri "$ApiBase/api/standards/types/$TypeKey/items?replaceByTitle=true" `
        -Method POST -Body $body -ContentType 'application/json; charset=utf-8' -TimeoutSec 60
    Write-Host "OK: $($resp.message). $TypeKey total now $($resp.total)" -ForegroundColor Green
} catch {
    Write-Host "API failed: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        $r = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host $r.ReadToEnd() -ForegroundColor Yellow
    }
    exit 1
}