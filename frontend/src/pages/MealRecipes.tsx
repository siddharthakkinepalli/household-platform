import React from 'react'
import { EmptyState, Button } from '@components/common'
import { Link } from 'react-router-dom'

const MealRecipes: React.FC = () => {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Recipe Library</h1>
          <p className="text-dark-text/60">Browse and manage your recipes</p>
        </div>
        <Link to="/meals/recipes/scan">
          <Button variant="primary">📷 Scan Receipt</Button>
        </Link>
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
