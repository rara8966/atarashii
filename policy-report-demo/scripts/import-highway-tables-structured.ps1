# 把客户合并 docx 里的指定表格用 POI 结构化解析后写入 standard_tables 表。
# 适用于"只从汇总 docx 里挑几张表"的场景，不会污染其它工程类型。
#
# 用法：
#   .\scripts\import-highway-tables-structured.ps1
#   .\scripts\import-highway-tables-structured.ps1 -TypeKey highway -Tables '表4.0.6-1','表4.0.6-2'
#
# 幂等：服务端用 (project_type, table_code) 检查，重复执行会更新而非新增。

param(
    [string]$DocxPath = "$env:USERPROFILE\Downloads\建设用地指标（各工程核心表格合并）.docx",
    [string]$ApiBase  = 'http://127.0.0.1:8080',
    [string]$TypeKey  = 'highway',
    [string[]]$Tables = @('表4.0.6-1','表4.0.6-2','表4.0.6-3','表4.0.6-4')
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $DocxPath)) {
    Write-Host "FILE NOT FOUND: $DocxPath" -ForegroundColor Red
    exit 1
}

Write-Host "Checking backend $ApiBase ..." -ForegroundColor Cyan
try {
    $types = Invoke-RestMethod -Uri "$ApiBase/api/standards/types" -Method GET -TimeoutSec 5
} catch {
    Write-Host "backend not reachable: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
$target = $types | Where-Object { $_.typeKey -eq $TypeKey }
if (-not $target) {
    Write-Host "type $TypeKey not found" -ForegroundColor Red
    exit 1
}
Write-Host "  $TypeKey ($($target.label)) before: $($target.tableCount) tables" -ForegroundColor Gray

# 构造 multipart 请求
$boundary = [System.Guid]::NewGuid().ToString()
$fileName = [System.IO.Path]::GetFileName($DocxPath)
$fileBytes = [System.IO.File]::ReadAllBytes($DocxPath)
$includeCodes = $Tables -join ','

# 用 byte 流手工拼 multipart body
$enc = [System.Text.Encoding]::UTF8
$ms = New-Object System.IO.MemoryStream

function Append-Part {
    param($Stream, $Boundary, $Name, $Value)
    $header = "--$Boundary`r`nContent-Disposition: form-data; name=`"$Name`"`r`n`r`n"
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($header + $Value + "`r`n")
    $Stream.Write($bytes, 0, $bytes.Length)
}

function Append-File {
    param($Stream, $Boundary, $Name, $FileName, $FileBytes)
    $header = "--$Boundary`r`nContent-Disposition: form-data; name=`"$Name`"; filename=`"$FileName`"`r`nContent-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document`r`n`r`n"
    $hb = [System.Text.Encoding]::UTF8.GetBytes($header)
    $Stream.Write($hb, 0, $hb.Length)
    $Stream.Write($FileBytes, 0, $FileBytes.Length)
    $tail = [System.Text.Encoding]::UTF8.GetBytes("`r`n")
    $Stream.Write($tail, 0, $tail.Length)
}

Append-Part $ms $boundary 'typeKey' $TypeKey
Append-Part $ms $boundary 'typeLabel' $target.label
Append-Part $ms $boundary 'replace' 'false'
Append-Part $ms $boundary 'includeTableCodes' $includeCodes
Append-File $ms $boundary 'file' $fileName $fileBytes
$footer = [System.Text.Encoding]::UTF8.GetBytes("--$boundary--`r`n")
$ms.Write($footer, 0, $footer.Length)

$body = $ms.ToArray()
$ms.Dispose()

Write-Host "POST $ApiBase/api/standards/tables/upload  (filter: $includeCodes) ..." -ForegroundColor Cyan
try {
    $resp = Invoke-RestMethod `
        -Uri "$ApiBase/api/standards/tables/upload" `
        -Method POST `
        -Body $body `
        -ContentType "multipart/form-data; boundary=$boundary" `
        -TimeoutSec 120
    Write-Host "OK: $($resp.message). $TypeKey total now $($resp.total)" -ForegroundColor Green
} catch {
    Write-Host "API failed: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        $r = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host $r.ReadToEnd() -ForegroundColor Yellow
    }
    exit 1
}

Write-Host ""
Write-Host "Tip: open /standards in the browser, click `查看表格` on the highway card to see them rendered." -ForegroundColor Cyan
