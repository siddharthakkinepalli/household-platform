#!/usr/bin/env powershell
<#
.SYNOPSIS
Sync wallet data from Expenses app to Android app resources.

.DESCRIPTION
Exports transaction and trip data from the Expenses app SQLite database
and copies it to the Android app's raw resources.

.EXAMPLE
.\sync_wallet_data.ps1
#>

param()

$ErrorActionPreference = "Stop"

$expensesDir = "C:\Projects\apps\Expenses"
$exportScript = "$expensesDir\export_wallet_data.py"
$exportJson = "$expensesDir\export\wallet_data.json"
$appRawRes = "C:\Projects\household-platform\android\src\main\res\raw\wallet_data.json"

Write-Host "=== Wallet Data Sync ===" -ForegroundColor Green
Write-Host ""

# Run export
Write-Host "1. Exporting wallet data from Expenses app database..." -ForegroundColor Cyan
python $exportScript

if (-not (Test-Path $exportJson)) {
    Write-Host "❌ Export failed - JSON not found" -ForegroundColor Red
    exit 1
}

# Copy to app resources
Write-Host ""
Write-Host "2. Copying to Android app resources..." -ForegroundColor Cyan
Copy-Item -Path $exportJson -Destination $appRawRes -Force
Write-Host "✓ Copied to $appRawRes" -ForegroundColor Green

# Show stats
Write-Host ""
Write-Host "3. Summary:" -ForegroundColor Cyan

$jsonContent = Get-Content $exportJson | ConvertFrom-Json
$txCount = $jsonContent.transactions.Count
$tripCount = $jsonContent.trips.Count

Write-Host "   Transactions: $txCount" -ForegroundColor White
Write-Host "   Trips: $tripCount" -ForegroundColor White

if ($txCount -gt 0) {
    $dates = $jsonContent.transactions.date | Sort-Object
    $dateRange = "$($dates[0]) to $($dates[-1])"
    Write-Host "   Date range: $dateRange" -ForegroundColor White
}

Write-Host ""
Write-Host "✓ Sync complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Rebuild app: cd household-platform && .\gradlew :android:assembleDebug" -ForegroundColor Gray
Write-Host "  2. Deploy app: .\gradlew :android:installDebug" -ForegroundColor Gray
Write-Host "  3. Test Wallet tab to see your synced transactions" -ForegroundColor Gray
