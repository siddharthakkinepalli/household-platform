# Household Dashboard Frontend

Unified React web dashboard for household management (expenses, meals, recipes, shopping).

## Quick Start

### Prerequisites
- Node.js 18+ (check with `node --version`)
- npm or yarn

### Installation

```bash
cd frontend

# Install dependencies
npm install

# Create .env file from template
cp .env.example .env

# Start development server
npm run dev
```

Visit **http://localhost:3000** in your browser.

## Commands

| Command | Purpose |
|---------|---------|
| `npm run dev` | Start Vite dev server with HMR |
| `npm run build` | Build optimized production bundle |
| `npm run preview` | Preview production build locally |
| `npm run type-check` | TypeScript type checking |
| `npm run lint` | Run ESLint |

## Project Structure

```
src/
├── pages/          # Route components
├── components/     # Reusable UI components
├── services/       # API client wrapper
├── hooks/          # React Query custom hooks
├── utils/          # Formatting & utilities
└── store/          # (Future) Zustand stores
```

See [FRONTEND_ARCHITECTURE.md](./FRONTEND_ARCHITECTURE.md) for detailed structure.

## Features

### Dashboard Home
- KPI cards (monthly spending, budget remaining, transactions, savings rate)
- Expense trend chart (30 days)
- Spending by category pie chart
- Recent activity feed

### Expenses Module
- View all transactions with filters
- Add/edit/delete expenses
- CSV bank statement import (auto-detect: Commerzbank, ING, N26, Revolut)
- Budget tracking
- Category breakdown

### Meals Module (Roadmap)
- Weekly meal planning calendar
- Recipe library with search
- Recipe scanner (OCR from images)
- Nutrition summary vs targets

### Shopping Module
- Master shopping list with checklist
- Auto-generated from meal plans
- Price tracking
- Category organization

### Settings
- Household profile & members
- Budget configuration by category
- Meal preferences
- Data backup/restore
- Privacy controls

### Reports
- Monthly spending trends
- Category breakdown
- Family member spending
- Export options (CSV, JSON, PDF)

## Technology Stack

- **React 18** — UI library
- **TypeScript** — Type safety
- **Vite 5** — Build tool (fast!)
- **Tailwind CSS** — Styling
- **React Router v6** — Routing
- **Axios** — HTTP client
- **React Query** — Data fetching
- **Recharts** — Charts & graphs
- **date-fns** — Date formatting

## Environment Configuration

Create `.env` file:
```env
VITE_API_URL=http://localhost:5000/api/v1
VITE_APP_NAME=Household Dashboard
```

## API Backend

Frontend expects backend running at `http://localhost:5000/api/v1`

Backend endpoints consumed:
- `GET /expenses/transactions`
- `POST /expenses/transactions`
- `GET /expenses/dashboard`
- `GET /meals/plans`
- `POST /meals/plans`
- `GET /shopping-list`
- And more...

See [FRONTEND_ARCHITECTURE.md](./FRONTEND_ARCHITECTURE.md#api-integration-points) for full list.

## Design System

**Dark Theme (Cyberpunk-inspired):**
- Primary: `#3b9eff` (cyan)
- Background: `#0f0f1e`
- Card: `#1a1a2e`
- Border: `#2d2d44`
- Text: `#e0e0e0`

**Responsive:**
- Mobile: 320-767px
- Tablet: 768-1199px
- Desktop: 1200px+

## Deployment

### Build
```bash
npm run build
# Output: dist/ folder
```

### Hosting

**Vercel/Netlify (recommended):**
- Connect GitHub repo
- Build: `npm run build`
- Output: `dist`
- Auto-deploys on push

**Docker:**
```dockerfile
FROM node:18-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build
EXPOSE 3000
CMD ["npm", "run", "preview"]
```

**Self-hosted:**
```bash
npm run build
# Serve dist/ folder with any web server
# Proxy /api requests to backend
```

## Development Workflow

1. **Create feature branch**
   ```bash
   git checkout -b feature/meal-planning
   ```

2. **Start dev server**
   ```bash
   npm run dev
   ```

3. **Make changes** (HMR auto-refreshes)

4. **Type check & lint**
   ```bash
   npm run type-check
   npm run lint
   ```

5. **Build & test**
   ```bash
   npm run build
   ```

6. **Commit & push**
   ```bash
   git add .
   git commit -m "Add meal planning calendar"
   git push origin feature/meal-planning
   ```

## Common Tasks

### Add a new page
1. Create file in `src/pages/NewPage.tsx`
2. Add route to `src/App.tsx`
3. Add nav link to `src/components/common/Navigation.tsx`

### Add a new API endpoint
1. Add method to `src/services/api_client.ts`
2. Create React Query hook in `src/hooks/index.ts`
3. Use hook in component

### Style a component
Use Tailwind utility classes + global styles in `src/index.css`

## Troubleshooting

### Port 3000 already in use
```bash
npm run dev -- --port 3001
```

### API connection errors
- Ensure backend is running: `curl http://localhost:5000/health`
- Check `VITE_API_URL` in `.env`
- Look at browser DevTools > Network tab

### Build errors
```bash
rm -rf node_modules dist
npm install
npm run build
```

## Performance Tips

- Use React DevTools Profiler to identify slow renders
- Check bundle size: `npm run build -- --analyze`
- Enable Vite source maps for dev debugging

## Security

- No sensitive data in `.env` (never commit)
- API keys stored in backend only
- CORS handled by backend proxy
- XSS protection via React JSX

## Testing (Future)

```bash
npm install --save-dev vitest @testing-library/react @testing-library/jest-dom
npm run test
npm run test:ui
```

## Contributing

1. Follow TypeScript best practices
2. Use functional components with hooks
3. Keep components small & reusable
4. Document complex logic
5. Test before submitting PR

## License

Private project

## Support

For issues or questions, contact the development team.

---

**Last Updated:** April 30, 2026  
**Version:** 1.0.0
