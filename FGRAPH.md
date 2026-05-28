# Household Platform — Feature Graph

Feature registry, status, and integration map. Update this file when features ship or change.

---

## Feature Status

| Feature | Status | Entry Point | Notes |
|---------|--------|-------------|-------|
| Expense Tracking | ✅ Live | `backend/main.py:/expenses/*` | CRUD + CSV import, dedup by MD5 hash |
| Meal Planning | ✅ Live | `backend/meal_routes.py` | Weekly plans, recipes, nutrition |
| Shopping List | ✅ Live | `backend/meals_routes.py` | Auto-gen from meal plans |
| Recipe Scanner | ✅ Live | `backend/receipt_parser/` | OCR + AI image parsing |
| AI Event Pipeline | ✅ Live | `backend/ai_pipeline.py` | Text/scan → events → automation logs |
| Backup / Restore | ✅ Live | `backend/main.py:/backup/*` | JSON snapshot per household |
| Android App | ✅ Live | `android/` | Kotlin; Wallet tab reads wallet_data.json |
| **Plaid Bank Sync** | 🔨 Building | `backend/plaid_routes.py` | Commerzbank + N26 via Plaid sandbox |
| Chatbot | 📋 Planned | — | Learns from all household data via Anthropic API |
| Clear Data | ✅ Live | `ui/v2/V2ConfigHubScreen.kt` — DangerZoneCard | Clears transactions, recurring bills, salary source, import audits; keeps rules + thresholds |
| Recurring Payments | ✅ Live | `ui/v2/SubscriptionHubScreen.kt` | Auto-detect ≥2 cycles, confirm/dismiss/edit, upcoming outflows, manual add |
| Budget Alerts | 📋 Planned | — | Per-category threshold notifications |
| PDF / Excel Export | 📋 Planned | — | From Reports page |

---

## Expense Data Pipeline

```
CSV Upload (any bank)
    │  POST /expenses/import
    │  dedup: MD5("{hh_id}_{date}_{desc}_{amount}")
    ▼
[household_expenses table]  ◄──── dedup prevents re-import
    ▲
    │  POST /plaid/sync
    │  dedup: MD5("plaid:{plaid_transaction_id}")
Plaid Sync (Commerzbank, N26)

household_expenses
    ├─→ GET /expenses/transactions  →  Expenses page table
    ├─→ GET /expenses/dashboard     →  Home KPI widgets
    ├─→ Reports page charts
    └─→ export_wallet_data.py       →  Android Wallet tab
```

---

## Plaid Integration Flow

```
Settings → Banks tab
    │
    ├─ GET /plaid/link_token          →  Plaid /link/token/create
    │   returns: { link_token }
    │
    ├─ [Plaid Link JS opens in browser]
    │   user authenticates with bank
    │   returns: public_token + institution metadata
    │
    ├─ POST /plaid/exchange_token     →  Plaid /item/public_token/exchange
    │   stores: access_token in bank_connections
    │
    ├─ POST /plaid/sync               →  Plaid /transactions/get
    │   inserts deduplicated rows into household_expenses
    │
    ├─ GET /plaid/connections         →  bank_connections (access_token NOT exposed)
    │
    └─ DELETE /plaid/connections/:id  →  soft-delete (is_active = false)
```

---

## Database Tables Quick Reference

| Table | Key Fields | Notes |
|-------|-----------|-------|
| `household_profiles` | `id`, `currency` | Root entity for everything |
| `household_members` | `household_id`, `role` | admin / member / parent / child |
| `household_expenses` | `hash` UNIQUE | Dedup key for CSV + Plaid + manual |
| `bank_connections` | `item_id` UNIQUE, `access_token` | access_token never returned in API responses |
| `household_events` | `event_type`, `source` | From AI pipeline (scan/voice/manual) |
| `recipes` | `household_id`, `cuisine_type` | |
| `meal_plans` | `start_date`, `end_date`, `is_active` | |
| `shopping_lists` | `meal_plan_id`, `is_completed` | |

---

## Credentials Reference

| Service | Env Vars | Used In |
|---------|----------|---------|
| Plaid | `PLAID_CLIENT_ID`, `PLAID_SANDBOX_SECRET` | `backend/plaid_routes.py` |
| Anthropic | `ANTHROPIC_API_KEY` | `backend/ai_pipeline.py` |
| GoCardless | `GOCARDLESS_API_KEY` | Potential future alternative to Plaid for EU banks |
| OpenRouter | `OPENROUTER_API_KEY` | Alternative LLM routing |

All credentials live in `C:\Projects\credentials.env`.

---

## Duplicate Detection Strategy

| Import Source | Hash Formula | Collision Risk |
|--------------|-------------|----------------|
| CSV import | `MD5("{hh_id}_{date}_{desc}_{amount}")` | Low |
| Plaid sync | `MD5("plaid:{plaid_transaction_id}")` | None (Plaid IDs are stable) |
| Manual entry | No hash set | Can create duplicates manually |
| Cross-source (CSV + Plaid) | Different hashes for same txn | Possible; user can `excluded=true` |
