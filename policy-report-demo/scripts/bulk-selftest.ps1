$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$test = Join-Path $root ".copilot-test"
$bulk = Join-Path $test "bulk"
$summaryPath = Join-Path $test "bulk-selftest-summary.json"

New-Item -ItemType Directory -Force $test, $bulk | Out-Null
Remove-Item -Force (Join-Path $bulk "*.txt") -ErrorAction SilentlyContinue

function Write-Fixture([string]$Name, [string]$Text) {
    Set-Content -Path (Join-Path $bulk $Name) -Value $Text -Encoding UTF8
}

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-CurlJson {
    param(
        [string]$Name,
        [object[]]$CurlArgs
    )

    $out = Join-Path $test "$Name.response"
    $status = & curl.exe @CurlArgs -s -o $out -w '%{http_code}'
    if (-not ($status -match '^2')) {
        $body = if (Test-Path $out) { Get-Content $out -Raw -Encoding UTF8 } else { '' }
        throw "$Name failed HTTP $status $body"
    }
    if ((Get-Item $out).Length -gt 0) {
        return Get-Content $out -Raw -Encoding UTF8 | ConvertFrom-Json
    }
    return $null
}

Write-Fixture "template-1009.txt" "1009 template material for API selftest. Total area 0.3800 ha."
Write-Fixture "late-step-attachment.txt" "Plain late-step attachment. No known routing keyword."
Write-Fixture "report-draft-source.txt" "Review report source material. Public interest and rectification are complete."

$policyCardPath = Join-Path $test "fake-policy-card.txt"
Set-Content -Path $policyCardPath -Value "policy card fixture for selftest" -Encoding UTF8
$fallbackFilePath = Join-Path $test "unrecognized-late-step.txt"
Set-Content -Path $fallbackFilePath -Value "plain attachment without known routing keyword" -Encoding UTF8

$healthStatus = & curl.exe -s -o (Join-Path $test "health.response") -w '%{http_code}' "http://localhost:8080/api/health"
Assert-True ($healthStatus -eq "200") "health failed HTTP $healthStatus"

$project = Invoke-CurlJson "project" @("http://localhost:8080/api/demo/project")
Assert-True ($project.steps.Count -eq 8) "project endpoint did not return 8 steps"

$ollama = Invoke-CurlJson "ollama" @("http://localhost:8080/api/ollama/status")
Assert-True ($null -ne $ollama.defaultModel) "ollama status endpoint returned invalid payload"

$stateBefore = Invoke-CurlJson "state-before" @("http://localhost:8080/api/workspace/state")
Assert-True ($stateBefore.steps.step1.stepId -eq "step1") "workspace state endpoint returned invalid payload"

$policyUpload = Invoke-CurlJson "policy-upload" @(
    "-X", "POST", "http://localhost:8080/api/policy-card/upload",
    "-F", "file=@$policyCardPath"
)
Assert-True ($policyUpload.fileName -eq "fake-policy-card.txt") "policy-card upload endpoint failed"

$files = Get-ChildItem $bulk -Filter "*.txt" | Sort-Object Name
$analyses = @()
foreach ($file in $files) {
    $analysis = Invoke-CurlJson ("analyze-" + $file.BaseName) @(
        "-X", "POST", "http://localhost:8080/api/documents/analyze",
        "-F", "file=@$($file.FullName)",
        "-F", "fallbackStep=step5",
        "-F", "aiProvider=rules"
    )
    $analyses += $analysis
}

$fallbackAnalysis = Invoke-CurlJson "analyze-unrecognized-fallback" @(
    "-X", "POST", "http://localhost:8080/api/documents/analyze",
    "-F", "file=@$fallbackFilePath",
    "-F", "fallbackStep=step7",
    "-F", "aiProvider=rules"
)
Assert-True ($fallbackAnalysis.targetStep -eq "step7") "unrecognized material did not fall back to the current step"

$fieldPayload = @{ stepId = "step1"; key = "projectName"; value = "Xingning Wutang Wind Farm Phase I - selftest"; status = "warn" } | ConvertTo-Json -Compress
$fieldPayloadPath = Join-Path $test "field-update-bulk.json"
Set-Content -Path $fieldPayloadPath -Value $fieldPayload -Encoding UTF8
$stateAfterField = Invoke-CurlJson "field-update-bulk" @(
    "-X", "PUT", "http://localhost:8080/api/workspace/fields",
    "-H", "Content-Type: application/json",
    "--data-binary", "@$fieldPayloadPath"
)

$situationPayload = @{ stepId = "step1"; groupId = "approvalSituation"; value = "3" } | ConvertTo-Json -Compress
$situationPayloadPath = Join-Path $test "situation-update.json"
Set-Content -Path $situationPayloadPath -Value $situationPayload -Encoding UTF8
$stateAfterSituation = Invoke-CurlJson "situation-update" @(
    "-X", "PUT", "http://localhost:8080/api/workspace/situations",
    "-H", "Content-Type: application/json",
    "--data-binary", "@$situationPayloadPath"
)

$firstFileId = ($analyses | Where-Object { $_.fileId } | Select-Object -First 1).fileId
Assert-True (-not [string]::IsNullOrWhiteSpace($firstFileId)) "analyze endpoint did not return fileId"

$sourceStatus = & curl.exe -s -o (Join-Path $test "source-file.response") -w '%{http_code}' "http://localhost:8080/api/documents/source/$firstFileId"
Assert-True ($sourceStatus -eq "200") "source file endpoint failed HTTP $sourceStatus"

$deleteState = Invoke-CurlJson "delete-fallback-file" @(
    "-X", "DELETE", "http://localhost:8080/api/documents/$($fallbackAnalysis.fileId)"
)
$fallbackStillExists = @($deleteState.steps.step7.analyses | Where-Object { $_.fileId -eq $fallbackAnalysis.fileId }).Count -gt 0
Assert-True (-not $fallbackStillExists) "delete document endpoint did not remove fallback analysis"

$reportRequest = @{ analyses = $analyses } | ConvertTo-Json -Depth 20
$reportRequestPath = Join-Path $test "bulk-report-request.json"
Set-Content -Path $reportRequestPath -Value $reportRequest -Encoding UTF8
$report = Invoke-CurlJson "bulk-report" @(
    "-X", "POST", "http://localhost:8080/api/reports/generate",
    "-H", "Content-Type: application/json",
    "--data-binary", "@$reportRequestPath"
)

$draftMarkdown = $report.markdown + "`n`nSELFTEST_ONLINE_DRAFT_EXPORT"
$exportPayload = @{ analyses = $analyses; markdown = $draftMarkdown } | ConvertTo-Json -Depth 20
$exportPayloadPath = Join-Path $test "bulk-export-request.json"
Set-Content -Path $exportPayloadPath -Value $exportPayload -Encoding UTF8

$exportMdPath = Join-Path $test "bulk-export.md"
$exportMdStatus = & curl.exe -s -o $exportMdPath -w '%{http_code}' -X POST "http://localhost:8080/api/reports/export/md" -H "Content-Type: application/json" --data-binary "@$exportPayloadPath"
Assert-True ($exportMdStatus -match '^2') "export-md failed HTTP $exportMdStatus"

$exportDocxPath = Join-Path $test "bulk-export.docx"
$exportDocxStatus = & curl.exe -s -o $exportDocxPath -w '%{http_code}' -X POST "http://localhost:8080/api/reports/export/docx" -H "Content-Type: application/json" --data-binary "@$exportPayloadPath"
Assert-True ($exportDocxStatus -match '^2') "export-docx failed HTTP $exportDocxStatus"

$stepCounts = @{}
foreach ($analysis in $analyses) {
    $stepCounts[$analysis.targetStep] = 1 + [int]($stepCounts[$analysis.targetStep])
}

$fieldOk = @(($stateAfterField.steps.step1.fields) | Where-Object { $_.key -eq "projectName" -and $_.value -like "*selftest*" }).Count -gt 0
$exportMdText = Get-Content $exportMdPath -Raw -Encoding UTF8
$summary = [pscustomobject]@{
    healthOk = $true
    projectOk = ($project.steps.Count -eq 8)
    ollamaStatusOk = ($null -ne $ollama.defaultModel)
    policyUploadOk = ($policyUpload.fileName -eq "fake-policy-card.txt")
    analyzedFiles = $analyses.Count
    routedSteps = $stepCounts
    fallbackStepOk = ($fallbackAnalysis.targetStep -eq "step7")
    firstFileId = $firstFileId
    sourceEndpointOk = ($sourceStatus -eq "200")
    deleteEndpointOk = (-not $fallbackStillExists)
    situationSaved = ($stateAfterSituation.steps.step1.situations.approvalSituation -eq "3")
    fieldSaved = $fieldOk
    reportTitle = $report.title
    reportMarkdownLength = $report.markdown.Length
    exportMdOk = ($exportMdText -like "*SELFTEST_ONLINE_DRAFT_EXPORT*")
    exportDocxOk = ((Get-Item $exportDocxPath).Length -gt 1000)
    exportDocxBytes = (Get-Item $exportDocxPath).Length
}

$summary | ConvertTo-Json -Depth 10 | Set-Content -Path $summaryPath -Encoding UTF8
$summary | ConvertTo-Json -Depth 10
