param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-zA-Z0-9-]+$')]
    [string]$Label,

    [string]$BaseUrl = "http://localhost:18080",
    [int[]]$Levels = @(1, 5, 10, 20),
    [int]$Repeats = 3,
    [string]$Duration = "20s",
    [string]$K6Path = "k6"
)

$ErrorActionPreference = "Stop"
$scriptPath = Join-Path $PSScriptRoot "reservations.js"
$resultsPath = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Path $resultsPath -Force | Out-Null

$k6 = Get-Command $K6Path -ErrorAction SilentlyContinue
if (-not $k6) {
    throw "Could not find k6 at '$K6Path'. Install k6 or pass -K6Path with the executable path."
}

$uri = [Uri]$BaseUrl
$listener = Get-NetTCPConnection -LocalPort $uri.Port -State Listen |
    Select-Object -First 1
if (-not $listener) {
    throw "No application is listening on port $($uri.Port). Start the app before running the matrix."
}
$appProcessId = $listener.OwningProcess

$preflight = Invoke-RestMethod -Uri "$BaseUrl/api/reservations?from=2026-10-18&to=2026-10-24"
if (@($preflight).Count -ne 259) {
    throw "Preflight expected 259 records for the configured date range; received $(@($preflight).Count)."
}

$resourceFile = Join-Path $resultsPath "$Label-resources.csv"
if (Test-Path $resourceFile) {
    Remove-Item -LiteralPath $resourceFile
}

function Invoke-K6Run {
    param(
        [string]$RunName,
        [int]$Vus,
        [bool]$Measured
    )

    $summaryFile = Join-Path $resultsPath "$RunName.json"
    $stdoutFile = Join-Path $resultsPath "$RunName.txt"
    $stderrFile = Join-Path $resultsPath "$RunName.err.txt"
    $arguments = @(
        "run",
        "--quiet",
        "--summary-export",
        "`"$summaryFile`"",
        "-e",
        "`"BASE_URL=$BaseUrl`"",
        "-e",
        "`"VUS=$Vus`"",
        "-e",
        "`"DURATION=$Duration`"",
        "`"$scriptPath`""
    ) -join " "
    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $k6.Source
    $startInfo.Arguments = $arguments
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $startInfo.StandardErrorEncoding = [System.Text.Encoding]::UTF8
    $k6Process = New-Object System.Diagnostics.Process
    $k6Process.StartInfo = $startInfo
    if (-not $k6Process.Start()) {
        throw "Could not start k6 for '$RunName'."
    }
    $stdoutTask = $k6Process.StandardOutput.ReadToEndAsync()
    $stderrTask = $k6Process.StandardError.ReadToEndAsync()
    $startTime = Get-Date
    $serverProcess = Get-Process -Id $appProcessId -ErrorAction SilentlyContinue
    if (-not $serverProcess) {
        throw "Application process $appProcessId exited before the k6 run started."
    }
    $startCpu = $serverProcess.TotalProcessorTime.TotalSeconds
    $peakWorkingSet = $serverProcess.WorkingSet64

    while (-not $k6Process.HasExited) {
        $serverProcess = Get-Process -Id $appProcessId -ErrorAction SilentlyContinue
        if ($serverProcess) {
            $peakWorkingSet = [Math]::Max($peakWorkingSet, $serverProcess.WorkingSet64)
        }
        Start-Sleep -Milliseconds 500
        $k6Process.Refresh()
    }
    $k6Process.WaitForExit()
    $endTime = Get-Date
    $stdout = $stdoutTask.Result
    $stderr = $stderrTask.Result
    [System.IO.File]::WriteAllText($stdoutFile, $stdout)
    if ($stderr.Length -gt 0) {
        [System.IO.File]::WriteAllText($stderrFile, $stderr)
    }
    elseif (Test-Path -LiteralPath $stderrFile) {
        Remove-Item -LiteralPath $stderrFile
    }

    if ($k6Process.ExitCode -ne 0) {
        Get-Content -LiteralPath $stdoutFile, $stderrFile -ErrorAction SilentlyContinue
        throw "k6 run '$RunName' failed with exit code $($k6Process.ExitCode)."
    }
    if (-not $Measured) {
        return
    }

    $serverProcess = Get-Process -Id $appProcessId -ErrorAction SilentlyContinue
    $endCpu = if ($serverProcess) { $serverProcess.TotalProcessorTime.TotalSeconds } else { $startCpu }
    if ($serverProcess) {
        $peakWorkingSet = [Math]::Max($peakWorkingSet, $serverProcess.WorkingSet64)
    }
    $elapsedSeconds = ($endTime - $startTime).TotalSeconds
    $averageCpuPercent = 100 * ($endCpu - $startCpu) /
        [Math]::Max(1, $elapsedSeconds * [Environment]::ProcessorCount)

    [PSCustomObject]@{
        Label = $Label
        VUs = $Vus
        Run = $RunName
        ElapsedSeconds = [Math]::Round($elapsedSeconds, 2)
        ServerAverageCpuPercentOfMachine = [Math]::Round($averageCpuPercent, 2)
        ServerPeakWorkingSetMB = [Math]::Round($peakWorkingSet / 1MB, 1)
    } | Export-Csv -LiteralPath $resourceFile -Append -NoTypeInformation

    Write-Output "Completed $RunName"
}

$warmupName = "$Label-warmup"
Invoke-K6Run -RunName $warmupName -Vus 1 -Measured $false

foreach ($vus in $Levels) {
    if ($vus -lt 1) {
        throw "Every workload level must be at least one VU."
    }
    for ($run = 1; $run -le $Repeats; $run++) {
        $runName = "$Label-vus-$vus-run-$run"
        Invoke-K6Run -RunName $runName -Vus $vus -Measured $true
    }
}
