# Household Platform - Build Plan

**Date:** April 27, 2026  
**Last updated:** May 28, 2026 (Wave 2 — FTS + D5 import review)
**Status:** Wave 2 complete — vault_entities_fts migration 20→21, D5 import review screen, advanceFromSalary filter fix  
**Target:** Running MVP with Expenses + Household Core

---

## Phase Progress

| Phase | Description | Status |
|-------|-------------|--------|
| A | Platform Core — DB, navigation, app shell | ✅ Done |
| B | Categorization, pantry, backup, documents | ✅ Done |
| C1 | Receipt OCR, vault interactions | ✅ Done |
| C2 | QR pairing, APK install via QR, new app icon | ✅ Done |
| C3 | Family module + Vault folder tree + Wallet Pulse card + budget fixes | ✅ Done |
| D1 | Salary detection from CSV — SalarySourceEntity, migration 10→11, confirmation UI | ✅ Done |
| D2 | Credit & transfer categorization fix — Income/Excluded logic, Revolut transfers | ✅ Done |
| D+ | 24-category engine, PayPal SEPA extraction, ING support, FTS fix, Smart Alert Feed, Receipt OCR engine, 7-step onboarding, Android 13 notifications | ✅ Done |
| D3 | Salary Allocation Bar in Pulse — Fixed / Discretionary / Remaining segmented bar | ✅ Done |
| D4 | Recurring bills detection + SubscriptionHubScreen — actual salary dates, dynamic cycle boundaries | ✅ Done |
| **S3a** | **Trip Tracker — WalletTripEntity, TripTrackerScreen, trip tagging in edit sheet** | ✅ Done |
| **S3b** | **Document vault fixes — expiry date range, MRZ extraction, alert creation, edit-on-tap, sort order, currency symbol** | ✅ Done |
| **S3c** | **Salary detection overhaul — confidence scoring, German/English keywords, auto-confirm, Mark as Salary button** | ✅ Done |
| **S3d** | **Document upload — original filename pre-population, PDF passport date extraction** | ✅ Done |
| **JUGAAD P0+2** | **JUGAAD Vault — Parser Registry, entity extraction, DB schema v19** | ✅ Done (commit 520abc1) |
| **JUGAAD Wave 1** | **JUGAAD Vault — OCR cache, page tracking, file dedup, 7 parsers, entity detail UI, DB v20** | ✅ Done (commit c612842) |
| **JUGAAD Wave 2** | **JUGAAD Vault — vault_entities_fts FTS4, searchFts/rebuildFts DAO, migration 20→21** | ✅ Done (uncommitted) |
| **D5** | **Import review screen for Uncategorized transactions — ReviewUncategorized state + composable** | ✅ Done (uncommitted) |
| **D6** | **PayPal Transaction Intelligence — PAYPAL \*MERCHANT extraction in MerchantNameCleaner** | ✅ Already done (Pattern 3 in MerchantNameCleaner + CsvParserService pipeline) |
| **D7** | **Income Summary Section — collapsible income row above transaction list** | ✅ Done (Wave 3) |
| **E3 Expiry Timeline** | **90-day document expiry card at top of Vault screen** | ✅ Done (Wave 3) |
| **JUGAAD P3 OCR** | **OcrEngine interface, OpenCV preprocessing, OcrRouter, PaddleOcrEngine (full ONNX)** | ✅ Done (Wave 4 + impl) |
| **JUGAAD P4 Search** | **Inline FTS search bar + results in Vault screen** | ✅ Done (Wave 3) |
| **E1 PipelineManager** | **WorkManager trigger→chain registry; CSV + document upload triggers** | ✅ Done (Wave 4) |
| **E2 SteuerKlar** | **Native Kotlin tax checklist reading Drive folder** | 🔲 Planned |
| **E** | **WorkManager pipeline full suite + SteuerKlar + Drive integration** | 🔄 E1 done, E2 planned |
| **F4 Sparklines** | **3-cycle mini-chart + delta label on each category tile** | ✅ Already done (commit c612842 — `CategorySparkline`, `CategoryGridItem` 3-bar chart) |
| **F** | **Tax preparation, receipt linking, tax tagging, spending trends** | 🔲 Planned (F1–F3 remaining) |

---

## JUGAAD Vault Wave 1 (2026-05-28) — Committed c612842

| Item | Details |
|------|---------|
| OCR Cache | `OcrCacheManager` — aHash perceptual page fingerprinting; cache lookup before rasterise+OCR; store on miss |
| PdfPageExtractor | Optional `cacheManager` param; cache hit skips OCR entirely |
| Page tracking | `vault_document_pages` row created/updated per document; state: `OCR_QUEUED → OCR_DONE → INDEXED` |
| File dedup | SHA-256 `fileHash` column on `VaultEntity`; `getByFileHash()` DAO; dedup check at `saveDocument()` |
| VaultDocumentParserWorker | State machine calls; all `pageDao` ops wrapped in `runCatching` |
| DB v19 → v20 | `fileHash` column + `idx_vault_entries_fileHash` index |
| 7 new parsers | VoterIdParser, OciParser, IndianDrivingLicenceParser, GermanDrivingLicenceParser, TaxDocParser, RentalContractParser, EmploymentLetterParser |
| ParserRegistry | 7 parsers inserted before GenericFallbackParser; 11 new anchor keywords |
| Entity detail UI | `DocumentDetailSheet` — `ExtractedInfoSection` shows ≤8 fields, deduped by type, sorted by confidence |

---

## JUGAAD Vault Wave 2 (2026-05-28) — Uncommitted

| Item | File | Details |
|------|------|---------|
| `VaultEntityFts` | `data/entities/JugaadDocumentEntities.kt` | `@Fts4(contentEntity=VaultDocumentEntityRecord::class)` virtual table `vault_entities_fts` |
| `searchFts()` | `data/dao/DocumentEntityDao.kt` | Inner join `vault_extracted_entities ⋈ vault_entities_fts MATCH :query`, ordered by confidence |
| `rebuildFts()` | `data/dao/DocumentEntityDao.kt` | `INSERT INTO vault_entities_fts(vault_entities_fts) VALUES('rebuild')` |
| Migration 20→21 | `data/AppDatabase.kt` | `CREATE VIRTUAL TABLE IF NOT EXISTS vault_entities_fts USING fts4(content=vault_extracted_entities, rawValue, normalizedValue)` + rebuild |
| Worker FTS refresh | `vault/workers/VaultDocumentParserWorker.kt` | `runCatching { db.documentEntityDao().rebuildFts() }.getOrNull()` after every `insertAll(records)` |
| D5 filter fix | `ui/viewmodels/ConfigViewModel.kt` | `advanceFromSalary` now catches both `"Uncategorized"` and `"Other"` (ignoreCase) before showing NeedsReview |
| D5 ReviewUncategorized | `ui/viewmodels/ConfigViewModel.kt` | `ImportWorkflow.ReviewUncategorized`, `handleAssignCategory`, `handleConfirmReview` |
| D5 Review UI | `ui/v2/V2ConfigHubScreen.kt` | `ReviewUncategorizedCard` composable — per-row category dropdown + "Make rule" checkbox, capped at 20, Done button |

---

## JUGAAD Vault Wave 3 / D7 / E3 (2026-05-28) — Uncommitted

| Item | File | Details |
|------|------|---------|
| D7 Income Summary | `ui/v2/V2FinanceScreen.kt` | `IncomeSection` redesigned: collapsed by default, `AnimatedVisibility`, `LumeEmerald.copy(alpha=0.12f)` glow, chevron toggle, `+€X.XX` rows. ViewModel data (`_incomeTransactions`) already existed. |
| E3 Expiry Timeline | `ui/v2/V2DocumentVaultScreen.kt` | `DocumentExpiryTimelineCard` rewritten: `LumeCyan` glow, "NEXT 90 DAYS" header, dot + title + "in N days" per row. `CriticalRed` ≤7d / `LumeAmber` ≤30d / `LumeEmerald` otherwise. VM + DAO (`getAlertsDueWithinDays(90)`) pre-existing. |
| JUGAAD P4 Search | `ui/viewmodels/VaultViewModel.kt`, `ui/v2/V2DocumentVaultScreen.kt` | `VaultSearchResult(documentId, entityType, displayValue, confidence)`; `onSearchQuery()` calls `searchFts("${query}*")` on IO; `VaultSearchBar` (frosted pill, Search icon, clear ×) + `VaultSearchResults` (up to 10 hits, confidence %, entity type label) + `SearchEmptyState`; uses existing `formatEntityType()` |

---

## Phase C3 — What was done (now committed)

### Family module
| File | Purpose |
|------|---------|
| `domain/models/FamilyRoles.kt` | Role constants (Primary, Adult, Child, Member, Other) |
| `domain/models/vault/VaultFolderPath.kt` | Typed path: category + member + subfolder |
| `domain/models/vault/VaultFolderTree.kt` | `VaultBrowseState` sealed hierarchy; tree logic |
| `domain/models/vault/VaultSubFolder.kt` | Per-category subfolder definitions |
| `ui/v2/FamilyMemberDetailScreen.kt` | Member detail: avatar, stats, expiry alerts, contracts, vault docs |
| `ui/v2/FamilyUiUtils.kt` | Shared UI helpers |
| `ui/v2/components/FamilyMemberEditorSheet.kt` | Add / edit member bottom sheet |
| `ui/v2/components/VaultFolderBrowser.kt` | Folder tree browser composable |
| `ui/v2/components/VaultFolderPickerSheet.kt` | Folder picker for document upload |
| `ui/viewmodels/FamilyViewModel.kt` | Members, doc counts, 30-day expiry alerts, CRUD |

**Bug fixed:** `FamilyMemberDetailScreen` was shaking due to `observeMemberDetail()` creating a new `StateFlow` on every recomposition. Fixed with `remember(memberId)`.

### Wallet — Household Pulse card
Replaced the circular gauge hero card with a smart "Household Pulse" card:
- **Status**: SAFE / WARNING / CRITICAL — driven by projected end-of-cycle surplus
- **Top risk**: detects category spending up >15% vs previous 7 days
- **Upcoming**: surfaces salary day when ≤4 days away
- **AI suggestion**: arithmetic cut recommendation when overspending or at-risk
- Glow color adapts to status (Emerald / Amber / Red)

### Wallet — Category grid + budget limits
- Grid reduced to **4 tracked categories only**: Groceries, Eat Out, Travel, Shopping
- Each tile shows **€spent / €limit** with a colour-coded utilisation bar (green → amber at 75% → red at 100%)
- Limits loaded from `CategoryThresholdEntity` DB at every refresh; fall back to defaults
- **Default limits updated**: Groceries €600, Travel €195, Dining €100, Shopping €100

### Wallet — Projected surplus fix
- Was measuring against `monthlyBudget` = €3000 (wrong — overall prefs value, unrelated to category budgets)
- Now measures against **sum of 4 category limits = €995**
- `canonicalCategoryVM()` used to normalise category strings before filtering (fixes "Eat Out" vs "Eat out" mismatch)

### Wallet — Search bar
- `BasicTextField` search bar between time-filter pill and transaction list
- Filters by `tx.description` case-insensitively; `✕` clear button appears when active
- Does not affect the Pulse card (which always uses full cycle data)

---

## Phase D — In Progress: CSV redesign + salary-based budgeting

### Problem with current approach
- `monthlyBudget` = hardcoded €3000 in `DashboardPrefs` — has no relation to actual income
- Salary amount should come from the CSV (credit transaction on anchor day)
- Uncategorized transactions have no UX for user to review and classify
- Utility/recurring bills (rent, electricity, internet, insurance, ARD, GEZ) are mixed in with discretionary spend — they shouldn't count against the discretionary budget but should be visible

### Planned architecture

**Salary detection from CSV**
- During import, detect large credit transactions near the `salaryAnchorDay`
- Offer to tag as "Salary" — store separately, use as `totalIncome` for the cycle
- Replace `monthlyBudget` DashboardPrefs with salary-derived income

**Import flow redesign**
- After parsing + auto-categorizing with existing merchant rules, show a **review screen**
- Pre-categorize using: existing merchant rules + known shop/grocery patterns + PayPal rules
- Highlight transactions that could not be categorized → user taps to assign category
- "Make this a rule" option inline — same as current merchant rule creation but surfaced during import

**Category taxonomy redesign**

| Type | Examples | Budget tracked | Shown in Pulse |
|------|----------|---------------|----------------|
| Discretionary (4) | Groceries, Dining, Travel, Shopping | Yes — per-category limits | Yes — surplus/risk |
| Recurring / Utility | Rent, electricity, internet, phone, ARD, insurance | No limit needed | As "upcoming" in Pulse |
| Income | Salary, freelance | Source of total budget | Yes — defines ceiling |
| Excluded | Internal transfers, SEPA self-transfers | No | No |

**Pulse card evolution**
- Salary allocation bar above the 4 category tiles: Fixed | Discretionary | Remaining (% of salary)
- Fixed costs section: collapsible list of recurring bills (rent, electricity, internet, etc.)
- Salary amount + date sourced from confirmed `SalarySourceEntity`
- Remaining = salary − fixed committed − discretionary spent this cycle
- The 4 category tiles and their limits are unchanged

---

### Phase D — Completed items

| Item | Status | Notes |
|------|--------|-------|
| Salary detection from CSV | ✅ Done | SalarySourceEntity, migration 10→11, SalaryConfirmation UI |
| Credit/transfer categorization | ✅ Done | Credits → Income; N26/ING/Revolut debits → Excluded |
| Salary allocation bar in Pulse | ✅ Done | `SalaryAllocationCard` + `SalaryAllocationData` in `ExpensesViewModel`; segmented bar Fixed/Discretionary/Remaining; collapsible recurring bill list |
| Recurring bills detection | ✅ Done | RecurringBillEntity, RecurringDetectionService + Worker, actual salary dates as cycle boundaries, SubscriptionHubScreen |
| Import review for uncategorized | 🔲 Planned | Post-salary step, inline category picker + make-rule option |
| PayPal intelligence | 🔲 Planned | Extract merchant from "PAYPAL *NETFLIX" → correct category |
| Income summary section | 🔲 Planned | Collapsible income row above transaction list |

---

## Session 3 (2026-05-28) — Completed Work

### S3a — Trip Tracker

| Item | File(s) | Notes |
|------|---------|-------|
| `WalletTripEntity` + `WalletTripDao` | `data/entities/WalletTripEntity.kt`, `data/dao/WalletTripDao.kt` | Already committed; wired in AppDatabase |
| DB migration 17→18 | `data/AppDatabase.kt` | Creates `wallet_trips` table; `ALTER TABLE wallet_transactions ADD COLUMN trip TEXT` wrapped in try/catch (column may already exist on fresh v17 installs) |
| `TripTrackerScreen` | `ui/v2/TripTrackerScreen.kt` | Trip CRUD, per-trip budget bar, transaction list filtered by trip |
| Navigation | `Screen.kt`, `V2AppNavHost.kt`, `V2FinanceScreen.kt` | "Trips" pill button in Wallet header → TripTrackerScreen |
| Edit sheet trip tagging | `V2FinanceScreen.kt:TransactionEditSheet` | `LaunchedEffect` loads trips; "TAG TO TRIP" chip row; `updateTransactionTrip()` called on tap |
| Transaction data class | `ui/viewmodels/ExpensesViewModel.kt` | Added `trip: String?` field; populated from `WalletDataLoader.WalletTransaction.trip` |

### S3b — Document Vault Bug Fixes

| Bug | File | Fix |
|-----|------|-----|
| Monthly cost shows `£` instead of `€` | `ui/v2/DocumentsScreen.kt:DocumentCard` | Changed `£` → `€` |
| Documents with no expiry sort to top | `data/dao/FamilyMemberDao.kt:DocumentDao.getAllDocuments()` | `ORDER BY CASE WHEN expiryDate IS NULL THEN 1 ELSE 0 END, expiryDate ASC` |
| Manual document add never creates `DocumentAlertEntity` | `data/repository/DocumentRepositoryImpl.kt` | `insertDocument()` now calls `createAlertIfExpiring()` if `expiryDate` set; `updateDocument()` deletes stale alert and recreates |
| `DocumentsViewModel` missing `updateDocument()` | `ui/v2/DocumentsScreen.kt` | Added method; calls `repo.updateDocument()` |
| Tapping document does nothing | `ui/v2/DocumentsScreen.kt` | `onClick = { editingDoc = doc }` → opens `AddDocumentSheet` pre-populated |
| No edit capability | `ui/v2/components/AddDocumentSheet.kt` | Added `existingDocument: DocumentEntity?` parameter; pre-populates all fields; "Save Document" ↔ "Update Document" label |

### S3c — Salary Detection Overhaul

| Item | File | Notes |
|------|------|-------|
| Expanded income keywords | `data/TransactionCategorizer.kt` | Added: GEHALTSZAHLUNG, GEHALTSÜBERWEISUNG, VERGÜTUNG, ARBEITSENTGELT, MONATSLOHN, WERKSTUDENT, KURZARBEITERGELD, BÜRGERGELD, SALARY, PAYROLL, WAGES, NET PAY, GROSS PAY, AUSZAHLUNG, ELEKTROBIT |
| `salaryConfidenceScore()` | `data/TransactionCategorizer.kt` | 0–100 score: keyword hit (50pts) + amount tier (30pts) + corporate payer GmbH/AG (20pts) |
| Auto-confirm high-confidence salary | `ui/viewmodels/ConfigViewModel.kt:resolveSalaryWorkflow()` | Score ≥ 70 → auto-saves to `SalarySourceEntity`, skips confirmation UI |
| Wider candidate pool | `ui/viewmodels/ConfigViewModel.kt:resolveSalaryWorkflow()` | All credits (not anchor-day only); anchor ±10 days + score ≥ 20 merged |
| "Mark as Salary" button | `ui/v2/V2FinanceScreen.kt:TransactionEditSheet` | Shown for positive-amount transactions; calls `reclassifyTransaction("Salary", applyToHistory)` |

### S3d — Document Upload Pipeline Fixes

| Bug | File | Fix |
|-----|------|-----|
| Passport expiry date silently dropped (date > 2 years in future) | `data/service/VaultDocumentParser.kt` | IDENTITY docs: `plusYears(15)`; all others: `plusYears(5)` |
| MRZ dates not parsed | `data/service/VaultDocumentParser.kt` | Added `DATE_MRZ` regex (6-digit YYMMDD in `<<<` context) + `DATE_YYMMDD` fallback + `parseMrzDate()` |
| More expiry keywords | `data/service/VaultDocumentParser.kt` | Added: gültigkeitsdatum, date of expiry, gültig (broad) |
| Upload title field empty | `ui/v2/V2DocumentVaultScreen.kt` | Extracts `DISPLAY_NAME` from URI via `ContentResolver`; strips extension + underscores; passes as `initialDocumentTitle` to `VaultFolderPickerSheet` |

---

## JUGAAD Vault — Full Architecture Blueprint (updated 2026-05-28)

Standalone privacy-first, offline-first intelligent document vault for Indian and German household documents.

**Stack:** Kotlin · CameraX · PaddleOCR ONNX · ML Kit · Tesseract · PdfBox · OpenCV · Room FTS · WorkManager · Hilt

---

### Processing Lifecycle

```
Document Import (camera / PDF / gallery)
    ↓ SHA-256 hash → duplicate? → link version, skip
    ↓ Store file encrypted
    ↓ Split into pages → pHash per page
    ↓ per page:
       PDF + embedded text (PdfBox >50 chars)? → TEXT PATH (zero OCR)
       else → page_hash in ocr_cache? → reuse cached text
               else → OpenCV preprocess → PaddleOCR ONNX
                       conf <0.75 → ML Kit | conf <0.60 → Tesseract
                       → store in ocr_cache (page_hash, engine_version)
    ↓ all pages merged
Country Detection → IN / DE / OTHER
    ↓
Document-Type Classification (anchor keywords + structural signals + parser votes)
    ↓
Parser Registry lookup → best matching DocumentParser
    ↓
Entity Extraction + Confidence Scoring
    ↓
Normalization Layer (dates, names, IDs, OCR artifacts)
    ↓
Metadata Storage (Room) + FTS indexing
    ↓
Alert Scheduling (expiry / renewal)
```

State machine: `PENDING → SPLITTING → OCR_QUEUED → OCR_DONE → CLASSIFYING → EXTRACTING → INDEXED → FAILED`

---

### OCR Rescanning Minimization

| Layer | Mechanism |
|-------|-----------|
| File dedup | SHA-256 — same file never reprocessed |
| Page dedup | pHash — identical page in two PDFs shares cached OCR |
| OCR cache | `(page_hash, engine_version)` key — model upgrade only rescans affected pages |
| State machine | Completed docs skip pipeline; only FAILED or manual trigger re-enters |
| Text PDF fast path | PdfBox bypasses OCR entirely for digital PDFs |

---

### Parser Registry Architecture

```kotlin
interface DocumentParser {
    val country: Country
    val docType: DocumentType
    fun confidence(signals: ClassificationSignals): Float   // 0.0–1.0
    fun extract(text: String, pages: List<PageText>): ExtractionResult
}
data class ExtractionResult(
    val entities: List<ExtractedEntity>,
    val confidence: Float,
    val partial: Boolean
)
data class ExtractedEntity(
    val type: EntityType,       // EXPIRY_DATE, FULL_NAME, DOB, ID_NUMBER, IBAN …
    val rawValue: String,
    val normalizedValue: String,
    val confidence: Float,
    val pageIndex: Int
)
```

**Registry (most specific → generic fallback):**

| Parser | Country | Key signals |
|--------|---------|-------------|
| `IndianPassportParser` | IN | "REPUBLIC OF INDIA", MRZ TD3, `[A-Z][0-9]{7}` |
| `AadhaarParser` | IN | "UIDAI", "Government of India", `\d{4}\s\d{4}\s\d{4}` |
| `PanParser` | IN | `[A-Z]{5}[0-9]{4}[A-Z]`, "Income Tax" |
| `IndianDrivingLicenceParser` | IN | "Driving Licence", state RTO codes |
| `VoterIdParser` | IN | "Election Commission", EPIC pattern |
| `OciParser` | IN | "Overseas Citizen", "OCI" |
| `GermanPassportParser` | DE | "BUNDESREPUBLIK DEUTSCHLAND", "REISEPASS", MRZ TD3 |
| `AufenthaltstitelParser` | DE | "Aufenthaltstitel", `§` references, "Bundesrepublik" |
| `MeldebescheinigungParser` | DE | "Meldebescheinigung", "Einwohnermeldeamt" |
| `GermanDrivingLicenceParser` | DE | "Führerschein", EU flag + DE code |
| `InsuranceDocParser` | ANY | "Versicherung"/"Insurance", policy number patterns |
| `TaxDocParser` | DE | "Finanzamt", "Steuernummer", "ELSTER" |
| `RentalContractParser` | DE | "Mietvertrag", "Vermieter", monthly cost |
| `EmploymentLetterParser` | DE | "Arbeitsvertrag", salary keywords |
| `GenericFallbackParser` | ANY | Extracts any dates, names, numbers |

---

### Country + Type Classification Pipeline

```
1. Script detection:  Devanagari present → IN
2. Anchor keywords:   "UIDAI" → AADHAAR | "REISEPASS" → DE_PASSPORT | "Aufenthaltstitel" → DE_AT
3. Structural:        MRZ TD3 line → PASSPORT | 12-digit → AADHAAR | PAN regex → PAN
4. Parser vote:       all matching parsers scored; highest ≥ 0.6 wins; else GenericFallback
```

---

### Entity Types + Extraction Strategy

| Entity | Docs | Strategy |
|--------|------|----------|
| Full name | All | Keyword anchor + next line; MRZ `<<` separator |
| Date of birth | All | `DD MMM YYYY` / `DD.MM.YYYY` / MRZ YYMMDD |
| Expiry date | Passport, DL, OCI, Aufenthaltstitel | Same formats + "Date of Expiry" / "Gültig bis" |
| Passport number | Passport | `[A-Z][0-9]{7}` (IN) / 9-char alpha (DE) |
| Aadhaar number | Aadhaar | `\d{4}\s\d{4}\s\d{4}` |
| PAN number | PAN | `[A-Z]{5}[0-9]{4}[A-Z]` |
| IBAN | Bank/Insurance | `DE\d{2}[0-9A-Z]{18}` |
| Steuernummer | Tax | `\d{2,3}/\d{3}/\d{5}` |
| Address | Aadhaar, Meldebescheinigung | Multi-line anchor |
| Monthly cost | Contracts | `€\s*\d+` / monthly keyword |

---

### Normalization Engine

Converts any raw date format → ISO 8601:
- `14/09/31` → `2031-09-14`
- `14.09.2031` → `2031-09-14`
- `14 SEP 2031` → `2031-09-14`
- MRZ `310914` → `2031-09-14`

Also normalizes: MRZ names (`AKKINEPALLI<<SIDDHARTH` → `Siddharth Akkinepalli`), IBANs (strip spaces, validate), OCR artifacts (I→1, O→0, common glitches).

---

### Database Schema

```
documents           — core: file_hash, country, doc_type, classification_confidence, state, ocr_engine_version
document_pages      — per-page: page_hash, text_source, state, ocr_engine_version
ocr_cache           — shared: PK(page_hash, engine_version), ocr_text, confidence
document_entities   — structured: entity_type, raw_value, normalized_value, confidence
document_tags       — AUTO/USER tags
expiry_alerts       — trigger_date, advance_days
processing_jobs     — job_type, state, attempt_count, error_message
family_members      — owner scopes
documents_fts       — VIRTUAL fts4(ocr_text)
entities_fts        — VIRTUAL fts4(raw_value, normalized_value)
```

---

### 10 Specialized Agents

| Agent | Responsibilities | Key risk |
|-------|-----------------|----------|
| **OCR Pipeline** | OcrEngine interface, PaddleOCR ONNX, ML Kit/Tesseract adapters, OcrRouter, OcrCacheManager | ONNX startup latency; JPEG2000 in scanned PDFs |
| **PDF & Preprocessing** | PdfBox text extraction, PdfRenderer rasterizer, OpenCV pipeline, PageHasher | PdfBox OOM on large files |
| **Classification** | Country detection, anchor scan, structural signals, parser confidence vote | Low-confidence ties on damaged scans |
| **Parser Registry** | DocumentParser interface, all 14 parsers, registry lookup, fallback chain | New government layouts breaking patterns |
| **Entity Extraction** | Per-entity extractors, MRZ TD3 parser, positional/anchor extraction, confidence scoring | OCR noise causing wrong field extraction |
| **Normalization Engine** | Date normalizer (all formats), name cleaner, ID validators, OCR artifact fixer | DD/MM vs MM/DD ambiguity |
| **Database & Search** | Room schema v1, DAOs, FTS, ocr_cache, migration chain | FTS out-of-sync after bulk import |
| **Pipeline & Jobs** | WorkManager chains, state machine, retry logic, IncrementalRescanScheduler | Chain broken on process kill |
| **Storage & Security** | Encrypted file store, ContentResolver URI copy, future AES vault | Scoped storage API 30+; URI permission loss |
| **Camera & UX** | CameraX, edge detection, capture quality, vault browser states, processing badges | OpenCV lib size; badge state after restart |

---

### Implementation Phases

| Phase | Deliverable | Fixes included |
|-------|------------|----------------|
| **P0 — Foundation** | Room schema v1, encrypted file store, vault browser, PDF/image import, no OCR yet | — |
| **P1 — Text Extraction** | PdfBox fast path, ML Kit OCR, ocr_cache, state machine, FTS search | — |
| **P2 — Classification + Entities** | Parser Registry + all 14 parsers, entity extraction, normalization, expiry alerts | **TASK-001**: IndianPassportParser with full MRZ TD3 + `DD MMM YYYY` date handling |
| **P3 — Advanced OCR** | PaddleOCR ONNX, OpenCV preprocessing, incremental rescan | — |
| **P4 — Search** | FTS UI, entity filters, tag system, duplicate detection | — |
| **P5 — Polish & Security** | AES vault, CameraX scanner, family scopes, expiry timeline | — |

**Module structure:** `app` · `feature:{vault,scanner,search,family,settings}` · `core:{common,database,storage,ocr,pdf,preprocessing,classification,extraction,pipeline}`

**Status:** Architecture + government document intelligence extension approved. Phase 0 execution pending.

---

## Phase E — Planned: WorkManager Pipeline Architecture + SteuerKlar

### Core principle

Every significant user action (CSV import, receipt scan, document upload) enqueues a WorkManager chain that runs in the background. Scheduled workers (daily, weekly) keep analysis fresh without user interaction. The app becomes reactive — data self-classifies, documents self-route, tax checklist self-updates.

### Google Drive as single source of truth for tax documents

Install **Google Drive for Desktop** on Windows: `C:\Income Tax\` becomes a Drive-synced folder. `tax_api.py` reads it unchanged. Android reads the same folder via Drive API. Both sides stay in sync automatically.

Drive folder structure (mirrors existing `C:\Income Tax\`):
```
Google Drive / Income Tax / {year} /
    German Tax return Info {year}.xlsx
    Payslips /
    Kita /
    Currentbills /
    BankStatements /
    Donation /
    TAXATION_COMPLETE.json
```

**`tax_api.py` is unaffected** — it continues to serve the Jenkins job and Jira Command Center. The Android app is an additional client of the same Drive folder, not a replacement.

### Pipeline trigger → chain map

| Trigger | Worker chain |
|---------|-------------|
| CSV imported | Categorize → RecurringDetect → ReceiptMatch → BankStatDriveUpload → TaxChecklist |
| Receipt scanned in vault | ReceiptMatch → PantryUpdate → TaxDocClassify → DriveTaxRoute |
| Document uploaded to vault | DocClassify → DriveTaxRoute → TaxChecklist |
| Doc tagged tax-relevant | DriveTaxRoute → TaxChecklist |
| Daily 7am (PeriodicWork) | DocExpiryCheck → TaxChecklist → RecurringDetect → PulseRefresh |
| Weekly Sunday (PeriodicWork) | FullDriveTaxScan → DocumentExpiryReport |

### Workers

| Worker | Constraints |
|--------|------------|
| `CategorizationWorker` | none |
| `RecurringDetectionWorker` | none |
| `ReceiptMatchingWorker` | none |
| `BankStatementDriveWorker` | requiresNetwork |
| `DriveTaxRouteWorker` | requiresNetwork |
| `TaxChecklistWorker` | requiresNetwork |
| `DailyAnalysisWorker` | requiresNetwork, runs at 7am |
| `DocumentExpiryWorker` | none |

### SteuerKlar — Tax mode in the app

Native Kotlin port of `tax_api.py` CHECKS logic, reading from the linked Drive folder. New tab in Vault (or Config). Shows live checklist of required tax documents with status, file count, and direct upload shortcut for missing items. Caches last result locally — works offline with "Last synced Xm ago" indicator. "Mark Complete" writes `TAXATION_COMPLETE.json` to Drive (same flag `tax_api.py` reads).

**Document routing table** (drives `DriveTaxRouteWorker`):

| Vault document type / OCR signal | Drive subfolder |
|----------------------------------|-----------------|
| Payslip (Brutto-Netto keywords) | `Payslips/` |
| Lohnsteuerbescheinigung | `Payslips/` |
| Kita / Betreuungskosten | `Kita/` |
| Utility bill (Strom, Gas, Wasser, Telekom) | `Currentbills/` |
| Bank statement PDF | `BankStatements/` |
| Donation / Spendenquittung | `Donation/` |
| CSV import (auto-generated) | `BankStatements/` |

### Phase E — also includes

- **Document expiry timeline** — 90-day visual timeline card at top of Vault (data from existing `document_alerts` table, pure UI)
- **Subscription & recurring cost hub** — Vault view cross-referencing recurring wallet transactions + contract documents; shows total monthly fixed costs as % of salary

---

## Phase F — Planned: Tax Preparation + Full Intelligence Suite

| Feature | Description |
|---------|-------------|
| Tax tagging layer | Long-press transaction or doc → tag with tax category (Work Equipment, Home Office, Medical Expenses, Donations, Work Commute, Other) — English labels as of S3 |
| Annual tax summary export | Group tagged items → PDF/CSV by German tax category |
| Receipt ↔ wallet linking | `ReceiptMatchingWorker` populates existing `linkedVaultEntryId` column; transaction list shows 📄 receipt icon |
| Spending trend mini-charts | 3-cycle sparkline per category tile; "↑ +€42 vs last cycle" label |

---

## Success Criteria for MVP

✓ Android app runs without crash  
✓ Can add/view expenses locally  
✓ Manual backup to device works  
✓ App data survives reinstall from backup  
✓ No data leaves device by default  
✓ Settings show privacy status  

---

## Build Artifacts

- `household-platform/android/` — main Android app
- `household-platform/backend/` — Python backend
- `household-platform/libs/household-core/` — shared Kotlin library
- `household-platform/docs/` — schema and API docs
