#!/bin/bash
# Sync wallet data from Expenses app to Android app resources
# Usage: ./sync_wallet_data.sh (from within the project directory)

set -e

EXPENSES_DIR="C:/Projects/apps/Expenses"
EXPORT_SCRIPT="$EXPENSES_DIR/export_wallet_data.py"
EXPORT_JSON="$EXPENSES_DIR/export/wallet_data.json"
APP_RAW_RES="C:/Projects/household-platform/android/src/main/res/raw/wallet_data.json"

echo "=== Wallet Data Sync ==="
echo ""

# Run export
echo "1. Exporting wallet data from Expenses app database..."
python "$EXPORT_SCRIPT"

if [ ! -f "$EXPORT_JSON" ]; then
    echo "❌ Export failed - JSON not found"
    exit 1
fi

# Copy to app resources
echo ""
echo "2. Copying to Android app resources..."
cp "$EXPORT_JSON" "$APP_RAW_RES"
echo "✓ Copied to $APP_RAW_RES"

# Show stats
echo ""
echo "3. Summary:"
STATS=$(python -c "
import json
with open('$EXPORT_JSON') as f:
    data = json.load(f)
    print(f'   Transactions: {len(data[\"transactions\"])}')
    print(f'   Trips: {len(data[\"trips\"])}')
    if data['transactions']:
        dates = [t['date'] for t in data['transactions']]
        print(f'   Date range: {min(dates)} to {max(dates)}')
")
echo "$STATS"

echo ""
echo "✓ Sync complete. Rebuild app with: ./gradlew :android:assembleDebug"
