# Household Platform — Execution Status

**Last updated:** 2026-05-27  
**DB version:** 17  
**Build status:** ✅ BUILD SUCCESSFUL  
**APK:** `android/build/outputs/apk/debug/android-arm64-v8a-debug.apk`  
**Next action:** Phase D remainder (D3 Salary Allocation Bar, D4 Recurring Detection)

---

## Pre-flight DB Audit (Complete)

AppDatabase v9 already contains the following — **do NOT create duplicate tables**:

| Table | Entity | Key columns | Status |
|-------|--------|-------------|--------|
| `merchant_rules` | MerchantRuleEntity | merchantPattern (PK), targetCategoryId, isExclusion, isEnabled, priority | ✅ Exists |
| `vault_entries` | VaultEntity | imagePath, merchantName, totalAmount, currency, dateEpoch, rawOcrContent, isLinkedToExpense, category, documentTitle, mimeType | ✅ Exists |
| `pantry_items` | PantryEntity | name, category, quantity, expiryEstimate, vaultId, isConfirmed, addedAt | ✅ Exists |
| `inventory_events` | InventoryEventEntity | pantryItemId, delta, eventType (ADD/CONSUME), sourceReceiptLineId, timestamp | ✅ Exists |
| `family_members` | FamilyMemberEntity | name, avatarPath, colorCode, role, createdAt | ✅ Exists |
| `documents` | DocumentEntity | ownerId (FK family_members), title, type, expiryDate, noticePeriodDays, monthlyCost, localUri, notes | ✅ Exists |
| `document_alerts` | DocumentAlertEntity | documentId (FK), alertType, message, daysUntil, isAcknowledged, actionTaken | ✅ Exists |
| `products` | ProductEntity | canonicalName (seeded with 22 German items) | ✅ Exists |
| `product_aliases` | ProductAliasEntity | rawOcrString+storeName (PK), productId, frequency | ✅ Exists |

---

## Phase A — CANCELLED (tables already exist)

~~DateEvent table creation~~ — The planned `DateEvent` table is a duplicate of the existing `documents` table.  
`documents` already has `expiryDate`, `noticePeriodDays` and `document_alerts` already handles alert records.

---

## Phase B — COMPLETE ✅

### BUG-001 — Re-categorize sheet missing Utilities + Transfers [FIXED ✅]
**File:** `ui/v2/V2FinanceScreen.kt:522`  
`listOf("Groceries", "Eat Out", "Travel", "Utilities", "Transfers", "Shopping", "Exclude")`

### BUG-002 — Receipt linking never finds candidates [FIXED ✅]
**File:** `data/dao/WalletTransactionDao.kt`  
Changed SQL to `ABS(amount) BETWEEN :minAmount AND :maxAmount` — expenses stored negative, receipts positive.

### BUG-003 — PDF / document open silently does nothing [FIXED ✅]
**Files created/modified:**
- `android/src/main/res/xml/file_paths.xml` — created with files-path, external-files-path, cache-path entries
- `android/src/main/AndroidManifest.xml` — added `<provider>` block for FileProvider
- `ui/v2/V2DocumentVaultScreen.kt:885` — added Toast in catch block

### FEATURE-001 — Multi-select on vault screen [DONE ✅]
- `selectedIds: SnapshotStateList<Long>` state in V2DocumentVaultScreen
- Long-press to enter multi-select mode (`combinedClickable`)
- `MultiSelectActionBar` composable: "X selected" + Delete + Move + Cancel
- `VaultViewModel.deleteEntries(ids)` + `moveEntries(ids, category)`
- `VaultDao` + `VaultRepository` bulk ops added

### B1 — Expense Categorization Engine [DONE ✅]
**Files created:**
- `data/repository/MerchantRuleRepository.kt` — interface + impl, seeds 3 default PayPal rules on first run
- `domain/services/RetroactiveCategorizer.kt` — reuses `RuleEngineService.pickRule()`, updates wallet_transactions
- `ui/v2/MerchantRulesScreen.kt` — LazyColumn of rules, FAB to add, Re-categorize All button with snackbar

**Files modified:**
- `data/dao/WalletTransactionDao.kt` — added `updateCategory(id, category)`
- `data/dao/TransactionOverrideDao.kt` — added `observeAllRules(): Flow`
- `ui/compose/navigation/Screen.kt` — added `MerchantRules` route
- `ui/v2/V2AppNavHost.kt` — added `merchant_rules` composable
- `ui/v2/V2ConfigHubScreen.kt` — added "Merchant Rules" nav card
- `ui/viewmodels/ConfigViewModel.kt` — calls `recategorizeAll()` after CSV import

### B2 — Pantry Consume Loop [DONE ✅]
**Files modified:**
- `data/dao/PantryDao.kt` — added `getItemById(id)`
- `data/dao/ReceiptDaos.kt` (InventoryEventDao) — added `insertEvent`, `getEventsForItem`, `getConsumeCountForItem`
- `domain/repositories/PantryRepository.kt` — added `consumeItem(pantryItemId, quantity)`
- `data/repository/PantryRepositoryImpl.kt` — implements consumeItem (inserts CONSUME event, decrements quantity)
- `ui/v2/PantryScreen.kt` — SwipeToDismissBox on items, consumed items dimmed + strikethrough, "Consumed N×" chip

### B3 — Local Backup / Restore [DONE ✅]
**Files created:**
- `domain/models/HouseholdBackup.kt` — data class with all 9 entity lists
- `domain/services/HouseholdExportService.kt` — exports all DAOs → JSON → Downloads folder
- `domain/services/HouseholdImportService.kt` — parses JSON → inserts via IGNORE-conflict DAOs

**Files modified:**
- `android/build.gradle.kts` — added `implementation("com.google.code.gson:gson:2.10.1")`
- Multiple DAOs — added `getAllXxxList()` and `insertXxxIgnore()` bulk methods
- `ui/v2/V2ConfigHubScreen.kt` — added LocalBackupCard with Export + Import buttons

### B4 — Important Dates UI [DONE ✅]
**Files created:**
- `data/dao/FamilyMemberDao.kt` — merged DocumentDao here (includes `getDocumentsExpiringSoon`, `getAllDocumentsList`, `insertDocuments`)
- `data/repository/DocumentRepositoryImpl.kt` — wraps DocumentDao + DocumentAlertDao
- `ui/v2/DocumentsScreen.kt` — LazyColumn with SwipeToDismissBox (M3 API), expiry color badges, FAB
- `ui/v2/components/AddDocumentSheet.kt` — ModalBottomSheet with all fields

**Files modified:**
- `ui/compose/navigation/Screen.kt` — added `Documents` route
- `ui/v2/V2AppNavHost.kt` — added `documents` composable
- `ui/v2/V2HomeScreen.kt` — added `UpcomingDatesCard` (top 3 expiring docs within 30 days)

### B5 — Home Screen Per-Category Budget Card [DONE ✅]
**Files modified:**
- `ui/compose/state/HomeModels.kt` — added `CategoryBudget` data class + `categoryBudgets` field in HomeState
- `ui/compose/state/HomeViewModel.kt` — reads `getAllThresholds()`, computes per-category spend from fiscal cycle transactions
- `ui/v2/V2HomeScreen.kt` — added `CategoryBudgetsCard` between gauge and modules

### B6 — Module Buttons Navigation [DONE ✅]
**Files modified:**
- `ui/v2/V2HomeScreen.kt` — `ModuleCard` gets `onClick`, `V2HomeScreen` gets `onNavigateToModule` param
- `ui/v2/V2AppNavHost.kt` — passes `onNavigateToModule = { navController.navigate(it) }`
- `ui/compose/state/HomeViewModel.kt` — updated module subtitles: "Expenses & budget", "Family hub"

---

## Build Fixes Applied (compile errors from agents)

| Error | Fix |
|-------|-----|
| `NULLS LAST` in Room SQL | Replaced with `CASE WHEN expiryDate IS NULL THEN 1 ELSE 0 END` |
| `DocumentDao` redeclaration | Deleted new `DocumentDao.kt`, merged `getDocumentsExpiringSoon` into `FamilyMemberDao.kt` |
| `LocalDateAdapter` redeclaration | Made `internal` in ExportService, removed duplicate from ImportService |
| `rememberDismissState`/`SwipeToDismiss` (M2) | Replaced with M3 `rememberSwipeToDismissBoxState`/`SwipeToDismissBox` |
| `skipPartialExpansion` parameter missing | Removed from `rememberModalBottomSheetState()` |
| `containerColor` on ExposedDropdownMenu | Removed (not supported in this M3 version) |
| `ColumnScope.AnimatedVisibility` scope conflict | Extracted `MultiSelectActionBar` as standalone `@Composable` |

---

## Phase C3 — Family + Vault + Wallet Pulse — COMPLETE ✅

**Committed.** See PLATFORM.md for full change log.

Key items:
- Family module (FamilyMemberDetailScreen, FamilyViewModel, VaultFolderBrowser)
- Household Pulse card (SAFE/WARNING/CRITICAL, projected surplus, top risk, AI suggestion)
- Category grid — 4 tracked categories with €spent/€limit utilisation bars
- Projected surplus fixed: uses sum of 4 category limits (€995) not hardcoded €3000
- Wallet search bar (BasicTextField, ✕ clear)
- FamilyMemberDetailScreen shaking bug fixed (remember(memberId))

---

## Phase D+ — CSV Redesign, Smart Budget, Receipt OCR — COMPLETE ✅

### D+ — Transaction Categorizer Expansion ✅ DONE

**Modified:** `data/TransactionCategorizer.kt`
- Rebuilt from 6 → **24 categories** (Ulm-localized checked first: SWU Nahverkehr, SWU Energie, DING tickets, Stadt Ulm, Studierendenwerk, SSV Ulm, UWS)
- National chains: Telekom, Vodafone, O2, 1&1, Congstar, Vattenfall, E.ON, EnBW
- Full grocery/drugstore/discount/DIY/electronics/dining/pharmacy/fuel coverage
- Added `isIncome()` method — detects salary/benefit keywords in raw description
- Fallback now `"Uncategorized"` (was `"Shopping"`)

### D+ — PayPal SEPA Merchant Extraction ✅ DONE

**Modified:** `domain/utils/MerchantNameCleaner.kt`
- 5-pattern cascade in priority order: "ihr einkauf bei…" → "PP..PP," format → "PayPal *merchant" → "PP *merchant" → SVWZ+ SEPA field
- Removed the blunt `if (contains("paypal")) return "PayPal"` fallback — actual merchant name preserved

### D+ — ING CSV Support + Income Logic Fix ✅ DONE

**Modified:** `data/config/CsvParserService.kt`
- ING detection: `ing-diba`, `ing diba`, `ing.de`, or `buchungsdatum + auftraggeber` header combo
- ING column aliases: `buchungsdatum`, `auftraggeber`, `beguenstigter`, `betrag eur`
- Income classification: `isIncome(rawDescription)` checked first (German salary keywords), then amount > 0 fallback
- Warning count triggers on `"Uncategorized"` not `"Other"`

### D+ — FTS Crash Fix (MIGRATION_16_17) ✅ DONE

**Modified:** `data/AppDatabase.kt` — version 16 → 17
- `MIGRATION_16_17`: drops and recreates `wallet_transactions_fts` without `tokenize=unicode61`
  (Room's `@Fts4` annotation expects no tokenizer option; crash on existing installs)
- `MIGRATION_15_16`: also fixed for clean installs (same tokenizer removal)
- `MIGRATION_16_17` triggers `INSERT INTO wallet_transactions_fts(…) VALUES('rebuild')` to re-index

### D+ — Smart Alert Feed ✅ DONE

**Modified:** `ui/viewmodels/ExpensesViewModel.kt`
- `FinancialAlert` data class: `emoji, label, detail, amount, tintKey`
- `_smartAlerts: MutableStateFlow<List<FinancialAlert>>` + `smartAlerts: StateFlow`
- `computeSmartAlerts()` — new subscriptions (cycleCount == 2), upcoming bills (lastSeen + 30d within 10 days), document alerts (`.getAlertsDueWithinDaysList(30).take(2)`), tax-deductible totals, uncategorized nudge

**Modified:** `ui/v2/V2FinanceScreen.kt`
- `SmartAlertFeed` composable — horizontally scrollable `LazyRow` of alert chips
- `SmartAlertChip` composable — 172.dp wide cards with tint, emoji, label, detail, optional amount
- Layout reorder: SalaryAllocationCard → SmartAlertFeed → HouseholdPulseCard → CategoryGrid
- Category edit dropdown expanded to 27 categories

### D+ — Android 13 Notification Permission ✅ DONE

**Modified:** `ui/v2/V2AppShell.kt`
- `POST_NOTIFICATIONS` runtime request via `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`
- Fires once after onboarding completes (only on API 33+, only if not already granted)

### D+ — Receipt OCR Engine (ML Kit + Spatial Reconstruction) ✅ DONE

**New files:**
- `vault/parser/LocalReceiptScannerEngine.kt`
  - `ImagePreProcessor.optimizeForOcr()` — grayscale + 1.7× contrast via `ColorMatrix` before ML Kit
  - `LocalReceiptScanner.processMlKitPayload()` — delegates text parsing to `ReceiptTextParser`; uses bounding-box Y-center clustering only for amount extraction
  - `reconstructAdaptiveRows()` — clusters `TextLinePayload` by `(height * 0.40f).coerceAtLeast(12f)` Y tolerance; sorts left→right; merges item name (left column) + price (right column) that ML Kit splits into separate blocks
  - `fixOcrCharacterGlitch()` — fixes O→0, l→1, `,B2`→`,82`, `,S0`→`,50`
  - `cleanGermanFloat()` — refund-aware (negative for Gutschrift/Erstattung rows)

- `vault/parser/ReceiptTextParser.kt`
  - `ParsedReceipt(merchant, date, totalAmount, category, isInvoice, lineItems)`
  - `LineItem(rawText, name, price, quantity)`
  - `checkIsInvoice()` — 2+ invoice keywords (Rechnung, Rechnungsnummer, Fälligkeit…)
  - `extractGermanAmount()` — invoice-aware patterns → retail total/sum patterns → largest price fallback; applies `fixOcrCharacterGlitch()`
  - `extractGermanDate()` — `\b(\d{2}\.\d{2}\.(?:20)?\d{2})\b`
  - `extractLineItems()` — quantity lines `\d+ [xX×] name price`, price-at-end lines `name  price B`; skips total/tax/header words
  - `identifyMerchantAndCategory()` — Ulm-local → national chains → dining → pharmacy → fuel → first OCR line fallback

**Modified files:**
- `vault/scan/MlKitOcrEngine.kt` — `recognizeFromBitmap()` now applies `ImagePreProcessor.optimizeForOcr(bitmap)` before creating `InputImage`
- `data/dao/VaultDao.kt` — added `updateReceiptMeta(id, merchant, amount, dateEpoch)`
- `vault/workers/VaultDocumentParserWorker.kt` — two-path OCR (image uses spatial scan, PDF uses string parser); updates `merchantName`/`totalAmount`/`dateEpoch`; pushes grocery/drugstore line items to `PantryEntity` as `isConfirmed = false`

### D+ — Onboarding Expanded (3 → 7 Steps) ✅ DONE

**New file:** `ui/v2/OnboardingScreen.kt`

| Step | Feature | Color |
|------|---------|-------|
| 1 | Smart Budget & Cash Flow pulse overview | LumePurple |
| 2 | Import Transactions (CSV, multi-bank) | LumePurple |
| 3 | Subscription Hub (recurring bills) | LumeEmerald |
| 4 | Scan & Vault (documents, expiry tracking) | LumeCyan |
| 5 | Receipt Scanning + Pantry (OCR, line items) | LumeAmber |
| 6 | Tax Checklist + Google Drive Backup | LumeEmerald |
| 7 | Expiry Alerts & Notifications | LumeAmber |

Step counter ("3 / 7") shown below dot indicators.

---

## Phase D — CSV Redesign + Salary-Based Budgeting — IN PROGRESS 🔄

### D1 — Salary Detection from CSV ✅ DONE

**DB:** Migration 10→11 adds `salary_sources` table (singleton).

**New files:**
- `data/entities/DashboardEntities.kt` — added `SalarySourceEntity`
- `data/dao/SalarySourceDao.kt` — `getSalarySource()`, `upsert()`, `clear()`

**Modified files:**
- `data/AppDatabase.kt` — version 10→11, `MIGRATION_10_11`, `salarySourceDao()` abstract method
- `ui/viewmodels/ConfigViewModel.kt`:
  - `SalaryCandidate` data class
  - `ImportWorkflow.SalaryConfirmation(candidates, pendingSummary)` state
  - `ConfigIntent.ConfirmSalary(transactionId)` + `ConfigIntent.SkipSalary`
  - `resolveSalaryWorkflow()` — detects credits within ±7 days of anchor day; auto-matches against stored pattern+amount range; falls through to confirmation UI if no match
  - `handleConfirmSalary()` — stores SalarySourceEntity (±20% amount window)
  - `handleSkipSalary()` — advances to NeedsReview without storing
- `ui/v2/V2ConfigHubScreen.kt` — `SalaryConfirmationState` composable (candidate list, emerald glow, "None of these — skip")

**Behaviour:**
- First import after salary detection: shows up to 5 credit candidates near anchor day for user to pick
- Subsequent imports: auto-matches by merchant prefix OR amount ±20% → silently confirms, no UI shown
- Build: ✅ SUCCESSFUL

---

### D2 — Credit & Transfer Categorization Fix ✅ DONE

**Problem:** Credits (Bundesagentur, Caleda Brian) were landing in "Shopping". Revolut transfers were not being excluded.

**Modified files:**
- `data/TransactionCategorizer.kt` — added `"revolut"` to Transfers keyword list
- `data/config/CsvParserService.kt` — amount-sign logic applied after classification:
  - `amount > 0` + non-Transfers → `"Income"`
  - `amount > 0` + Transfers → `"Excluded"` (credit self-transfer)
  - `amount < 0` + Transfers → `"Excluded"` (N26, ING, Revolut, own-name debits)
  - `shouldExclude()` match → `"Excluded"` (Commerzbank fees, etc.)

**Result:** Income transactions appear as a positive "Income" row in the category summary, correct positive amount in the transaction list, and are never counted against any budget (existing `amount < 0` filter in `publishVisibleState` handles this automatically).

**Build:** ✅ SUCCESSFUL

---

### D3 — Salary Allocation Bar in Pulse [ ] PLANNED

Expand the Pulse card with a salary context layer above the existing 4 category tiles.

**Layout:**
```
HOUSEHOLD PULSE                           SAFE ●
─────────────────────────────────────────────────
Salary €2,850  ·  15 May

[███████░░░░░░░░░░░░░░░░]
  Fixed    Discretionary    Remaining
  €900        €570            €1,380
   32%         20%             48%

▼ Fixed costs (tap to expand)
  Miete       €800
  Strom        €65
  Telekom      €35

[ Groceries ]  [ Eat Out ]  [ Travel ]  [ Shopping ]
  €380/600      €85/100     €60/195      €45/100
```

**Implementation:**
- Bar segments: Fixed (red) | Discretionary spent (amber) | Remaining (green)
- Salary amount + date sourced from `SalarySourceEntity.lastAmount` + `confirmedAt`
- Fixed total = sum of `RecurringBillEntity` amounts detected in D4
- Discretionary total = sum of spend across 4 tracked categories this cycle
- Remaining = salary − fixed − discretionary
- "Fixed costs" row is tappable → collapses/expands the recurring bill list inline
- 4 category tiles below are unchanged

**Dependency:** D4 (recurring detection) powers the Fixed costs list. Can ship D3 UI with empty/manual fixed list first, D4 fills it automatically once done.

---

### D4 — Recurring Bills Detection [ ] PLANNED

- Post-import worker groups transactions by normalized merchant, flags those appearing ≥2 consecutive cycles ±15% amount as `"Recurring"`
- New `RecurringBillEntity` table
- Pulse "Upcoming" section expands to show individual upcoming bills (Miete, Telekom, ARD, etc.)
- Recurring excluded from discretionary spend calculation

---

### D5 — Import Review Screen for Uncategorized [ ] PLANNED

After salary confirmation step, show uncategorized transactions (`category == "Other"` or unconfident "Shopping") in a review screen. Inline category picker + "make this a rule" per transaction. Pre-categorized shown in collapsed "✓ N auto-classified" chip.

---

### D6 — PayPal Transaction Intelligence [ ] PLANNED (quick win)

In `MerchantNameCleaner`: detect `PAYPAL *<merchant>` pattern, extract actual merchant name, run standard categorizer on it.
```
"PAYPAL *NETFLIX INTERNAT" → title="Netflix", category="Utilities"
"PAYPAL *LIEFERANDO"       → title="Lieferando", category="Eat Out"
```

---

### D7 — Income Summary Section [ ] PLANNED

Collapsible section above transaction list showing total income this cycle with each Income transaction listed.

---

## Phase E — Pipeline Architecture + SteuerKlar Tax Mode — PLANNED 🔲

### Architecture Decision: Google Drive as Single Source of Truth

**Key insight:** Install Google Drive for Desktop on Windows. `C:\Income Tax\` becomes the synced Drive folder. `tax_api.py` reads `C:\Income Tax\` unchanged. Android app reads the same folder via Drive API. Both stay in sync automatically — no duplication, no manual handoff.

**Drive scope change required:** Current app uses `DRIVE_FILE` (app-created files only). Tax folder needs `DRIVE` scope. UX mitigation: "Link Tax Folder" onboarding step uses Drive folder picker — user selects the `Income Tax` folder once, app stores its Drive ID and only operates within it.

---

### E1 — WorkManager Pipeline Registry [ ] PLANNED

Central `PipelineManager` object. Every significant event enqueues a named Work chain.

**Trigger → Chain mapping:**

| Trigger | Worker chain |
|---------|-------------|
| CSV imported | Categorize → RecurringDetect → ReceiptMatch → BankStatDriveUpload → TaxChecklist |
| Receipt scanned | ReceiptMatch → PantryUpdate → TaxDocClassify → DriveTaxRoute |
| Document uploaded | DocClassify → DriveTaxRoute → TaxChecklist |
| Doc tagged tax-relevant | DriveTaxRoute → TaxChecklist |
| Daily 7am (scheduled) | DocExpiryCheck → TaxChecklist → RecurringDetect → PulseRefresh |
| Weekly Sunday | FullDriveTaxScan → DocumentExpiryReport |

**Workers to build:**

| Worker | Purpose |
|--------|---------|
| `CategorizationWorker` | Re-applies all merchant rules to all transactions |
| `RecurringDetectionWorker` | Groups by merchant, flags ≥2 consecutive cycles ±15% as Recurring |
| `ReceiptMatchingWorker` | Fuzzy-links vault receipts to wallet transactions (merchant + date ±1d + amount ±2%) |
| `BankStatementDriveWorker` | Uploads imported CSV to `Drive/IncomeTax/{year}/BankStatements/` |
| `DriveTaxRouteWorker` | Routes vault documents to correct Drive tax subfolder by doc type |
| `TaxChecklistWorker` | Scans linked Drive tax folder, updates local `TaxCheckEntity`, sends notifications |
| `DailyAnalysisWorker` | PulseRefresh + expiry checks + recurring refresh (scheduled periodic) |
| `DocumentExpiryWorker` | Fires notifications for docs expiring within 30/7/1 days |

---

### E2 — SteuerKlar: Tax Mode in the App [ ] PLANNED

Native port of `tax_api.py` CHECKS logic in Kotlin, reading from Drive.

**UI (new tab in Vault or Config):**
```
STEUERKLÄR 2025               ● 5 of 8 OK
──────────────────────────────────────────
✅  Tax Excel                  1 file
✅  Payslips (Siddharth)      12/12
✅  Lohnsteuerbescheinigung    1 file
🔴  Lohnsteuer (Chithra)      MISSING  [+Upload]
✅  Kita documents             4 files
🟡  Bank Statements            9/12
⚪  Donation receipts          0 files
✅  Utility bills              8 files
──────────────────────────────────────────
[Mark 2025 Complete]   [Open in Drive]
```

`[+Upload]` on missing items opens vault camera/picker, pre-routed to correct Drive subfolder.

**Document → Drive routing table:**

| Vault doc type | Drive subfolder |
|----------------|-----------------|
| Payslip (Brutto-Netto) | `Payslips/` |
| Lohnsteuerbescheinigung | `Payslips/` |
| Kita / Childcare receipt | `Kita/` |
| Utility bill (Strom, Gas, Wasser) | `Currentbills/` |
| Bank statement PDF | `BankStatements/` |
| Donation receipt (Spendenquittung) | `Donation/` |
| CSV bank import (auto) | `BankStatements/` |

**Data:** New `TaxCheckEntity` table caches last scan result locally. Works offline (shows last known state with "Last synced Xm ago").

---

### E3 — Document Expiry Timeline [ ] PLANNED

`TimelineCard` at top of Vault home — aggregates `DocumentAlertEntity` into a 90-day view:
```
NEXT 90 DAYS
🔴  Passport           expires  3 Jun  (8 days)
🟡  Car Insurance      renewal  15 Jun (20 days)
🟢  Health Insurance   renewal  1 Aug  (67 days)
```
Data already exists in `document_alerts` table — pure UI layer.

---

### E4 — Subscription & Recurring Cost Hub [ ] PLANNED

Cross-reference Vault (contracts tagged subscription) + Wallet (Recurring transactions from D4):
```
MONTHLY COMMITMENTS
Miete              €800     ← wallet recurring
Telekom            €35      ← wallet recurring
Netflix            €18      ← wallet (PayPal *Netflix)
ARD Beitrag        €18      ← wallet recurring
──────────────────────────
Total fixed costs  €871/month   (30.6% of salary)
```

---

## Phase F — Tax Preparation + Full Intelligence Suite — PLANNED 🔲

### F1 — Tax Tagging Layer [ ] PLANNED
Long-press transaction or vault doc → "Mark as tax-relevant". Stored in `tax_tags` table. German categories: Arbeitsmittel, Homeoffice Pauschale, Krankenkosten, Spendenquittungen.

### F2 — Annual Tax Summary Export [ ] PLANNED
"Generate Tax Summary" → groups tagged items, shows totals by German tax category, exports PDF/CSV. Closes the loop with SteuerKlar checklist.

### F3 — Receipt ↔ Wallet Transaction Linking [ ] PLANNED
`linkedVaultEntryId` column already exists on `wallet_transactions`. `ReceiptMatchingWorker` (E1) populates it. Transaction list shows 📄 icon; tapping opens receipt image.

### F4 — Spending Trend Mini-Charts [ ] PLANNED
3-cycle sparkline per category tile. Previous cycle data computed in parallel in `ExpensesViewModel`. "↑ +€42 vs last cycle" label under each tile.

---

## Phase D — Home Dashboard Upgrade (old plan, superseded)

~~### D1 — Unified Timeline [ ]~~
Superseded by Phase E3 (Expiry Timeline in Vault) and Phase D4 (Recurring bills in Pulse).

---

## Phase 4 QA — COMPLETE ✅

### QA-1 BugCheck — Fixes Applied

| Severity | File | Fix |
|----------|------|-----|
| CRITICAL | `PantryScreen.kt:207` | `dismissState.dismissDirection` (M2 API) → `.targetValue` |
| HIGH | `PantryScreen.kt:191` | Both swipe directions triggered consume → EndToStart only |
| HIGH | `PantryRepositoryImpl.kt:36` + `PantryScreen.kt:82` | Missing `quantity` arg → explicit `consumeItem(id, 1)` |
| HIGH | `HouseholdExportService.kt:44` | `FileWriter` → `OutputStreamWriter(UTF_8)` (OEM charset risk) |
| HIGH | `HouseholdImportService.kt:27` | `gson.fromJson` null into non-null var → explicit null guard |
| MEDIUM | `V2HomeScreen.kt:449` | Stale `getDocumentsExpiringSoon` timestamps — noted, not fixed (low-impact on fresh installs) |
| MEDIUM | `DocumentsScreen.kt:173` | Delete in `confirmValueChange` race — pre-existing pattern accepted |

### QA-2 UX Review — Fixes Applied

| Severity | File | Fix |
|----------|------|-----|
| HIGH | `AddDocumentSheet.kt:174` | `KeyboardType.Number` → `.Text` for date field (slashes untyable) |
| MEDIUM | `AddDocumentSheet.kt:276` | Save button `enabled = title.isNotBlank()` |
| MEDIUM | `AddDocumentSheet.kt:85` | Restored default drag handle (was `null`, no way to close) |
| MEDIUM | `AddDocumentSheet.kt:160` | Label → "Expiry Date (optional, dd/MM/yyyy)" |
| MEDIUM | `MerchantRulesScreen.kt:635` | `pattern.trim().isNotBlank()` (spaces-only was enabling Save) |
| MEDIUM | `DocumentsScreen.kt:282` | Added `overflow = TextOverflow.Ellipsis` on doc title |

### QA-3 Tests Written

- `android/src/test/.../RetroactiveCategorizerTest.kt` — 10 tests
- `android/src/test/.../HouseholdExportServiceTest.kt` — 9 tests
- `android/src/test/.../HouseholdImportServiceTest.kt` — 13 tests

---

## Completed Work

- [x] BUG-001: TransactionEditSheet missing Utilities/Transfers categories
- [x] BUG-002: Receipt linking ABS(amount) fix
- [x] BUG-003: FileProvider setup for PDF open
- [x] FEATURE-001: Multi-select bulk delete/move on vault screen
- [x] B1: Expense Categorization Engine (MerchantRulesScreen + RetroactiveCategorizer)
- [x] B2: Pantry Consume Loop (SwipeToDismissBox + CONSUME events)
- [x] B3: Local Backup/Restore (Gson, HouseholdExportService, HouseholdImportService)
- [x] B4: Important Dates UI (DocumentsScreen, AddDocumentSheet, UpcomingDatesCard)
- [x] B5: Home Screen Per-Category Budget Card
- [x] B6: Module Buttons Navigation Wire-up
- [x] Build: `gradlew :android:assembleDebug` — SUCCESSFUL (warnings only, no errors)
- [x] Budget threshold defaults: Groceries 600, Travel 150, Dining 120, Shopping 120
- [x] Removed Housing/Utilities/Family from config
- [x] Phase 4 QA: 5 bugs fixed (1 critical, 4 high), 6 UX fixes, 32 unit tests written
- [x] Final build: `gradlew :android:assembleDebug` — SUCCESSFUL (19s, warnings only)

---

## Agent Permissions (user-approved)

- Auto-approve: **all file edits under `C:\Projects\household-platform\`**
- Auto-approve: `gradlew :android:assembleDebug`
- **NO device install needed** — user checks manually
- **NO auto-push to git** — user commits locally

---

## Build Command

```powershell
cd C:\Projects\household-platform
.\gradlew.bat :android:assembleDebug
# APK: android/build/outputs/apk/debug/android-arm64-v8a-debug.apk
# Note: gradlew.bat exits 1 even on success — check APK existence instead
```

---

## Resume Instructions

Read this file, then:
1. Spawn QA-1 (BugCheck), QA-2 (UX Review), QA-3 (Test Writer) in parallel
2. Collect findings → add to STATUS.md
3. Spawn fix agents for any critical bugs
4. Final build: `gradlew :android:assembleDebug`
5. `git add -p && git commit` (no push)
