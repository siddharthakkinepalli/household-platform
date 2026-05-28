"""
household_models.py
SQLAlchemy models for Household Platform.

Tables:
  - household_profiles       : household entities
  - household_members        : family members
  - household_expenses       : unified expense tracking
  - household_backups        : backup metadata
  - household_events         : normalized events from pipeline
  - automation_logs          : automation action log
  - recipes                  : recipe library
  - ingredients              : ingredient database
  - recipe_ingredient_mapping: recipe composition
  - nutrition_info           : nutrition facts per recipe
  - meals                    : daily meal assignments
  - meal_plans               : weekly/custom meal plans
  - shopping_lists           : aggregated shopping lists
  - shopping_list_items      : individual grocery items
"""

import json
from datetime import datetime
from sqlalchemy import Column, Integer, String, Float, DateTime, Boolean, ForeignKey, Text
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import relationship

Base = declarative_base()


class HouseholdProfile(Base):
    """Represents a household entity."""
    __tablename__ = 'household_profiles'

    id = Column(Integer, primary_key=True, autoincrement=True)
    name = Column(String(255), nullable=False)
    description = Column(Text)
    currency = Column(String(3), default='USD')
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    members = relationship('HouseholdMember', back_populates='household', cascade='all, delete-orphan')
    expenses = relationship('HouseholdExpense', back_populates='household', cascade='all, delete-orphan')
    backups = relationship('HouseholdBackup', back_populates='household', cascade='all, delete-orphan')
    recipes = relationship('Recipe', cascade='all, delete-orphan')
    ingredients = relationship('Ingredient', cascade='all, delete-orphan')

    def to_dict(self):
        return {
            'id': self.id,
            'name': self.name,
            'description': self.description,
            'currency': self.currency,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None,
        }


class HouseholdMember(Base):
    """Represents a family member in the household."""
    __tablename__ = 'household_members'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    name = Column(String(255), nullable=False)
    role = Column(String(100), default='member')  # member, admin, parent, child
    email = Column(String(255))
    created_at = Column(DateTime, default=datetime.utcnow)

    # Relationships
    household = relationship('HouseholdProfile', back_populates='members')

    def to_dict(self):
        return {
            'id': self.id,
            'household_id': self.household_id,
            'name': self.name,
            'role': self.role,
            'email': self.email,
            'created_at': self.created_at.isoformat() if self.created_at else None,
        }


class HouseholdExpense(Base):
    """Represents an expense transaction in the household."""
    __tablename__ = 'household_expenses'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    date = Column(String(10), nullable=False)  # ISO format YYYY-MM-DD
    description = Column(String(255))
    amount = Column(Float, nullable=False)  # negative = expense, positive = income
    category = Column(String(100))
    budget_category = Column(String(100))
    bank = Column(String(100))  # Commerzbank, ING, N26, Revolut, etc.
    member_id = Column(Integer, ForeignKey('household_members.id'))
    imported_at = Column(DateTime, default=datetime.utcnow)
    hash = Column(String(255), unique=True)  # prevents duplicate imports
    excluded = Column(Boolean, default=False)

    # Relationships
    household = relationship('HouseholdProfile', back_populates='expenses')
    member = relationship('HouseholdMember')

    def to_dict(self):
        return {
            'id': self.id,
            'household_id': self.household_id,
            'date': self.date,
            'description': self.description,
            'amount': self.amount,
            'category': self.category,
            'budget_category': self.budget_category,
            'bank': self.bank,
            'member_id': self.member_id,
            'imported_at': self.imported_at.isoformat() if self.imported_at else None,
            'excluded': self.excluded,
        }


class HouseholdBackup(Base):
    """Represents a backup of household data."""
    __tablename__ = 'household_backups'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    backup_name = Column(String(255), nullable=False)
    backup_path = Column(String(500))  # local file path
    data_size = Column(Integer)  # bytes
    created_at = Column(DateTime, default=datetime.utcnow)
    expires_at = Column(DateTime)  # optional expiration

    # Relationships
    household = relationship('HouseholdProfile', back_populates='backups')

    def to_dict(self):
        return {
            'id': self.id,
            'household_id': self.household_id,
            'backup_name': self.backup_name,
            'backup_path': self.backup_path,
            'data_size': self.data_size,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'expires_at': self.expires_at.isoformat() if self.expires_at else None,
        }


class HouseholdEvent(Base):
    """Represents a normalized household event/reminder/expense-trigger item."""
    __tablename__ = 'household_events'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    title = Column(String(255), nullable=False)
    event_date = Column(String(10))  # YYYY-MM-DD
    event_time = Column(String(5))   # HH:MM
    event_type = Column(String(50), default='event')
    source = Column(String(50), default='manual')
    raw_text = Column(Text)
    confidence = Column(Float, default=0.0)
    created_at = Column(DateTime, default=datetime.utcnow)

    household = relationship('HouseholdProfile')

    def to_dict(self):
        return {
            'id': self.id,
            'household_id': self.household_id,
            'title': self.title,
            'event_date': self.event_date,
            'event_time': self.event_time,
            'event_type': self.event_type,
            'source': self.source,
            'raw_text': self.raw_text,
            'confidence': self.confidence,
            'created_at': self.created_at.isoformat() if self.created_at else None,
        }


class AutomationLog(Base):
    """Logs automation actions created from event graph rules."""
    __tablename__ = 'automation_logs'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    event_id = Column(Integer, ForeignKey('household_events.id'))
    action = Column(String(100), nullable=False)
    payload = Column(Text)
    status = Column(String(20), default='queued')
    created_at = Column(DateTime, default=datetime.utcnow)

    household = relationship('HouseholdProfile')
    event = relationship('HouseholdEvent')

    def to_dict(self):
        return {
            'id': self.id,
            'household_id': self.household_id,
            'event_id': self.event_id,
            'action': self.action,
            'payload': self.payload,
            'status': self.status,
            'created_at': self.created_at.isoformat() if self.created_at else None,
        }


# ==============================================================================
# Meal Planning & Recipe Models
# ==============================================================================


class Recipe(Base):
    """Represents a recipe in the household."""
    __tablename__ = 'recipes'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    name = Column(String(255), nullable=False)
    description = Column(Text)
    cuisine = Column(String(100))  # e.g., 'Indian', 'Italian', 'Mexican'
    cuisine_type = Column(String(50), default='vegetarian')  # vegetarian, vegan, meat, seafood
    servings = Column(Integer, default=1)
    prep_time_minutes = Column(Integer)  # preparation time
    cook_time_minutes = Column(Integer)  # cooking time
    total_time_minutes = Column(Integer)  # total time
    difficulty = Column(String(20), default='medium')  # easy, medium, hard
    source = Column(String(255))  # recipe source (book, website, manual)
    instructions = Column(Text)  # cooking instructions
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    ingredients = relationship('RecipeIngredientMapping', cascade='all, delete-orphan')
    nutrition_info = relationship('NutritionInfo', uselist=False, cascade='all, delete-orphan')

    def to_dict(self):
        return {
            'id': self.id,
            'household_id': self.household_id,
            'name': self.name,
            'description': self.description,
            'cuisine': self.cuisine,
            'cuisine_type': self.cuisine_type,
            'servings': self.servings,
            'prep_time_minutes': self.prep_time_minutes,
            'cook_time_minutes': self.cook_time_minutes,
            'total_time_minutes': self.total_time_minutes,
            'difficulty': self.difficulty,
            'source': self.source,
            'instructions': self.instructions,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None,
        }


class Ingredient(Base):
    """Represents an ingredient that can be used in recipes."""
    __tablename__ = 'ingredients'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    name = Column(String(255), nullable=False)
    category = Column(String(100))  # vegetables, fruits, grains, dairy, meat, spices, etc.
    unit = Column(String(50), default='g')  # grams, ml, pieces, cups, tbsp, tsp
    calories_per_unit = Column(Float, default=0)  # calories per 100g or per unit
    protein_g = Column(Float, default=0)
    carbs_g = Column(Float, default=0)
    fat_g = Column(Float, default=0)
    fiber_g = Column(Float, default=0)
    is_seasonal = Column(Boolean, default=False)
    notes = Column(Text)
    created_at = Column(DateTime, default=datetime.utcnow)

    def to_dict(self):
        return {
            'id': self.id,
            'household_id': self.household_id,
            'name': self.name,
            'category': self.category,
            'unit': self.unit,
            'calories_per_unit': self.calories_per_unit,
            'protein_g': self.protein_g,
            'carbs_g': self.carbs_g,
            'fat_g': self.fat_g,
            'fiber_g': self.fiber_g,
            'is_seasonal': self.is_seasonal,
            'notes': self.notes,
            'created_at': self.created_at.isoformat() if self.created_at else None,
        }


class RecipeIngredientMapping(Base):
    """Maps ingredients to recipes with quantities."""
    __tablename__ = 'recipe_ingredient_mapping'

    id = Column(Integer, primary_key=True, autoincrement=True)
    recipe_id = Column(Integer, ForeignKey('recipes.id'), nullable=False)
    ingredient_id = Column(Integer, ForeignKey('ingredients.id'), nullable=False)
    quantity = Column(Float, nullable=False)  # how many units
    unit = Column(String(50), default='g')
    optional = Column(Boolean, default=False)

    recipe = relationship('Recipe', back_populates='ingredients')
    ingredient = relationship('Ingredient')

    def to_dict(self):
        return {
            'id': self.id,
            'recipe_id': self.recipe_id,
            'ingredient_id': self.ingredient_id,
            'ingredient_name': self.ingredient.name if self.ingredient else None,
            'quantity': self.quantity,
            'unit': self.unit,
            'optional': self.optional,
        }


class NutritionInfo(Base):
    """Nutritional information for a recipe (per serving)."""
    __tablename__ = 'nutrition_info'

    id = Column(Integer, primary_key=True, autoincrement=True)
    recipe_id = Column(Integer, ForeignKey('recipes.id'), nullable=False, unique=True)
    calories = Column(Float, default=0)
    protein_g = Column(Float, default=0)
    carbs_g = Column(Float, default=0)
    fat_g = Column(Float, default=0)
    fiber_g = Column(Float, default=0)
    sodium_mg = Column(Float, default=0)
    sugar_g = Column(Float, default=0)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    recipe = relationship('Recipe', back_populates='nutrition_info')

    def to_dict(self):
        return {
            'id': self.id,
            'recipe_id': self.recipe_id,
            'calories': self.calories,
            'protein_g': self.protein_g,
            'carbs_g': self.carbs_g,
            'fat_g': self.fat_g,
            'fiber_g': self.fiber_g,
            'sodium_mg': self.sodium_mg,
            'sugar_g': self.sugar_g,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None,
        }


class Meal(Base):
    """Represents a meal (breakfast, lunch, dinner, snack) on a specific date."""
    __tablename__ = 'meals'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    date = Column(String(10), nullable=False)  # YYYY-MM-DD
    meal_type = Column(String(50), nullable=False)  # breakfast, lunch, dinner, snack
    recipe_id = Column(Integer, ForeignKey('recipes.id'), nullable=False)
    servings = Column(Float, default=1)
    notes = Column(Text)
    created_at = Column(DateTime, default=datetime.utcnow)

    recipe = relationship('Recipe')

    def to_dict(self):
        return {
            'id': self.id,
            'household_id': self.household_id,
            'date': self.date,
            'meal_type': self.meal_type,
            'recipe_id': self.recipe_id,
            'recipe_name': self.recipe.name if self.recipe else None,
            'servings': self.servings,
            'notes': self.notes,
            'created_at': self.created_at.isoformat() if self.created_at else None,
        }


class MealPlan(Base):
    """Represents a meal plan (weekly or custom date range)."""
    __tablename__ = 'meal_plans'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    name = Column(String(255), nullable=False)
    description = Column(Text)
    start_date = Column(String(10), nullable=False)  # YYYY-MM-DD
    end_date = Column(String(10), nullable=False)  # YYYY-MM-DD
    plan_type = Column(String(50), default='weekly')  # weekly, custom
    is_active = Column(Boolean, default=True)
    notes = Column(Text)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    meals = relationship('Meal', cascade='all, delete-orphan')

    def to_dict(self):
        return {
            'id': self.id,
            'household_id': self.household_id,
            'name': self.name,
            'description': self.description,
            'start_date': self.start_date,
            'end_date': self.end_date,
            'plan_type': self.plan_type,
            'is_active': self.is_active,
            'notes': self.notes,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None,
        }


class ShoppingList(Base):
    """Represents a shopping list generated from meal plan."""
    __tablename__ = 'shopping_lists'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    meal_plan_id = Column(Integer, ForeignKey('meal_plans.id'))
    name = Column(String(255), nullable=False)
    start_date = Column(String(10))  # YYYY-MM-DD
    end_date = Column(String(10))    # YYYY-MM-DD
    is_completed = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    meal_plan = relationship('MealPlan')
    items = relationship('ShoppingListItem', cascade='all, delete-orphan')

    def to_dict(self):
        return {
            'id': self.id,
            'household_id': self.household_id,
            'meal_plan_id': self.meal_plan_id,
            'name': self.name,
            'start_date': self.start_date,
            'end_date': self.end_date,
            'is_completed': self.is_completed,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'updated_at': self.updated_at.isoformat() if self.updated_at else None,
        }


class BankConnection(Base):
    """Plaid Open Banking connection — one row per connected bank item."""
    __tablename__ = 'bank_connections'

    id = Column(Integer, primary_key=True, autoincrement=True)
    household_id = Column(Integer, ForeignKey('household_profiles.id'), nullable=False)
    item_id = Column(String(255), unique=True)       # Plaid item ID (stable per bank link)
    access_token = Column(String(500))               # Plaid access token — NEVER expose in API responses
    bank_name = Column(String(255))                  # e.g., 'N26', 'Commerzbank'
    institution_id = Column(String(100))             # Plaid institution ID
    account_ids = Column(Text)                       # JSON array of Plaid account IDs
    cursor = Column(String(500))                     # Plaid transactions cursor for incremental sync
    last_sync_at = Column(DateTime)
    created_at = Column(DateTime, default=datetime.utcnow)
    is_active = Column(Boolean, default=True)

    household = relationship('HouseholdProfile')

    def to_dict(self):
        return {
            'id': self.id,
            'household_id': self.household_id,
            'bank_name': self.bank_name,
            'institution_id': self.institution_id,
            'account_ids': json.loads(self.account_ids) if self.account_ids else [],
            'last_sync_at': self.last_sync_at.isoformat() if self.last_sync_at else None,
            'created_at': self.created_at.isoformat() if self.created_at else None,
            'is_active': self.is_active,
        }


class ShoppingListItem(Base):
    """Items in a shopping list (aggregated from recipes)."""
    __tablename__ = 'shopping_list_items'

    id = Column(Integer, primary_key=True, autoincrement=True)
    shopping_list_id = Column(Integer, ForeignKey('shopping_lists.id'), nullable=False)
    ingredient_id = Column(Integer, ForeignKey('ingredients.id'), nullable=False)
    quantity = Column(Float, nullable=False)
    unit = Column(String(50), default='g')
    is_checked = Column(Boolean, default=False)
    notes = Column(Text)

    shopping_list = relationship('ShoppingList', back_populates='items')
    ingredient = relationship('Ingredient')

    def to_dict(self):
        return {
            'id': self.id,
            'shopping_list_id': self.shopping_list_id,
            'ingredient_id': self.ingredient_id,
            'ingredient_name': self.ingredient.name if self.ingredient else None,
            'quantity': self.quantity,
            'unit': self.unit,
            'is_checked': self.is_checked,
            'notes': self.notes,
        }
