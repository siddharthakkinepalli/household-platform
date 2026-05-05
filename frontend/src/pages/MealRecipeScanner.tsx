import React from 'react'
import { Card, EmptyState } from '@components/common'

const MealRecipeScanner: React.FC = () => {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Recipe Scanner</h1>
        <p className="text-dark-text/60">Scan recipe images and auto-extract ingredients</p>
      </div>
      <EmptyState
        icon="📸"
        title="Recipe scanner coming soon"
        description="Upload or capture recipe images to automatically extract ingredients and cooking instructions"
      />
    </div>
  )
}

export default MealRecipeScanner
