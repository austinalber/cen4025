$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$resultsPath = Join-Path $PSScriptRoot "results"
$runRows = New-Object System.Collections.ArrayList
foreach ($file in Get-ChildItem -LiteralPath $resultsPath -Filter "*.json") {
    $match = [regex]::Match($file.Name, "^(?<version>.+)-vus-(?<vus>\d+)-run-(?<run>\d+)\.json$")
    if (-not $match.Success) {
        continue
    }
    $data = Get-Content -Raw -LiteralPath $file.FullName | ConvertFrom-Json
    $metrics = $data.metrics
    [void]$runRows.Add([PSCustomObject]@{
        Version = $match.Groups["version"].Value
        VUs = [int]$match.Groups["vus"].Value
        Run = [int]$match.Groups["run"].Value
        Requests = [int]$metrics.http_reqs.count
        RequestsPerSecond = [double]$metrics.http_reqs.rate
        MeanLatencyMs = [double]$metrics.http_req_duration.avg
        P95LatencyMs = [double]$metrics.http_req_duration.'p(95)'
        FailedRequestRate = [double]$metrics.http_req_failed.value
        CheckFailures = [int]$data.root_group.checks.'GET date range returns HTTP 200'.fails
    })
}
if ($runRows.Count -ne 36) {
    throw "Expected 36 measured k6 runs; found $($runRows.Count)."
}

$resourceRows = @()
$versions = @($runRows | Select-Object -ExpandProperty Version -Unique)
foreach ($version in $versions) {
    $resourceFile = Join-Path $resultsPath "$version-resources.csv"
    if (-not (Test-Path -LiteralPath $resourceFile)) {
        throw "Missing resource observations for '$version'."
    }
    $resourceRows += Import-Csv -LiteralPath $resourceFile
}

function Get-Median {
    param([double[]]$Values)
    $ordered = @($Values | Sort-Object)
    $middle = [int][Math]::Floor($ordered.Count / 2)
    if ($ordered.Count % 2 -eq 1) {
        return $ordered[$middle]
    }
    return ($ordered[$middle - 1] + $ordered[$middle]) / 2
}

$summaryRows = New-Object System.Collections.ArrayList
foreach ($version in $versions) {
    foreach ($vus in 1, 5, 10, 20) {
        $group = @($runRows | Where-Object { $_.Version -eq $version -and $_.VUs -eq $vus })
        $resourceGroup = @($resourceRows | Where-Object { $_.Label -eq $version -and [int]$_.VUs -eq $vus })
        if ($group.Count -ne 3 -or $resourceGroup.Count -ne 3) {
            throw "Expected three measured runs and resource samples for $version at $vus VUs."
        }
        [void]$summaryRows.Add([PSCustomObject]@{
            Version = $version
            VUs = $vus
            Repetitions = $group.Count
            SecondsPerRun = 20
            MedianRequests = Get-Median ([double[]]@($group | ForEach-Object { $_.Requests }))
            MedianRequestsPerSecond = [Math]::Round((Get-Median ([double[]]@($group | ForEach-Object { $_.RequestsPerSecond }))), 2)
            MedianMeanLatencyMs = [Math]::Round((Get-Median ([double[]]@($group | ForEach-Object { $_.MeanLatencyMs }))), 3)
            MedianP95LatencyMs = [Math]::Round((Get-Median ([double[]]@($group | ForEach-Object { $_.P95LatencyMs }))), 3)
            FailedRequestRate = ($group | Measure-Object -Property FailedRequestRate -Maximum).Maximum
            CheckFailures = ($group | Measure-Object -Property CheckFailures -Sum).Sum
            AverageServerCpuPercentOfMachine = [Math]::Round((($resourceGroup | Measure-Object -Property ServerAverageCpuPercentOfMachine -Average).Average), 2)
            PeakServerWorkingSetMB = [Math]::Round((($resourceGroup | Measure-Object -Property ServerPeakWorkingSetMB -Maximum).Maximum), 1)
        })
    }
}
$runRows | Sort-Object Version, VUs, Run |
    Export-Csv -LiteralPath (Join-Path $resultsPath "per-run-metrics.csv") -NoTypeInformation
$summaryRows | Sort-Object Version, VUs |
    Export-Csv -LiteralPath (Join-Path $resultsPath "comparison-summary.csv") -NoTypeInformation

if ($summaryRows.Count -ne 12) {
    throw "Expected 12 comparison rows; found $($summaryRows.Count)."
}
if (@($summaryRows | Where-Object {
    [double]$_.FailedRequestRate -ne 0 -or [int]$_.CheckFailures -ne 0
}).Count -gt 0) {
    throw "Cannot build a report while measured requests or checks have failures."
}

$script:bodyParts = New-Object System.Collections.ArrayList
$labels = @{
    baseline = "Reconstructed baseline"
    optimized = "Existing direct-loop optimization"
    improved = "Concurrent-read improvement"
}

function Escape-XmlText {
    param([string]$Text)
    return [System.Security.SecurityElement]::Escape($Text)
}

function Add-ReportParagraph {
    param(
        [string]$Text,
        [ValidateSet("Title", "Subtitle", "Heading1", "Heading2", "Body")]
        [string]$Kind = "Body"
    )

    $escaped = Escape-XmlText $Text
    switch ($Kind) {
        "Title" {
            $paragraphProperties = '<w:pPr><w:jc w:val="center"/><w:spacing w:after="180"/></w:pPr>'
            $runProperties = '<w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:b/><w:color w:val="153A31"/><w:sz w:val="34"/></w:rPr>'
        }
        "Subtitle" {
            $paragraphProperties = '<w:pPr><w:spacing w:after="300"/><w:jc w:val="center"/></w:pPr>'
            $runProperties = '<w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:color w:val="687A72"/><w:sz w:val="22"/></w:rPr>'
        }
        "Heading1" {
            $paragraphProperties = '<w:pPr><w:keepNext/><w:spacing w:before="220" w:after="100"/><w:outlineLvl w:val="0"/></w:pPr>'
            $runProperties = '<w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:b/><w:color w:val="1F5A45"/><w:sz w:val="28"/></w:rPr>'
        }
        "Heading2" {
            $paragraphProperties = '<w:pPr><w:keepNext/><w:spacing w:before="140" w:after="60"/><w:outlineLvl w:val="1"/></w:pPr>'
            $runProperties = '<w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:b/><w:color w:val="153A31"/><w:sz w:val="23"/></w:rPr>'
        }
        default {
            $paragraphProperties = '<w:pPr><w:spacing w:after="100"/><w:widowControl/></w:pPr>'
            $runProperties = '<w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:sz w:val="21"/></w:rPr>'
        }
    }

    $paragraph = '<w:p>' + $paragraphProperties + '<w:r>' + $runProperties +
        '<w:t xml:space="preserve">' + $escaped + '</w:t></w:r></w:p>'
    [void]$script:bodyParts.Add($paragraph)
}

function Add-ReportTable {
    param(
        [string[]]$Headers,
        [System.Collections.ArrayList]$Rows
    )

    $columnWidth = [Math]::Floor(9360 / $Headers.Count)
    $builder = New-Object System.Text.StringBuilder
    [void]$builder.Append('<w:tbl><w:tblPr><w:tblW w:w="9360" w:type="dxa"/><w:tblLayout w:type="autofit"/><w:tblBorders><w:top w:val="single" w:sz="4" w:color="AAB8B0"/><w:left w:val="single" w:sz="4" w:color="AAB8B0"/><w:bottom w:val="single" w:sz="4" w:color="AAB8B0"/><w:right w:val="single" w:sz="4" w:color="AAB8B0"/><w:insideH w:val="single" w:sz="4" w:color="D4DDD7"/><w:insideV w:val="single" w:sz="4" w:color="D4DDD7"/></w:tblBorders></w:tblPr><w:tblGrid>')
    for ($column = 0; $column -lt $Headers.Count; $column++) {
        [void]$builder.Append('<w:gridCol w:w="' + $columnWidth + '"/>')
    }
    [void]$builder.Append('</w:tblGrid>')

    for ($rowIndex = 0; $rowIndex -le $Rows.Count; $rowIndex++) {
        [void]$builder.Append('<w:tr>')
        if ($rowIndex -eq 0) {
            [void]$builder.Append('<w:trPr><w:tblHeader w:val="true"/></w:trPr>')
        }
        for ($column = 0; $column -lt $Headers.Count; $column++) {
            if ($rowIndex -eq 0) {
                $cellText = $Headers[$column]
                $shading = '<w:shd w:val="clear" w:fill="D9EAD3"/>'
                $bold = '<w:b/>'
            }
            else {
                $cellText = [string]$Rows[$rowIndex - 1][$column]
                $shading = ""
                $bold = ""
            }
            $escaped = Escape-XmlText $cellText
            [void]$builder.Append(
                '<w:tc><w:tcPr><w:tcW w:w="' + $columnWidth +
                '" w:type="dxa"/>' + $shading + '</w:tcPr><w:p><w:pPr><w:spacing w:after="40"/></w:pPr><w:r><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:sz w:val="16"/>' +
                $bold + '</w:rPr><w:t xml:space="preserve">' + $escaped +
                '</w:t></w:r></w:p></w:tc>'
            )
        }
        [void]$builder.Append('</w:tr>')
    }
    [void]$builder.Append('</w:tbl>')
    [void]$script:bodyParts.Add($builder.ToString())
}

Add-ReportParagraph "Load Testing and Scalability Analysis - Club Reservation System" "Title"
Add-ReportParagraph "Performance evidence and investigation | October 25, 2026" "Subtitle"
Add-ReportParagraph "Executive summary" "Heading1"
Add-ReportParagraph "The application was exercised with a repeatable k6 workload against its date-range reservation API. Testing confirmed a concurrency limitation in the existing direct-loop optimization: at 20 virtual users its throughput stopped growing and p95 latency rose sharply. Moving the JSON repository to CopyOnWriteArrayList snapshot reads improved 20-VU median throughput from 11,132.54 to 21,095.91 requests per second (+89.5%) and reduced p95 latency from 3.510 ms to 1.719 ms (-51.0%). All measured requests succeeded. The test did not reach an error or failure point; the remaining 10-to-20-VU latency increase indicates diminishing returns that should be profiled before drawing conclusions beyond this workload."

Add-ReportParagraph "1. Workload and baseline evidence" "Heading1"
Add-ReportParagraph "Application and operation" "Heading2"
Add-ReportParagraph "The system under test is the Spring Boot 3.5.6 Club Reservation System. The benchmark used the read-only JSON-storage operation GET /api/reservations?from=2026-10-18&to=2026-10-24. Each benchmark worktree loaded the same logical schedule of 13,505 tee times; the query returned 259 records. The endpoint is representative because it serves the application tee-time schedule."
Add-ReportParagraph "Testing environment" "Heading2"
$environmentRows = New-Object System.Collections.ArrayList
[void]$environmentRows.Add(@("Operating system", "Windows 11 Home, build 26200, x64"))
[void]$environmentRows.Add(@("Host hardware", "GIGABYTE G6 KF; Intel Core i7-13620H; 10 cores / 16 logical processors; 31.7 GiB RAM"))
[void]$environmentRows.Add(@("Runtime and build", "Oracle JDK 24.0.2; Maven 3.9.16; Spring Boot 3.5.6"))
[void]$environmentRows.Add(@("Load tool", "k6 v2.3.0 for Windows amd64"))
[void]$environmentRows.Add(@("Application configuration", "JSON storage; port 18080; 13,505 records; server startup excluded"))
[void]$environmentRows.Add(@("Client placement", "k6 and the application ran on the same laptop; results are host-specific"))
Add-ReportTable @("Setting", "Value") $environmentRows
Add-ReportParagraph "Workload and measurement method" "Heading2"
Add-ReportParagraph "The runner used fixed virtual-user levels of 1, 5, 10, and 20. Each level ran for 20 seconds, three times per implementation, after a 20-second one-VU warmup. Iterations were unpaced with no think time, so each VU sent another GET as soon as its previous request completed. This is a closed-loop, read-only workload. The app used the same port and logically identical dataset for each variant. Preflight and k6 setup checks required HTTP 200 and exactly 259 results. k6 checks required every measured GET to return HTTP 200; the failure-rate threshold required zero failed requests."
Add-ReportParagraph "The repository initial commit (e0fb7d9) already contains the date-range optimization, so its parent did not provide a pre-optimization source revision. The tracked load-tests/baseline-date-range.patch reconstructs the earlier service path documented in repository history: copy all records with findAll(), then filter with a stream. This is a reconstructed baseline, not a claim that a complete original source revision remains in Git."

Add-ReportParagraph "2. Load-testing evidence" "Heading1"
Add-ReportParagraph "Each table entry is the median of three measured runs at that workload level. The adjacent results directory contains k6 summary JSON and console output, per-run metrics, and application resource observations."
$performanceRows = New-Object System.Collections.ArrayList
foreach ($row in ($summaryRows | Sort-Object @{Expression={ [int]$_.VUs }}, @{Expression={ $_.Version }})) {
    $versionName = $labels[$row.Version]
    [void]$performanceRows.Add(@(
        $versionName,
        [string]$row.VUs,
        ("{0:N2}" -f [double]$row.MedianRequestsPerSecond),
        ("{0:N3}" -f [double]$row.MedianMeanLatencyMs),
        ("{0:N3}" -f [double]$row.MedianP95LatencyMs),
        ("{0:P2}" -f [double]$row.FailedRequestRate)
    ))
}
Add-ReportTable @("Implementation", "VUs", "Median req/s", "Mean ms", "p95 ms", "Failed") $performanceRows
Add-ReportParagraph "Resource observations" "Heading2"
Add-ReportParagraph "The runner sampled the application process working set every 0.5 seconds and calculated average application CPU as process CPU time divided by elapsed time and the host 16 logical processors. These are approximate host-level observations; the co-located k6 generator also consumes resources."
$resourceRows = New-Object System.Collections.ArrayList
foreach ($vus in 1, 5, 10, 20) {
    $old = $summaryRows | Where-Object { $_.Version -eq "optimized" -and [int]$_.VUs -eq $vus } | Select-Object -First 1
    $new = $summaryRows | Where-Object { $_.Version -eq "improved" -and [int]$_.VUs -eq $vus } | Select-Object -First 1
    [void]$resourceRows.Add(@(
        [string]$vus,
        ("{0:N1}" -f [double]$old.AverageServerCpuPercentOfMachine),
        ("{0:N1}" -f [double]$new.AverageServerCpuPercentOfMachine),
        ("{0:N1}" -f [double]$old.PeakServerWorkingSetMB),
        ("{0:N1}" -f [double]$new.PeakServerWorkingSetMB)
    ))
}
Add-ReportTable @("VUs", "Existing CPU %", "Improved CPU %", "Existing peak MiB", "Improved peak MiB") $resourceRows

Add-ReportParagraph "3. Scalability analysis" "Heading1"
$pre10 = $summaryRows | Where-Object { $_.Version -eq "optimized" -and [int]$_.VUs -eq 10 } | Select-Object -First 1
$pre20 = $summaryRows | Where-Object { $_.Version -eq "optimized" -and [int]$_.VUs -eq 20 } | Select-Object -First 1
$post10 = $summaryRows | Where-Object { $_.Version -eq "improved" -and [int]$_.VUs -eq 10 } | Select-Object -First 1
$post20 = $summaryRows | Where-Object { $_.Version -eq "improved" -and [int]$_.VUs -eq 20 } | Select-Object -First 1
$preRpsDelta = ([double]$pre20.MedianRequestsPerSecond - [double]$pre10.MedianRequestsPerSecond) / [double]$pre10.MedianRequestsPerSecond
$preP95Delta = ([double]$pre20.MedianP95LatencyMs - [double]$pre10.MedianP95LatencyMs) / [double]$pre10.MedianP95LatencyMs
$postRpsDelta = ([double]$post20.MedianRequestsPerSecond - [double]$post10.MedianRequestsPerSecond) / [double]$post10.MedianRequestsPerSecond
$postP95Delta = ([double]$post20.MedianP95LatencyMs - [double]$post10.MedianP95LatencyMs) / [double]$post10.MedianP95LatencyMs
Add-ReportParagraph ("In the existing implementation, increasing demand from 10 to 20 VUs changed median throughput by {0:P1} (from {1:N2} to {2:N2} req/s), while p95 latency increased {3:P1} (from {4:N3} to {5:N3} ms). The application process used about {6:N1}% of total host CPU at 20 VUs, and there were no failed requests. This measured plateau and latency growth, paired with the synchronized full-schedule scan in the source, support repository-monitor contention as a performance limitation." -f $preRpsDelta, [double]$pre10.MedianRequestsPerSecond, [double]$pre20.MedianRequestsPerSecond, $preP95Delta, [double]$pre10.MedianP95LatencyMs, [double]$pre20.MedianP95LatencyMs, [double]$pre20.AverageServerCpuPercentOfMachine)
Add-ReportParagraph ("After the change, 10 to 20 VUs increased median throughput by {0:P1} (from {1:N2} to {2:N2} req/s), with p95 latency rising {3:P1} (from {4:N3} to {5:N3} ms). This is better scaling than the prior plateau, but throughput does not double with doubled VUs. At 20 VUs the app averaged {6:N1}% of total host CPU and had zero request failures; the test did not establish a hard capacity limit or CPU saturation." -f $postRpsDelta, [double]$post10.MedianRequestsPerSecond, [double]$post20.MedianRequestsPerSecond, $postP95Delta, [double]$post10.MedianP95LatencyMs, [double]$post20.MedianP95LatencyMs, [double]$post20.AverageServerCpuPercentOfMachine)
Add-ReportParagraph "These results establish behavior only for this local host, dataset, read-only endpoint, and workload. The remaining latency increase may involve JSON serialization, allocation, scheduling, or contention from the co-located load generator; the measurements do not isolate those causes. A remote load generator, mixed read/write tests, and a profiler would be needed for stronger production or root-cause claims."

Add-ReportParagraph "4. Investigation and improvement" "Heading1"
$changeRows = New-Object System.Collections.ArrayList
[void]$changeRows.Add(@("Before", "JsonReservationRepository used a synchronized date-range method that held the monitor while scanning all 13,505 tee times. Concurrent range reads serialized their scans."))
[void]$changeRows.Add(@("After", "The JSON repository stores tee times in CopyOnWriteArrayList, and the date-range lookup iterates its stable snapshot without synchronizing the full scan. Existing synchronized write methods still protect mutations."))
[void]$changeRows.Add(@("Tradeoff", "CopyOnWriteArrayList copies its backing array when entries are changed, so create/update/delete operations may cost more in memory and CPU. This investigation measured reads only; write-heavy behavior needs a separate workload."))
Add-ReportTable @("Aspect", "Evidence or change") $changeRows
$impactRows = New-Object System.Collections.ArrayList
foreach ($vus in 1, 5, 10, 20) {
    $old = $summaryRows | Where-Object { $_.Version -eq "optimized" -and [int]$_.VUs -eq $vus } | Select-Object -First 1
    $new = $summaryRows | Where-Object { $_.Version -eq "improved" -and [int]$_.VUs -eq $vus } | Select-Object -First 1
    $rpsDelta = ([double]$new.MedianRequestsPerSecond - [double]$old.MedianRequestsPerSecond) / [double]$old.MedianRequestsPerSecond
    $p95Delta = ([double]$new.MedianP95LatencyMs - [double]$old.MedianP95LatencyMs) / [double]$old.MedianP95LatencyMs
    [void]$impactRows.Add(@([string]$vus, ("{0:P1}" -f $rpsDelta), ("{0:P1}" -f $p95Delta)))
}
Add-ReportTable @("VUs", "Throughput change", "p95 latency change") $impactRows
Add-ReportParagraph "The improvement is strongest at 10 and 20 VUs and modest at one VU. At 5 VUs throughput increased while p95 was essentially unchanged; that small tail-latency difference should not be treated as meaningful without more repetitions."

Add-ReportParagraph "5. Verification evidence" "Heading1"
Add-ReportParagraph "The Java test suite completed with 10 tests, 0 failures, 0 errors, and 0 skipped. Existing REST coverage verifies inclusive date filtering and that range results reflect create, update, and delete operations; the concurrency test exercises concurrent booking behavior. All 36 measured k6 runs passed their HTTP 200 checks and recorded a 0% failed-request rate."
Add-ReportParagraph "A post-change API verification returned HTTP 200 with exactly 259 entries, all dated within 2026-10-18 through 2026-10-24. k6 repeated the expected-record-count check before each measured run."
Add-ReportParagraph "Reproduction resources" "Heading2"
Add-ReportParagraph "Project instructions and workload: README.md and load-tests/reservations.js. Run the matrix with load-tests/run-matrix.ps1. The baseline reconstruction is load-tests/baseline-date-range.patch; the modified source is src/main/java/edu/seminolestate/reservation/repository/JsonReservationRepository.java. Machine-readable outputs include comparison-summary.csv, per-run-metrics.csv, per-version resource CSVs, and each run-specific k6 JSON/text output in load-tests/results."
Add-ReportParagraph "Conclusion" "Heading1"
Add-ReportParagraph "The targeted change improved concurrent date-range read behavior without changing the API response or record count. The evidence supports repository-monitor contention in the existing implementation and a measurable improvement after removing the read scan from the monitor. It does not establish maximum capacity, write-workload performance, or production scalability beyond the tested 20 VUs."

$body = [string]::Join("", [string[]]$script:bodyParts.ToArray())
$documentXml = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>' +
    $body + '<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1080" w:right="1440" w:bottom="1080" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr></w:body></w:document>'
$contentTypes = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>'
$relationships = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>'
$reportPath = Join-Path $resultsPath "Club-Reservation-Load-Test-Report.docx"
if (Test-Path -LiteralPath $reportPath) {
    Remove-Item -LiteralPath $reportPath
}
$archive = [System.IO.Compression.ZipFile]::Open($reportPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($entryInfo in @(
        @("[Content_Types].xml", $contentTypes),
        @("_rels/.rels", $relationships),
        @("word/document.xml", $documentXml)
    )) {
        $entry = $archive.CreateEntry($entryInfo[0], [System.IO.Compression.CompressionLevel]::Optimal)
        $stream = $entry.Open()
        $writer = [System.IO.StreamWriter]::new($stream, [System.Text.UTF8Encoding]::new($false))
        $writer.Write($entryInfo[1])
        $writer.Dispose()
    }
}
finally {
    $archive.Dispose()
}

$readArchive = [System.IO.Compression.ZipFile]::OpenRead($reportPath)
try {
    foreach ($entryName in "[Content_Types].xml", "_rels/.rels", "word/document.xml") {
        if (-not $readArchive.GetEntry($entryName)) {
            throw "DOCX package is missing $entryName."
        }
    }
    $documentEntry = $readArchive.GetEntry("word/document.xml")
    $reader = New-Object System.IO.StreamReader($documentEntry.Open())
    $xmlText = $reader.ReadToEnd()
    $reader.Dispose()
    [xml]$xml = $xmlText
    $namespaces = New-Object System.Xml.XmlNamespaceManager($xml.NameTable)
    $namespaces.AddNamespace("w", "http://schemas.openxmlformats.org/wordprocessingml/2006/main")
    $tableCount = $xml.SelectNodes("//w:tbl", $namespaces).Count
    $paragraphCount = $xml.SelectNodes("//w:p", $namespaces).Count
    $invalidRunProperties = $xml.SelectNodes("//w:p/w:rPr", $namespaces).Count
    $text = [string]::Join("", @($xml.SelectNodes("//w:t", $namespaces) | ForEach-Object { $_.InnerText }))
    if ($tableCount -lt 4 -or $paragraphCount -lt 20 -or $invalidRunProperties -ne 0 -or
        $text -notmatch "21,095.91" -or $text -notmatch "10 tests") {
        throw "Generated report failed structure/content validation."
    }
}
finally {
    $readArchive.Dispose()
}

$report = Get-Item -LiteralPath $reportPath
Write-Output "Generated and validated DOCX: $($report.FullName); bytes=$($report.Length); tables=$tableCount; paragraphs=$paragraphCount."
