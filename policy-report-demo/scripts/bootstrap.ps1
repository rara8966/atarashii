param(
    [ValidateSet("all", "backend", "frontend", "check")]
    [string]$Mode = "all",
    [switch]$SkipBuild,
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$WorkspaceRoot = Split-Path -Parent $ProjectRoot
$RuntimeRoot = Join-Path $ProjectRoot ".runtime"
$CacheRoot = Join-Path $RuntimeRoot "cache"
$LogRoot = Join-Path $RuntimeRoot "logs"
$MavenRepo = Join-Path $RuntimeRoot "m2"
$MavenSettings = Join-Path $RuntimeRoot "maven-settings.xml"
$BackendDir = Join-Path $ProjectRoot "backend"
$FrontendDir = Join-Path $ProjectRoot "frontend"

New-Item -ItemType Directory -Force $RuntimeRoot, $CacheRoot, $LogRoot, $MavenRepo | Out-Null

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok([string]$Message) {
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-WarnLine([string]$Message) {
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Get-CommandPath([string[]]$Names) {
    foreach ($name in $Names) {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd) { return $cmd.Source }
    }
    return $null
}

function Get-WebText([string]$Url) {
    $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 20 -Headers @{ "User-Agent" = "PolicyReportDemoLauncher/1.0" }
    return $response.Content
}

function Test-ZipFile([string]$ZipFile) {
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
        $stream = [System.IO.File]::OpenRead($ZipFile)
        try {
            $archive = New-Object System.IO.Compression.ZipArchive($stream, [System.IO.Compression.ZipArchiveMode]::Read)
            try { return $archive.Entries.Count -gt 0 }
            finally { $archive.Dispose() }
        }
        finally { $stream.Dispose() }
    }
    catch { return $false }
}

function Download-File([string[]]$Urls, [string]$OutFile) {
    if ((Test-Path $OutFile) -and (Get-Item $OutFile).Length -gt 1024) {
        if ($OutFile.EndsWith(".zip", [System.StringComparison]::OrdinalIgnoreCase) -and -not (Test-ZipFile $OutFile)) {
            Write-WarnLine "Cached download is not a valid zip and will be removed: $OutFile"
            Remove-Item -Force $OutFile -ErrorAction SilentlyContinue
        } else {
            Write-Ok "Using cached download: $OutFile"
            return
        }
    }
    foreach ($url in $Urls | Where-Object { $_ }) {
        try {
            Write-Host "Downloading: $url"
            Remove-Item -Force $OutFile -ErrorAction SilentlyContinue
            Invoke-WebRequest -Uri $url -OutFile $OutFile -UseBasicParsing -TimeoutSec 900 -Headers @{ "User-Agent" = "PolicyReportDemoLauncher/1.0" }
            if ((Test-Path $OutFile) -and (Get-Item $OutFile).Length -gt 1024) {
                if ($OutFile.EndsWith(".zip", [System.StringComparison]::OrdinalIgnoreCase) -and -not (Test-ZipFile $OutFile)) {
                    Write-WarnLine "Downloaded file is not a valid zip and will be ignored: $url"
                    Remove-Item -Force $OutFile -ErrorAction SilentlyContinue
                    continue
                }
                return
            }
            Write-WarnLine "Downloaded file is too small and will be ignored: $url"
            Remove-Item -Force $OutFile -ErrorAction SilentlyContinue
        }
        catch {
            Write-WarnLine "Download failed: $url"
            Write-WarnLine $_.Exception.Message
            Remove-Item -Force $OutFile -ErrorAction SilentlyContinue
        }
    }
    throw "All download mirrors failed for $OutFile"
}

function Expand-ZipClean([string]$ZipFile, [string]$Destination) {
    Remove-Item -Recurse -Force $Destination -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force $Destination | Out-Null
    Expand-Archive -Path $ZipFile -DestinationPath $Destination -Force
}

function Get-JavaMajor([string]$JavaExe) {
    try {
        $text = (& cmd.exe /c "`"$JavaExe`" -version 2>&1") -join "`n"
        if ($text -match 'version "(\d+)\.(\d+)') {
            if ($Matches[1] -eq "1") { return [int]$Matches[2] }
            return [int]$Matches[1]
        }
        if ($text -match 'version "(\d+)') { return [int]$Matches[1] }
    }
    catch { return 0 }
    return 0
}

function Get-NodeMajor([string]$NodeExe) {
    try {
        $text = & $NodeExe -v
        if ($text -match 'v(\d+)') { return [int]$Matches[1] }
    }
    catch { return 0 }
    return 0
}

function Get-JdkUrls {
    $urls = New-Object System.Collections.Generic.List[string]
    $bases = @(
        "https://mirrors.huaweicloud.com/adoptium/17/jdk/x64/windows/",
        "https://mirrors.huaweicloud.com/Adoptium/17/jdk/x64/windows/",
        "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jdk/x64/windows/"
    )
    $knownFiles = @(
        "OpenJDK17U-jdk_x64_windows_hotspot_17.0.19_10.zip",
        "OpenJDK17U-jdk_x64_windows_hotspot_17.0.15_6.zip",
        "OpenJDK17U-jdk_x64_windows_hotspot_17.0.14_7.zip",
        "OpenJDK17U-jdk_x64_windows_hotspot_17.0.13_11.zip"
    )
    foreach ($base in $bases) {
        try {
            $html = Get-WebText $base
            $names = [regex]::Matches($html, 'href="([^"/]*OpenJDK17U-jdk_x64_windows_hotspot_[^"/]*\.zip)"') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Descending
            if ($names) { $urls.Add($base + ($names | Select-Object -First 1)) }
        }
        catch {
            Write-WarnLine "Could not query Adoptium index: $base"
        }
        foreach ($file in $knownFiles) { $urls.Add($base + $file) }
    }
    $urls.Add("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.19%2B10/OpenJDK17U-jdk_x64_windows_hotspot_17.0.19_10.zip")
    return $urls | Select-Object -Unique
}

function Ensure-Jdk {
    Write-Step "Checking JDK 17+"
    $runtimeJava = Get-ChildItem -Path (Join-Path $RuntimeRoot "jdk") -Recurse -Filter java.exe -ErrorAction SilentlyContinue | Where-Object { $_.FullName -match "\\bin\\java\.exe$" } | Select-Object -First 1
    if ($runtimeJava) {
        $major = Get-JavaMajor $runtimeJava.FullName
        if ($major -ge 17) {
            $javaHome = Split-Path -Parent (Split-Path -Parent $runtimeJava.FullName)
            Write-Ok "Using bundled JDK ${major}: $javaHome"
            return @{ JavaHome = $javaHome; JavaExe = $runtimeJava.FullName }
        }
    }

    $systemJava = $null
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        $systemJava = Join-Path $env:JAVA_HOME "bin\java.exe"
    }
    if (-not $systemJava) { $systemJava = Get-CommandPath @("java.exe") }
    if ($systemJava) {
        $major = Get-JavaMajor $systemJava
        if ($major -ge 17) {
            $javaHome = Split-Path -Parent (Split-Path -Parent $systemJava)
            Write-Ok "Using system JDK ${major}: $javaHome"
            return @{ JavaHome = $javaHome; JavaExe = $systemJava }
        }
    }

    Write-WarnLine "JDK 17+ was not found. Downloading portable JDK from China mirror."
    $zip = Join-Path $CacheRoot "jdk17.zip"
    Download-File (Get-JdkUrls) $zip
    $dest = Join-Path $RuntimeRoot "jdk"
    Expand-ZipClean $zip $dest
    $java = Get-ChildItem -Path $dest -Recurse -Filter java.exe | Where-Object { $_.FullName -match "\\bin\\java\.exe$" } | Select-Object -First 1
    if (-not $java) { throw "Portable JDK was downloaded but java.exe was not found." }
    $javaHome = Split-Path -Parent (Split-Path -Parent $java.FullName)
    Write-Ok "Bundled JDK ready: $javaHome"
    return @{ JavaHome = $javaHome; JavaExe = $java.FullName }
}

function Ensure-Maven {
    Write-Step "Checking Maven 3.9+"
    $runtimeMvn = Get-ChildItem -Path (Join-Path $RuntimeRoot "maven") -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($runtimeMvn) {
        Write-Ok "Using bundled Maven: $($runtimeMvn.FullName)"
        return $runtimeMvn.FullName
    }

    $systemMvn = Get-CommandPath @("mvn.cmd", "mvn.exe", "mvn")
    if ($systemMvn) {
        Write-Ok "Using system Maven: $systemMvn"
        return $systemMvn
    }

    Write-WarnLine "Maven was not found. Downloading portable Maven from China mirror."
    $version = "3.9.11"
    $zip = Join-Path $CacheRoot "apache-maven-$version-bin.zip"
    $urls = @(
        "https://mirrors.aliyun.com/apache/maven/maven-3/$version/binaries/apache-maven-$version-bin.zip",
        "https://mirrors.tuna.tsinghua.edu.cn/apache/maven/maven-3/$version/binaries/apache-maven-$version-bin.zip"
    )
    Download-File $urls $zip
    $dest = Join-Path $RuntimeRoot "maven"
    Expand-ZipClean $zip $dest
    $mvn = Get-ChildItem -Path $dest -Recurse -Filter mvn.cmd | Select-Object -First 1
    if (-not $mvn) { throw "Portable Maven was downloaded but mvn.cmd was not found." }
    Write-Ok "Bundled Maven ready: $($mvn.FullName)"
    return $mvn.FullName
}

function Ensure-Node {
    Write-Step "Checking Node.js 18/20"
    $runtimeNode = Get-ChildItem -Path (Join-Path $RuntimeRoot "node") -Recurse -Filter node.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($runtimeNode) {
        $major = Get-NodeMajor $runtimeNode.FullName
        if ($major -ge 18 -and $major -le 20) {
            Write-Ok "Using bundled Node.js ${major}: $($runtimeNode.FullName)"
            return $runtimeNode.FullName
        }
    }

    $systemNode = Get-CommandPath @("node.exe")
    if ($systemNode) {
        $major = Get-NodeMajor $systemNode
        if ($major -ge 18 -and $major -le 20) {
            Write-Ok "Using system Node.js ${major}: $systemNode"
            return $systemNode
        }
        Write-WarnLine "System Node.js major $major is not used; this demo uses Node 18/20 for stable Vite build."
    }

    Write-WarnLine "Node.js 18/20 was not found. Downloading portable Node.js from npmmirror."
    $version = "18.20.8"
    $zip = Join-Path $CacheRoot "node-v$version-win-x64.zip"
    $urls = @(
        "https://npmmirror.com/mirrors/node/v$version/node-v$version-win-x64.zip",
        "https://registry.npmmirror.com/-/binary/node/v$version/node-v$version-win-x64.zip"
    )
    Download-File $urls $zip
    $dest = Join-Path $RuntimeRoot "node"
    Expand-ZipClean $zip $dest
    $node = Get-ChildItem -Path $dest -Recurse -Filter node.exe | Select-Object -First 1
    if (-not $node) { throw "Portable Node.js was downloaded but node.exe was not found." }
    Write-Ok "Bundled Node.js ready: $($node.FullName)"
    return $node.FullName
}

function Write-MavenSettings {
    $xml = @'
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun-public</id>
      <mirrorOf>*</mirrorOf>
      <name>Aliyun Maven Public</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
'@
    Set-Content -Path $MavenSettings -Value $xml -Encoding UTF8
}

function Invoke-LoggedCommand([string]$Name, [string]$WorkingDirectory, [string]$Exe, [string[]]$Arguments, [hashtable]$ExtraEnv = @{}) {
    Write-Step $Name
    Push-Location $WorkingDirectory
    $oldPath = $env:PATH
    $oldJavaHome = $env:JAVA_HOME
    try {
        foreach ($key in $ExtraEnv.Keys) { Set-Item -Path "env:$key" -Value $ExtraEnv[$key] }
        & $Exe @Arguments
        if ($LASTEXITCODE -ne 0) { throw "$Name failed with exit code $LASTEXITCODE" }
    }
    finally {
        $env:PATH = $oldPath
        if ($null -eq $oldJavaHome) { Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue } else { $env:JAVA_HOME = $oldJavaHome }
        Pop-Location
    }
    Write-Ok $Name
}

function Stop-Port([int]$Port) {
    $ids = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($processId in $ids) {
        try {
            Stop-Process -Id $processId -Force -ErrorAction Stop
            Write-WarnLine "Stopped existing process on port ${Port}: PID $processId"
        }
        catch {
            Write-WarnLine "Could not stop PID $processId on port $Port"
        }
    }
}

function Wait-Http([string]$Url, [string]$Contains = "", [int]$Seconds = 90) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                if (-not $Contains -or ($response.Content -like "*$Contains*")) { return $true }
            }
        }
        catch { Start-Sleep -Seconds 2 }
    } while ((Get-Date) -lt $deadline)
    throw "Timeout waiting for $Url. Logs: $LogRoot"
}

function New-LauncherScripts([string]$JavaHome, [string]$MavenExe, [string]$NodeExe) {
    $toolPaths = @()
    if ($JavaHome) { $toolPaths += (Join-Path $JavaHome "bin") }
    if ($MavenExe) { $toolPaths += (Split-Path -Parent $MavenExe) }
    if ($NodeExe) { $toolPaths += (Split-Path -Parent $NodeExe) }
    $toolPath = $toolPaths -join ";"
    $backendLauncher = Join-Path $RuntimeRoot "run-backend.ps1"
    $frontendLauncher = Join-Path $RuntimeRoot "run-frontend.ps1"
    $npmExe = if ($NodeExe) { Join-Path (Split-Path -Parent $NodeExe) "npm.cmd" } else { "npm.cmd" }
    $backendLog = Join-Path $LogRoot "backend.log"
    $frontendLog = Join-Path $LogRoot "frontend.log"

    $backendText = @"
`$ErrorActionPreference = "Stop"
Start-Transcript -Path '$backendLog' -Append
`$env:JAVA_HOME = '$JavaHome'
`$env:PATH = '$toolPath;' + `$env:PATH
Set-Location '$BackendDir'
try {
    & '$MavenExe' -s '$MavenSettings' '-Dmaven.repo.local=$MavenRepo' spring-boot:run
}
finally {
    Stop-Transcript
}
"@
    $frontendText = @"
`$ErrorActionPreference = "Stop"
Start-Transcript -Path '$frontendLog' -Append
`$env:PATH = '$toolPath;' + `$env:PATH
Set-Location '$FrontendDir'
try {
    & '$npmExe' run dev -- --host 127.0.0.1
}
finally {
    Stop-Transcript
}
"@
    Set-Content -Path $backendLauncher -Value $backendText -Encoding UTF8
    Set-Content -Path $frontendLauncher -Value $frontendText -Encoding UTF8
    return @{ Backend = $backendLauncher; Frontend = $frontendLauncher }
}

function Start-DemoProcess([string]$Title, [string]$ScriptPath) {
    Start-Process powershell.exe -ArgumentList @("-NoExit", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $ScriptPath) -WorkingDirectory $ProjectRoot -WindowStyle Normal | Out-Null
    Write-Ok "Started $Title"
}

function Invoke-BasicSelfTest([bool]$NeedBackend, [bool]$NeedFrontend) {
    Write-Step "Running startup self-test"
    if ($NeedBackend) {
        Wait-Http "http://localhost:8080/api/health" "ok" 120 | Out-Null
        $body = '{"analyses":[]}'
        $report = Invoke-RestMethod -Uri "http://localhost:8080/api/reports/generate" -Method Post -ContentType "application/json" -Body $body
        if (-not $report.markdown -or $report.markdown.Length -lt 100) { throw "Report endpoint returned an invalid response." }
        Write-Ok "Backend health and report endpoint passed"
    }
    if ($NeedFrontend) {
        Wait-Http "http://127.0.0.1:5173/" 'id="root"' 120 | Out-Null
        Write-Ok "Frontend page passed"
    }
}

function Open-BrowserOnce {
    if (-not $NoBrowser -and ($Mode -eq "all" -or $Mode -eq "frontend")) {
        Start-Process "http://127.0.0.1:5173/" | Out-Null
    }
}

Write-Host "Policy Report Demo portable launcher" -ForegroundColor White
Write-Host "Mode: $Mode"
Write-Host "Project: $ProjectRoot"

$needBackend = $Mode -eq "all" -or $Mode -eq "backend" -or $Mode -eq "check"
$needFrontend = $Mode -eq "all" -or $Mode -eq "frontend" -or $Mode -eq "check"

$jdk = if ($needBackend) { Ensure-Jdk } else { $null }
$mavenExe = if ($needBackend) { Ensure-Maven } else { $null }
$nodeExe = if ($needFrontend) { Ensure-Node } else { $null }
Write-MavenSettings

$pathParts = @()
if ($needBackend) { $pathParts += (Join-Path $jdk.JavaHome "bin"); $pathParts += (Split-Path -Parent $mavenExe) }
if ($needFrontend) { $pathParts += (Split-Path -Parent $nodeExe) }
$toolEnv = @{}
$toolEnv["PATH"] = (($pathParts -join ";") + ";" + $env:PATH)
if ($needBackend) { $toolEnv["JAVA_HOME"] = $jdk.JavaHome }

if (-not $SkipBuild) {
    if ($needBackend) {
        Invoke-LoggedCommand "Backend dependency check and tests" $BackendDir $mavenExe @("-s", $MavenSettings, "-Dmaven.repo.local=$MavenRepo", "test") $toolEnv
    }
    if ($needFrontend) {
        $npm = Join-Path (Split-Path -Parent $nodeExe) "npm.cmd"
        Invoke-LoggedCommand "Frontend dependency install" $FrontendDir $npm @("install", "--registry=https://registry.npmmirror.com", "--cache", (Join-Path $RuntimeRoot "npm-cache")) $toolEnv
        Invoke-LoggedCommand "Frontend build check" $FrontendDir $npm @("run", "build") $toolEnv
    }
}

if ($Mode -eq "check") {
    Write-Ok "Check mode completed. Nothing was launched."
    exit 0
}

$launcherJavaHome = if ($needBackend) { $jdk.JavaHome } else { "" }
$launcherMavenExe = if ($needBackend) { $mavenExe } else { "" }
$launcherNodeExe = if ($needFrontend) { $nodeExe } else { "" }
$launchers = New-LauncherScripts $launcherJavaHome $launcherMavenExe $launcherNodeExe

if ($Mode -eq "all" -or $Mode -eq "backend") { Stop-Port 8080; Start-DemoProcess "backend" $launchers.Backend }
if ($Mode -eq "all" -or $Mode -eq "frontend") { Stop-Port 5173; Start-DemoProcess "frontend" $launchers.Frontend }

Invoke-BasicSelfTest ($Mode -eq "all" -or $Mode -eq "backend") ($Mode -eq "all" -or $Mode -eq "frontend")
Open-BrowserOnce

Write-Host ""
Write-Ok "Demo is ready."
if ($Mode -eq "all" -or $Mode -eq "frontend") { Write-Host "Frontend: http://127.0.0.1:5173/" }
if ($Mode -eq "all" -or $Mode -eq "backend") { Write-Host "Backend : http://localhost:8080/" }