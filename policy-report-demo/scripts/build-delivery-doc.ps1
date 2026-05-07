$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$deliveryName = -join ([char[]](20132, 20184, 25991, 26723))
$deliveryDir = Join-Path $root $deliveryName
$mdPath = (Get-ChildItem -Path $deliveryDir -File -Filter "*.md" | Select-Object -First 1).FullName
if (-not $mdPath) {
    throw "Delivery markdown was not found."
}
$docxPath = [System.IO.Path]::ChangeExtension($mdPath, ".docx")
$build = Join-Path $root ".copilot-test\docxbuild"
$utf8 = [System.Text.UTF8Encoding]::new($false)

Remove-Item -Recurse -Force $build -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force (Join-Path $build "_rels"), (Join-Path $build "word") | Out-Null

$contentTypes = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>'
$rels = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>'

[IO.File]::WriteAllText((Join-Path $build "[Content_Types].xml"), $contentTypes, $utf8)
[IO.File]::WriteAllText((Join-Path $build "_rels\.rels"), $rels, $utf8)

$script:document = [System.Text.StringBuilder]::new()
[void]$script:document.Append('<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>')

function Add-DocParagraph {
    param(
        [string]$Text,
        [ValidateSet("title", "heading", "bullet", "normal")]
        [string]$Kind
    )

    $paragraphProperties = '<w:pPr><w:spacing w:after="90"/><w:ind w:firstLine="440"/></w:pPr>'
    $runProperties = '<w:rPr><w:sz w:val="21"/></w:rPr>'
    $textValue = $Text

    if ($Kind -eq "title") {
        $paragraphProperties = '<w:pPr><w:jc w:val="center"/><w:spacing w:after="220"/></w:pPr>'
        $runProperties = '<w:rPr><w:b/><w:sz w:val="34"/></w:rPr>'
    }
    elseif ($Kind -eq "heading") {
        $paragraphProperties = '<w:pPr><w:spacing w:before="180" w:after="100"/></w:pPr>'
        $runProperties = '<w:rPr><w:b/><w:sz w:val="26"/></w:rPr>'
    }
    elseif ($Kind -eq "bullet") {
        $paragraphProperties = '<w:pPr><w:spacing w:after="60"/><w:ind w:left="420" w:hanging="220"/></w:pPr>'
        $textValue = "• $Text"
    }

    $escaped = [System.Security.SecurityElement]::Escape($textValue)
    [void]$script:document.Append("<w:p>$paragraphProperties<w:r>$runProperties<w:t xml:space=`"preserve`">$escaped</w:t></w:r></w:p>")
}

Get-Content $mdPath -Encoding UTF8 | ForEach-Object {
    $line = $_
    if ([string]::IsNullOrWhiteSpace($line)) { return }
    if ($line.StartsWith("# ")) { Add-DocParagraph $line.Substring(2) "title" }
    elseif ($line.StartsWith("## ")) { Add-DocParagraph $line.Substring(3) "heading" }
    elseif ($line.StartsWith("- ")) { Add-DocParagraph $line.Substring(2) "bullet" }
    else { Add-DocParagraph $line "normal" }
}

[void]$script:document.Append('<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>')
[IO.File]::WriteAllText((Join-Path $build "word\document.xml"), $script:document.ToString(), $utf8)

Remove-Item -Force $docxPath -ErrorAction SilentlyContinue
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($docxPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $build "[Content_Types].xml"), "[Content_Types].xml") | Out-Null
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $build "_rels\.rels"), "_rels/.rels") | Out-Null
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $build "word\document.xml"), "word/document.xml") | Out-Null
}
finally {
    $zip.Dispose()
}

$item = Get-Item $docxPath
[pscustomobject]@{
    Docx = $item.FullName
    Bytes = $item.Length
} | ConvertTo-Json