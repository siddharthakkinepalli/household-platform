# household-platform — Agent Orientation

## On every new conversation — do this first, before anything else

1. Read `STATUS.md` — look at the **"Next action"** line and the bottom completed/pending list
2. Read `FGRAPH.md` — Feature Status table shows what's live vs planned
3. Proceed with the next pending items **without asking** — launch parallel agents (Wave pattern)

Do NOT re-scan the codebase from scratch. The three files above are always up to date.

---

## Project identity

Privacy-first Android household finance + document vault app.
- **Language:** Kotlin + Jetpack Compose (Material 3)
- **DB:** Room v21 — `data/AppDatabase.kt`
- **Build:** `.\gradlew.bat :android:assembleDebug` (Windows; exit 1 even on success — check APK)
- **APK:** `android/build/outputs/apk/debug/android-arm64-v8a-debug.apk`

---

## How this project is run

- Work in **waves** of 2–3 parallel agents on non-overlapping files
- Update `STATUS.md` and `PLATFORM.md` after every agent completes (not at the end)
- Commit and push after every wave: `git add -A && git commit && git push origin main`
- **No stubs** — every class must have real logic; if external data needed (model file, API key), implement the code fully and tell the user what to provide

---

## Key file locations

| What | Where |
|------|-------|
| All screens | `android/src/main/java/com/household/app/ui/v2/` |
| All ViewModels | `android/src/main/java/com/household/app/ui/viewmodels/` |
| Room DB + migrations | `android/src/main/java/com/household/app/data/AppDatabase.kt` |
| All DAOs | `android/src/main/java/com/household/app/data/dao/` |
| WorkManager workers | `android/src/main/java/com/household/app/vault/workers/` |
| Pipeline trigger map | `android/src/main/java/com/household/app/pipeline/PipelineManager.kt` |
| OCR stack | `android/src/main/java/com/household/app/vault/scan/` |
| Document parsers | `android/src/main/java/com/household/app/vault/classification/parsers/` |
| Navigation routes | `android/src/main/java/com/household/app/ui/compose/navigation/Screen.kt` |
| Nav graph | `android/src/main/java/com/household/app/ui/v2/V2AppNavHost.kt` |

---

## Color tokens (use these — don't hardcode)

`LumePurple` · `LumeEmerald` · `LumeAmber` · `LumeCyan` · `CriticalRed`  
`TextMain` · `TextSecondary` · `TextMuted`

Glass card pattern: `EliteGlassCard(glowColor = LumeXxx.copy(alpha = 0.12f))`

---

## Next pending work (Wave 5)

| Item | Description |
|------|-------------|
| **F1** | Tax Tagging Layer — long-press tx/doc → tag with tax category. `tax_tags` table already in DB. |
| **F2** | Annual Tax Summary Export — group tagged items → PDF/CSV. `TaxSummaryScreen.kt` exists, needs VM. |
| **F3** | Receipt ↔ Wallet Linking UI — `linkedVaultEntryId` already populated; show 📄 icon in transaction list. |
| **E2** | SteuerKlar Drive sync — write `TAXATION_COMPLETE.json` to Drive on "Mark Complete". |

See `STATUS.md` for full details and `FGRAPH.md` for architecture.
