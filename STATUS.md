# Household Platform — Execution Status

**Last updated:** 2026-05-29 (Wave 5 + Theme engine complete)
**DB version:** 21  
**Build status:** ✅ BUILD SUCCESSFUL (Wave 5 + theme engine — all clean)
**APK:** `android/build/outputs/apk/debug/android-arm64-v8a-debug.apk`  
**Next action:** Wave 6 — E2 SteuerKlar Drive write · F3 Receipt↔Wallet 📄 icon · F1 Tax Tagging

---

## ⚠️ Product Audit Findings (2026-05-28) — act on these before adding new features

### Vibe-coded systems that need fixing

| Issue | File | Problem | Fix required |
|-------|------|---------|-------------|
| Smart Alert Feed is read-only | `V2FinanceScreen.kt` | ~~Alerts show info but tapping does nothing~~ | ✅ FIXED Wave 5N — every chip navigates |
| SteuerKlar is a static checklist | `SteuerKlarScreen.kt` | "Mark Complete" doesn't write `TAXATION_COMPLETE.json` to Drive | E2: implement Drive write (Wave 6) |
| Meals module is a shell | `V2MealsScreen.kt` | ~~ViewModel is empty~~ | ✅ FIXED Wave 5O — removed from nav rail |
| Vault data drives no automation | `VaultDocumentParserWorker.kt` | ~~Contract/employment docs don't trigger downstream~~ | ✅ FIXED Wave 5M — automation bridge added |

### Product principles now active (JUGAAD OS)
- Germany-first (no UPI, no India payments — SEPA/CSV is the payment layer)
- Automation over interaction: if vault extracts structured data, the system must react to it
- No vibe-coded features: every screen must complete a real task, not just display info
- Alert deeplinks are mandatory — alerts without resolution paths are noise

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

### D4 — Recurring Bills Detection ✅ DONE

**Implemented (earlier session + salary-date fix this session):**
- `RecurringBillEntity` table + `RecurringBillDao` (observeAutoBills / observeConfirmedBills / clearAutoBills / clearAll)
- `RecurringDetectionService` — groups by normalized merchant, ≥2 fiscal cycles, ±30% amount tolerance; writes AUTO bills
- `RecurringDetectionWorker` — loads actual Elektrobit salary dates via `getSalaryTransactionsByPattern()`, passes `List<LocalDate>` to service for dynamic cycle boundaries (replaces fixed anchor-day)
- `SubscriptionHubScreen` + `SubscriptionHubViewModel` — full UI: auto-detected pending review, confirm/dismiss, confirmed bills toggle/edit, upcoming outflows, contract costs, manual add FAB
- **Bug fixed this session:** `LazyColumn` duplicate keys crash on confirm — same `id` briefly in both `autoBills` and `confirmedBills` sections; fixed with namespaced keys `"auto_${it.id}"` / `"confirmed_${it.id}"` / `"upcoming_${it.first.id}"`

---

### D5 — Import Review Screen for Uncategorized ✅ DONE

After salary confirmation, `advanceFromSalary()` filters `"Uncategorized"` OR `"Other"` (ignoreCase) and routes to `ImportWorkflow.ReviewUncategorized` before `NeedsReview`.

| Item | File | Details |
|------|------|---------|
| `ReviewUncategorized` state | `ConfigViewModel.kt` | `pending: List<ParsedTransactionCandidate>`, `allAssignments: Map<Int,String>`, `pendingSummary: ImportSummary` |
| `handleAssignCategory()` | `ConfigViewModel.kt` | Updates assignments map in workflow state; pure in-memory, no DB write yet |
| `handleConfirmReview()` | `ConfigViewModel.kt` | Applies category overrides to DB (`walletTransactionDao().updateCategory`), creates merchant rules, advances to `NeedsReview` |
| `ReviewUncategorizedCard` | `V2ConfigHubScreen.kt` | Per-row ExposedDropdownMenuBox (8 categories) + Checkbox "Make rule", Done button |
| `advanceFromSalary` filter | `ConfigViewModel.kt` | Was `category == "Other"` only; now catches `"Uncategorized"` too (ignoreCase) |

---

### D6 — PayPal Transaction Intelligence ✅ ALREADY DONE

`MerchantNameCleaner` Pattern 3 (`PAYPAL\s*[*]\s*([^,.\n]+)`) already extracts the actual merchant name; `CsvParserService` runs `classifyCategory(cleanedTitle)` on the result. Verified:
- `"PAYPAL *NETFLIX INTERNAT"` → `title="Netflix Internat"` → `"Media Subscriptions"` ✓
- `"PAYPAL *LIEFERANDO"` → `title="Lieferando"` → `"Dining & Restaurants"` ✓
- Pattern 2 (`PP.ID.PP/. merchant`) + Pattern 4 (`PP*merchant`) also covered.

---

### D7 — Income Summary Section ✅ DONE

Collapsible "INCOME THIS CYCLE" card between CategoryGrid and transaction filter pill in `V2FinanceScreen.kt`.

| Item | Details |
|------|---------|
| ViewModel data | `_incomeTransactions` / `incomeTransactions` already existed — populated by `publishVisibleState()` filtering `category == "Income"` |
| `IncomeSection` composable | Replaced with spec design: collapsed by default, AnimatedVisibility expansion, `LumeEmerald` glow, `+€X.XX` per row, `TextOverflow.Ellipsis` |
| Placement | Between `CategoryGrid` and `LumeFilterPill`, above the transaction LazyColumn |

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

### E4 — Subscription & Recurring Cost Hub ✅ DONE

Implemented as `SubscriptionHubScreen` (see D4). Cross-references Vault contract costs + Wallet recurring transactions:
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

## Session 2026-05-28 (Wave 4 — F4 + E1 + JUGAAD P3) — Uncommitted

| Item | File(s) | Notes |
|------|---------|-------|
| F4 Sparklines | already committed (c612842) | `CategorySparkline`, 3-cycle `publishVisibleState()`, 3-bar `CategoryGridItem` — no action needed |
| E1 PipelineManager | `vault/workers/PipelineManager.kt`, `vault/workers/DocumentExpiryWorker.kt`, `VaultDocumentParserWorker.kt` | `onDocumentUploaded()` added; `DocumentExpiryWorker` stub; trigger wired before `Result.success()`; CSV chain already existed |
| JUGAAD P3 OCR | `vault/scan/OcrEngine.kt`, `MlKitOcrEngine.kt`, `PdfPageExtractor.kt`, new `OpenCvPreprocessor.kt`, `OcrRouter.kt`, `PaddleOcrEngine.kt` | OcrEngine interface extended; OpenCV preprocess→adaptiveThreshold pipeline with runCatching fallback; OcrRouter chains engines; PaddleOcr stub ready for ONNX model; PdfPageExtractor now calls OcrRouter |

**OCR chain after P3:**
```
VaultDocumentParserWorker → PdfPageExtractor.extractText(context, file)
  → OcrRouter.recognizeText(bitmap)
      → OpenCvPreprocessor.preprocess(bitmap)   ← NEW
      → MlKitOcrEngine.recognizeText(bitmap)
          → ImagePreProcessor.optimizeForOcr()  ← preserved
          → ML Kit TextRecognizer
```

---

## Session 2026-05-28 (Wave 3 — D7 + E3 + JUGAAD P4) — Uncommitted

| Item | File | Notes |
|------|------|-------|
| D7 Income Summary | `V2FinanceScreen.kt` | `IncomeSection` composable redesigned — collapsed by default, AnimatedVisibility, LumeEmerald glow, +€X.XX per row. ViewModel income data already existed (`_incomeTransactions`). |
| E3 Expiry Timeline | `V2DocumentVaultScreen.kt` | `DocumentExpiryTimelineCard` rewritten — LumeCyan glow, "NEXT 90 DAYS", dot + title + daysUntil, CriticalRed ≤7d / LumeAmber ≤30d / LumeEmerald otherwise. ViewModel + DAO already wired. |
| JUGAAD P4 Search | `VaultViewModel.kt`, `V2DocumentVaultScreen.kt` | `VaultSearchResult` data class; `_searchQuery`/`_searchResults` StateFlows; `onSearchQuery()` calls `searchFts(query*)` on IO dispatcher; `VaultSearchBar` + `VaultSearchResults` + `SearchEmptyState` composables inserted above main content |

---

## Session 2026-05-28 (Wave 1 — JUGAAD Vault) — Committed c612842

| Item | Notes |
|------|-------|
| OCR Cache + PdfPageExtractor | aHash perceptual fingerprint, cache lookup before OCR, store on miss |
| Page state machine | `vault_document_pages` rows; `OCR_QUEUED → OCR_DONE → INDEXED`; `runCatching` guards all pageDao calls |
| File dedup | SHA-256 `fileHash` on `VaultEntity`; dedup at `saveDocument()` returns existing id |
| DB v19 → v20 | `fileHash` column + index |
| 7 new parsers | VoterIdParser, OciParser, IndianDrivingLicenceParser, GermanDrivingLicenceParser, TaxDocParser, RentalContractParser, EmploymentLetterParser |
| ParserRegistry | 7 parsers inserted before GenericFallbackParser; 11 new keywords |
| Entity detail UI | `ExtractedInfoSection` in `DocumentDetailSheet` — ≤8 fields, deduped, confidence-sorted |

---

## Session 2026-05-28 (Wave 2 — FTS + D5) — Uncommitted

| Item | File | Notes |
|------|------|-------|
| `VaultEntityFts` FTS4 entity | `JugaadDocumentEntities.kt` | Links to `vault_extracted_entities` as content table |
| `searchFts()` + `rebuildFts()` | `DocumentEntityDao.kt` | FTS MATCH query + rebuild command |
| Migration 20→21 | `AppDatabase.kt` | Creates `vault_entities_fts` virtual table + initial rebuild |
| Worker FTS refresh | `VaultDocumentParserWorker.kt` | `runCatching { rebuildFts() }` after every insertAll |
| D5 `advanceFromSalary` fix | `ConfigViewModel.kt` | Filter broadened from `== "Other"` to `"Uncategorized" OR "Other"` (ignoreCase) |

---

## Session 2026-05-27 (session 2) — Fixes & Features

### FEATURE-002 — Clear All Data (Danger Zone) ✅ DONE

**Files modified:**
- `data/dao/TransactionOverrideDao.kt` — added `ImportAuditDao.clearAll()` (`DELETE FROM import_audits`)
- `ui/viewmodels/ConfigViewModel.kt` — added `ConfigIntent.ClearAllData` + `clearAllData()` (clears wallet_transactions, recurring_bills, salary_sources, import_audits, transaction_overrides, excluded_transactions; preserves merchant rules + category thresholds)
- `ui/v2/V2ConfigHubScreen.kt` — added `DangerZoneCard` composable (red-bordered, inline confirm/cancel, placed before About card)

### BUG-004 — Edit Transaction Sheet: category chips overflow ✅ FIXED

**File:** `ui/v2/V2FinanceScreen.kt`
- Root cause: `categories.chunked(3).forEach { Row(...) }` — rigid 3-per-row layout; long names like "Long-Distance Transport", "Local Activities & Sports" overflowed the row width
- Fix: replaced with `FlowRow` (stable in Compose 1.6 / BOM 2024.02.00); chips now wrap naturally by content width
- Added `import androidx.compose.foundation.layout.ExperimentalLayoutApi` + `import FlowRow`; merged `@OptIn` annotation

### BUG-005 — Subscription Hub crashes on scroll after confirm ✅ FIXED

**File:** `ui/v2/SubscriptionHubScreen.kt`
- Root cause: `LazyColumn` uses `key = { it.id }` for both `autoBills` and `confirmedBills` item sections. On confirm, the same bill `id` briefly exists in both sections simultaneously → Compose detects duplicate keys → crash
- Fix: namespaced all keys within the `LazyColumn`:
  - `items(autoBills, key = { "auto_${it.id}" })`
  - `items(confirmedBills, key = { "confirmed_${it.id}" })`
  - `items(upcomingOutflows, key = { "upcoming_${it.first.id}" })`

---

## Session 2026-05-28 (session 3) — Trip Tracker, Categories, Recurring

### D3 — Salary Allocation Bar ✅ DONE (was already implemented in previous session)

`SalaryAllocationCard` composable + `SalaryAllocationData` in `ExpensesViewModel` confirmed complete.

### DB Migration 17→18 ✅ DONE

**File:** `data/AppDatabase.kt`
- `MIGRATION_17_18`: creates `wallet_trips` table, adds `trip TEXT` column to `wallet_transactions`
- DB version bumped 17 → 18

### TripTracker Feature ✅ DONE (navigation wired)

**New file:** `ui/v2/TripTrackerScreen.kt` — full screen with ViewModel, trip CRUD, budget bars, transaction list
**Modified:**
- `ui/compose/navigation/Screen.kt` — added `Trips` route
- `ui/v2/V2AppNavHost.kt` — wired Trips route to TripTrackerScreen
- `ui/v2/V2FinanceScreen.kt` — "Trips" pill button in Wallet header

### Transaction Edit Sheet — Simplified Categories ✅ DONE

**File:** `ui/v2/V2FinanceScreen.kt:TransactionEditSheet`
- Reduced from 27 categories → 8: Groceries, Dining, Travel, Shopping, Entertainment, School, Utilities, Exclude

### Transaction Edit Sheet — Trip Tagging ✅ DONE

**File:** `ui/v2/V2FinanceScreen.kt:TransactionEditSheet`
- `LaunchedEffect` loads trips from `WalletTripDao`
- "TAG TO TRIP" section with chips (None + trip names)
- Chip tap calls `walletTransactionDao().updateTransactionTrip()` directly

### Tax Categories → English ✅ DONE

- `V2FinanceScreen.kt:TaxTagDialog`: Arbeitsmittel→Work Equipment, Homeoffice→Home Office, Krankenkosten→Medical Expenses, Spendenquittung→Donations, Fahrtkosten→Work Commute, Andere→Other
- `V2DocumentVaultScreen.kt`: same translation

### Recurring Detection Enhancements ✅ DONE

**File:** `data/service/RecurringDetectionService.kt`
- `normalizeMerchant()` now calls `MerchantNameCleaner.clean()` first (strips SEPA/PayPal noise before truncation)
- Truncation window: 14 → 20 chars (catches more full merchant names)
- Amount tolerance: 30% → 50% (handles utility bills with seasonal variation)
- Bill range window: ±15% → ±30% (more realistic match window for future auto-matching)
- Added `oneOffCategories` filter — travel, transfers, bank fees never flagged as recurring
- Minimum amount filter: ≥ €0.50 (removes rounding noise)

---

## Session 2026-05-28 (session 3 continued) — Vault/Docs/Salary/JUGAAD

### BUG-006 — DB migration crash on existing v17 installs ✅ FIXED

**File:** `data/AppDatabase.kt`
- Root cause: `MIGRATION_17_18` ran `ALTER TABLE wallet_transactions ADD COLUMN trip TEXT` but `trip` column already existed on fresh-installed v17 devices (Room had created it from entity schema)
- Fix: wrapped ALTER TABLE in `try { } catch (e: Exception) { }` — safe to ignore on duplicate column

### BUG-007 — Document monthly cost shows £ instead of € ✅ FIXED

**File:** `ui/v2/DocumentsScreen.kt:DocumentCard` — `£` → `€`

### BUG-008 — Documents with no expiry sort to top of list ✅ FIXED

**File:** `data/dao/FamilyMemberDao.kt:DocumentDao.getAllDocuments()`
- Changed `ORDER BY expiryDate ASC` → `ORDER BY CASE WHEN expiryDate IS NULL THEN 1 ELSE 0 END, expiryDate ASC`
- Documents without expiry dates now sink to the bottom

### BUG-009 — Manual document save never creates DocumentAlertEntity ✅ FIXED

**File:** `data/repository/DocumentRepositoryImpl.kt`
- `insertDocument()` now calls `createAlertIfExpiring()` — creates `DocumentAlertEntity` if `expiryDate` is within `noticePeriodDays`
- `updateDocument()` deletes stale alerts and recreates fresh ones
- Smart Alert Feed and Vault alert badge now correctly show manually-added document expiry warnings

### BUG-010 — Tapping a document does nothing (no edit) ✅ FIXED

**Files modified:**
- `ui/v2/DocumentsScreen.kt` — `onClick = {}` → `onClick = { editingDoc = doc }`; added `editingDoc` state
- `ui/v2/components/AddDocumentSheet.kt` — added `existingDocument: DocumentEntity?` parameter; pre-populates all fields (title, type, expiry date, notice period, monthly cost, notes, owner); Save/Update button label adapts

---

### FEATURE-003 — Document Upload: Original Filename Pre-population ✅ DONE

**File:** `ui/v2/V2DocumentVaultScreen.kt`
- Extracts `DISPLAY_NAME` from URI via `ContentResolver.query(OpenableColumns.DISPLAY_NAME)` when file is picked
- Strips file extension, replaces underscores with spaces
- Passes result as `initialDocumentTitle` to `VaultFolderPickerSheet` — title field is pre-populated instead of blank

### FEATURE-004 — Passport / ID Expiry Date Extraction Fixed ✅ DONE

**File:** `data/service/VaultDocumentParser.kt`

**Root cause:** `extractExpiryDate()` used `today.plusYears(2)` as the upper limit — a passport expiring in 2030 was silently discarded.

**Fixes:**
- IDENTITY docs: `plusYears(15)` (passports valid 10 years, IDs up to 15)
- All other categories: `plusYears(5)`
- Added `DATE_MRZ` regex: detects 6-digit YYMMDD dates embedded in `<<<` MRZ sequences
- Added `DATE_YYMMDD` regex: standalone YYMMDD fallback for identity documents
- Added `parseMrzDate()`: interprets 2-digit year (< 70 → 2000s, ≥ 70 → 1900s)
- Expanded `EXPIRY_KEYWORDS` set: added gültigkeitsdatum, date of expiry, gültig (broad)

### FEATURE-005 — Salary Detection Overhaul ✅ DONE

**Files modified:**

**`data/TransactionCategorizer.kt`**
- Expanded `incomeKeywords` from 15 → 28 terms
- New keywords: GEHALTSZAHLUNG, GEHALTSÜBERWEISUNG, VERGÜTUNG, ARBEITSENTGELT, ARBEITSLOHN, BEZÜGE, DIENSTBEZÜGE, MONATSLOHN, MONATSVERDIENST, WERKSTUDENT, KURZARBEITERGELD, BÜRGERGELD, SALARY, PAYROLL, WAGES, NET PAY, GROSS PAY, AUSZAHLUNG, ELEKTROBIT
- New method `salaryConfidenceScore(description, amount): Int` → 0–100:
  - Strong salary keyword: +50pts
  - Medium keyword (BEZÜGE, MINIJOB, etc.): +30pts
  - Amount ≥ €2000: +30pts / ≥ €800: +20pts / ≥ €300: +10pts
  - Corporate payer (GmbH, AG, KG, Ltd, Inc): +20pts

**`ui/viewmodels/ConfigViewModel.kt`**
- Added `import com.household.app.data.TransactionCategorizer`
- Rewrote `resolveSalaryWorkflow()`:
  - Step 1: if stored salary source exists → try auto-match by title prefix + amount range (unchanged)
  - Step 2: score ALL credit transactions with `salaryConfidenceScore()`; if top score ≥ 70 → auto-confirm, save to `SalarySourceEntity`, skip UI
  - Step 3: merge anchor-day candidates (±10 days, ≥ €300) with scored candidates (score ≥ 20); show top 5 in confirmation UI

**`ui/v2/V2FinanceScreen.kt`**
- Added `onMarkAsSalary: (applyToHistory: Boolean) -> Unit` parameter to `TransactionEditSheet`
- "Mark as Salary" `FilledTonalButton` shown at bottom of sheet for positive-amount transactions
- Hint text updates based on "Create Merchant Rule" checkbox state
- Call site passes `onMarkAsSalary = { applyToHistory -> viewModel.reclassifyTransaction(..., "Salary", applyToHistory) }`

---

### JUGAAD Vault — Architecture Blueprint ✅ DESIGNED (no code yet)

Full architecture designed for a standalone privacy-first Android document vault. Key specs:

**Stack:** Kotlin · CameraX · PaddleOCR ONNX · ML Kit · Tesseract · PdfBox · OpenCV · Room FTS · WorkManager · Hilt

**Core design decisions:**
- `OcrEngine` interface — PaddleOCR / ML Kit / Tesseract swappable with no business logic changes
- Two-level deduplication: SHA-256 (file) + pHash (page) — OCR never reruns for identical content
- Text-based PDF fast path — PdfBox extracts text; zero OCR cost
- Processing state machine (PENDING → SPLITTING → OCR_QUEUED → OCR_DONE → CLASSIFYING → EXTRACTING → INDEXED) persisted in DB — crash-safe restart
- Engine version column — model upgrade triggers incremental rescan of affected pages only; pages with current version untouched
- Page-level granularity — multi-page PDFs process pages independently and in parallel

**Schema:** `documents`, `document_pages`, `ocr_cache` (keyed by `page_hash + engine_version`), `document_entities`, `document_tags`, `expiry_alerts`, `processing_jobs`, `family_members` + two FTS virtual tables

**10 specialized agents defined:** DB & Schema · Storage & Security · OCR Pipeline · PDF & Preprocessing · Classification · Entity Extraction · Pipeline & Jobs · Search · Camera & Scanner · UX & State

**5 implementation phases:** P0 Foundation → P1 Text Extraction → P2 Classification + Entities → P3 Advanced OCR → P4 Search → P5 Polish & Security

**Status:** Architecture approved + government document intelligence extension added. Awaiting Phase 0 execution kickoff.

---

## Session 2026-05-28 (session 3 continued) — Vault Upload Bugs + JUGAAD Extension

### BUG-011 — Document moves out of user-set folder after parsing ✅ FIXED

**File:** `vault/workers/VaultDocumentParserWorker.kt`
- Root cause: `updateParsedMeta` always wrote `meta.subFolder.id` — when OCR failed on a scanned PDF (JPEG2000), the parser returned `UNFILED`, moving the doc out of the user's chosen folder (e.g. Siddharth → Identity → Passport)
- Fix: `parserMayOverrideSubFolder = entry.subFolder == "unfiled" || entry.subFolder == "other"` — parser only overrides if user never set a specific subfolder

### BUG-012 — Receipt parsing ran on all document types ✅ FIXED

**File:** `vault/workers/VaultDocumentParserWorker.kt`
- Root cause: `ReceiptTextParser` executed on passports, contracts, insurance docs — nonsense fields written
- Fix: Added `isReceiptLike` guard — receipt parsing skipped for IDENTITY, INSURANCE, CONTRACT, MEDICAL, PROPERTY

### TASK-001 — Indian passport date extraction not working 🔲 DEFERRED → JUGAAD Phase 2

**Status:** Fix partially implemented (added `DATE_MONTH_SHORT` for `15 APR 2033` format, `MRZ_LINE2` TD3 regex, expanded MONTH_MAP with 3-letter abbreviations) but not verified via live device test.

**Root cause identified from logs:** OCR succeeds (3000×1942px bitmap processed, `PipelineManager: OCR process succeeded via visionkit pipeline`) but `extractExpiryDate` does not recognise `DD MMM YYYY` abbreviated month format used on Indian passports, nor the MRZ YYMMDD at fixed TD3 offset.

**Will be addressed properly during JUGAAD Vault Phase 2 (Classification + Entity Extraction)** when the dedicated `IndianPassportParser` is implemented with full MRZ TD3 parsing and Indian date format support.

**Debug logging added:** `Log.d("VaultParser", ...)` in worker shows OCR text preview + parsed result for future diagnosis.

### JUGAAD Vault — Government Document Intelligence Extension ✅ ARCHITECTURE EXTENDED

Full updated architecture incorporating:
- Parser Registry pattern (`DocumentParser` interface, per-type parsers)
- Country + document type classification pipeline
- Structured entity extraction with confidence scores
- Normalization engine (dates, names, IDs, addresses)
- Indian documents: Aadhaar, PAN, Passport, DL, Voter ID, OCI
- German documents: Passport, Aufenthaltstitel, Meldebescheinigung, DL, insurance, tax, contracts
- 10 specialized agents updated with new responsibilities

See PLATFORM.md for full updated blueprint.

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
- [x] Budget threshold defaults: Groceries 600, Travel 150, Dining 120, Shopping 120
- [x] Phase 4 QA: 5 bugs fixed (1 critical, 4 high), 6 UX fixes, 32 unit tests written
- [x] FEATURE-002: Clear All Data (Danger Zone) in Config
- [x] BUG-004: Edit Transaction Sheet category chips overflow (FlowRow fix)
- [x] BUG-005: Subscription Hub crash on scroll after confirm (duplicate LazyColumn keys)
- [x] D3: Salary Allocation Bar — SalaryAllocationCard + SalaryAllocationData (confirmed complete)
- [x] DB migration 17→18: wallet_trips table + trip column (with crash-safe try/catch)
- [x] FEATURE: Trip Tracker — TripTrackerScreen, navigation, wallet header button
- [x] Transaction Edit Sheet — simplified to 8 categories + trip tagging chip row
- [x] Tax categories → English (V2FinanceScreen + V2DocumentVaultScreen)
- [x] Recurring detection: MerchantNameCleaner integration, 50% tolerance, one-off category filter, min amount €0.50
- [x] BUG-006: DB migration crash on duplicate trip column
- [x] BUG-007: Document monthly cost £ → €
- [x] BUG-008: Documents NULL-expiry sort order fixed
- [x] BUG-009: Manual document save now creates DocumentAlertEntity
- [x] BUG-010: Document edit on tap (AddDocumentSheet pre-populate)
- [x] FEATURE-003: Vault upload title pre-populated from original filename
- [x] FEATURE-004: Passport/ID expiry extraction fixed — 15yr range, MRZ regex, expanded keywords
- [x] FEATURE-005: Salary detection overhaul — 28 keywords, confidence scoring, auto-confirm ≥70, Mark as Salary button
- [x] JUGAAD Vault — complete architecture blueprint (10 agents, 5 phases, DB schema, OCR decision tree)
- [x] BUG-011: Vault document moves to UNFILED after OCR failure — subFolder preservation fix
- [x] BUG-012: Receipt parsing ran on passport/insurance docs — isReceiptLike guard added
- [x] JUGAAD Vault — government document intelligence extension (Parser Registry, 14 parsers, normalization engine, IN+DE doc support)
- [x] JUGAAD Wave 1 — OCR cache, page state machine, file dedup, 7 parsers, entity UI (DB v20, committed c612842)
- [x] JUGAAD Wave 2 — vault_entities_fts FTS4, searchFts/rebuildFts DAO, migration 20→21, worker FTS refresh (uncommitted)
- [x] D5 — Import review screen: ReviewUncategorized state, handleAssignCategory/handleConfirmReview, ReviewUncategorizedCard composable (uncommitted)
- [x] D5 fix — advanceFromSalary filter broadened to catch "Uncategorized" + "Other" (uncommitted)
- [x] TASK-001: Indian passport date extraction — DONE. IndianPassportParser fully implements MRZ TD3 line1+2 (DOB, expiry, name), DD MMM YYYY visual zone fallback, keyword-anchored fallbacks. OcrSpace fix: mrzCandidates strips intra-line spaces before MRZ regex so OCR-inserted spaces don't break matching.
- [x] D6: PayPal Transaction Intelligence — already done via MerchantNameCleaner Pattern 3 + CsvParserService categorizer pipeline
- [x] D7: Income Summary Section — collapsible IncomeSection card in V2FinanceScreen between CategoryGrid and filter pill
- [x] F4: Spending trend sparklines — already done (c612842); `CategorySparkline` data class, 3-cycle boundaries in `publishVisibleState()`, 3-bar `CategoryGridItem` chart + delta label
- [x] E1: WorkManager Pipeline Registry — `PipelineManager.onDocumentUploaded()` + `DocumentExpiryWorker` (fully implemented); wired into `VaultDocumentParserWorker`; CSV chain pre-existing
- [x] DocumentExpiryWorker FULL: queries `document_alerts` (daysUntil ≤ 30, !isAcknowledged); posts 🔴/🟡 notifications; reuses `ExpiryNotificationWorker.CHANNEL_ID`; `acknowledgeAlert()` after each post; handles notifications-disabled gracefully
- [x] JUGAAD P3: `OcrEngine` interface, `OpenCvPreprocessor`, `OcrRouter`, `PaddleOcrEngine` FULL — lazy ONNX session from assets, bitmap→float CHW tensor [1,3,48,W], greedy CTC decode, Latin+German charset; `OcrRouter.init(context)` prepends PaddleOcr before ML Kit; `VaultDocumentParserWorker` calls `OcrRouter.init()` at top of `doWork()`
- [x] JUGAAD P4: Vault FTS Search UI — `VaultSearchBar` + `VaultSearchResults` + `SearchEmptyState` composables; `VaultSearchResult` data class + `onSearchQuery()` in VaultViewModel; FTS prefix matching with `*`
- [x] E3: Document Expiry Timeline card — `DocumentExpiryTimelineCard` rewritten; glow=LumeCyan, "NEXT 90 DAYS", dot+title+days, CriticalRed ≤7d / LumeAmber ≤30d / LumeEmerald otherwise

## Theme Engine — COMPLETE ✅ (2026-05-29)

- [x] `JugaadThemeSelection` enum — 6 themes: LUMINESCENT_GLASS, JUGAAD_CHILLI, NORDIC_EINKAUF, MATRIX_PIPELINE, MONSOON_FOREST, TWILIGHT_CASHMERE
- [x] All 6 `ColorScheme` objects defined in `Theme.kt`
- [x] `JugaadTheme(themeSelection, content)` — parameterized composable; `HouseholdPlatformTheme` is a backward-compat alias
- [x] `ThemePreferencesManager` — DataStore persistence (`theme_prefs`)
- [x] `ThemeViewModel` + `ThemeViewModelFactory` — `currentTheme: StateFlow` + `setTheme()`
- [x] `ThemeSelector` — Radio-button picker composable wired into ConfigHub as `AppThemeCard`
- [x] `MainActivity` — owns `ThemeViewModel` via `viewModels {}`, collects state, wraps `setContent` in `JugaadTheme`
- [x] `AppNavHost` — removed per-screen `HouseholdPlatformTheme {}` wrappers (were overriding user selection)
- [x] `V2AppShell` — removed redundant theme wrapper (theme now owned at Activity level)
- [x] `Color.kt` — added `TextMutedDark` (70% white) and `TextSecondaryDark` (80% white) for WCAG contrast on dark cards
- [x] `BudgetGauge` — fixed inverted gradient; now uses pacing-aware solid color (spend vs time fraction → green/amber/red)
- [x] `JugaadGlassCard` — new standardized card with strict 40% surface alpha + 15% primary border stroke
- [x] `StringExtensions.kt` — `String.capitalizeWords()` util in `domain/utils/`

## Wave 5 — COMPLETE ✅ (2026-05-29)

### Wave 5M — Vault Automation Bridge ✅ DONE

**Files modified:**
- `vault/extraction/EntityType.kt` — added `GROSS_SALARY` enum value
- `vault/classification/parsers/EmploymentLetterParser.kt` — salary entity now uses `EntityType.GROSS_SALARY` (was `MONTHLY_COST`)
- `data/dao/RecurringBillDao.kt` — added `getByMerchantPattern(pattern)` query
- `vault/workers/VaultDocumentParserWorker.kt` — Step 2c automation bridge:
  - `RENTAL_CONTRACT` + `MONTHLY_COST` → inserts `RecurringBillEntity` (isActive=false, source="VAULT") if not already present
  - `EMPLOYMENT_LETTER` + `GROSS_SALARY` → upserts `SalarySourceEntity` with ±20% range
  - `IDENTITY` expiry → additional alerts at -90d and -180d (skips stale/past ones)

### Wave 5N — Smart Alert Deeplinks ✅ DONE

**Files modified:**
- `ui/viewmodels/ExpensesViewModel.kt` — `FinancialAlert` gets `destination: String` field; all 5 alert types in `computeSmartAlerts()` set a destination route
- `ui/v2/V2FinanceScreen.kt` — `SmartAlertFeed` + `SmartAlertChip` accept `onNavigate` callback; chips are clickable via `Modifier.clickable`
- `ui/v2/V2AppNavHost.kt` — `V2FinanceScreen` call passes `onNavigate = { navController.navigate(it) }`

Alert routing: new subscription/upcoming bill → `subscription_hub`; expiry → `documents`; tax-deductible → `tax_summary`; uncategorized → `config`

### Wave 5O — Meals Module Removed ✅ DONE

**Files modified:**
- `ui/compose/navigation/Screen.kt` — `Meals` removed from `Screen.all` (object definition kept)
- `ui/v2/V2AppNavHost.kt` — meals composable route removed
- `ui/compose/AppNavHost.kt` — legacy nav host meals route removed
- `ui/compose/state/HomeViewModel.kt` — Meals removed from modules list

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
