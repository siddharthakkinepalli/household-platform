# Household Dashboard Frontend — Quick Reference

**Version:** 1.0.0 | **Updated:** April 30, 2026

---

## 🚀 Quick Start (2 minutes)

```bash
cd frontend
npm install          # First time only
npm run dev          # Start dev server
# Visit http://localhost:3000
```

---

## 📂 Project Layout Quick Map

```
frontend/src/
├── pages/           🔵 Routes → Components (10 files)
├── components/
│   ├── common/      🟢 Reusable UI (Header, Card, Button, etc.)
│   ├── expenses/    ⚪ Expense-specific (future)
│   ├── meals/       ⚪ Meal-specific (future)
│   └── shopping/    ⚪ Shopping-specific (future)
├── services/        🔵 API wrapper (api_client.ts)
├── hooks/           🔵 React Query hooks (all data fetching)
├── utils/           🔵 Helpers (formatting, calc, parse)
├── store/           ⚪ Zustand state (future)
├── App.tsx          🔵 Router + Layout
└── index.css        🔵 Global Tailwind styles
```

**Legend:** 🔵 Active | ⚪ Ready for expansion

---

## 🗺️ Routes → Components

| URL | Component | Status |
|-----|-----------|--------|
| `/` | `pages/Home.tsx` | ✅ |
| `/expenses` | `pages/Expenses.tsx` | ✅ |
| `/expenses/import` | `pages/ExpenseImport.tsx` | ✅ |
| `/expenses/trips` | `pages/ExpenseTrips.tsx` | ⏳ |
| `/meals` | `pages/Meals.tsx` | ⏳ |
| `/meals/recipes` | `pages/MealRecipes.tsx` | ⏳ |
| `/meals/recipes/scan` | `pages/MealRecipeScanner.tsx` | ⏳ |
| `/shopping` | `pages/Shopping.tsx` | ✅ |
| `/settings` | `pages/Settings.tsx` | ✅ |
| `/reports` | `pages/Reports.tsx` | ✅ |

---

## 🧩 Common Components Quick Reference

### Import
```typescript
import { Card, Button, Input, Select, Badge, Loader, EmptyState } from '@components/common'
```

### Usage Examples

**Card Container:**
```tsx
<Card className="p-6">
  <h3>Title</h3>
  {/* content */}
</Card>
```

**KPI Card:**
```tsx
<KPICard 
  title="Monthly Spending" 
  value="€3,500" 
  change={+12}
  trend="up"
  icon="💰"
/>
```

**Button:**
```tsx
<Button variant="primary" size="md" loading={isLoading}>
  Save
</Button>
// Variants: primary, secondary, ghost
// Sizes: sm, md, lg
```

**Input:**
```tsx
<Input 
  label="Amount"
  type="number"
  value={amount}
  onChange={(e) => setAmount(e.target.value)}
  error={errors.amount}
/>
```

**Badge:**
```tsx
<Badge variant="success">Paid</Badge>
// Variants: success, warning, error, info
```

**Empty State:**
```tsx
<EmptyState
  icon="📭"
  title="No data"
  description="Add your first item"
  action={{ label: "Add", onClick: () => {} }}
/>
```

---

## 📡 API Client Usage

### Import
```typescript
import apiClient from '@services/api_client'
```

### Direct Usage (low-level)
```typescript
const { data } = await apiClient.getTransactions({ category: 'Groceries' })
const { data: dashboard } = await apiClient.getExpenseDashboard()
```

### Via React Query Hooks (recommended)
```typescript
import { useTransactions, useCreateTransaction } from '@hooks'

// Query
const { data, isLoading, error } = useTransactions({ category: 'Groceries' })

// Mutation
const createMutation = useCreateTransaction()
await createMutation.mutateAsync({ date, amount, description, category })
```

---

## 🎣 React Query Hooks — Quick List

### Read Data (Queries)
```typescript
useHousehold(id)              // Single household
useTransactions(params)       // All transactions
useExpenseDashboard(params)   // Dashboard stats
useCategories()               // Expense categories
useMealPlans(params)          // Meal plans
useRecipes(params)            // Recipe list
useSearchRecipes(query)       // Recipe search
useShoppingList(params)       // Shopping items
```

### Write Data (Mutations)
```typescript
useCreateTransaction()        // Add expense
useUpdateTransaction()        // Edit expense
useDeleteTransaction()        // Delete expense
useImportCSV()                // Import bank CSV

useCreateMealPlan()           // Add meal plan
useCreateRecipe()             // Add recipe
useScanRecipe()               // OCR image

useAddShoppingItem()          // Add shopping item
useUpdateShoppingItem()       // Edit shopping item
useDeleteShoppingItem()       // Delete shopping item

useExportBackup()             // Export all data
useImportBackup()             // Restore from backup
```

### Usage Pattern
```typescript
const { data, isLoading, error } = useTransactions()

const mutation = useCreateTransaction()
const handleSave = async (data) => {
  try {
    await mutation.mutateAsync(data)
    // Queries auto-invalidated
  } catch (err) {
    console.error(err)
  }
}
```

---

## 🛠️ Utilities Reference

### Formatting
```typescript
import { 
  formatCurrency, 
  formatDate, 
  formatPercent,
  getCategoryIcon,
  getCategoryColor 
} from '@utils/formatting'

formatCurrency(1234.56)       // "€1.234,56"
formatDate('2026-04-30')      // "30.04.2026"
formatPercent(0.856)          // "85.6%"
getCategoryIcon('Groceries')  // "🛒"
getCategoryColor('Groceries') // "bg-green-500/20 text-green-400"
```

### Other Utilities
```typescript
calculatePercentageChange(100, 80)  // 25%
truncateText(long, 50)              // "Long text..."
capitalize('hello')                 // "Hello"
downloadFile(blob, 'data.csv')      // Browser download
formatFileSize(1024*1024)           // "1 MB"
parseCSV(text)                      // Array of arrays
```

---

## 🎨 Tailwind Quick Classes

### Dark Theme Colors
```
text-primary               💙 #3b9eff
bg-dark-bg               ⬛ #0f0f1e
bg-dark-card             ⬛ #1a1a2e
border-dark-border       ⬛ #2d2d44
text-dark-text           ⚪ #e0e0e0
text-status-success      🟢 #10b981
text-status-error        🔴 #ef4444
text-status-warning      🟠 #f59e0b
```

### Common Patterns
```tsx
// Card
<div className="bg-dark-card border border-dark-border rounded-card p-4">

// Button
<button className="px-4 py-2 bg-primary text-white rounded-lg hover:bg-primaryDark">

// Badge
<span className="badge badge-success">Approved</span>

// Input
<input className="w-full px-4 py-2 bg-dark-bg border border-dark-border rounded-lg 
  focus:border-primary focus:ring-1 focus:ring-primary" />

// Grid
<div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
```

---

## 📋 Common Tasks

### Add a new page
1. Create `src/pages/MyPage.tsx`
2. Add import + route to `src/App.tsx`
3. Add link to `src/components/common/Navigation.tsx`

**Template:**
```tsx
import React from 'react'
import { Card, Button } from '@components/common'

const MyPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-bold">My Page</h1>
      <Card>Content</Card>
    </div>
  )
}

export default MyPage
```

### Add API endpoint
1. Add method to `src/services/api_client.ts`
2. Create hook in `src/hooks/index.ts`
3. Use in component

**API Client Example:**
```typescript
async getMyData(params?: any) {
  return this.client.get('/my/data', { params })
}
```

**Hook Example:**
```typescript
export const useMyData = (params?: any) => {
  return useQuery({
    queryKey: ['mydata', params],
    queryFn: async () => {
      const { data } = await apiClient.getMyData(params)
      return data
    },
  })
}
```

### Add a chart
```tsx
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'

<ResponsiveContainer width="100%" height={300}>
  <LineChart data={data}>
    <CartesianGrid strokeDasharray="3 3" stroke="#2d2d44" />
    <XAxis dataKey="date" stroke="#a0a0a0" />
    <YAxis stroke="#a0a0a0" />
    <Tooltip contentStyle={{ backgroundColor: '#1a1a2e', border: '1px solid #2d2d44' }} />
    <Line type="monotone" dataKey="value" stroke="#3b9eff" />
  </LineChart>
</ResponsiveContainer>
```

### Add a form
```tsx
const [data, setData] = useState({ name: '', amount: 0 })
const mutation = useCreateTransaction()

const handleSubmit = async (e: React.FormEvent) => {
  e.preventDefault()
  try {
    await mutation.mutateAsync(data)
    setData({ name: '', amount: 0 })
  } catch (err) {
    console.error(err)
  }
}

return (
  <form onSubmit={handleSubmit} className="space-y-4">
    <Input
      label="Name"
      value={data.name}
      onChange={(e) => setData({ ...data, name: e.target.value })}
      required
    />
    <Input
      type="number"
      label="Amount"
      value={data.amount}
      onChange={(e) => setData({ ...data, amount: parseFloat(e.target.value) })}
      required
    />
    <Button type="submit" loading={mutation.isPending}>Save</Button>
  </form>
)
```

---

## 🔧 Dev Commands

```bash
npm run dev              # Start dev server
npm run build            # Build production
npm run preview          # Preview build locally
npm run type-check       # TypeScript check
npm run lint             # Run ESLint
```

---

## 🌐 Environment Setup

**Create `.env` file:**
```env
VITE_API_URL=http://localhost:5000/api/v1
```

**Optional:**
```env
VITE_APP_NAME=Household Dashboard
VITE_DEBUG=false
```

---

## 🎯 Debug Tips

### React DevTools
1. Install React DevTools browser extension
2. Open DevTools > Components tab
3. Inspect component props, state, hooks

### Network Tab
1. Open DevTools > Network
2. Watch API calls: `http://localhost:5000/api/v1/*`
3. Check response payloads

### TypeScript Errors
```bash
npm run type-check   # Find all type issues
```

### Build Errors
```bash
rm -rf node_modules dist
npm install
npm run build
```

---

## 📱 Responsive Breakpoints

```
Mobile:  320px - 767px   (single column)
Tablet:  768px - 1199px  (2 columns)
Desktop: 1200px+         (3-4 columns)
```

**Tailwind modifiers:**
- `sm:` (640px), `md:` (768px), `lg:` (1024px), `xl:` (1280px)

---

## 🚀 Deploy

### Build
```bash
npm run build
# Output: dist/ folder
```

### Upload to hosting
- **Vercel:** Auto-deploy from Git
- **Netlify:** Same
- **Self-hosted:** Serve `dist/` folder

### Configure backend
```env
VITE_API_URL=https://api.yourdomain.com/v1
```

---

## 🆘 Troubleshooting

| Issue | Solution |
|-------|----------|
| Port 3000 in use | `npm run dev -- --port 3001` |
| API error | Check backend running, update VITE_API_URL |
| Build fails | `rm -rf node_modules && npm install && npm run build` |
| Data not showing | Check Network tab, verify API response |
| Type error | Run `npm run type-check` |
| Weird styling | Clear browser cache, run `npm run build` |

---

## 📚 Learn More

- **Architecture:** `FRONTEND_ARCHITECTURE.md`
- **Getting Started:** `README.md`
- **Delivery Checklist:** `DELIVERY_SUMMARY.md`

---

## 🤝 Code Style

- **Naming:** camelCase for functions/variables, PascalCase for components
- **Components:** Functional components + hooks only
- **Types:** Always type props with interfaces
- **Imports:** Use path aliases (`@components`, `@pages`, etc.)
- **Comments:** JSDoc for complex functions

**Example:**
```typescript
interface ButtonProps {
  variant?: 'primary' | 'secondary'
  onClick: () => void
  children: React.ReactNode
}

export const Button: React.FC<ButtonProps> = ({ 
  variant = 'primary', 
  onClick, 
  children 
}) => (
  <button onClick={onClick} className={`btn btn-${variant}`}>
    {children}
  </button>
)
```

---

**Need help?** Check the docs or ask the team. Happy coding! 🚀

---

**Last Updated:** April 30, 2026
