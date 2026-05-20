# Household Platform — Roadmap

## Vision
A private household operating system with contextual intelligence.
Not "AI organiser." A household memory and decision system.

Tracks finances, documents, contracts, meals, pantry, family events, and important dates.
Intelligence layer surfaces what you didn't know you needed to know.

---

## Phase Model

**Phase 1 — Deterministic Infrastructure** ← current stage
Reliable ingestion, normalization, persistence, retrieval, domain modeling.
Optimises for: trust, explainability, offline support, predictable behavior, zero cost.
This is correct. Household systems require trust above all else.
Users tolerate "AI is limited." They do not tolerate wrong bill totals or hallucinated dates.

**Phase 2 — Intelligence Foundations**
Canonical entity layer, LLM extraction pipeline with validation, vectorized memory.
Prerequisite for everything in Phase 3. Cannot skip.

**Phase 3 — Household Cognition**
Insight engine: cross-domain, proactive, longitudinal.
Financial drift. Expiration intelligence. Consumption patterns. Family coordination.
This is the product moat — not the LLM, but the accumulated household context.

**Phase 4 — Conversational Interface**
Natural language queries over a mature data layer.
Only valuable after Phase 3. Without proactive intelligence beneath it, chat becomes
"search, but slower."

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

## Phase 2 — Intelligence Foundations 🧱

These are prerequisites. The insight engine and conversational layer cannot function
reliably without this layer being solid first.

### AI-0. Canonical Entity Normalization (highest leverage, build first)
This is underestimated and more important than the LLM itself.
Without it, insights are noisy, trends are unreliable, and AI context quality degrades.

The problem:
```
"Vodafone GmbH" / "Vodafone" / "VODAFONE DE" / "VF D2"  →  provider: Vodafone, category: Telecom
"REWE" / "Rewe Markt" / "REWE Kevin Junker oHG"          →  merchant: REWE, category: Grocery
"HUK-Coburg" / "HUK COBURG" / "HUK Versicherung"         →  provider: HUK-Coburg, category: Insurance
```

Every domain has this problem: merchants, utility providers, insurance companies,
subscription services, family member names, recurring payees.

Checkpoints:
- [ ] `EntityRegistry` — canonical store: `(id, canonicalName, aliases[], category, metadata)`
- [ ] Fuzzy match on ingest: Levenshtein + token overlap, confidence-scored
- [ ] Auto-suggest canonical match on new entity, user confirms or creates new
- [ ] Pre-seed with common German merchants, utilities, insurers, telecoms
- [ ] All existing records link to canonical entity (not raw string)
- [ ] Canonical name used in all insights, trends, and LLM context — never raw OCR strings
- [ ] Admin screen to view/edit/merge canonical entities

### AI-1. LLM Extraction Pipeline with Validation
Current regex refiners are Phase 1 — correct for now, but maintenance cost grows
exponentially as receipt formats, layouts, OCR drift, and languages diversify.

The transition is gradual, not a rewrite:
- Keep deterministic refiners as the baseline
- Add LLM as fallback when confidence < threshold
- Validate ALL LLM output deterministically before persisting
- Never trust raw LLM output for financial or legal records

**Production pipeline (the right architecture):**
```
OCR text
  → LLM extraction  (structured JSON output)
  → Validation layer (deterministic checks)
  → Confidence scoring
  → User confirmation (if confidence < threshold)
  → Persistence
```

**Validation layer checks (non-negotiable):**
- Does extracted total appear verbatim in OCR text?
- Is date parseable and plausible (not in the future, not > 10 years ago)?
- Does currency match document language/region?
- Line item sum ≈ total (within rounding)?
- VAT math plausible for stated category?
- Is this a duplicate of a recently stored document?
- Does merchant name exist in entity registry?

LLM extraction targets:
- Receipt → `{merchant, total, date, line_items[], tax_rate, currency}`
- Contract → `{type, parties, start_date, end_date, notice_period_days, monthly_cost, auto_renewal}`
- Insurance → `{provider, policy_number, coverage_type, renewal_date, annual_premium}`
- Utility bill → `{provider, billing_period, amount_due, due_date, consumption_kwh}`

Checkpoints:
- [ ] `LLMExtractionService` — wraps API call, returns typed structured output
- [ ] `ExtractionValidator` — deterministic validation layer, returns `ValidationResult`
- [ ] Gradual rollout: LLM as fallback first, promote to primary once validated
- [ ] Gemini API primary, Claude API secondary (both support structured output / JSON mode)
- [ ] On-device: Gemini Nano for basic classification on supported devices (Pixel 8+, S24+)
- [ ] Privacy gate: explicit per-document opt-in before text leaves device
- [ ] Confidence score surfaces in UI — user sees when extraction is uncertain

### AI-2. Vectorized Household Memory
Required for semantic querying, similarity search, and giving the LLM
useful context without dumping the entire database into a prompt.

Checkpoints:
- [ ] Embed all stored text (document OCR, expense descriptions, pantry items, notes)
      using on-device embedding model (MiniLM via ONNX / TFLite)
- [ ] Vector store (SQLite with sqlite-vec extension, or local FAISS index)
- [ ] Semantic search: "find all documents related to my car" → nearest neighbors
- [ ] Context retrieval for insight engine: given an insight topic, fetch relevant
      document chunks rather than all raw data
- [ ] Re-embed on document update; background job for initial indexing of existing vault

---

## Phase 3 — Household Cognition 🧠

The product moat. Not the LLM — the accumulated longitudinal household context.

### AI-3. Insight Engine
Background job (WorkManager, nightly) — reads across ALL domains, generates ranked
insights the user did not ask for. This is the core differentiator.

**Four insight categories:**

**Financial drift detection** — households are terrible at noticing slow financial leakage
- Utility cost creep: "Electricity up 8% every quarter for a year — check your tariff"
- Subscription accumulation: "You have 7 active subscriptions totalling €94/month"
- Insurance duplication: "3 separate accident insurance policies detected"
- Spending deviation: "Dining spend 2× normal this month, salary in 4 days"
- Recurring cost forecasting: "Based on history, expect ~€340 in fixed costs this month"

**Expiration intelligence** — especially critical in Germany where contracts auto-renew
- Contract notice windows: "Lease ends March 2026, notice deadline is December 2025"
- Auto-renewal alert: "Gym contract renews in 3 weeks — last chance to cancel"
- Document expiry: passport, driving licence, visa, residence permit
- Warranty expiry: "Washing machine warranty expires next month"
- Tax / filing deadlines

**Consumption intelligence** — makes the pantry genuinely useful
- Food waste signal: "Bought milk 24× in 6 months, rarely appears in meal plans"
- Depletion forecast: "Based on purchase frequency, eggs likely running low"
- Meal-vs-purchase mismatch: "Planned pasta week but no pasta in pantry"
- Seasonal patterns: "Heating costs spike in Oct — budget accordingly"

**Family operational coordination**
- Overlapping appointments / travel conflicts
- School document tracking and deadlines
- Vaccination and medical appointment gaps
- Vehicle maintenance schedules (MOT, service intervals)
- Shared shopping — item requested by family member vs pantry stock

Checkpoints:
- [ ] `InsightEngine` service — WorkManager, nightly, reads structured summaries not raw data
- [ ] `HouseholdInsight` model: `(id, title, body, category, priority, sourceIds[], actionRoute, isDismissed)`
- [ ] Insight categories: `FINANCIAL_DRIFT`, `EXPIRATION`, `CONSUMPTION`, `FAMILY_OPS`, `ANOMALY`
- [ ] Context builder: constructs minimal structured summary per domain for LLM prompt
      (never raw document text — summaries only, to minimise data exposure and token cost)
- [ ] Home screen powered by real InsightEngine output, not static heuristics
- [ ] Dismiss / snooze (remind in N days) / act (deep-link to source)
- [ ] Insight history — user can review past surfaced insights
- [ ] Feedback loop: dismissed vs acted-on insights improve future relevance ranking

---

## Phase 4 — Conversational Interface 💬

Built last, on top of a mature data layer. Without Phase 3 beneath it, this becomes
"search, but slower."

### AI-4. Natural Language Query
- [ ] `HouseholdQueryService` — NL query → vector retrieval → LLM answer + source refs
- [ ] Context injection: fetch relevant chunks via vector memory (AI-2), not full DB dump
- [ ] Source citations: every answer links to the document/expense it came from
- [ ] Query scope declared in prompt: only answer from household data, no hallucination
- [ ] Chat UI: home screen bottom sheet, persisted query history
- [ ] Voice input via Android SpeechRecognizer (future)

---

## Privacy & On-Device Architecture

Household data is among the most sensitive: contracts, finances, family identities,
addresses, children's data. This is not optional — especially for European users (GDPR).

| Task | Where it runs | Rationale |
|------|--------------|-----------|
| OCR | On-device (ML Kit) | Always offline, no exposure |
| Entity matching / classification | On-device | Fast, deterministic, free |
| Embeddings / vector search | On-device (ONNX/TFLite) | Sensitive text never leaves |
| Receipt extraction (basic) | On-device rule-based | Current system, reliable |
| Receipt extraction (complex) | Gemini Nano on-device → Gemini API | Fallback chain |
| Contract / doc understanding | Cloud API (Gemini / Claude) | Explicit consent required |
| Insight engine context | Cloud API — structured summaries only | Never raw document text |
| NL query context | Cloud API — retrieved chunks only | Minimal exposure |

Checkpoints:
- [ ] AI settings screen: on-device only / allow cloud (per document type) / BYO API key
- [ ] Per-document cloud consent prompt (shown once per vault category, remembered)
- [ ] Data minimisation enforced in code: `ContextBuilder` never includes raw OCR strings
      in cloud-bound payloads — only structured extracted fields and summaries
- [ ] Audit log: what was sent to cloud API, when, for which document ID
- [ ] Full on-device mode must remain functional (degraded intelligence, not broken app)

---

## Later / Nice to Have 💡

- [ ] Google Drive full sync (not just receipts)
- [ ] Export vault as PDF report
- [ ] Multi-language receipt support (English receipts)
- [ ] Barcode scanning for pantry items
- [ ] Recurring expense detection (auto-detect monthly subscriptions from receipts)
- [ ] Budget categories with spend-limit alerts
- [ ] Widget for home screen (Android home screen widget)
