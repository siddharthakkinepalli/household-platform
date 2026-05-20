# Household Platform — Roadmap

## Vision
A unified household OS: tracks finances, documents, contracts, meals, pantry, family events,
and important dates — all from a single home dashboard.

The AI layer is what transforms it from a smart database into a proactive household assistant
that tells you things you didn't know you needed to know.

---

## Done ✅

### Receipt Scanning & Vault
- [x] ML Kit OCR pipeline with `WeightedReceiptRefiner`
- [x] Merchant, amount, date extraction (German receipts — REWE, Lidl, Aldi etc.)
- [x] Fuzzy date parsing (`15.11.2e24` OCR artifacts, space-separated dates)
- [x] Most-frequent amount strategy (receipt total printed twice → prefer repeated)
- [x] Unit-price penalty (per-kg lines no longer win)
- [x] Scanner guide brackets resized for full receipts
- [x] Expandable FAB (scan + upload) with overlay fixed

### Vault UI
- [x] Document gallery with category filter chips
- [x] Click document → detail sheet (full image, title, amount, date, link status)
- [x] Delete document (with confirmation dialog)
- [x] Upload documents (PDF, image) with category + title

### Pantry
- [x] Receipt items parsed and staged after every scan
- [x] `ReceiptItemParser` tightened — German ALL-CAPS product names only, metadata filtered
- [x] Staging screen to review + confirm items before saving
- [x] Pantry browser screen (view confirmed items grouped by category)
- [x] Accessible via 🛒 icon in Vault top bar

### Home Dashboard
- [x] Budget runway gauge (days to salary)
- [x] Unlinked receipts count — tappable, navigates to Vault
- [x] Recent activity feed

### Finance
- [x] Expense tracking with categories
- [x] Receipt → expense linking (paperclip)
- [x] Drive sync for vault entries

---

## Next Up 🔜

### 1. Important Dates System (highest priority)
The connective tissue that makes the platform proactive.

- [ ] `DateEvent` data model: `(id, title, date, type, sourceVaultId?, reminderDays, isRecurring)`
- [ ] Types: `CONTRACT_END`, `RENEWAL`, `PAYMENT_DUE`, `WARRANTY`, `SUBSCRIPTION`, `CUSTOM`
- [ ] Room table + DAO + Repository
- [ ] Manual "Add date" screen (title, date, type, reminder lead days)
- [ ] Auto-extraction from scanned documents (see section below)
- [ ] Home screen "Next 30 days" timeline widget
- [ ] WorkManager-based notification scheduler (remind N days before)

### 2. Document → Date Auto-Extraction
When a contract, insurance doc, or utility bill is scanned, extract key dates automatically.

- [ ] New `DocumentRefiner` (separate from `WeightedReceiptRefiner`) for non-receipt docs
- [ ] Extract: effective date, end date, renewal date, payment due date
- [ ] Pattern library for German documents (Vertragslaufzeit, Kündigungsfrist, gültig bis, etc.)
- [ ] On save → auto-create `DateEvent` entries if confidence is high enough
- [ ] Show extracted dates in the document detail sheet for user confirmation

### 3. Pantry Consume Loop
Without this, pantry is a receipt log, not a real tracker.

- [ ] "Consume" action on pantry items (tap to use 1, long-press for quantity)
- [ ] Quantity tracking: scanning the same item again increments count
- [ ] "Running low" threshold per category (e.g. dairy < 1 = alert)
- [ ] Home dashboard widget: pantry summary + low-stock alerts
- [ ] Delete individual pantry item

### 4. Home Dashboard Upgrade
Turn it into a real household command centre.

- [ ] "Upcoming" section: next 7-30 days of events, dates, reminders
- [ ] Contract expiry alerts (< 30 days remaining → amber, < 7 days → red)
- [ ] Pantry low-stock section
- [ ] Today's meals (from meal planner)
- [ ] Quick-add button: add expense / scan receipt / add date event

### 5. Meals ↔ Pantry Connection
- [ ] Meal plan screen: add recipes with ingredients
- [ ] Cross-check planned meal ingredients against pantry
- [ ] "Missing ingredients" warning before cooking day
- [ ] Shopping list generated from missing ingredients

### 6. Family Events
- [ ] Family member profiles (name, birthday, relationship)
- [ ] Birthday / anniversary reminders feed into DateEvent system
- [ ] Shared household calendar view (month/week)
- [ ] Event types: medical appointment, school event, travel, etc.

### 7. Notifications & Reminders
- [ ] Android notification channel setup
- [ ] WorkManager periodic check for upcoming DateEvents
- [ ] Configurable reminder lead time per event (1 day / 1 week / 1 month)
- [ ] Notification taps deep-link to relevant screen

### 8. Multi-Page Document Scanning
Current camera is single-page and optimised for receipts. Contracts, insurance policies,
and lease agreements are multi-page — they need a different scanning flow.

**Decision: use ML Kit Document Scanner API, not a custom implementation.**

Google's `GmsDocumentScanner` (play-services-mlkit-document-scanner) is essentially
built-in CamScanner: automatic page detection, perspective correction, enhancement
(colour / grayscale / B&W), multi-page capture, and native PDF output.
Building it ourselves (page stitching + perspective warp + PDF generation) would be
months of work for an inferior result.

The existing upload flow already accepts PDFs and serves as the fallback for users who
already have a scanned PDF from CamScanner / Adobe Scan / their phone's built-in scanner.

Checkpoints:
- [ ] Add `play-services-mlkit-document-scanner` dependency
- [ ] New `DocumentScannerLauncher` wrapper — calls `GmsDocumentScannerOptions.Builder`
      with `setPageLimit(20)`, `setResultFormats(PDF + JPEG)`, `setGalleryImportAllowed(true)`
- [ ] FAB "Scan Document" option launches `GmsDocumentScanner` instead of custom camera
      (keep existing receipt scanner as a separate "Scan Receipt" option)
- [ ] On result: save the PDF via `FileStorageService`, store in vault with detected category
- [ ] Pass PDF text (extracted via PdfRenderer + MLKit) through `DocumentRefiner`
      for date extraction (feeds into Important Dates system)
- [ ] Thumbnail generation from first page for vault gallery card

---

## AI Layer — Where It Becomes a Real Household Assistant 🤖

This is what separates a smart document store from a proactive AI tool.
The app has all the data — expenses, documents, contracts, pantry, family, calendar.
The AI layer connects it and surfaces things the user didn't know they needed to know.

### AI-1. LLM Document Understanding (replace rule-based parsers)
Current `WeightedReceiptRefiner` and planned `DocumentRefiner` are regex + scoring heuristics.
They break on unusual formats and require constant maintenance.

Replace with a single LLM call per document:
- Receipt → `{merchant, total, date, line_items[], tax}`
- Contract → `{type, parties, start_date, end_date, notice_period, monthly_cost, auto_renewal, key_clauses[]}`
- Insurance → `{provider, policy_number, coverage_type, renewal_date, annual_premium}`
- Utility bill → `{provider, billing_period, amount_due, due_date, consumption}`

Checkpoints:
- [ ] `DocumentUnderstandingService` — wraps LLM API call, returns structured JSON
- [ ] Gemini API integration (primary) with Claude API as fallback
- [ ] Confidence threshold: if confidence < 0.7, fall back to current rule-based parser
- [ ] Privacy gate: explicit user opt-in before any document text leaves the device
- [ ] On-device fallback: Gemini Nano (via Google AI Edge SDK) for basic extraction
      on supported devices (Pixel 8+, Samsung S24+) without internet/API cost
- [ ] Structured output replaces `RefinedScan` and feeds directly into `DateEvent` creation

### AI-2. The Insight Engine (proactive, cross-domain intelligence)
A background job (WorkManager, runs daily) that looks across ALL stored data and generates
actionable insights the user didn't ask for. This is the core differentiator.

Examples of real insights:
- "Your electricity bill has increased 8% every quarter — you may be on the wrong tariff"
- "Car insurance renews in 6 weeks. You have 3 insurance contracts — bundling could save money"
- "You've bought milk 24 times in 6 months but it never appears in meal plans — possible waste"
- "Grocery spend is 2× normal this month and salary is 4 days away — heads up"
- "Your gym membership renews tomorrow. You haven't scanned a receipt for it in 3 months"

Checkpoints:
- [ ] `InsightEngine` — scheduled WorkManager job, runs nightly
- [ ] Reads across: expenses (last 6 months), vault (all docs), pantry, family, date events
- [ ] LLM prompt with structured household data → returns ranked list of insights
- [ ] `HouseholdInsight` model: `(id, title, body, type, priority, sourceIds[], createdAt, isDismissed)`
- [ ] Home screen insight card (already has the slot) powered by real AI insights, not static rules
- [ ] Dismiss / snooze / act on insight
- [ ] Insight types: COST_ALERT, RENEWAL_WARNING, PATTERN, ANOMALY, SUGGESTION

### AI-3. Natural Language Query Interface
The app has all the data. Users should be able to ask questions instead of navigating screens.

- "When does my internet contract end?"
- "How much did we spend on groceries in April?"
- "What's expiring in the next 60 days?"
- "Do I have eggs in the pantry?"
- "What was the last electricity bill amount?"

Checkpoints:
- [ ] `HouseholdQueryService` — takes natural language query, builds context from DB,
      calls LLM, returns structured answer + source references
- [ ] Chat-style UI on home screen (bottom sheet or dedicated tab)
- [ ] Context window: inject relevant DB summaries (not raw data) to manage token cost
- [ ] Source citations — answer links to the document/expense it came from
- [ ] Voice input via Android SpeechRecognizer (optional, later)

### AI-4. Smart Receipt & Pantry Intelligence
- [ ] LLM fallback for receipts where rule-based confidence < 0.6
- [ ] Item deduplication: "H-MILCH 1,5%" and "MILCH 1.5%" are the same product
- [ ] Auto-categorise pantry items using embeddings (better than keyword matching)
- [ ] "Running low" prediction: if you buy milk every 5 days and last bought 4 days ago, flag it
- [ ] Shopping list generation: cross-check pantry against planned meals → what to buy

### Architecture Decision: On-Device vs Cloud
Privacy is critical for a household app (contracts, finances, family data).

| Task | Approach |
|------|----------|
| Receipt parsing (basic) | On-device — current rule-based, fast, free |
| Receipt parsing (fallback) | Gemini Nano on-device if available, else Gemini API |
| Document understanding (contracts etc.) | Gemini/Claude API — with explicit user consent |
| Insight engine | Gemini/Claude API — summarised data, no raw document text |
| NL query | Gemini/Claude API — context-injected, not full document dump |
| Pantry categorisation | On-device embedding model (TFLite) |

- [ ] Settings screen: AI preferences — on-device only / allow cloud / API key (BYO)
- [ ] Data minimisation: send summaries and structured data to API, never raw document images
- [ ] Clear user-facing explanation of what is and isn't sent to the cloud

---

## Later / Nice to Have 💡

- [ ] Google Drive full sync (not just receipts)
- [ ] Export vault as PDF report
- [ ] Multi-language receipt support (English receipts)
- [ ] Barcode scanning for pantry items
- [ ] Recurring expense detection (auto-detect monthly subscriptions from receipts)
- [ ] Budget categories with spend-limit alerts
- [ ] Widget for home screen (Android home screen widget)
