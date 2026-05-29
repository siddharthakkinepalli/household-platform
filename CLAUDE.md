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

## Theme system (active — do not re-implement)

Dynamic theme engine is fully wired. Key files:

| What | Where |
|------|-------|
| Enum + 6 ColorSchemes | `ui/compose/theme/Theme.kt` — `JugaadThemeSelection` |
| DataStore persistence | `ui/compose/theme/ThemePreferencesManager.kt` |
| ViewModel | `ui/compose/theme/ThemeViewModel.kt` + `ThemeViewModelFactory.kt` |
| Picker UI | `ui/compose/theme/ThemeSelector.kt` — rendered inside `AppThemeCard` in ConfigHub |
| Root wiring | `MainActivity.kt` — `viewModels { ThemeViewModelFactory }` → `JugaadTheme(selectedTheme)` wraps `setContent` |

**6 themes:** LUMINESCENT_GLASS (default) · JUGAAD_CHILLI · NORDIC_EINKAUF · MATRIX_PIPELINE · MONSOON_FOREST · TWILIGHT_CASHMERE

New color tokens: `TextMutedDark` (70% white) · `TextSecondaryDark` (80% white) — use these on dark glass cards.  
New card: `JugaadGlassCard` — strict 40% surface alpha, for uniform card backgrounds without glow effects.  
New util: `String.capitalizeWords()` in `domain/utils/StringExtensions.kt`.

---

## Wave 6 — next work (3 parallel agents, launch immediately)

| Agent | Task | What it delivers |
|-------|------|-----------------|
| **E2 — SteuerKlar Drive write** | `SteuerKlarScreen.kt` | "Mark Complete" writes `TAXATION_COMPLETE.json` to Google Drive with year + checklist summary + completedAt timestamp |
| **F3 — Receipt↔Wallet 📄 icon** | `V2FinanceScreen.kt` transaction list | Show 📄 icon on rows where `linkedVaultEntryId` is set; tap opens vault entry detail. `ReceiptMatchingWorker` already populates the link — UI layer only. |
| **F1 — Tax Tagging Layer** | `V2FinanceScreen.kt` + `V2DocumentVaultScreen.kt` | Long-press tx/vault doc → "Mark as tax-relevant" sheet. New `tax_tags` table (DB v21→v22). German categories: Work Equipment, Home Office, Medical, Donations, Work Commute. Show 💶 badge on tagged items. |

**Wave 7 after Wave 6:** F2 Annual Tax Summary Export · E2 full Drive scan + live checklist status

See `STATUS.md` for full context.
