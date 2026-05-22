# Household Platform - Build Plan

**Date:** April 27, 2026  
**Last updated:** May 22, 2026  
**Status:** Phase C3 complete — committing. Phase D planned (CSV redesign + salary-based budgeting)  
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
| **D** | **CSV redesign — salary detection, uncategorized review, recurring bills** | 🔲 Planned |

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

## Phase D — Planned: CSV redesign + salary-based budgeting

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
- Upcoming section expands to show known recurring bills due this cycle
- Total budget ceiling = detected salary (not hardcoded)
- Discretionary surplus = salary − recurring total − projected discretionary spend

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
