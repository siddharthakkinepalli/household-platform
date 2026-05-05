param(
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$AppId = "com.jugaad.home",
    [string]$Activity = "com.household.app.MainActivity",
    [string]$DeviceId = "",
    [string]$WorkflowFile = "$PSScriptRoot\workflows.emulator.json",
    [switch]$InstallFirst,
    [string]$GradleTask = ":android:installDebug"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -ErrorAction SilentlyContinue) {
    $global:PSNativeCommandUseErrorActionPreference = $false
}

function Write-Step {
    param([string]$Message)
    Write-Host "[EMU-TEST] $Message"
}

function New-ArtifactDir {
    $base = Join-Path $PSScriptRoot "artifacts"
    if (-not (Test-Path $base)) {
        New-Item -ItemType Directory -Path $base | Out-Null
    }

    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $runDir = Join-Path $base "emulator_$stamp"
    New-Item -ItemType Directory -Path $runDir | Out-Null
    return $runDir
}

function Resolve-DeviceId {
    param([string]$Preferred)

    if ($Preferred) {
        return $Preferred
    }

    $lines = & $AdbPath devices
    $emulators = @($lines -split "`n" | Where-Object { $_ -match "^emulator-\d+\s+device$" })
    if ($emulators.Count -eq 0) {
        throw "No running emulator detected. Start emulator first, then rerun."
    }

    return (($emulators[0] -split "\s+")[0]).Trim()
}

function Invoke-Adb {
    param(
        [string]$TargetDevice,
        [string[]]$CommandArgs,
        [switch]$ReturnOutput
    )

    for ($attempt = 0; $attempt -lt 4; $attempt++) {
        $output = @()
        if ($ReturnOutput) {
            $output = & $AdbPath -s $TargetDevice @CommandArgs
        }
        else {
            & $AdbPath -s $TargetDevice @CommandArgs | Out-Null
        }
        $exitCode = $LASTEXITCODE

        if ($exitCode -eq 0) {
            return $output
        }

        if ($attempt -lt 3) {
            & $AdbPath -s $TargetDevice wait-for-device | Out-Null
            Start-Sleep -Seconds 1
            continue
        }

        throw "adb failed (exit $exitCode): $($CommandArgs -join ' ')"
    }
}

function Capture-State {
    param(
        [string]$TargetDevice,
        [string]$RunDir,
        [string]$Label
    )

    $safeLabel = $Label -replace "[^A-Za-z0-9_-]", "_"
    $remotePng = "/sdcard/${safeLabel}.png"
    $remoteXml = "/sdcard/${safeLabel}.xml"
    $localPng = Join-Path $RunDir "${safeLabel}.png"
    $localXml = Join-Path $RunDir "${safeLabel}.xml"

    Invoke-Adb -TargetDevice $TargetDevice -CommandArgs @("shell", "screencap", "-p", $remotePng) | Out-Null
    Invoke-Adb -TargetDevice $TargetDevice -CommandArgs @("shell", "uiautomator", "dump", $remoteXml) | Out-Null
    Invoke-Adb -TargetDevice $TargetDevice -CommandArgs @("pull", $remotePng, $localPng) | Out-Null
    Invoke-Adb -TargetDevice $TargetDevice -CommandArgs @("pull", $remoteXml, $localXml) | Out-Null
    Invoke-Adb -TargetDevice $TargetDevice -CommandArgs @("shell", "rm", $remotePng, $remoteXml) | Out-Null

    return $localXml
}

function Parse-BoundsCenter {
    param([string]$Bounds)
    if ($Bounds -notmatch "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
        return $null
    }

    $x1 = [int]$Matches[1]
    $y1 = [int]$Matches[2]
    $x2 = [int]$Matches[3]
    $y2 = [int]$Matches[4]

    if (($x2 - $x1) -le 0 -or ($y2 - $y1) -le 0) {
        return $null
    }

    return [PSCustomObject]@{
        X = [int](($x1 + $x2) / 2)
        Y = [int](($y1 + $y2) / 2)
    }
}

function Find-NodeByLabel {
    param(
        [xml]$Doc,
        [string]$Label,
        [string]$PackageName
    )

    $nodes = $Doc.SelectNodes("//node")
    foreach ($node in $nodes) {
        $nodeText = $node.GetAttribute("text")
        $nodeDesc = $node.GetAttribute("content-desc")
        $nodePkg = $node.GetAttribute("package")
        $clickable = $node.GetAttribute("clickable")
        $focusable = $node.GetAttribute("focusable")
        $selected = $node.GetAttribute("selected")

        if (($nodeText -eq $Label -or $nodeDesc -eq $Label) -and $nodePkg -eq $PackageName -and ($clickable -eq "true" -or $focusable -eq "true" -or $selected -eq "true")) {
            return $node
        }
    }

    return $null
}

function UiContainsAnyText {
    param(
        [xml]$Doc,
        [string[]]$ExpectedTexts
    )

    $nodes = $Doc.SelectNodes("//node")
    foreach ($expected in $ExpectedTexts) {
        foreach ($node in $nodes) {
            if ($node.GetAttribute("text") -eq $expected -or $node.GetAttribute("content-desc") -eq $expected) {
                return $true
            }
        }
    }

    return $false
}

function Assert-AppForeground {
    param(
        [string]$TargetDevice,
        [string]$PackageName
    )

    $lastSignal = ""
    for ($i = 0; $i -lt 6; $i++) {
        $windowDump = Invoke-Adb -TargetDevice $TargetDevice -CommandArgs @("shell", "dumpsys", "window") -ReturnOutput
        $focusMatch = $windowDump | Select-String -Pattern "mCurrentFocus|mFocusedApp" | Select-Object -First 1
        $focusLine = if ($focusMatch) { $focusMatch.Line } else { "" }
        if ($focusLine -match [regex]::Escape($PackageName)) {
            return
        }

        $activityDump = Invoke-Adb -TargetDevice $TargetDevice -CommandArgs @("shell", "dumpsys", "activity", "activities") -ReturnOutput
        $resumedMatch = $activityDump | Select-String -Pattern "mResumedActivity|topResumedActivity" | Select-Object -First 1
        $resumedLine = if ($resumedMatch) { $resumedMatch.Line } else { "" }
        if ($resumedLine -match [regex]::Escape($PackageName)) {
            return
        }

        $lastSignal = "focus='$focusLine' resumed='$resumedLine'"
        Start-Sleep -Seconds 1
    }

    throw "App is not in foreground after retries. $lastSignal"
}

function Collect-LogcatTail {
    param(
        [string]$TargetDevice,
        [string]$RunDir
    )

    $logFile = Join-Path $RunDir "logcat_tail.txt"
    $logs = Invoke-Adb -TargetDevice $TargetDevice -CommandArgs @("logcat", "-d") -ReturnOutput
    $logs | Out-File -FilePath $logFile -Encoding utf8
    return $logFile
}

function Assert-NoFatalErrors {
    param(
        [string]$LogFile,
        [string]$PackageName
    )

    $lines = Get-Content -Path $LogFile
    $packagePattern = "Process:\s*$([regex]::Escape($PackageName))"

    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match $packagePattern) {
            $start = [Math]::Max(0, $i - 5)
            $window = $lines[$start..$i] -join "`n"
            if ($window -match "FATAL EXCEPTION|NoClassDefFoundError|InflateException|java\.lang\.RuntimeException") {
                throw "App crash signature detected in logcat. See $LogFile"
            }
        }
    }
}

if (-not (Test-Path $AdbPath)) {
    throw "adb not found at: $AdbPath"
}

if (-not (Test-Path $WorkflowFile)) {
    throw "Workflow file not found: $WorkflowFile"
}

$runDir = New-ArtifactDir
$summaryPath = Join-Path $runDir "summary.json"
Write-Step "Artifacts: $runDir"

$targetDevice = Resolve-DeviceId -Preferred $DeviceId
Write-Step "Using emulator device: $targetDevice"

$workflow = Get-Content -Raw -Path $WorkflowFile | ConvertFrom-Json
$tabs = @($workflow.tabs)
$overall = @($workflow.overallWorkflow)

if ($InstallFirst) {
    Write-Step "Installing latest build with Gradle task $GradleTask"
    Push-Location (Join-Path $PSScriptRoot "..\..")
    try {
        $gradleOutput = .\gradlew.bat $GradleTask 2>&1
        $gradleText = ($gradleOutput | Out-String)
        if ($gradleText -match "BUILD FAILED") {
            throw "Gradle task '$GradleTask' failed."
        }
    }
    finally {
        Pop-Location
    }
}

Write-Step "Clearing logcat and launching app"
Invoke-Adb -TargetDevice $targetDevice -CommandArgs @("logcat", "-c") | Out-Null
Invoke-Adb -TargetDevice $targetDevice -CommandArgs @("shell", "am", "force-stop", $AppId) | Out-Null
Invoke-Adb -TargetDevice $targetDevice -CommandArgs @("shell", "am", "start", "-n", "$AppId/$Activity") | Out-Null
Start-Sleep -Seconds 3
Capture-State -TargetDevice $targetDevice -RunDir $runDir -Label "00_launch" | Out-Null
Assert-AppForeground -TargetDevice $targetDevice -PackageName $AppId

$result = [ordered]@{
    timestamp = (Get-Date -Format s)
    appId = $AppId
    device = $targetDevice
    checks = [ordered]@{
        iconsVisibleClickable = @()
        tabWorkflows = @()
        overallWorkflow = @()
    }
    status = "PASS"
    reason = ""
}

try {
    Write-Step "Check 1/3: Bottom navigation icons visible + clickable"
    foreach ($tab in $tabs) {
        $xmlPath = Capture-State -TargetDevice $targetDevice -RunDir $runDir -Label ("icon_check_" + $tab.name)
        [xml]$doc = Get-Content -Raw -Path $xmlPath

        $node = Find-NodeByLabel -Doc $doc -Label $tab.navLabel -PackageName $AppId
        if (-not $node) {
            throw "Bottom nav item '$($tab.navLabel)' is missing, hidden, or non-clickable."
        }

        $point = Parse-BoundsCenter -Bounds $node.GetAttribute("bounds")
        if (-not $point) {
            throw "Bottom nav item '$($tab.navLabel)' has invalid bounds: $($node.GetAttribute('bounds'))"
        }

        $result.checks.iconsVisibleClickable += [ordered]@{
            tab = $tab.navLabel
            visible = $true
            clickable = $true
            bounds = $node.GetAttribute("bounds")
        }
    }

    Write-Step "Check 2/3: Per-tab workflows"
    foreach ($tab in $tabs) {
        $xmlPath = Capture-State -TargetDevice $targetDevice -RunDir $runDir -Label ("pre_tab_" + $tab.name)
        [xml]$doc = Get-Content -Raw -Path $xmlPath
        $node = Find-NodeByLabel -Doc $doc -Label $tab.navLabel -PackageName $AppId
        if (-not $node) {
            throw "Cannot tap tab '$($tab.navLabel)' during workflow run."
        }

        $point = Parse-BoundsCenter -Bounds $node.GetAttribute("bounds")
        if (-not $point) {
            throw "Cannot parse tap point for tab '$($tab.navLabel)'."
        }

        Invoke-Adb -TargetDevice $targetDevice -CommandArgs @("shell", "input", "tap", "$($point.X)", "$($point.Y)") | Out-Null
        Start-Sleep -Milliseconds 1600

        Assert-AppForeground -TargetDevice $targetDevice -PackageName $AppId

        $postXmlPath = Capture-State -TargetDevice $targetDevice -RunDir $runDir -Label ("tab_" + $tab.name)
        [xml]$postDoc = Get-Content -Raw -Path $postXmlPath

        $matched = UiContainsAnyText -Doc $postDoc -ExpectedTexts @($tab.expectedTexts)
        if (-not $matched) {
            throw "Tab '$($tab.navLabel)' opened but expected markers not found: $($tab.expectedTexts -join ', ')"
        }

        $result.checks.tabWorkflows += [ordered]@{
            tab = $tab.navLabel
            expectedMarkers = @($tab.expectedTexts)
            markerMatched = $true
            foregroundStable = $true
        }
    }

    Write-Step "Check 3/3: Overall workflow"
    foreach ($stepLabel in $overall) {
        $xmlPath = Capture-State -TargetDevice $targetDevice -RunDir $runDir -Label ("overall_pre_" + ($stepLabel -replace "\s+", "_"))
        [xml]$doc = Get-Content -Raw -Path $xmlPath
        $node = Find-NodeByLabel -Doc $doc -Label $stepLabel -PackageName $AppId
        if (-not $node) {
            throw "Overall workflow failed: missing step '$stepLabel'."
        }

        $point = Parse-BoundsCenter -Bounds $node.GetAttribute("bounds")
        if (-not $point) {
            throw "Overall workflow failed: invalid bounds for '$stepLabel'."
        }

        Invoke-Adb -TargetDevice $targetDevice -CommandArgs @("shell", "input", "tap", "$($point.X)", "$($point.Y)") | Out-Null
        Start-Sleep -Milliseconds 1100
        Assert-AppForeground -TargetDevice $targetDevice -PackageName $AppId

        $result.checks.overallWorkflow += [ordered]@{
            step = $stepLabel
            ok = $true
        }
    }

    $logFile = Collect-LogcatTail -TargetDevice $targetDevice -RunDir $runDir
    Assert-NoFatalErrors -LogFile $logFile -PackageName $AppId
}
catch {
    $result.status = "FAIL"
    $result.reason = $_.Exception.Message
}

$result | ConvertTo-Json -Depth 8 | Out-File -FilePath $summaryPath -Encoding utf8

if ($result.status -eq "PASS") {
    Write-Step "PASS - All icon/workflow checks succeeded."
    Write-Step "Summary: $summaryPath"
    exit 0
}

Write-Step "FAIL - $($result.reason)"
Write-Step "Summary: $summaryPath"
exit 1
