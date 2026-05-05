import React, { useState } from 'react'
import { Card, Button, Input, Select, Badge, Loader, EmptyState } from '@components/common'
import { useTransactions, useCategories, useCreateTransaction, useDeleteTransaction, useUpdateTransaction } from '@hooks'
import { formatCurrency, formatDate, getCategoryIcon, calculatePercentageChange } from '@utils/formatting'

const Expenses: React.FC = () => {
  const [filters, setFilters] = useState({ category: '', month: '' })
  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [formData, setFormData] = useState({
    date: new Date().toISOString().split('T')[0],
    description: '',
    amount: 0,
    category: '',
  })

  const { data: transactions, isLoading, error } = useTransactions(filters)
  const { data: categories = [] } = useCategories()
  const createMutation = useCreateTransaction()
  const updateMutation = useUpdateTransaction()
  const deleteMutation = useDeleteTransaction()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      if (editingId) {
        await updateMutation.mutateAsync({ id: editingId, data: formData })
        setEditingId(null)
      } else {
        await createMutation.mutateAsync(formData)
      }
      setFormData({ date: new Date().toISOString().split('T')[0], description: '', amount: 0, category: '' })
      setShowForm(false)
    } catch (err) {
      console.error('Error:', err)
    }
  }

  const handleDelete = async (id: number) => {
    if (confirm('Delete this transaction?')) {
      try {
        await deleteMutation.mutateAsync(id)
      } catch (err) {
        console.error('Error:', err)
      }
    }
  }

  const handleEdit = (tx: any) => {
    setEditingId(tx.id)
    setFormData(tx)
    setShowForm(true)
  }

  const totalExpenses = transactions?.reduce((sum: number, tx: any) => {
    return sum + (tx.amount < 0 ? Math.abs(tx.amount) : 0)
  }, 0) || 0

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">Expenses</h1>
          <p className="text-dark-text/60">Manage and track your household expenses</p>
        </div>
        <Button variant="primary" onClick={() => setShowForm(!showForm)}>
          {showForm ? '✕ Close' : '+ Add Expense'}
        </Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
        <Card className="text-center">
          <div className="text-sm text-dark-text/60 mb-1">Total Expenses</div>
          <div className="text-2xl font-bold text-primary">{formatCurrency(totalExpenses)}</div>
        </Card>
        <Card className="text-center">
          <div className="text-sm text-dark-text/60 mb-1">Transactions</div>
          <div className="text-2xl font-bold text-primary">{transactions?.length || 0}</div>
        </Card>
        <Card className="text-center">
          <div className="text-sm text-dark-text/60 mb-1">Categories</div>
          <div className="text-2xl font-bold text-primary">{categories?.length || 0}</div>
        </Card>
      </div>

      {/* Add Form */}
      {showForm && (
        <Card>
          <h3 className="text-lg font-semibold mb-4">{editingId ? 'Edit' : 'Add New'} Expense</h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                type="date"
                value={formData.date}
                onChange={(e) => setFormData({ ...formData, date: e.target.value })}
                required
              />
              <Input
                type="number"
                placeholder="Amount"
                value={formData.amount}
                onChange={(e) => setFormData({ ...formData, amount: parseFloat(e.target.value) })}
                step="0.01"
                required
              />
            </div>
            <Input
              type="text"
              placeholder="Description"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              required
            />
            <Select
              label="Category"
              value={formData.category}
              onChange={(e) => setFormData({ ...formData, category: e.target.value })}
              options={categories.map((cat: any) => ({ value: cat.id, label: cat.name }))}
              required
            />
            <div className="flex gap-2">
              <Button variant="primary" type="submit" loading={createMutation.isPending || updateMutation.isPending}>
                {editingId ? 'Update' : 'Add'} Expense
              </Button>
              <Button
                variant="secondary"
                type="button"
                onClick={() => {
                  setShowForm(false)
                  setEditingId(null)
                  setFormData({ date: new Date().toISOString().split('T')[0], description: '', amount: 0, category: '' })
                }}
              >
                Cancel
              </Button>
            </div>
          </form>
        </Card>
      )}

      {/* Filters */}
      <div className="flex gap-4 flex-wrap">
        <Select
          options={categories.map((cat: any) => ({ value: cat.id, label: cat.name }))}
          value={filters.category}
          onChange={(e) => setFilters({ ...filters, category: e.target.value })}
        />
        <Input
          type="month"
          value={filters.month}
          onChange={(e) => setFilters({ ...filters, month: e.target.value })}
          className="max-w-xs"
        />
      </div>

      {/* Transactions List */}
      {isLoading ? (
        <div className="flex justify-center py-12">
          <Loader size="lg" />
        </div>
      ) : error ? (
        <EmptyState icon="❌" title="Error loading expenses" description="Please try again" />
      ) : transactions?.length === 0 ? (
        <EmptyState
          icon="📭"
          title="No expenses yet"
          description="Add your first expense to get started"
          action={{ label: '+ Add Expense', onClick: () => setShowForm(true) }}
        />
      ) : (
        <Card>
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-dark-border">
                  <th className="text-left py-3 px-4 font-semibold">Date</th>
                  <th className="text-left py-3 px-4 font-semibold">Description</th>
                  <th className="text-left py-3 px-4 font-semibold">Category</th>
                  <th className="text-right py-3 px-4 font-semibold">Amount</th>
                  <th className="text-right py-3 px-4 font-semibold">Actions</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((tx: any) => (
                  <tr key={tx.id} className="border-b border-dark-border/50 hover:bg-dark-bg transition-colors">
                    <td className="py-3 px-4">{formatDate(tx.date)}</td>
                    <td className="py-3 px-4 font-medium">{tx.description}</td>
                    <td className="py-3 px-4">
                      <Badge variant="info">{getCategoryIcon(tx.category)} {tx.category}</Badge>
                    </td>
                    <td className={`py-3 px-4 text-right font-semibold ${tx.amount < 0 ? 'text-status-error' : 'text-status-success'}`}>
                      {tx.amount < 0 ? '−' : '+'}{formatCurrency(Math.abs(tx.amount))}
                    </td>
                    <td className="py-3 px-4 text-right space-x-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleEdit(tx)}
                      >
                        Edit
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => handleDelete(tx.id)}
                      >
                        Delete
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  )
}

export default Expenses
