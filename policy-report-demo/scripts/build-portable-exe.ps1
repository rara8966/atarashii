param(
    [string]$OutputZip
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$WorkspaceRoot = Split-Path -Parent $ProjectRoot
$FrontendDir = Join-Path $ProjectRoot "frontend"
$BackendDir = Join-Path $ProjectRoot "backend"
$BuildRoot = Join-Path $WorkspaceRoot ".copilot-temp\policy-report-demo-exe"
$BackendStage = Join-Path $BuildRoot "backend-stage"
$InputDir = Join-Path $BuildRoot "jpackage-input"
$ImageDest = Join-Path $BuildRoot "image"
$PackageRoot = Join-Path $BuildRoot "package"

if (-not $OutputZip) {
    $OutputZip = Join-Path $WorkspaceRoot "policy-report-demo-client-exe-deepseek.zip"
}

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Get-RequiredCommand([string]$Name) {
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $cmd) { throw "$Name was not found in PATH." }
    return $cmd.Source
}

function Invoke-Tool([string]$WorkingDirectory, [string]$Exe, [string[]]$Arguments) {
    Push-Location $WorkingDirectory
    try {
        & $Exe @Arguments
        if ($LASTEXITCODE -ne 0) { throw "$Exe failed with exit code $LASTEXITCODE" }
    }
    finally {
        Pop-Location
    }
}

function Copy-TreeClean([string]$Source, [string]$Destination, [string[]]$ExcludeDirs = @()) {
    Remove-Item -Recurse -Force $Destination -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $Destination | Out-Null
    $excludeArgs = @()
    if ($ExcludeDirs.Count -gt 0) { $excludeArgs = @("/XD") + $ExcludeDirs }
    robocopy $Source $Destination /E @excludeArgs /XF *.log *.tsbuildinfo | Out-Host
    if ($LASTEXITCODE -gt 7) { throw "robocopy failed with exit code $LASTEXITCODE" }
}

$npm = Get-RequiredCommand "npm.cmd"
$mvn = Get-RequiredCommand "mvn.cmd"
$jpackage = Get-RequiredCommand "jpackage.exe"

Write-Host "Policy Report Demo EXE packager" -ForegroundColor White
Write-Host "Project: $ProjectRoot"
Write-Host "Output : $OutputZip"

Remove-Item -Recurse -Force $BuildRoot, $OutputZip -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $BuildRoot, $InputDir, $ImageDest, $PackageRoot | Out-Null

Write-Step "Building frontend"
Invoke-Tool $FrontendDir $npm @("install", "--registry=https://registry.npmmirror.com", "--cache", (Join-Path $BuildRoot "npm-cache"))
Invoke-Tool $FrontendDir $npm @("run", "build")

Write-Step "Preparing backend stage"
Copy-TreeClean $BackendDir $BackendStage @("target", "data")
$staticDir = Join-Path $BackendStage "src\main\resources\static"
New-Item -ItemType Directory -Force $staticDir | Out-Null
Copy-Item -Path (Join-Path $FrontendDir "dist\*") -Destination $staticDir -Recurse -Force

Write-Step "Building Spring Boot jar"
Invoke-Tool $BackendStage $mvn @("test")
Invoke-Tool $BackendStage $mvn @("package", "-DskipTests")
$jar = Get-ChildItem -Path (Join-Path $BackendStage "target") -Filter "*.jar" | Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1
if (-not $jar) { throw "Spring Boot jar was not generated." }
Copy-Item -Path $jar.FullName -Destination (Join-Path $InputDir "policy-report-demo.jar") -Force

Write-Step "Creating Windows app image"
& $jpackage `
    --type app-image `
    --name PolicyReportDemo `
    --app-version 1.0.0 `
    --dest $ImageDest `
    --input $InputDir `
    --main-jar policy-report-demo.jar `
    --java-options "-Dserver.port=8080" `
    --java-options "-Dapp.open-browser=true" `
    --java-options "-Djava.awt.headless=false" `
    --win-console
if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }

Write-Step "Creating client zip"
$appImage = Join-Path $ImageDest "PolicyReportDemo"
if (-not (Test-Path (Join-Path $appImage "PolicyReportDemo.exe"))) { throw "PolicyReportDemo.exe was not generated." }
Copy-Item -Path $appImage -Destination $PackageRoot -Recurse -Force

$launcher = @'
@echo off
cd /d "%~dp0PolicyReportDemo"
start "Policy Report Demo" "%~dp0PolicyReportDemo\PolicyReportDemo.exe"
'@
[System.IO.File]::WriteAllText((Join-Path $PackageRoot "start.bat"), $launcher, [System.Text.Encoding]::ASCII)
$cnLauncherName = [string]::Concat([char]0x542F, [char]0x52A8, ".bat")
[System.IO.File]::WriteAllText((Join-Path $PackageRoot $cnLauncherName), $launcher, [System.Text.Encoding]::ASCII)

$guide = @'
# Policy Report Demo - EXE Edition

This package does not require JDK, Maven, or Node.js on the client machine.
It does not include any local LLM model files.

## Start

Double-click `start.bat`, or open `PolicyReportDemo\PolicyReportDemo.exe` directly.

The app starts a console window and opens the browser automatically.
If the browser does not open, visit:

http://127.0.0.1:8080/

Close the console window to stop the demo.

## DeepSeek

Choose DeepSeek in the AI configuration panel, then enter the client's DeepSeek API Key.
The model name can be:

deepseek-chat

The API Key is stored only in the current browser.

## Notes

This package includes the Java runtime and the demo application only.
It does not include node_modules, Maven repositories, local uploaded data, or local model files.
'@
[System.IO.File]::WriteAllText((Join-Path $PackageRoot "README-EXE.md"), $guide, [System.Text.UTF8Encoding]::new($false))

Compress-Archive -Path (Join-Path $PackageRoot "*") -DestinationPath $OutputZip -Force
$item = Get-Item $OutputZip
[pscustomobject]@{
    Zip = $OutputZip
    Bytes = $item.Length
    MB = [math]::Round($item.Length / 1MB, 2)
    Exe = "PolicyReportDemo\PolicyReportDemo.exe"
} | ConvertTo-Json -Compress