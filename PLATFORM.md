# Household Platform - Build Plan

**Date:** April 27, 2026  
**Status:** Parallel build in progress  
**Target:** Running MVP with Expenses + Household Core

---

## Reusable Assets

### From Expensesdroid
- Android app scaffolding (build.gradle, manifest, release signing)
- Play Console integration and publishing script
- Room/SQLite database pattern
- Kotlin architecture (ViewModel, Repository, UI layer)
- WorkManager for background sync

### From Expenses (Python)
- Flask backend structure
- Statement parsing logic (combined_statement_parser.py)
- Categorization engine (simple_categorizer.py)
- Budget reporting

### From IndianMealPlanner
- Flutter adaptive planning logic (referenceable)
- Nutrition engine
- Profile/preference persistence patterns

---

## MVP Scope

### Phase 1A: Platform Core (parallel agents)
1. **Household Core Library** (Kotlin)
   - Shared database abstractions
   - Encryption utilities
   - Backup/restore framework
   - Household model (profile, members, shared settings)

2. **Expenses Module** (Android)
   - Reuse Expensesdroid Room schema
   - Adapt UI to platform design
   - Local backup integration

3. **Household Backend** (Python)
   - Adapt Expenses Flask backend
   - Add household context layer
   - Multi-app endpoint routing

4. **Platform App Shell** (Android)
   - Navigation framework
   - Settings/backup UI
   - Module integration points

### Phase 1B: Testing & Integration
- Verify Expenses ingestion works
- Test backup/restore cycle
- Validate data persistence across updates

---

## Agents Spawned

| Agent | Task | Status |
|-------|------|--------|
| Core Library Builder | Household Kotlin core | Queued |
| Expenses Adapter | Expenses module integration | Queued |
| Backend Unifier | Python backend consolidation | Queued |
| Platform Shell | Android app navigation & UI | Queued |

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

All agents will commit progress here.
