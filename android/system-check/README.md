# JUGAAD Phone Smoke Check

This module runs an automated on-device smoke check after install:

- launches JUGAAD
- auto-taps bottom tabs (Hub, Expenses, Meals, Recipes, Family, then back to Expenses)
- captures screenshot + UI XML dump after each step
- captures logcat and fails if fatal/runtime errors are found

## Run

From `C:\Projects\household-platform`:

```powershell
powershell -ExecutionPolicy Bypass -File .\android\system-check\run_phone_smoke.ps1
```

Install + test in one command:

```powershell
powershell -ExecutionPolicy Bypass -File .\android\system-check\run_phone_smoke.ps1 -InstallFirst
```
 
Windows shortcut launcher (installs first by default):

```bat
.\\android\\system-check\\run_phone_smoke.bat
```

## Output

Artifacts are written to:

`android/system-check/artifacts/smoke_YYYYMMDD_HHMMSS/`

Each run contains:

- `summary.txt`
- `logcat_tail.txt`
- screenshots: `00_launch.png`, `10_expenses.png`, etc.
- UI dumps: matching `.xml` files

## Notes

- Phone must be unlocked (script exits if keyguard/lockscreen is detected).
- If app is not installed and `-InstallFirst` is not provided, script writes a FAIL summary with the exact reason.
- Uses adb path: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` by default.
- You can override adb path and app/activity using script parameters.
