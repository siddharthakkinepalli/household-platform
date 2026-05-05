import React, { useState } from 'react'
import { Card, Button, Input, Loader, EmptyState, Badge } from '@components/common'
import { useShoppingList, useAddShoppingItem, useDeleteShoppingItem } from '@hooks'

const Shopping: React.FC = () => {
  const [items, setItems] = useState<any[]>([])
  const [newItem, setNewItem] = useState('')
  const [showForm, setShowForm] = useState(false)

  const { data: shoppingList = [], isLoading } = useShoppingList()
  const addMutation = useAddShoppingItem()
  const deleteMutation = useDeleteShoppingItem()

  const handleAddItem = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newItem.trim()) return

    try {
      await addMutation.mutateAsync({ name: newItem, checked: false })
      setNewItem('')
      setShowForm(false)
    } catch (err) {
      console.error('Error:', err)
    }
  }

  const handleToggleItem = async (id: number, checked: boolean) => {
    try {
      await deleteMutation.mutateAsync(id)
    } catch (err) {
      console.error('Error:', err)
    }
  }

  const checkedCount = shoppingList?.filter((item: any) => item.checked).length || 0
  const totalCount = shoppingList?.length || 0

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">Shopping List</h1>
          <p className="text-dark-text/60">Manage your household shopping</p>
        </div>
        <Button variant="primary" onClick={() => setShowForm(!showForm)}>
          {showForm ? '✕ Close' : '+ Add Item'}
        </Button>
      </div>

      {/* Progress */}
      <Card>
        <div className="flex items-center justify-between mb-3">
          <span className="font-medium">Completed</span>
          <span className="text-primary font-bold">{checkedCount}/{totalCount}</span>
        </div>
        <div className="w-full bg-dark-bg rounded-full h-2">
          <div
            className="bg-primary h-2 rounded-full transition-all duration-300"
            style={{ width: `${totalCount > 0 ? (checkedCount / totalCount) * 100 : 0}%` }}
          />
        </div>
      </Card>

      {/* Add Form */}
      {showForm && (
        <Card>
          <form onSubmit={handleAddItem} className="flex gap-2">
            <Input
              type="text"
              placeholder="Item name..."
              value={newItem}
              onChange={(e) => setNewItem(e.target.value)}
              className="flex-1"
            />
            <Button variant="primary" type="submit" loading={addMutation.isPending}>
              Add
            </Button>
            <Button
              variant="secondary"
              type="button"
              onClick={() => {
                setShowForm(false)
                setNewItem('')
              }}
            >
              Cancel
            </Button>
          </form>
        </Card>
      )}

      {/* Shopping List */}
      {isLoading ? (
        <div className="flex justify-center py-12">
          <Loader size="lg" />
        </div>
      ) : totalCount === 0 ? (
        <EmptyState
          icon="🛒"
          title="Shopping list is empty"
          description="Add items to get started"
          action={{ label: '+ Add Item', onClick: () => setShowForm(true) }}
        />
      ) : (
        <Card>
          <div className="space-y-2">
            {shoppingList.map((item: any) => (
              <div key={item.id} className="flex items-center gap-3 p-3 bg-dark-bg rounded hover:bg-dark-border transition-colors">
                <input
                  type="checkbox"
                  checked={item.checked}
                  onChange={(e) => handleToggleItem(item.id, e.target.checked)}
                  className="w-5 h-5 cursor-pointer"
                />
                <span className={`flex-1 ${item.checked ? 'line-through text-dark-text/40' : ''}`}>
                  {item.name}
                </span>
                {item.category && (
                  <Badge variant="info" className="text-xs">{item.category}</Badge>
                )}
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => handleToggleItem(item.id, true)}
                >
                  ✕
                </Button>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}

export default Shopping
