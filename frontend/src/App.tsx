import React from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClientProvider, QueryClient } from '@tanstack/react-query'

// Layouts & Common
import Header from '@components/common/Header'
import Navigation from '@components/common/Navigation'

// Pages
import Home from '@pages/Home'
import Expenses from '@pages/Expenses'
import ExpenseTrips from '@pages/ExpenseTrips'
import ExpenseImport from '@pages/ExpenseImport'
import Meals from '@pages/Meals'
import MealRecipes from '@pages/MealRecipes'
import MealRecipeScanner from '@pages/MealRecipeScanner'
import Shopping from '@pages/Shopping'
import Settings from '@pages/Settings'
import Reports from '@pages/Reports'

// Create QueryClient
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes
      gcTime: 1000 * 60 * 10, // 10 minutes (formerly cacheTime)
      retry: 1,
    },
  },
})

const App: React.FC = () => {
  const [sidebarOpen, setSidebarOpen] = React.useState(false)

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <div className="flex h-screen bg-dark-bg text-dark-text overflow-hidden">
          {/* Sidebar Navigation */}
          <Navigation open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

          {/* Main Content */}
          <div className="flex-1 flex flex-col overflow-hidden">
            {/* Header */}
            <Header onMenuClick={() => setSidebarOpen(!sidebarOpen)} />

            {/* Routes */}
            <main className="flex-1 overflow-auto">
              <div className="max-w-7xl mx-auto p-4 sm:p-6 lg:p-8">
                <Routes>
                  {/* Home */}
                  <Route path="/" element={<Home />} />

                  {/* Expenses */}
                  <Route path="/expenses" element={<Expenses />} />
                  <Route path="/expenses/trips" element={<ExpenseTrips />} />
                  <Route path="/expenses/import" element={<ExpenseImport />} />

                  {/* Meals */}
                  <Route path="/meals" element={<Meals />} />
                  <Route path="/meals/recipes" element={<MealRecipes />} />
                  <Route path="/meals/recipes/scan" element={<MealRecipeScanner />} />

                  {/* Shopping */}
                  <Route path="/shopping" element={<Shopping />} />

                  {/* Settings */}
                  <Route path="/settings" element={<Settings />} />

                  {/* Reports */}
                  <Route path="/reports" element={<Reports />} />

                  {/* Fallback */}
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
              </div>
            </main>
          </div>
        </div>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
