# License admin tool -- issue / list / revoke licenses on the cloud server.
# ASCII-only on purpose (avoids PowerShell 5.1 encoding breakage).
# Run via the .bat next to it, or:
#   powershell -ExecutionPolicy Bypass -File issue-license.ps1

$ErrorActionPreference = 'Stop'
$server = 'https://policy.xieyuchen.xyz'

# Admin token is read from admin-token.txt (kept out of git).
$tokenFile = Join-Path $PSScriptRoot 'admin-token.txt'
if (-not (Test-Path $tokenFile)) {
    Write-Host 'ERROR: admin-token.txt not found next to this script.'
    Read-Host 'Press Enter to exit'
    exit 1
}
$adminToken = (Get-Content $tokenFile -Raw).Trim()
$headers = @{ 'X-Admin-Token' = $adminToken }

Write-Host '========================================'
Write-Host '  License Admin  (policy.xieyuchen.xyz)'
Write-Host '========================================'
Write-Host '  1) Issue new license'
Write-Host '  2) List all licenses'
Write-Host '  3) Revoke a license'
Write-Host ''
$choice = Read-Host 'Choose 1 / 2 / 3'

try {
    if ($choice -eq '1') {
        $name = Read-Host 'Customer name'
        $days = Read-Host 'Valid days (e.g. 365)'
        $bodyJson = @{ customerName = $name; validDays = [int]$days } | ConvertTo-Json -Compress
        $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($bodyJson)
        $r = Invoke-RestMethod -Uri "$server/api/admin/licenses" -Method Post `
            -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bodyBytes
        Write-Host ''
        Write-Host '----- New license -----'
        Write-Host ('  License key : ' + $r.licenseKey)
        Write-Host ('  Customer    : ' + $r.customerName)
        Write-Host ('  Expires     : ' + $r.expiresAt)
        Write-Host ''
        Write-Host 'Put this License key into the customer license.key file.'
    }
    elseif ($choice -eq '2') {
        $list = Invoke-RestMethod -Uri "$server/api/admin/licenses" -Method Get -Headers $headers
        $list | Format-Table licenseKey, customerName, status, bound, expiresAt -AutoSize
    }
    elseif ($choice -eq '3') {
        $key = Read-Host 'License key to revoke'
        $r = Invoke-RestMethod -Uri "$server/api/admin/licenses/$key/revoke" -Method Post -Headers $headers
        Write-Host ('Result: ' + $r.message)
    }
    else {
        Write-Host 'Unknown choice.'
    }
}
catch {
    Write-Host ('ERROR: ' + $_.Exception.Message)
}

Write-Host ''
Read-Host 'Press Enter to exit'
