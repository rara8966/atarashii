# 演示脚本：创建一个 wind-power 项目，注入 15 条模拟字段（覆盖 6 张已标注的标准表），
# 然后调用功能区合规分析接口，把结果在终端打印出来。
#
# 前提：后端已重启（包含 POST /_debug/inject-mock-fields 接口）
# 用法：.\scripts\demo-functional-zone-verdict.ps1

param(
    [string]$ApiBase = 'http://127.0.0.1:8080',
    [string]$ProjectName = '功能区合规演示项目'
)

$ErrorActionPreference = 'Stop'

# 1. 检测后端
try {
    $types = Invoke-RestMethod -Uri "$ApiBase/api/standards/types" -Method GET -TimeoutSec 5
} catch {
    Write-Host "backend not reachable: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "请先重启后端：cd backend && mvn spring-boot:run" -ForegroundColor Yellow
    exit 1
}

# 2. 创建 wind-power 项目
Write-Host "Creating wind-power project '$ProjectName'..." -ForegroundColor Cyan
$createBody = [System.Text.Encoding]::UTF8.GetBytes((@{
    projectName = $ProjectName
    projectType = 'wind-power'
    owner       = '演示业主'
    location    = '广西兴宁'
} | ConvertTo-Json))

try {
    $project = Invoke-RestMethod -Uri "$ApiBase/api/v2/projects" -Method POST `
        -Body $createBody -ContentType 'application/json; charset=utf-8' -TimeoutSec 15
    Write-Host "  created: $($project.id)  [$($project.projectCode)]" -ForegroundColor Green
} catch {
    Write-Host "create failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
$projectId = $project.id

# 3. 设 projectType 为 wind-power（确保 standardSet 正确）
$confirmBody = [System.Text.Encoding]::UTF8.GetBytes((@{ projectType = 'wind-power' } | ConvertTo-Json))
try {
    Invoke-RestMethod -Uri "$ApiBase/api/v2/projects/$projectId/confirm-type" -Method PUT `
        -Body $confirmBody -ContentType 'application/json; charset=utf-8' -TimeoutSec 10 | Out-Null
} catch { Write-Host "  warn: confirm-type failed: $($_.Exception.Message)" -ForegroundColor Yellow }

# 4. 注入 15 条字段（按功能区覆盖 6 张表）
$fields = @(
    # ── 风电机组（表3.1.2: 单机容量+风机基础型式 → 用地指标 m²/台）──
    @{ label='单机容量';    value='2000';     functionalZone='风电机组' }
    @{ label='风机基础型式'; value='扩展基础';  functionalZone='风电机组' }
    @{ label='用地指标';    value='810';      functionalZone='风电机组' }     # 标准 ≤ 800 → 超标 ~1.3%

    # ── 机组变电站（表3.2.2: 单机容量 → 用地指标 m²/台）──
    @{ label='单机容量';    value='1500';     functionalZone='机组变电站' }
    @{ label='用地指标';    value='32';       functionalZone='机组变电站' }   # 1250~2000 标准 ≤30 → 超标 ~6.7%

    # ── 集电线路-电缆（表4.1.2: 电压等级+敷设方式 → 用地指标 hm²/km）──
    @{ label='电压等级';    value='35';       functionalZone='集电线路' }
    @{ label='敷设方式';    value='直埋';     functionalZone='集电线路' }
    @{ label='用地指标';    value='0.09';     functionalZone='集电线路' }     # 标准 ≤0.10 → 通过

    # ── 集电线路-架空（表4.2.2: 同上但用 回路数 而非 敷设方式）──
    # 注意：同 zone 有 2 张表都叫"用地指标"，VerdictEngine 会把项目"用地指标"
    # 同时拿去比对两张表（属于已知模糊匹配局限），观察输出
    @{ label='回路数';      value='双回';     functionalZone='集电线路' }

    # ── 升压变电站及运行管理中心（表5.0.2: 电压等级+主变压器容量 → 用地指标 hm²）──
    @{ label='电压等级';    value='35';       functionalZone='升压变电站及运行管理中心' }
    @{ label='主变压器容量'; value='2×20';     functionalZone='升压变电站及运行管理中心' }
    @{ label='用地指标';    value='0.55';     functionalZone='升压变电站及运行管理中心' }  # 标准 ≤0.60 → 通过

    # ── 交通工程（表6.0.2: 道路类型+路面宽度 → 用地指标 hm²/km）──
    @{ label='道路类型';    value='进场道路';  functionalZone='交通工程' }
    @{ label='路面宽度';    value='6.0';      functionalZone='交通工程' }
    @{ label='用地指标';    value='0.88';     functionalZone='交通工程' }     # 标准 ≤0.80 → 超标 10%
)

$injectBody = [System.Text.Encoding]::UTF8.GetBytes((@{
    fileName = '模拟项目说明.txt'
    fields   = $fields
} | ConvertTo-Json -Depth 5))

Write-Host "Injecting $($fields.Count) mock fields..." -ForegroundColor Cyan
try {
    $injectResp = Invoke-RestMethod -Uri "$ApiBase/api/v2/projects/$projectId/_debug/inject-mock-fields" `
        -Method POST -Body $injectBody -ContentType 'application/json; charset=utf-8' -TimeoutSec 20
    if ($injectResp.error) {
        Write-Host "  inject error: $($injectResp.error)" -ForegroundColor Red
        Write-Host "  （需要后端已重启加载新接口）" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "  injected fileId=$($injectResp.fileId), fields=$($injectResp.fieldCount)" -ForegroundColor Green
} catch {
    Write-Host "inject failed: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        $r = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host $r.ReadToEnd() -ForegroundColor Yellow
    }
    exit 1
}

# 5. 调功能区合规分析
Write-Host "`nFetching functional-zone-verdict..." -ForegroundColor Cyan
try {
    $verdict = Invoke-RestMethod -Uri "$ApiBase/api/v2/projects/$projectId/functional-zone-verdict" `
        -Method GET -TimeoutSec 30
} catch {
    Write-Host "verdict failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# 6. 打印结果
Write-Host "`n============ 功能区合规分析 ============`n" -ForegroundColor White

$totalPass = 0; $totalFail = 0; $totalWarn = 0; $totalUnknown = 0
foreach ($zone in $verdict) {
    Write-Host "▣ 功能区：$($zone.functionalZone)" -ForegroundColor Cyan
    Write-Host "  命中表：$($zone.matchedTables)/$($zone.totalAnnotatedTables) · 项目相关字段：$($zone.projectFieldCount)" -ForegroundColor Gray
    if ($zone.tables.Count -eq 0) {
        Write-Host "  (无可比对的表)" -ForegroundColor DarkGray
        continue
    }
    foreach ($tbl in $zone.tables) {
        Write-Host "`n  ┌─ $($tbl.tableCode) $($tbl.tableTitle)" -ForegroundColor White
        if ($tbl.matchedQueryKeys) {
            $keys = ($tbl.matchedQueryKeys.PSObject.Properties | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ', '
            $rowInfo = if ($tbl.matchedRowIndex -ge 0) { "命中第 $($tbl.matchedRowIndex + 1) 行" } else { "未命中行" }
            $color = if ($tbl.matchedRowIndex -ge 0) { 'Gray' } else { 'Yellow' }
            Write-Host "  │ 查询键：$keys  → $rowInfo" -ForegroundColor $color
        }
        foreach ($ind in $tbl.indicators) {
            $col = switch ($ind.verdict) {
                'PASS'    { 'Green' }
                'FAIL'    { 'Red' }
                'WARN'    { 'Yellow' }
                default   { 'DarkGray' }
            }
            $label = switch ($ind.verdict) {
                'PASS'    { '✓ 通过' }
                'FAIL'    { '✗ 不通过' }
                'WARN'    { '⚠ 注意' }
                default   { '? 未核对' }
            }
            $delta = if ($ind.deltaPct -ne $null) { "  ($([math]::Round($ind.deltaPct, 1))%)" } else { '' }
            $line = "  │ {0,-20} 标准:{1,-10} 实际:{2,-10} {3}{4}  {5}" -f $ind.indicatorName, $ind.standardValue, $ind.actualValue, $label, $delta, $ind.note
            Write-Host $line -ForegroundColor $col
            switch ($ind.verdict) {
                'PASS'    { $totalPass++ }
                'FAIL'    { $totalFail++ }
                'WARN'    { $totalWarn++ }
                default   { $totalUnknown++ }
            }
        }
    }
    Write-Host ""
}

Write-Host "============ 汇总 ============" -ForegroundColor White
Write-Host "✓ 通过: $totalPass" -ForegroundColor Green
Write-Host "✗ 不通过: $totalFail" -ForegroundColor Red
Write-Host "⚠ 注意: $totalWarn" -ForegroundColor Yellow
Write-Host "? 未核对: $totalUnknown" -ForegroundColor DarkGray
Write-Host ""
Write-Host "前端查看：访问 $ApiBase/.. 或 http://127.0.0.1:5173/projects/$projectId/workbench" -ForegroundColor Cyan
