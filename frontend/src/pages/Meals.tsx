import React, { useState } from 'react'
import { Card, Button, Loader, EmptyState } from '@components/common'
import { useMealPlans, useCreateMealPlan } from '@hooks'
import { formatDate } from '@utils/formatting'

const Meals: React.FC = () => {
  const [selectedWeek, setSelectedWeek] = useState(new Date().toISOString().split('T')[0])
  const { data: mealPlans = [], isLoading } = useMealPlans({ week: selectedWeek })
  const createMutation = useCreateMealPlan()

  const mealTypes = ['Breakfast', 'Lunch', 'Snack', 'Dinner']
  const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

  const handleAddMeal = () => {
    // TODO: Open modal for adding meal
    alert('Meal planning UI coming soon')
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">Meal Planning</h1>
          <p className="text-dark-text/60">Plan your weekly meals and track nutrition</p>
        </div>
        <Button variant="primary" onClick={handleAddMeal}>
          + Add Meal
        </Button>
      </div>

      {/* Week Selector */}
      <Card>
        <div className="flex items-center gap-4">
          <label className="text-sm font-medium">Week of:</label>
          <input
            type="date"
            value={selectedWeek}
            onChange={(e) => setSelectedWeek(e.target.value)}
            className="px-4 py-2 bg-dark-bg border border-dark-border rounded-lg"
          />
        </div>
      </Card>

      {/* Weekly Calendar */}
      {isLoading ? (
        <div className="flex justify-center py-12">
          <Loader size="lg" />
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-7 gap-2">
          {days.map((day) => (
            <Card key={day} className="p-3">
              <h4 className="font-semibold text-center mb-3">{day}</h4>
              <div className="space-y-2">
                {mealTypes.map((type) => (
                  <div key={type} className="p-2 bg-dark-bg rounded text-sm">
                    <div className="font-medium text-xs text-primary mb-1">{type}</div>
                    <div className="text-dark-text/60 text-xs">
                      <button onClick={handleAddMeal} className="hover:text-primary">
                        + Add meal
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
        <Card className="text-center">
          <div className="text-sm text-dark-text/60 mb-1">Meals Planned</div>
          <div className="text-2xl font-bold text-primary">{mealPlans.length || 0}</div>
        </Card>
        <Card className="text-center">
          <div className="text-sm text-dark-text/60 mb-1">Recipes Used</div>
          <div className="text-2xl font-bold text-primary">0</div>
        </Card>
        <Card className="text-center">
          <div className="text-sm text-dark-text/60 mb-1">Nutrition %</div>
          <div className="text-2xl font-bold text-primary">85%</div>
        </Card>
      </div>
    </div>
  )
}

export default Meals
