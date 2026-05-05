import React from 'react'
import { Card, Loader, EmptyState } from '@components/common'
import { useExpenseDashboard } from '@hooks'
import { formatCurrency } from '@utils/formatting'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer, LineChart, Line, PieChart, Pie, Cell
} from 'recharts'

const Reports: React.FC = () => {
  const { data: dashboard, isLoading } = useExpenseDashboard()

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-96">
        <Loader size="lg" />
      </div>
    )
  }

  const monthlyData = dashboard?.monthly_comparison || []
  const categoryData = dashboard?.spending_by_category || []
  const familyData = dashboard?.member_spending || []

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Reports & Analytics</h1>
        <p className="text-dark-text/60">Analyze spending patterns and insights</p>
      </div>

      {/* Summary Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card className="text-center">
          <div className="text-sm text-dark-text/60 mb-1">Average Monthly</div>
          <div className="text-2xl font-bold text-primary">
            {formatCurrency((dashboard?.monthly_spending || 0) / 12)}
          </div>
        </Card>
        <Card className="text-center">
          <div className="text-sm text-dark-text/60 mb-1">Highest Category</div>
          <div className="text-2xl font-bold text-primary">
            {categoryData?.[0]?.category || 'N/A'}
          </div>
        </Card>
        <Card className="text-center">
          <div className="text-sm text-dark-text/60 mb-1">Member Count</div>
          <div className="text-2xl font-bold text-primary">{familyData?.length || 1}</div>
        </Card>
      </div>

      {/* Monthly Comparison */}
      {monthlyData && monthlyData.length > 0 && (
        <Card>
          <h3 className="text-lg font-semibold mb-4">Monthly Spending Comparison</h3>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={monthlyData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#2d2d44" />
              <XAxis dataKey="month" stroke="#a0a0a0" />
              <YAxis stroke="#a0a0a0" />
              <Tooltip
                contentStyle={{ backgroundColor: '#1a1a2e', border: '1px solid #2d2d44' }}
                labelStyle={{ color: '#e0e0e0' }}
                formatter={(value) => formatCurrency(value as number)}
              />
              <Legend />
              <Bar dataKey="budget" fill="#3b9eff" />
              <Bar dataKey="spent" fill="#ff6b6b" />
            </BarChart>
          </ResponsiveContainer>
        </Card>
      )}

      {/* Category Breakdown */}
      {categoryData && categoryData.length > 0 && (
        <Card>
          <h3 className="text-lg font-semibold mb-4">Spending by Category</h3>
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
        </Card>
      )}

      {/* Family Spending */}
      {familyData && familyData.length > 0 && (
        <Card>
          <h3 className="text-lg font-semibold mb-4">Spending by Member</h3>
          <div className="space-y-3">
            {familyData.map((member: any, idx: number) => (
              <div key={idx} className="flex items-center justify-between p-3 bg-dark-bg rounded">
                <div>
                  <p className="font-medium">{member.name}</p>
                  <p className="text-sm text-dark-text/60">{member.transaction_count} transactions</p>
                </div>
                <p className="font-bold text-primary">{formatCurrency(member.total_spending)}</p>
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* Export Actions */}
      <Card>
        <h3 className="text-lg font-semibold mb-4">Export Options</h3>
        <div className="flex flex-wrap gap-2">
          <button className="px-4 py-2 bg-primary text-white rounded-lg hover:bg-primaryDark transition-colors">
            📥 Export as CSV
          </button>
          <button className="px-4 py-2 bg-dark-border text-dark-text rounded-lg hover:bg-dark-border/80 transition-colors">
            📄 Export as PDF
          </button>
          <button className="px-4 py-2 bg-dark-border text-dark-text rounded-lg hover:bg-dark-border/80 transition-colors">
            📊 Export as JSON
          </button>
        </div>
      </Card>
    </div>
  )
}

export default Reports
