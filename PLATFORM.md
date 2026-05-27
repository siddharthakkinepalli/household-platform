# Household Platform - Build Plan

**Date:** April 27, 2026  
**Last updated:** May 27, 2026  
**Status:** Phase D+ complete — CSV expansion, Smart Budget feed, Receipt OCR, 7-step onboarding shipped  
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
| D+ | 24-category engine, PayPal SEPA extraction, ING support, FTS fix, Smart Alert Feed, Receipt OCR engine (spatial row reconstruction), 7-step onboarding, Android 13 notifications | ✅ Done |
| **D3** | **Salary Allocation Bar in Pulse — Fixed / Discretionary / Remaining segmented bar** | 🔲 Planned |
| **D4** | **Recurring bills detection — RecurringBillEntity, ≥2 cycles ±15% auto-detect** | 🔲 Planned |
| **D5** | **Import review screen for Uncategorized transactions** | 🔲 Planned |
| **E** | **WorkManager pipeline architecture + SteuerKlar tax mode + Drive integration** | 🔲 Planned |
| **F** | **Tax preparation, receipt linking, spending trends, full intelligence suite** | 🔲 Planned |

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
| Salary allocation bar in Pulse | 🔲 Planned | Salary → Fixed → Discretionary → Remaining; segmented bar + collapsible recurring list above 4 tiles |
| Recurring bills detection | 🔲 Planned | RecurringBillEntity, auto-detect ≥2 cycles same merchant ±15% |
| Import review for uncategorized | 🔲 Planned | Post-salary step, inline category picker + make-rule option |
| PayPal intelligence | 🔲 Planned | Extract merchant from "PAYPAL *NETFLIX" → correct category |
| Income summary section | 🔲 Planned | Collapsible income row above transaction list |

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
| Tax tagging layer | Long-press transaction or doc → tag with German tax category (Arbeitsmittel, Homeoffice, Krankenkosten, Spendenquittung) |
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
