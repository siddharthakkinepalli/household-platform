param(
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$AppId = "com.household.app",
    [string]$Activity = ".MainActivity",
    [switch]$InstallFirst,
    [string]$GradleTask = ":android:installDebug"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[SMOKE] $Message"
}

function Invoke-Adb {
    param([string]$Args)
    & $AdbPath $Args
}

function Ensure-File {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        throw "Required file not found: $Path"
    }
}

function New-ArtifactDir {
    $base = Join-Path $PSScriptRoot "artifacts"
    if (-not (Test-Path $base)) {
        New-Item -ItemType Directory -Path $base | Out-Null
    }

    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $runDir = Join-Path $base "smoke_$stamp"
    New-Item -ItemType Directory -Path $runDir | Out-Null
    return $runDir
}

function Write-Summary {
    param(
        [string]$SummaryPath,
        [string]$Result,
        [string]$Reason,
        [string]$RunDir
    )

    "JUGAAD phone smoke test" | Out-File -FilePath $SummaryPath -Encoding utf8
    "Timestamp: $(Get-Date -Format s)" | Add-Content -Path $SummaryPath
    "Result: $Result" | Add-Content -Path $SummaryPath
    "Reason: $Reason" | Add-Content -Path $SummaryPath
    "Artifacts dir: $RunDir" | Add-Content -Path $SummaryPath
}

function Is-DeviceConnected {
    $out = & $AdbPath devices
    return @($out -split "`n" | Where-Object { $_ -match "\sdevice$" }).Count -gt 0
}

function Is-AppInstalled {
    param([string]$PackageName)
    $pkg = & $AdbPath shell pm list packages $PackageName
    return @($pkg | Where-Object { $_ -match "package:$PackageName" }).Count -gt 0
}

function Is-KeyguardShowing {
    $windowDump = & $AdbPath shell dumpsys window
    if ($windowDump -match "mDreamingLockscreen=true") { return $true }
    if ($windowDump -match "isStatusBarKeyguard=true") { return $true }
    if ($windowDump -match "mShowingLockscreen=true") { return $true }
    return $false
}

function Get-DisplaySize {
    $sizeLine = & $AdbPath shell wm size | Select-String -Pattern "Physical size"
    if (-not $sizeLine) {
        throw "Unable to read device display size from adb wm size"
    }

    if ($sizeLine.ToString() -notmatch "(\d+)x(\d+)") {
        throw "Unexpected display size format: $sizeLine"
    }

    return [PSCustomObject]@{
        Width = [int]$Matches[1]
        Height = [int]$Matches[2]
    }
}

function Capture-State {
    param(
        [string]$RunDir,
        [string]$Label
    )

    $safeLabel = $Label -replace "[^A-Za-z0-9_-]", "_"
    $remotePng = "/sdcard/${safeLabel}.png"
    $remoteXml = "/sdcard/${safeLabel}.xml"
    $localPng = Join-Path $RunDir "${safeLabel}.png"
    $localXml = Join-Path $RunDir "${safeLabel}.xml"

    & $AdbPath shell screencap -p $remotePng | Out-Null
    & $AdbPath shell uiautomator dump $remoteXml | Out-Null
    & $AdbPath pull $remotePng $localPng | Out-Null
    & $AdbPath pull $remoteXml $localXml | Out-Null
    & $AdbPath shell rm $remotePng $remoteXml | Out-Null
}

function Is-AppForeground {
    param([string]$PackageName)
    $focus = & $AdbPath shell dumpsys window | Select-String -Pattern "mCurrentFocus"
    if (-not $focus) { return $false }
    return ($focus.ToString() -match [regex]::Escape($PackageName))
}

function Get-TapPointFromNodeBounds {
    param([string]$Bounds)
    if ($Bounds -notmatch "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
        return $null
    }

    $x1 = [int]$Matches[1]
    $y1 = [int]$Matches[2]
    $x2 = [int]$Matches[3]
    $y2 = [int]$Matches[4]

    return [PSCustomObject]@{
        X = [int](($x1 + $x2) / 2)
        Y = [int](($y1 + $y2) / 2)
    }
}

function Tap-ByText {
    param(
        [string]$RunDir,
        [string]$Text,
        [string]$PackageName,
        [string]$Label
    )

    $xmlLabel = "tap_lookup_$Label"
    Capture-State -RunDir $RunDir -Label $xmlLabel
    $xmlPath = Join-Path $RunDir ("${xmlLabel}.xml")

    [xml]$doc = Get-Content -Raw -Path $xmlPath
    $nodes = $doc.SelectNodes("//node")

    $candidate = $null
    foreach ($node in $nodes) {
        $nodeText = $node.GetAttribute("text")
        $nodeDesc = $node.GetAttribute("content-desc")
        $nodePkg = $node.GetAttribute("package")
        $clickable = $node.GetAttribute("clickable")
        if (($nodeText -eq $Text -or $nodeDesc -eq $Text) -and $nodePkg -eq $PackageName -and $clickable -eq "true") {
            $candidate = $node
        }
    }

    if (-not $candidate) {
        throw "Unable to find clickable node with text '$Text'"
    }

    $bounds = $candidate.GetAttribute("bounds")
    $point = Get-TapPointFromNodeBounds -Bounds $bounds
    if (-not $point) {
        throw "Unable to parse bounds '$bounds' for text '$Text'"
    }

    & $AdbPath shell input tap $point.X $point.Y | Out-Null
}

function Try-TapByText {
    param(
        [string]$RunDir,
        [string]$Text,
        [string]$PackageName,
        [string]$Label
    )

    try {
        Tap-ByText -RunDir $RunDir -Text $Text -PackageName $PackageName -Label $Label
        return $true
    }
    catch {
        return $false
    }
}

function Collect-LogcatTail {
    param([string]$RunDir)
    $logFile = Join-Path $RunDir "logcat_tail.txt"
    $logs = & $AdbPath logcat -d
    $logs | Out-File -FilePath $logFile -Encoding utf8
    return $logFile
}

function Find-FatalErrors {
    param(
        [string]$LogFile,
        [string]$PackageName
    )
    $patterns = @(
        "FATAL EXCEPTION",
        "Process:\s*$([regex]::Escape($PackageName))",
        "NoClassDefFoundError",
        "InflateException",
        "java\.lang\.RuntimeException"
    )
    $matches = Select-String -Path $LogFile -Pattern ($patterns -join "|")
    return $matches
}

Ensure-File -Path $AdbPath

if (-not (Is-DeviceConnected)) {
    throw "No adb device detected. Connect/unlock phone and enable USB debugging."
}

$runDir = New-ArtifactDir
Write-Step "Artifacts: $runDir"
$summary = Join-Path $runDir "summary.txt"

if ($InstallFirst) {
    Write-Step "Installing latest build with Gradle task $GradleTask"
    Push-Location (Join-Path $PSScriptRoot "..\..")
    try {
        .\gradlew.bat $GradleTask
        if ($LASTEXITCODE -ne 0) {
            Write-Summary -SummaryPath $summary -Result "FAIL" -Reason "Gradle task '$GradleTask' failed (exit code $LASTEXITCODE)." -RunDir $runDir
            throw "Gradle task '$GradleTask' failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}

if (-not (Is-AppInstalled -PackageName $AppId)) {
    Write-Summary -SummaryPath $summary -Result "FAIL" -Reason "App '$AppId' is not installed. Re-run with -InstallFirst." -RunDir $runDir
    throw "App '$AppId' is not installed. Re-run with -InstallFirst."
}

if (Is-KeyguardShowing) {
    Capture-State -RunDir $runDir -Label "00_keyguard_blocked"
    Write-Summary -SummaryPath $summary -Result "BLOCKED" -Reason "Phone appears locked (keyguard showing). Unlock and rerun." -RunDir $runDir
    throw "Phone appears locked (keyguard showing). Unlock phone and re-run smoke test."
}

$size = Get-DisplaySize
Write-Step "Device size detected: $($size.Width)x$($size.Height)"

Write-Step "Clearing logcat and launching app"
& $AdbPath logcat -c
& $AdbPath shell am force-stop $AppId
& $AdbPath shell am start -n "$AppId/$Activity" | Out-Null

Start-Sleep -Seconds 3
Capture-State -RunDir $runDir -Label "00_launch"

# Tap bottom tabs by relative x positions: Hub, Expenses, Meals, Recipes, Family
$tabSequence = @(
    @{ Name = "expenses"; Text = "Expenses" },
    @{ Name = "meals"; Text = "Meals" },
    @{ Name = "recipes"; Text = "Recipes" },
    @{ Name = "family"; Text = "Family" },
    @{ Name = "expenses_return"; Text = "Expenses" }
)

foreach ($step in $tabSequence) {
    if (-not (Is-AppForeground -PackageName $AppId)) {
        Write-Step "App lost foreground; relaunching before step '$($step.Name)'"
        & $AdbPath shell am start -n "$AppId/$Activity" | Out-Null
        Start-Sleep -Seconds 2
    }

    Write-Step "Tapping tab: $($step.Name) [$($step.Text)]"
    Tap-ByText -RunDir $runDir -Text $step.Text -PackageName $AppId -Label ("top_" + $step.Name)
    Start-Sleep -Seconds 2
    Capture-State -RunDir $runDir -Label ("10_" + $step.Name)
    if (-not (Is-AppForeground -PackageName $AppId)) {
        Capture-State -RunDir $runDir -Label ("99_lost_foreground_" + $step.Name)
        throw "App left foreground after step '$($step.Name)'"
    }
}

# Validate nested Expenses tabs too.
$openedExpensesModule = $false
$openedDirect = Try-TapByText -RunDir $runDir -Text "Dashboard" -PackageName $AppId -Label "expenses_probe_dashboard"
if ($openedDirect) {
    Start-Sleep -Seconds 2
    Capture-State -RunDir $runDir -Label "20_expenses_dashboard"
    $openedExpensesModule = $true
}

if (-not $openedExpensesModule) {
    Write-Step "Dashboard tab not visible yet; trying launcher button OPEN EXPENSES"
    $openedViaLauncher = Try-TapByText -RunDir $runDir -Text "OPEN EXPENSES" -PackageName $AppId -Label "expenses_open_launcher"
    if ($openedViaLauncher) {
        Start-Sleep -Seconds 2
        Capture-State -RunDir $runDir -Label "20_expenses_opened"
        $openedExpensesModule = $true
    }
}

if (-not $openedExpensesModule) {
    Capture-State -RunDir $runDir -Label "99_expenses_not_opened"
    throw "Expenses module did not open: neither Dashboard tab nor OPEN EXPENSES launcher button was tappable."
}

$expensesSubTabs = @("Dashboard", "Transactions", "Budget", "Trips", "Import")
foreach ($sub in $expensesSubTabs) {
    if (-not (Is-AppForeground -PackageName $AppId)) {
        Write-Step "App lost foreground before Expenses sub-tab '$sub'; relaunching"
        & $AdbPath shell am start -n "$AppId/$Activity" | Out-Null
        Start-Sleep -Seconds 2
        Tap-ByText -RunDir $runDir -Text "Expenses" -PackageName $AppId -Label "recover_expenses"
        Start-Sleep -Seconds 2
    }

    if ($sub -eq "Dashboard") {
        Write-Step "Dashboard already validated while opening Expenses module"
        continue
    }

    Write-Step "Tapping Expenses sub-tab: $sub"
    Tap-ByText -RunDir $runDir -Text $sub -PackageName $AppId -Label ("expenses_sub_" + $sub)
    Start-Sleep -Seconds 2
    Capture-State -RunDir $runDir -Label ("20_expenses_" + $sub.ToLowerInvariant())
    if (-not (Is-AppForeground -PackageName $AppId)) {
        Capture-State -RunDir $runDir -Label ("99_lost_foreground_expenses_" + $sub.ToLowerInvariant())
        throw "App left foreground after Expenses sub-tab '$sub'"
    }
}

$logFile = Collect-LogcatTail -RunDir $runDir
$fatal = Find-FatalErrors -LogFile $logFile -PackageName $AppId
Write-Summary -SummaryPath $summary -Result "IN_PROGRESS" -Reason "Flow completed, evaluating logs." -RunDir $runDir
"Device size: $($size.Width)x$($size.Height)" | Add-Content -Path $summary

if ($fatal.Count -gt 0) {
    "Result: FAIL" | Add-Content -Path $summary
    "Reason: Fatal/runtime errors detected in logcat." | Add-Content -Path $summary
    "" | Add-Content -Path $summary
    "Relevant log lines:" | Add-Content -Path $summary
    $fatal | Select-Object -Last 80 | ForEach-Object { $_.Line } | Add-Content -Path $summary
    Write-Host "[SMOKE] FAIL - see $summary"
    exit 1
}

"Result: PASS" | Add-Content -Path $summary
"Reason: No fatal/runtime errors detected during automated tap flow." | Add-Content -Path $summary
Write-Host "[SMOKE] PASS - see $summary"
exit 0
