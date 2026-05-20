# Household Platform — Roadmap

## Vision
A unified household OS: tracks finances, documents, contracts, meals, pantry, family events,
and important dates — all from a single home dashboard.

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

---

## Later / Nice to Have 💡

- [ ] Google Drive full sync (not just receipts)
- [ ] Export vault as PDF report
- [ ] Multi-language receipt support (English receipts)
- [ ] Barcode scanning for pantry items
- [ ] Recurring expense detection (auto-detect monthly subscriptions from receipts)
- [ ] Budget categories with spend-limit alerts
- [ ] Widget for home screen (Android home screen widget)
