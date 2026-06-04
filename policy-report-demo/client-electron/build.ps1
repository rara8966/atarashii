# Client packaging build script (ASCII-only to avoid PowerShell 5.1 encoding issues).
# Assembles bundled JRE + client jar + Electron, produces a Windows installer.
#
# Prerequisites: Node.js (npm), JDK 17; client jar already built via:
#   mvn -P client -DskipTests clean package
# Usage (run inside client-electron dir):
#   powershell -ExecutionPolicy Bypass -File build.ps1

$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot

# === Edit this to your JDK 17 path ===
$jdk = 'C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot'

$clientJar = Join-Path $here '..\backend\target\policy-report-demo-0.0.1-SNAPSHOT.jar'
$runtime   = Join-Path $here 'runtime'

# 1. Validate prerequisites
if (-not (Test-Path $jdk)) {
    throw "JDK not found: $jdk -- edit `$jdk in this script to your JDK 17 path"
}
if (-not (Test-Path $clientJar)) {
    throw "Client jar not found. Build it first: mvn -P client -DskipTests clean package"
}

# 2. Recreate runtime dir
if (Test-Path $runtime) { Remove-Item $runtime -Recurse -Force }
New-Item -ItemType Directory -Path $runtime | Out-Null

# 3. Copy client jar
Copy-Item $clientJar (Join-Path $runtime 'policy-report-client.jar')
Write-Host '[1/3] client jar staged'

# 4. jlink a bundled JRE (all modules, so Spring Boot reflection/scan never misses one)
& "$jdk\bin\jlink.exe" `
    --module-path "$jdk\jmods" `
    --add-modules ALL-MODULE-PATH `
    --output (Join-Path $runtime 'jre') `
    --strip-debug --no-header-files --no-man-pages --compress=2
if ($LASTEXITCODE -ne 0) { throw 'jlink failed' }
Write-Host '[2/3] bundled JRE created'

# 5. Install deps and package
Push-Location $here
try {
    npm install
    if ($LASTEXITCODE -ne 0) { throw 'npm install failed' }
    npm run dist
    if ($LASTEXITCODE -ne 0) { throw 'electron-builder failed' }
} finally {
    Pop-Location
}

Write-Host ''
Write-Host '[3/3] Done. Installer is in client-electron\dist\'
Write-Host 'Before shipping: put the customer license code into license.key in the installed program dir.'
