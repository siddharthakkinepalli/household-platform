# Household Platform - Next Session State

## Canonical Resume File

Use `LATEST_STATUS.md` in this folder as the primary handoff and resume source.
This file is retained for historical context.

Last updated: 2026-04-28

## What exists now (verified)

### 1) Single-app structure (Android)
- App is now one unified hub with tabs:
  - Hub
  - Expenses
  - Meals
  - Docs
  - Family
- Navigation files are configured and compiling.

Key files:
- android/src/main/res/layout/activity_main.xml
- android/src/main/res/menu/bottom_nav_menu.xml
- android/src/main/res/navigation/nav_graph.xml
- android/src/main/java/com/household/app/ui/fragments/HomeFragment.kt
- android/src/main/java/com/household/app/ui/fragments/ExpensesFragment.kt
- android/src/main/java/com/household/app/ui/fragments/MealsFragment.kt
- android/src/main/java/com/household/app/ui/fragments/DocumentsFragment.kt
- android/src/main/java/com/household/app/ui/fragments/FamilyFragment.kt

### 2) Hub command-center UX
- Hub includes:
  - Today
  - Alerts
  - Quick Capture (Scan / Speak / Parse Inbox)
  - Auto Integrations
  - Unified Timeline

Key files:
- android/src/main/res/layout/fragment_home.xml
- android/src/main/java/com/household/app/ui/fragments/HomeFragment.kt

### 3) Real backend pipeline (Flask)
- Pipeline scaffold implemented and tested:
  - extract -> normalize -> event graph -> automation actions
- New endpoints live in backend:
  - POST /pipeline/process_text
  - POST /pipeline/process_scan_image (reuses existing Grocery OCR backend at :5000)
  - GET /timeline/today
  - GET /automation/logs
- Existing household/expenses/backup endpoints remain.

Key files:
- backend/main.py
- backend/ai_pipeline.py
- backend/household_models.py

### 4) Data model additions
- Added DB entities:
  - household_events
  - automation_logs
- Existing entities still in use:
  - household_profiles
  - household_members
  - household_expenses
  - household_backups

### 5) Theme + design assets
- Applied calm productivity palette (blue/green/light background) in Android resources.
- Figma JSON saved for import.
- Browser preview updated for single-app command-center flow.

Key files:
- android/src/main/res/values/colors.xml
- android/src/main/res/values/themes.xml
- design/household-hub-figma.json
- single-app-preview.html

## Runtime state and ports

### Active service convention
- New household backend: http://127.0.0.1:5001
- Reused OCR backend from Grocery scanner: http://127.0.0.1:5000
- Legacy expenses web app: http://127.0.0.1:8000

### Why confusion happened before
- Running python main.py from C:\Projects fails because script path is wrong.
- Old backend run on 5000 lacked new pipeline endpoints.
- Correct command should use explicit script path in household-platform/backend.

## Verified checks

- Android Kotlin compile: BUILD SUCCESSFUL for :android:compileDebugKotlin.
- Backend syntax check passed using --check.
- OCR dependency check: Grocery OCR backend currently down on :5000 and must be started for image scan flow.
- Pipeline smoke test passed:
  - create household
  - process text via /pipeline/process_text
  - read timeline via /timeline/today
  - read automation entries via /automation/logs

## What still needs to be done

### Priority 1 (next coding session)
1. Wire real image upload + OCR path for Scan action:
  - Backend endpoint for image file intake is now added: /pipeline/process_scan_image
  - Endpoint reuses existing Grocery scanner OCR backend (/ocr on :5000)
  - Remaining: connect Android image picker/camera flow to this endpoint

2. Wire real voice capture in Android:
   - Replace sample text with speech-to-text input
   - Send recognized text to /pipeline/process_text

3. Add real timeline screen/tab behavior:
   - Move timeline list from Hub-only labels to full list UI
   - Pull from /timeline/today endpoint

### Priority 2
4. Family roles and visibility:
   - parent/child/guest role model
   - role-based visibility rules in API responses

5. Automation execution engine:
   - currently actions are logged as queued
   - implement execution service for reminders/tasks

### Priority 3
6. Integrations hardening:
   - Google Calendar connector
   - conflict handling and dedup for repeated extracted events

## Run commands for next time

### Backend (pipeline-enabled)
- C:/Projects/.venv/Scripts/python.exe c:/Projects/household-platform/backend/main.py --port 5001

### Reused OCR backend (from Grocery scanner)
- C:/Projects/.venv/Scripts/python.exe c:/Projects/apps/Grocery/ocr-backend/server.py

### Android compile check
- cd c:/Projects/household-platform
- .\gradlew.bat :android:compileDebugKotlin

### Legacy expenses app (if needed)
- C:/Projects/.venv/Scripts/python.exe c:/Projects/apps/Expenses/local_server.py

## Resume pointer

Do not build a new OCR stack. Reuse Grocery OCR via /pipeline/process_scan_image, then wire Android Scan button to upload an image file to that endpoint. Keep voice capture as the next item.
