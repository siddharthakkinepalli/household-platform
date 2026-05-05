# Household Dashboard Frontend Architecture

## Overview
React 18 web dashboard for unified household management (expenses, meals, recipes, shopping). Built with TypeScript, Vite, Tailwind CSS, and modern React patterns.

**Tech Stack:**
- Frontend: React 18 + TypeScript
- Build: Vite 5
- Styling: Tailwind CSS 3
- State: React Query (data fetching) + Zustand (optional for complex state)
- Charts: Recharts 2
- Routing: React Router v6
- HTTP: Axios with custom API client
- Date handling: date-fns

## Project Structure

```
frontend/
├── src/
│   ├── components/
│   │   ├── common/              # Reusable components
│   │   │   ├── Header.tsx       # Top navigation header
│   │   │   ├── Navigation.tsx   # Sidebar navigation
│   │   │   └── index.tsx        # Card, KPICard, Button, Input, Badge, etc.
│   │   ├── expenses/            # Expense-specific components (future)
│   │   ├── meals/               # Meal-specific components (future)
│   │   ├── shopping/            # Shopping-specific components (future)
│   │   └── dashboard/           # Dashboard-specific components (future)
│   ├── pages/                   # Page components (routes)
│   │   ├── Home.tsx             # Dashboard overview
│   │   ├── Expenses.tsx         # Expense tracking
│   │   ├── ExpenseTrips.tsx     # Trip management
│   │   ├── ExpenseImport.tsx    # CSV import
│   │   ├── Meals.tsx            # Meal planning
│   │   ├── MealRecipes.tsx      # Recipe library
│   │   ├── MealRecipeScanner.tsx# Recipe scanner
│   │   ├── Shopping.tsx         # Shopping list
│   │   ├── Settings.tsx         # Configuration
│   │   └── Reports.tsx          # Analytics & reports
│   ├── services/
│   │   └── api_client.ts        # Axios API client wrapper
│   ├── hooks/
│   │   └── index.ts             # Custom React Query hooks
│   ├── store/                   # Zustand stores (if needed)
│   ├── utils/
│   │   └── formatting.ts        # Date, currency, formatting utilities
│   ├── App.tsx                  # Main app + routing
│   ├── main.tsx                 # React entry point
│   └── index.css                # Global Tailwind styles
├── public/                      # Static assets
├── index.html                   # HTML entry point
├── package.json                 # Dependencies
├── tsconfig.json                # TypeScript config
├── vite.config.ts               # Vite configuration
├── tailwind.config.ts           # Tailwind configuration
└── postcss.config.js            # PostCSS configuration
```

## Routing Map

| Route | Component | Purpose |
|-------|-----------|---------|
| `/` | `Home.tsx` | Dashboard overview with KPIs, charts, activity feed |
| `/expenses` | `Expenses.tsx` | Transaction list, budget, category breakdown |
| `/expenses/trips` | `ExpenseTrips.tsx` | Trip management and debt settlement |
| `/expenses/import` | `ExpenseImport.tsx` | CSV bank statement import |
| `/meals` | `Meals.tsx` | Weekly meal planning calendar |
| `/meals/recipes` | `MealRecipes.tsx` | Recipe search and library |
| `/meals/recipes/scan` | `MealRecipeScanner.tsx` | Upload/capture recipe images |
| `/shopping` | `Shopping.tsx` | Master shopping list with checklist |
| `/settings` | `Settings.tsx` | Household, members, budget, preferences, privacy |
| `/reports` | `Reports.tsx` | Analytics, trends, family breakdown, export |

## Component Hierarchy

```
App (Router)
├── Header
│   ├── Navigation toggle (mobile)
│   ├── Notifications
│   └── User menu
├── Navigation (Sidebar)
│   ├── Logo
│   ├── Nav items (desktop always, mobile toggle)
│   └── Footer
├── Main Content Area
│   └── Routes
│       ├── Home
│       │   ├── KPICards (4-column grid)
│       │   ├── Charts (LineChart, PieChart)
│       │   └── ActivityFeed
│       ├── Expenses
│       │   ├── Stats
│       │   ├── TransactionForm
│       │   ├── Filters
│       │   └── TransactionTable
│       ├── ... (other routes)
│       └── Settings
│           ├── Tabs (Household, Members, Budget, etc.)
│           └── Forms
```

## Reusable Components

**Common Components (`src/components/common/index.tsx`):**

- `<Card>` — Dark themed card container
- `<KPICard>` — Key performance indicator card with trend
- `<Badge>` — Labeled badge (success, warning, error, info)
- `<Button>` — Primary, secondary, ghost variants + loading state
- `<Input>` — Controlled input with label & error message
- `<Select>` — Dropdown with options
- `<Loader>` — Spinning loader (sm, md, lg)
- `<EmptyState>` — Placeholder for empty data

**Layout Components:**

- `<Header>` — Top navigation bar with notifications & user menu
- `<Navigation>` — Sidebar with nav links (responsive)

## API Client

**File:** `src/services/api_client.ts`

Custom Axios wrapper with methods for all backend endpoints:

```typescript
// Expenses
apiClient.getTransactions(params)
apiClient.createTransaction(data)
apiClient.updateTransaction(id, data)
apiClient.deleteTransaction(id)
apiClient.importCSV(file, bank)

// Meals
apiClient.getMealPlans(params)
apiClient.createMealPlan(data)
apiClient.getRecipes(params)
apiClient.createRecipe(data)
apiClient.scanRecipe(file)

// Shopping
apiClient.getShoppingList(params)
apiClient.addShoppingItem(data)

// Backup
apiClient.exportBackup()
apiClient.importBackup(file)
```

## Custom Hooks

**File:** `src/hooks/index.ts`

React Query hooks for data fetching and mutations:

**Queries (read-only):**
```typescript
useHousehold(id)
useTransactions(params)
useExpenseDashboard(params)
useCategories()
useMealPlans(params)
useRecipes(params)
useSearchRecipes(query)
useShoppingList(params)
```

**Mutations (write/delete):**
```typescript
useCreateTransaction()
useUpdateTransaction()
useDeleteTransaction()
useImportCSV()
useCreateMealPlan()
useCreateRecipe()
useScanRecipe()
useAddShoppingItem()
useUpdateShoppingItem()
useDeleteShoppingItem()
useExportBackup()
useImportBackup()
```

All mutations automatically invalidate related queries on success.

## Utilities

**File:** `src/utils/formatting.ts`

- `formatCurrency(amount, currency)` — Format with locale & currency symbol
- `formatNumber(num, decimals)` — Round to decimals
- `formatPercent(value, decimals)` — Format as percentage
- `formatDate(date, format)` — Format using date-fns
- `formatRelativeTime(date)` — "2 hours ago" format
- `getCategoryIcon(category)` — Emoji for category
- `getCategoryColor(category)` — CSS color for category badge
- `calculatePercentageChange(current, previous)` — % change calculation
- `downloadFile(blob, filename)` — Browser file download
- `parseCSV(text)` — Parse CSV string to array

## Design System

**Colors (Dark Theme):**
- Primary: `#3b9eff` (cyan accent)
- Dark BG: `#0f0f1e`
- Dark Card: `#1a1a2e`
- Dark Border: `#2d2d44`
- Dark Text: `#e0e0e0`
- Success: `#10b981` (green)
- Warning: `#f59e0b` (amber)
- Error: `#ef4444` (red)

**Spacing:**
- Card radius: `16px`
- Pill radius: `9999px`
- Padding: 4px base unit (4, 8, 12, 16, 20, etc.)

**Typography:**
- Font: DM Sans (fallback: Roboto)
- Weights: 400 (normal), 500 (medium), 600 (semibold), 700 (bold)

**Animations:**
- FadeUp: 0.3s ease-out
- Transition: 200ms all colors

## State Management

**React Query (primary):**
- Handles all async data fetching
- Auto caching, background refetch, invalidation
- Reduces need for Redux/Context

**Optional Zustand (future):**
For non-async state (UI filters, modal state):
```typescript
// Example: store/filters.ts
import create from 'zustand'

export const useFilterStore = create((set) => ({
  filters: { category: '', month: '' },
  setFilters: (f) => set({ filters: f }),
}))
```

## API Integration Points

**Base URL:** Configurable via `VITE_API_URL` env var or defaults to `http://localhost:5000/api/v1`

**Endpoints consumed:**
- `GET /expenses/transactions` — List transactions
- `POST /expenses/transactions` — Create transaction
- `GET /expenses/dashboard` — Dashboard stats
- `GET /expenses/categories` — Category list
- `POST /expenses/import` — CSV import
- `GET /meals/plans` — Meal plans
- `POST /meals/plans` — Create meal plan
- `GET /meals/recipes` — Recipe list
- `POST /recipes/scan` — OCR recipe image
- `GET /shopping-list` — Shopping list
- `POST /shopping-list/items` — Add item
- `POST /backup/export` — Export backup
- `POST /backup/restore` — Restore backup

**Error Handling:**
- Consistent `ApiError` interface with message, status, code
- Response interceptor catches all HTTP errors
- UI displays errors via EmptyState or toast (TBD)

## Development Setup

```bash
# Install dependencies
npm install

# Start dev server (http://localhost:3000)
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Type check
npm run type-check
```

## Environment Variables

**`.env` file (create in project root):**
```
VITE_API_URL=http://localhost:5000/api/v1
VITE_APP_NAME=Household Dashboard
```

## Responsive Breakpoints

- Mobile: 320-767px (single column, sidebar off-canvas)
- Tablet: 768-1199px (2-column, sidebar always visible)
- Desktop: 1200px+ (full 3-4 column layouts)

**Tailwind Utilities:**
- `sm:` (640px), `md:` (768px), `lg:` (1024px), `xl:` (1280px)

## Performance Optimization

- **Code splitting:** Automatic per-route via Vite
- **Lazy loading:** React.lazy + Suspense (can be added to routes)
- **Caching:** React Query with 5-min stale time
- **Bundle:** CSS purging via Tailwind production build
- **Compression:** gzip by default in Vite

## Security Considerations

- **CORS:** Backend proxy handles cross-origin requests
- **Auth:** Currently stateless; add JWT middleware if needed
- **XSS:** React sanitizes JSX by default
- **CSRF:** Add CSRF token to forms if backend requires

## Testing (Future)

**Suggested libraries:**
- Jest + React Testing Library
- Vitest (faster, Vite-native)
- Playwright (E2E)

```bash
# Example test file structure
src/
  components/
    common/
      __tests__/
        Button.test.tsx
```

## Known Limitations & TODOs

1. **Real-time Sync** — Polling currently; add WebSocket for live updates
2. **Offline Support** — No Service Worker yet; add for offline cache
3. **Authentication** — Stateless UI; backend can add JWT
4. **Notifications** — Toast notifications not yet implemented
5. **File Upload** — Basic CSV; enhance with drag-drop for all files
6. **Dark Mode Toggle** — Currently always dark; add toggle if needed
7. **Accessibility** — Add ARIA labels and keyboard navigation
8. **Internationalization** — Hard-coded English; add i18n if needed

## Deployment

**Vite builds to `dist/` folder:**
```bash
npm run build
# Serves `dist/index.html` at root
# All `/api/*` requests proxy to backend
```

**Hosting options:**
- Vercel, Netlify (auto-deploy from Git)
- Docker: `docker build -t household-dashboard . && docker run -p 3000:3000`
- Self-hosted: Copy `dist/` to web server

## Further Development

### Phase 1 (MVP - Current)
- ✅ Basic layout + routing
- ✅ Dashboard KPIs & charts
- ✅ Expense CRUD + import
- ✅ Shopping list basic
- ✅ Settings tabs

### Phase 2 (Enhancements)
- [ ] Meal planning calendar (drag-drop)
- [ ] Recipe scanner with OCR
- [ ] Shopping price tracking
- [ ] Real-time WebSocket sync
- [ ] Family member sharing UI
- [ ] Budget alerts & notifications

### Phase 3 (Advanced)
- [ ] Service Worker + offline mode
- [ ] Mobile PWA wrapper
- [ ] Data export (PDF, Excel)
- [ ] Analytics dashboards
- [ ] Machine learning recommendations (meal planning)
- [ ] Integration with third-party APIs (Jira, calendar)

## Support & Troubleshooting

**Port conflicts:**
```bash
npm run dev -- --port 3001  # Use alternate port
```

**API connection issues:**
- Check backend is running: `curl http://localhost:5000/health`
- Update `VITE_API_URL` if backend on different host
- Check browser DevTools > Network tab

**Build errors:**
```bash
rm -rf node_modules dist
npm install
npm run build
```

## Contact & Maintenance

Created for Household Platform MVP. Maintained by core dev team.

---

**Last Updated:** April 30, 2026  
**Version:** 1.0.0
