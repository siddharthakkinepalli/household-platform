import React from 'react'
import { Card, EmptyState } from '@components/common'

const MealRecipes: React.FC = () => {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">Recipe Library</h1>
        <p className="text-dark-text/60">Browse and manage your recipes</p>
      </div>
      <EmptyState
        icon="👨‍🍳"
        title="Recipe library coming soon"
        description="Search, organize, and manage your favorite recipes"
      />
    </div>
  )
}

export default MealRecipes
