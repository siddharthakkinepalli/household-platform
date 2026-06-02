# Household Platform — Feature & Architecture Graph

Complete workflow reference. Update when features ship, screens change, or data flows are modified.

---

## App at a Glance

Privacy-first Android household management app — expenses, documents, family, meals, tax.
All data stays on-device (Room DB). Optional Google Drive sync for tax documents.

**DB version:** 21  
**Min SDK:** 26 (Android 8)  
**Build:** `gradlew :android:assembleDebug` → `android/build/outputs/apk/debug/android-arm64-v8a-debug.apk`

---

## Navigation Map

```
OnboardingScreen (first launch only)
        │
        ▼
V2AppShell ── Bottom Rail ──┬── Home        (V2HomeScreen)
                             ├── Wallet      (V2FinanceScreen)
                             ├── Meals       (V2MealsScreen)
                             ├── Docs        (V2DocumentVaultScreen)
                             ├── Family      (V2FamilyScreen)
                             └── Config      (V2ConfigHubScreen)

Screen push routes (not in rail):
  Wallet       →  Trips                (TripTrackerScreen)
  Docs         →  Scanner              (V2ScannerScreen)
  Docs         →  PantryStaging/:id    (PantryStagingScreen)
  Docs         →  Pantry               (PantryScreen)
  Docs         →  Vault for member/:id (V2DocumentVaultScreen filtered)
  Family       →  FamilyMemberDetail/:id
  FamilyDetail →  Documents/owner/:id  (DocumentsScreen filtered)
  Config       →  MerchantRules
  Config       →  SubscriptionHub
  Config       →  SteuerKlar
  Config       →  TaxSummary
  Config       →  QrHost / QrScan
  Config       →  Family
```

---

## Screen Reference

| Screen | File | ViewModel | Purpose |
|--------|------|-----------|---------|
| Onboarding | `OnboardingScreen.kt` | — | 7-step intro; sets `onboardingDone` pref |
| Home | `V2HomeScreen.kt` | `HomeViewModel` | Insight card, upcoming dates, category budgets, module shortcuts |
| Wallet | `V2FinanceScreen.kt` | `ExpensesViewModel` | Transactions, salary allocation bar, 4-category grid + sparklines, income summary, smart alerts |
| Trips | `TripTrackerScreen.kt` | inline VM | Trip CRUD, per-trip budget bar, transaction list filtered by trip |
| Meals | `V2MealsScreen.kt` | — | Weekly meal planner |
| Document Vault | `V2DocumentVaultScreen.kt` | `VaultViewModel` | Folder tree, 90-day expiry timeline, FTS search bar, document cards with entity detail panel |
| Scanner | `V2ScannerScreen.kt` | `VaultViewModel` | CameraX capture → VaultDocumentParserWorker |
| Pantry Staging | `PantryStagingScreen.kt` | `PantryViewModel` | Review OCR-extracted line items before committing to pantry |
| Pantry | `PantryScreen.kt` | `PantryViewModel` | Stock list, swipe-left to consume |
| Family | `V2FamilyScreen.kt` | `FamilyViewModel` | Member cards, 30-day expiry alerts per member |
| Family Member Detail | `FamilyMemberDetailScreen.kt` | `FamilyViewModel` | Avatar, stats, contracts, vault docs for one member |
| Documents | `DocumentsScreen.kt` | `FamilyViewModel` | Important docs list (passports, contracts); expiry badges; edit-on-tap |
| Config | `V2ConfigHubScreen.kt` | `ConfigViewModel` | CSV import flow, backup/restore, danger zone, nav cards |
| Merchant Rules | `MerchantRulesScreen.kt` | — | CRUD merchant → category rules; Re-categorize All |
| Subscription Hub | `SubscriptionHubScreen.kt` | `SubscriptionHubViewModel` | Auto-detected recurring bills; confirm/dismiss; upcoming outflows |
| SteuerKlar | `SteuerKlarScreen.kt` | — | Tax document checklist against Drive folder |
| Tax Summary | `TaxSummaryScreen.kt` | — | Annual tax summary export |
| QR Host / Scan | `QrHostScreen.kt`, `QrScanScreen.kt` | — | QR code data sharing between devices |

---

## CSV Import Flow (Wallet)

```
User taps "Import CSV" (V2ConfigHubScreen)
        │
        ▼
CsvParserService.parse()
  ├─ Detect bank format (Commerzbank / ING / N26 / generic)
  ├─ MerchantNameCleaner.clean()  — strip SEPA noise, extract PAYPAL *Merchant
  ├─ TransactionCategorizer       — 24+ keyword categories, income detection
  ├─ RuleEngineService            — apply saved MerchantRuleEntity overrides
  └─ Returns ParsedTransactionCandidate list
        │
        ▼
ConfigViewModel.resolveSalaryWorkflow()
  ├─ Auto-match stored SalarySourceEntity (prefix + ±20% amount)
  ├─ OR score all credits with salaryConfidenceScore() (0-100)
  │     score ≥ 70 → auto-confirm, skip UI
  │     score < 70 → ImportWorkflow.SalaryConfirmation (show top-5 candidates)
  └─ User confirms OR skips
        │
        ▼
ConfigViewModel.advanceFromSalary()
  ├─ Filter transactions where category = "Uncategorized" OR "Other"
  ├─ uncategorized.isEmpty → ImportWorkflow.NeedsReview (skip review)
  └─ uncategorized.isNotEmpty → ImportWorkflow.ReviewUncategorized
        │ (D5 review screen)
        ▼
ReviewUncategorizedCard (V2ConfigHubScreen)
  ├─ Per-row ExposedDropdownMenu (8 categories)
  ├─ Checkbox "Always use this category for X" → creates MerchantRuleEntity
  └─ "Done — Finish Import"
        │
        ▼
ConfigViewModel.commitImport()
  ├─ INSERT wallet_transactions (dedup by hash)
  ├─ INSERT import_audits
  ├─ RetroactiveCategorizer.recategorizeAll()
  └─ PipelineManager.enqueueCsvImportChain()
          └─ RecurringDetectionWorker → CategorizationWorker
```

---

## Document Vault OCR Pipeline

```
Document Source
  ├─ Camera scan  (V2ScannerScreen → CameraX capture)
  └─ File picker  (PDF / image from gallery or Files)
        │
        ▼
VaultRepositoryImpl.saveDocument()
  ├─ SHA-256 fileHash → dedup check (getByFileHash)
  └─ Store file → INSERT vault_entries (state=PENDING)
        │
        ▼
VaultDocumentParserWorker.doWork()
  ├─ OcrRouter.init(context)   — registers PaddleOcrEngine before ML Kit
  ├─ Create/update vault_document_pages row
  │
  ├─ PDF path (PdfBox)
  │     ├─ extractEmbeddedText() — zero OCR if ≥50 chars found
  │     └─ rasterAndOcr() per page
  │           ├─ OcrCacheManager.getCached(pageHash, engineVersion)
  │           │     hit  → reuse cached text
  │           │     miss → OcrRouter.recognizeText(bitmap)
  │           │               ├─ OpenCvPreprocessor (grayscale → denoise → adaptiveThreshold)
  │           │               ├─ PaddleOcrEngine    (ONNX, H=32, CTC decode — ASCII/Latin)
  │           │               └─ MlKitOcrEngine     (fallback — handles umlauts, handwriting)
  │           └─ OcrCacheManager.store(pageHash, engineVersion, text)
  │
  ├─ Image path (receipt-like)
  │     └─ LocalReceiptScannerEngine
  │           ├─ ImagePreProcessor.optimizeForOcr()
  │           ├─ ML Kit TextRecognizer (bounding-box spatial reconstruction)
  │           └─ ReceiptTextParser → ParsedReceipt (merchant, total, lineItems)
  │
  ├─ isReceiptLike guard — skip receipt parsing for IDENTITY/INSURANCE/CONTRACT/MEDICAL
  │
  ├─ state: OCR_QUEUED → OCR_DONE
  │
  ├─ ParserRegistry.classify(text, pages)
  │     ├─ Country detection (Devanagari → IN; anchor keywords → DE/IN/ANY)
  │     ├─ Parser confidence vote (14 parsers, best ≥ 0.6 wins, else GenericFallback)
  │     └─ Selected parser: extract() → List<ExtractedEntity>
  │           Parsers: IndianPassportParser, AadhaarParser, PanParser,
  │                    IndianDrivingLicenceParser, VoterIdParser, OciParser,
  │                    GermanPassportParser, AufenthaltstitelParser,
  │                    MeldebescheinigungParser, GermanDrivingLicenceParser,
  │                    InsuranceDocParser, TaxDocParser, RentalContractParser,
  │                    EmploymentLetterParser, GenericFallbackParser
  │
  ├─ NormalizationEngine — dates → ISO 8601, MRZ names, IBAN validation, OCR artifacts
  ├─ INSERT vault_extracted_entities
  ├─ DocumentEntityDao.rebuildFts()  — refresh vault_entities_fts
  ├─ state: INDEXED
  │
  ├─ subFolder preservation — only overrides if user never set a specific folder
  ├─ Receipt line items → INSERT pantry_items (isConfirmed=false) if grocery/drugstore
  │
  └─ PipelineManager.onDocumentUploaded()
          └─ DocumentExpiryWorker (checks document_alerts, posts notifications)
```

---

## Vault FTS Search

```
User types in VaultSearchBar (V2DocumentVaultScreen)
  query.length < 2 → clear results
  query.length ≥ 2 →
        VaultViewModel.onSearchQuery("${query}*")   ← prefix matching
                │
                ▼
        DocumentEntityDao.searchFts(query)
          SELECT e.* FROM vault_extracted_entities e
          INNER JOIN vault_entities_fts fts ON e.rowid = fts.rowid
          WHERE vault_entities_fts MATCH :query
          ORDER BY e.confidence DESC
                │
                ▼
        VaultSearchResults composable
          ├─ Up to 10 hits: displayValue, entity type label, confidence %
          └─ SearchEmptyState if query active but no hits
```

---

## Background Workers (WorkManager)

| Worker | Trigger | What it does |
|--------|---------|--------------|
| `VaultDocumentParserWorker` | Document upload/scan | Full OCR pipeline (see above) |
| `RecurringDetectionWorker` | CSV import, daily | Groups transactions by merchant; flags ≥2 consecutive cycles ±50% as recurring |
| `CategorizationWorker` | CSV import | Re-applies all MerchantRuleEntity overrides to uncategorized transactions |
| `ReceiptMatchingWorker` | Document upload | Fuzzy-links vault receipt to wallet transaction (merchant + date ±3d + amount ±10%) |
| `DocumentExpiryWorker` | Document upload, daily | Posts notifications for `document_alerts` where `daysUntil ≤ 30` and unacknowledged |
| `ExpiryNotificationWorker` | Scheduled daily | Redundant expiry notification path (legacy) |
| `BankStatementDriveWorker` | CSV import | Uploads imported CSV to Drive `IncomeTax/{year}/BankStatements/` |
| `DriveTaxRouteWorker` | Document upload | Routes vault doc to correct Drive tax subfolder by doc type |
| `TaxChecklistWorker` | Scheduled weekly | Scans Drive tax folder, updates `TaxCheckEntity`, sends notifications |
| `DbBackupWorker` | Manual / scheduled | SQLite backup to local storage |
| `DriveSyncWorker` | Manual | Full Drive sync |

**Pipeline chains (PipelineManager):**
```
onCsvImported()    → RecurringDetectionWorker → CategorizationWorker
onDocumentUploaded() → DocumentExpiryWorker
```

---

## Database Tables (DB v21)

### Wallet & Finance

| Table | Key columns | Used by |
|-------|-------------|---------|
| `wallet_transactions` | `hash` UNIQUE, `category`, `trip`, `linkedVaultEntryId` | ExpensesViewModel, CSV import |
| `wallet_transactions_fts` | VIRTUAL FTS4 mirror | Wallet search bar |
| `wallet_trips` | `name`, `budget`, `startDate` | TripTrackerScreen |
| `merchant_rules` | `merchantPattern` PK, `targetCategoryId`, `priority` | RuleEngineService, MerchantRulesScreen |
| `transaction_overrides` | `transactionId`, `overrideCategory` | Manual category edits |
| `excluded_transactions` | `hash` | Permanently excluded transfers |
| `import_audits` | `fileName`, `hash`, `importedAt` | Dedup import re-runs |
| `salary_sources` | `pattern`, `lastAmount`, `anchorDay` | Auto-confirm salary on re-import |
| `recurring_bills` | `merchantPattern`, `averageAmount`, `isActive` | SubscriptionHubScreen |
| `category_thresholds` | `category`, `limitAmount` | Wallet category grid limits |
| `dashboard_prefs` | singleton | Currency, salary anchor day |
| `tax_tags` | `transactionId`, `taxCategory` | Tax tagging layer |

### Vault & Documents

| Table | Key columns | Used by |
|-------|-------------|---------|
| `vault_entries` | `fileHash`, `subFolder`, `docType`, `state` | V2DocumentVaultScreen |
| `vault_document_pages` | `vaultEntryId`, `pageHash`, `state`, `ocrEngineVersion` | VaultDocumentParserWorker |
| `ocr_cache` | PK(`pageHash`, `engineVersion`), `ocrText` | OcrCacheManager — prevents re-OCR |
| `vault_extracted_entities` | `entityType`, `rawValue`, `normalizedValue`, `confidence` | Entity detail panel |
| `vault_entities_fts` | VIRTUAL FTS4 content=vault_extracted_entities | VaultSearchBar |
| `documents` | `ownerId FK`, `title`, `type`, `expiryDate` | DocumentsScreen |
| `document_alerts` | `documentId FK`, `daysUntil`, `isAcknowledged` | ExpiryTimelineCard, DocumentExpiryWorker |

### Family, Pantry & Meals

| Table | Key columns | Used by |
|-------|-------------|---------|
| `family_members` | `name`, `role`, `avatarPath` | FamilyScreen |
| `pantry_items` | `name`, `quantity`, `expiryEstimate`, `vaultId` | PantryScreen |
| `inventory_events` | `pantryItemId`, `delta`, `eventType` | Consume loop |
| `products` | `canonicalName` (22 seeded German items) | Product alias matching |
| `product_aliases` | `rawOcrString + storeName` PK, `frequency` | OCR learning |
| `receipt_lines` | `vaultEntryId`, `rawText`, `price` | Receipt audit |
| `meals_summary` | `weekStart`, `mealPlanJson` | V2MealsScreen |
| `weight_snapshots` | `date`, `weightKg` | (future health tracking) |

### Tax & Backup

| Table | Key columns | Used by |
|-------|-------------|---------|
| `tax_checks` | `year`, `category`, `status`, `fileCount` | SteuerKlarScreen |

---

## Feature Status

| Feature | Status | Screen / Entry Point | Notes |
|---------|--------|---------------------|-------|
| CSV Import | ✅ Live | `V2ConfigHubScreen` | Multi-bank (Commerzbank, ING, N26, generic) |
| Salary Detection | ✅ Live | `ConfigViewModel` | 28 keywords, confidence scoring, auto-confirm ≥70 |
| D5 Import Review | ✅ Live | `V2ConfigHubScreen` — ReviewUncategorizedCard | Per-row category picker + make-rule checkbox |
| Merchant Rules | ✅ Live | `MerchantRulesScreen` | Pattern → category; retroactive re-categorize |
| Wallet — 4-Category Grid | ✅ Live | `V2FinanceScreen` | Groceries / Dining / Travel / Shopping; €spent/€limit |
| Wallet — Sparklines (F4) | ✅ Live | `V2FinanceScreen` | 3-cycle bar chart + delta vs last cycle per tile |
| Wallet — Income Summary (D7) | ✅ Live | `V2FinanceScreen` | Collapsible income card above transaction list |
| Wallet — Smart Alerts | ✅ Live | `V2FinanceScreen` | New subscriptions, upcoming bills, document expiry nudges |
| Salary Allocation Bar (D3) | ✅ Live | `V2FinanceScreen` | Fixed / Discretionary / Remaining segmented bar |
| Trip Tracker | ✅ Live | `TripTrackerScreen` | Trip tagging, per-trip budget bar |
| Recurring Bills (D4) | ✅ Live | `SubscriptionHubScreen` | Auto-detect ≥2 cycles; confirm/dismiss/edit |
| Document Vault | ✅ Live | `V2DocumentVaultScreen` | Folder tree, upload PDF/image |
| JUGAAD Vault P0–P4 | ✅ Live | `V2DocumentVaultScreen` | Full OCR pipeline (14 parsers, entity extraction, FTS) |
| OCR Cache | ✅ Live | `OcrCacheManager` | pHash dedup — same page never re-OCR'd |
| File Dedup | ✅ Live | `VaultRepositoryImpl` | SHA-256 — same file never reprocessed |
| PaddleOCR Engine | ✅ Live | `PaddleOcrEngine` | ONNX Runtime; `en_PP-OCRv4_rec_mobile` (7.3MB); H=48 |
| OpenCV Preprocessing | ✅ Live | `OpenCvPreprocessor` | Grayscale → medianBlur → adaptiveThreshold |
| FTS Vault Search | ✅ Live | `V2DocumentVaultScreen` | Inline search bar; prefix matching; tap result opens document detail |
| **Gemma 4 Document AI** | ✅ Live | `:core:llm-runtime` + `:core:document-ai` | llama.cpp JNI (latest); Gemma 4 E2B Q4_K_M; in-app WiFi download; structured field extraction per document type |
| Expiry Timeline (E3) | ✅ Live | `V2DocumentVaultScreen` | 90-day card; 🔴 ≤7d / 🟡 ≤30d / 🟢 otherwise |
| Document Expiry Notifications | ✅ Live | `DocumentExpiryWorker` | Posts Android notifications; marks acknowledged |
| Receipt OCR + Pantry | ✅ Live | `V2ScannerScreen` → `PantryStagingScreen` | Spatial line-item reconstruction → pantry items |
| Receipt ↔ Wallet Linking | ✅ Live | `ReceiptMatchingWorker` | Fuzzy match by merchant + date + amount |
| Family Module | ✅ Live | `V2FamilyScreen` | Members, roles, 30-day expiry alerts |
| Important Documents | ✅ Live | `DocumentsScreen` | Passports, contracts; expiry badges; edit-on-tap |
| Local Backup / Restore | ✅ Live | `V2ConfigHubScreen` | Gson JSON export/import to Downloads |
| Clear All Data | ✅ Live | `V2ConfigHubScreen` — DangerZoneCard | Clears transactions, bills, salary; preserves rules |
| WorkManager Pipeline (E1) | ✅ Live | `PipelineManager` | CSV import → RecurringDetect → Categorize; Doc upload → ExpiryWorker |
| SteuerKlar | ✅ Live | `SteuerKlarScreen` | Tax document checklist; reads Drive folder |
| Tax Summary | ✅ Live | `TaxSummaryScreen` | Annual export by tax category |
| QR Device Pairing | ✅ Live | `QrHostScreen` / `QrScanScreen` | Share household data via QR |
| Onboarding | ✅ Live | `OnboardingScreen` | 7 steps; fires once; POST_NOTIFICATIONS permission |
| InsightCard Swipe Dismiss | ✅ Live | `InsightCard` | SwipeToDismissBox EndToStart + tap-X |
| Meal Planning | ✅ Live | `V2MealsScreen` | Weekly plans |
| Plaid Bank Sync | 🔨 Building | `backend/plaid_routes.py` | Commerzbank / N26 via Plaid sandbox (backend only) |
| Chat Assistant | 🔲 Planned | `:feature:assistant` | NL queries over household DB using LlamaEngine context provider pattern |
| F1 Tax Tagging | 🔲 Planned | — | Long-press tx/doc → tax category tag |
| F2 Tax Export | 🔲 Planned | — | Group tagged items → PDF/CSV |
| JUGAAD P5 | 🔲 Planned | — | AES vault encryption, CameraX edge detection |
| E2 SteuerKlar Drive Sync | 🔲 Planned | — | Write TAXATION_COMPLETE.json to Drive |

---

## OCR Engine Decision Tree

```
Bitmap input
    │
    ▼
OpenCvPreprocessor.preprocess()
    → RGBA→Grayscale → medianBlur(3) → adaptiveThreshold(Gaussian, 15, 8)
    → runCatching fallback: returns original if OpenCV unavailable
    │
    ▼
OcrRouter — try engines in order, return first non-blank result
    │
    ├─ 1. PaddleOcrEngine   (en_PP-OCRv4_rec_mobile, ONNX, H=48)
    │       loads assets/paddle_ocr_v4_rec.onnx lazily
    │       charset: printable ASCII 33–126 (94 chars, blank at index 94)
    │       → null if model absent or inference fails
    │
    └─ 2. MlKitOcrEngine    (fallback — handles German umlauts, handwriting)
            ImagePreProcessor.optimizeForOcr() (grayscale + 1.7× contrast)
            ML Kit TextRecognizer
```

---

## Credential & Config Reference

| Item | Location | Used by |
|------|----------|---------|
| ONNX model | `android/src/main/assets/paddle_ocr_v4_rec.onnx` | PaddleOcrEngine |
| Gemma 4 model | `context.filesDir/models/gemma4_e2b_q4km.gguf` (downloaded at runtime, 3.2GB) | DocumentInferenceModel |
| Gemma download URL | `ModelDownloadManager.MODEL_DOWNLOAD_URL` in `:core:llm-runtime` | ModelDownloadManager |
| Google Drive scope | `DRIVE_FILE` (app files) + `DRIVE` (tax folder) | DriveSyncWorker, BankStatementDriveWorker |
| Salary anchor day | `dashboard_prefs.salaryAnchorDay` | FiscalDateUtils, CSV import |
| Category limits | `category_thresholds` DB table | Wallet category grid |
| Merchant rules | `merchant_rules` DB table | RuleEngineService |

---

## Key File Locations

```
android/src/main/java/com/household/app/
├─ data/
│   ├─ AppDatabase.kt              ← Room DB v21, all migrations (1→21)
│   ├─ TransactionCategorizer.kt   ← 24+ category keyword engine
│   ├─ config/CsvParserService.kt  ← Multi-bank CSV parser
│   ├─ config/RuleEngineService.kt ← MerchantRule matching
│   ├─ dao/                        ← All Room DAOs
│   ├─ entities/                   ← All Room entities
│   └─ service/
│       ├─ RecurringDetectionService.kt
│       └─ ReceiptResolutionService.kt
├─ domain/
│   ├─ utils/MerchantNameCleaner.kt  ← SEPA / PayPal merchant extraction
│   ├─ utils/FiscalDateUtils.kt      ← Salary cycle boundaries
│   └─ services/HouseholdExport/ImportService.kt
├─ pipeline/
│   └─ PipelineManager.kt          ← WorkManager trigger → chain map
├─ ui/
│   ├─ v2/                         ← All screens
│   ├─ viewmodels/                 ← ExpensesViewModel, VaultViewModel, ConfigViewModel…
│   └─ compose/
│       ├─ navigation/Screen.kt    ← All nav routes
│       ├─ theme/                  ← LumePurple, LumeEmerald, LumeAmber, LumeCyan…
│       └─ components/InsightCard.kt
└─ vault/
    ├─ scan/
    │   ├─ OcrRouter.kt            ← Engine dispatch + OpenCV preprocess
    │   ├─ PaddleOcrEngine.kt      ← ONNX Runtime inference
    │   ├─ MlKitOcrEngine.kt       ← ML Kit fallback
    │   ├─ OpenCvPreprocessor.kt   ← Image preprocessing
    │   ├─ OcrCacheManager.kt      ← pHash dedup cache
    │   └─ PdfPageExtractor.kt     ← PdfBox text + rasterize
    ├─ classification/
    │   ├─ ParserRegistry.kt       ← 14 parser + fallback chain
    │   └─ parsers/                ← IndianPassportParser, AadhaarParser, etc.
    └─ workers/
        ├─ VaultDocumentParserWorker.kt
        ├─ PipelineManager.kt
        ├─ DocumentExpiryWorker.kt
        ├─ RecurringDetectionWorker.kt
        └─ ReceiptMatchingWorker.kt
```
