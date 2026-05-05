import React from 'react'
import { Card, KPICard, Loader, EmptyState } from '@components/common'
import { useExpenseDashboard } from '@hooks'
import { formatCurrency, formatDate, formatPercent } from '@utils/formatting'
import {
  LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer
} from 'recharts'

const Home: React.FC = () => {
  const { data: dashboard, isLoading, error } = useExpenseDashboard()

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader size="lg" />
      </div>
    )
  }

  if (error) {
    return (
      <EmptyState
        icon="❌"
        title="Failed to load dashboard"
        description="Please try refreshing the page"
      />
    )
  }

  const kpis = [
    {
      title: 'Monthly Spending',
      value: formatCurrency(dashboard?.monthly_spending || 0),
      unit: 'of budget used',
      icon: '💰',
      change: dashboard?.spending_change || 0,
      trend: (dashboard?.spending_change || 0) > 0 ? 'up' : 'down',
    },
    {
      title: 'Budget Remaining',
      value: formatCurrency(Math.max(0, (dashboard?.budget_limit || 0) - (dashboard?.monthly_spending || 0))),
      unit: 'available',
      icon: '📊',
      change: Math.abs(dashboard?.spending_change || 0),
      trend: (dashboard?.spending_change || 0) < 0 ? 'up' : 'down',
    },
    {
      title: 'Transactions',
      value: dashboard?.transaction_count || 0,
      unit: 'this month',
      icon: '📈',
    },
    {
      title: 'Savings Rate',
      value: formatPercent((dashboard?.savings_rate || 0) * 100),
      unit: 'of income',
      icon: '🎯',
    },
  ]

  const categoryData = dashboard?.spending_by_category || []
  const trendData = dashboard?.daily_trend || []

  return (
    <div className="space-y-8">
      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {kpis.map((kpi, idx) => (
          <KPICard key={idx} {...kpi} />
        ))}
      </div>

      {/* Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Trend Chart */}
        <Card>
          <h3 className="text-lg font-semibold mb-4">Spending Trend (30 days)</h3>
          {trendData.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={trendData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#2d2d44" />
                <XAxis dataKey="date" stroke="#a0a0a0" />
                <YAxis stroke="#a0a0a0" />
                <Tooltip
                  contentStyle={{ backgroundColor: '#1a1a2e', border: '1px solid #2d2d44' }}
                  labelStyle={{ color: '#e0e0e0' }}
                />
                <Legend />
                <Line
                  type="monotone"
                  dataKey="amount"
                  stroke="#3b9eff"
                  strokeWidth={2}
                  dot={{ fill: '#3b9eff', r: 4 }}
                  activeDot={{ r: 6 }}
                />
              </LineChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-72 flex items-center justify-center text-dark-text/40">
              No data available
            </div>
          )}
        </Card>

        {/* Category Chart */}
        <Card>
          <h3 className="text-lg font-semibold mb-4">Spending by Category</h3>
          {categoryData.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={categoryData}
                  cx="50%"
                  cy="50%"
                  labelLine={false}
                  label={({ category, percent }) => `${category} ${(percent * 100).toFixed(0)}%`}
                  outerRadius={80}
                  fill="#3b9eff"
                  dataKey="amount"
                >
                  {categoryData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={['#3b9eff', '#0a9396', '#ff6b6b', '#ffd93d'][index % 4]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{ backgroundColor: '#1a1a2e', border: '1px solid #2d2d44' }}
                  labelStyle={{ color: '#e0e0e0' }}
                  formatter={(value) => formatCurrency(value as number)}
                />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div className="h-72 flex items-center justify-center text-dark-text/40">
              No data available
            </div>
          )}
        </Card>
      </div>

      {/* Activity Feed */}
      <Card>
        <h3 className="text-lg font-semibold mb-4">Recent Activity</h3>
        {dashboard?.recent_transactions && dashboard.recent_transactions.length > 0 ? (
          <div className="space-y-3">
            {dashboard.recent_transactions.slice(0, 5).map((tx: any, idx: number) => (
              <div key={idx} className="flex items-center justify-between p-3 bg-dark-bg rounded-lg">
                <div className="flex-1">
                  <p className="font-medium">{tx.description}</p>
                  <p className="text-sm text-dark-text/60">{formatDate(tx.date)}</p>
                </div>
                <div className="text-right">
                  <p className={`font-semibold ${tx.amount < 0 ? 'text-status-error' : 'text-status-success'}`}>
                    {tx.amount < 0 ? '−' : '+'}{formatCurrency(Math.abs(tx.amount))}
                  </p>
                  <p className="text-xs text-dark-text/60">{tx.category}</p>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-center py-8 text-dark-text/40">
            No recent transactions
          </div>
        )}
      </Card>
    </div>
  )
}

export default Home
