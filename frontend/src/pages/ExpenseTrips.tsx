import React from 'react'
import { Card, EmptyState } from '@components/common'

const ExpenseTrips: React.FC = () => {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Trip Management</h1>
        <p className="text-dark-text/60">Manage group trips and settle debts</p>
      </div>
      <EmptyState
        icon="✈️"
        title="Trip feature coming soon"
        description="Organize expenses for group trips and settle debts between members"
      />
    </div>
  )
}

export default ExpenseTrips
