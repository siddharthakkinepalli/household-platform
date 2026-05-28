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

## Product principles (JUGAAD OS — active from 2026-05-28)

This is a **Germany-first** household OS. No UPI, no India payments. SEPA/CSV is the payment layer.

**Automation over interaction:** If vault extracts structured data, the system must react automatically.  
**No vibe-coded features:** Every screen must complete a real task, not just display information.  
**Alert deeplinks are mandatory:** Alerts without resolution paths are noise, not UX.  
**No half-built tabs:** A broken nav tab destroys trust. Build it or remove it.

---

## Wave 5 — next work (3 parallel agents, launch immediately)

| Agent | Task | What it fixes |
|-------|------|--------------|
| **M — Vault automation bridge** | `VaultDocumentParserWorker.kt` post-extraction hook | `MONTHLY_COST` from Mietvertrag/contract → auto-propose `RecurringBillEntity`. `GROSS_SALARY` from Arbeitsvertrag → pre-seed salary expectation in `SalarySourceEntity`. Extended expiry alerts at -90d and -180d for IDENTITY docs (passports). |
| **N — Smart Alert deeplinks** | `V2FinanceScreen.kt` SmartAlertFeed | Every SmartAlert chip must navigate somewhere on tap: "uncategorized" → import review, "expiry" → DocumentsScreen, "new subscription" → SubscriptionHub, "tax total" → TaxSummary. Read the existing alert types in `ExpensesViewModel.computeSmartAlerts()` first. |
| **O — Meals module decision** | `V2MealsScreen.kt` | Read the file. If ViewModel is empty/stub: **remove Meals from the nav rail** (`Screen.kt` + `V2AppNavHost.kt`) and repurpose that slot OR build a minimal real flow (weekly plan → auto-generate shopping list from pantry stock). No half-built tabs. |

**Wave 6 after Wave 5:** E2 SteuerKlar Drive write · F3 Receipt↔Wallet 📄 icon · F1 Tax Tagging

See `STATUS.md` audit findings section for full context on what's broken and why.
