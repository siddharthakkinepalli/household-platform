"""
meals_routes.py
FastAPI Blueprint-style routes for meal planning module.

Endpoints:
  GET  /api/v1/meals/plans                    → list meal plans
  POST /api/v1/meals/plans                    → create meal plan
  GET  /api/v1/meals/plans/{plan_id}          → get plan with meals
  POST /api/v1/meals/plans/{plan_id}/meals    → add meal to day
  GET  /api/v1/meals/recipes                  → list recipes
  GET  /api/v1/meals/recipes/{recipe_id}      → get recipe details
  POST /api/v1/meals/shopping-list            → generate from meal plan
  GET  /api/v1/meals/nutrition/{plan_id}      → nutrition summary
"""

from datetime import datetime
from sqlalchemy import and_
from flask import request, jsonify

from household_models import (
    MealPlan, Meal, Recipe, Ingredient, RecipeIngredientMapping,
    NutritionInfo, ShoppingList, ShoppingListItem, HouseholdProfile
)


def register_meals_routes(app, get_db):
    """Register meal planning routes with Flask app."""

    # -----------------------------------------------------------------------
    # Meal Plans CRUD
    # -----------------------------------------------------------------------

    @app.route('/api/v1/meals/plans', methods=['GET'])
    def list_meal_plans():
        """
        GET /api/v1/meals/plans?household_id=1&active_only=true
        Returns list of meal plans.
        """
        try:
            household_id = request.args.get('household_id', type=int)
            active_only = request.args.get('active_only', 'false').lower() == 'true'

            if not household_id:
                return jsonify({'error': 'household_id is required'}), 400

            db = get_db()

            query = db.query(MealPlan).filter_by(household_id=household_id)
            if active_only:
                query = query.filter_by(is_active=True)

            plans = query.order_by(MealPlan.start_date.desc()).all()
            result = [p.to_dict() for p in plans]

            db.close()
            return jsonify({'status': 'ok', 'data': result}), 200

        except Exception as e:
            return jsonify({'status': 'error', 'error': str(e)}), 500

    @app.route('/api/v1/meals/plans', methods=['POST'])
    def create_meal_plan():
        """
        POST /api/v1/meals/plans
        Body: {
          "household_id": 1,
          "name": "Weekly Plan",
          "start_date": "2026-05-01",
          "end_date": "2026-05-07",
          "plan_type": "weekly",
          "description": "Healthy weekly plan"
        }
        Returns: meal plan object
        """
        try:
            data = request.get_json() or {}
            household_id = data.get('household_id')
            name = data.get('name')
            start_date = data.get('start_date')
            end_date = data.get('end_date')

            if not household_id or not name or not start_date or not end_date:
                return jsonify({'status': 'error', 'error': 'household_id, name, start_date, end_date required'}), 400

            db = get_db()

            # Verify household exists
            household = db.query(HouseholdProfile).filter_by(id=household_id).first()
            if not household:
                db.close()
                return jsonify({'status': 'error', 'error': 'Household not found'}), 404

            plan = MealPlan(
                household_id=household_id,
                name=name,
                description=data.get('description', ''),
                start_date=start_date,
                end_date=end_date,
                plan_type=data.get('plan_type', 'weekly'),
                is_active=True,
            )
            db.add(plan)
            db.commit()
            db.refresh(plan)

            result = plan.to_dict()
            db.close()
            return jsonify({'status': 'ok', 'data': result}), 201

        except Exception as e:
            return jsonify({'status': 'error', 'error': str(e)}), 500

    @app.route('/api/v1/meals/plans/<int:plan_id>', methods=['GET'])
    def get_meal_plan(plan_id):
        """
        GET /api/v1/meals/plans/{plan_id}?household_id=1
        Returns meal plan with all meals for the week.
        """
        try:
            household_id = request.args.get('household_id', type=int)
            if not household_id:
                return jsonify({'status': 'error', 'error': 'household_id is required'}), 400

            db = get_db()

            plan = db.query(MealPlan).filter_by(id=plan_id, household_id=household_id).first()
            if not plan:
                db.close()
                return jsonify({'status': 'error', 'error': 'Meal plan not found'}), 404

            # Get all meals for this plan
            meals = db.query(Meal).filter(
                and_(
                    Meal.household_id == household_id,
                    Meal.date >= plan.start_date,
                    Meal.date <= plan.end_date,
                )
            ).order_by(Meal.date.asc(), Meal.meal_type.asc()).all()

            result = plan.to_dict()
            result['meals'] = [m.to_dict() for m in meals]

            db.close()
            return jsonify({'status': 'ok', 'data': result}), 200

        except Exception as e:
            return jsonify({'status': 'error', 'error': str(e)}), 500

    @app.route('/api/v1/meals/plans/<int:plan_id>/meals', methods=['POST'])
    def add_meal_to_plan(plan_id):
        """
        POST /api/v1/meals/plans/{plan_id}/meals
        Body: {
          "household_id": 1,
          "date": "2026-05-01",
          "meal_type": "breakfast",
          "recipe_id": 5,
          "servings": 2,
          "notes": "Make extra for lunch"
        }
        Returns: meal object
        """
        try:
            data = request.get_json() or {}
            household_id = data.get('household_id')
            date = data.get('date')
            meal_type = data.get('meal_type')
            recipe_id = data.get('recipe_id')

            if not household_id or not date or not meal_type or not recipe_id:
                return jsonify({'status': 'error', 'error': 'household_id, date, meal_type, recipe_id required'}), 400

            db = get_db()

            # Verify plan exists
            plan = db.query(MealPlan).filter_by(id=plan_id, household_id=household_id).first()
            if not plan:
                db.close()
                return jsonify({'status': 'error', 'error': 'Meal plan not found'}), 404

            # Verify recipe exists
            recipe = db.query(Recipe).filter_by(id=recipe_id, household_id=household_id).first()
            if not recipe:
                db.close()
                return jsonify({'status': 'error', 'error': 'Recipe not found'}), 404

            meal = Meal(
                household_id=household_id,
                date=date,
                meal_type=meal_type,
                recipe_id=recipe_id,
                servings=data.get('servings', 1),
                notes=data.get('notes', ''),
            )
            db.add(meal)
            db.commit()
            db.refresh(meal)

            result = meal.to_dict()
            db.close()
            return jsonify({'status': 'ok', 'data': result}), 201

        except Exception as e:
            return jsonify({'status': 'error', 'error': str(e)}), 500

    # -----------------------------------------------------------------------
    # Recipes
    # -----------------------------------------------------------------------

    @app.route('/api/v1/meals/recipes', methods=['GET'])
    def list_recipes():
        """
        GET /api/v1/meals/recipes?household_id=1&cuisine=Indian&search=paneer
        Returns list of recipes.
        """
        try:
            household_id = request.args.get('household_id', type=int)
            cuisine = request.args.get('cuisine')
            search = request.args.get('search', '').lower()
            cuisine_type = request.args.get('cuisine_type')  # vegetarian, vegan, meat, seafood

            if not household_id:
                return jsonify({'status': 'error', 'error': 'household_id is required'}), 400

            db = get_db()

            query = db.query(Recipe).filter_by(household_id=household_id)

            if cuisine:
                query = query.filter_by(cuisine=cuisine)

            if cuisine_type:
                query = query.filter_by(cuisine_type=cuisine_type)

            recipes = query.all()

            # Apply text search filter
            if search:
                recipes = [r for r in recipes if search in r.name.lower() or (r.description and search in r.description.lower())]

            recipes = sorted(recipes, key=lambda r: r.name)
            result = [r.to_dict() for r in recipes]

            db.close()
            return jsonify({'status': 'ok', 'data': result}), 200

        except Exception as e:
            return jsonify({'status': 'error', 'error': str(e)}), 500

    @app.route('/api/v1/meals/recipes/<int:recipe_id>', methods=['GET'])
    def get_recipe(recipe_id):
        """
        GET /api/v1/meals/recipes/{recipe_id}?household_id=1
        Returns recipe details with ingredients and nutrition info.
        """
        try:
            household_id = request.args.get('household_id', type=int)
            if not household_id:
                return jsonify({'status': 'error', 'error': 'household_id is required'}), 400

            db = get_db()

            recipe = db.query(Recipe).filter_by(id=recipe_id, household_id=household_id).first()
            if not recipe:
                db.close()
                return jsonify({'status': 'error', 'error': 'Recipe not found'}), 404

            result = recipe.to_dict()

            # Add ingredients
            ingredient_mappings = db.query(RecipeIngredientMapping).filter_by(recipe_id=recipe_id).all()
            result['ingredients'] = [m.to_dict() for m in ingredient_mappings]

            # Add nutrition info
            nutrition = db.query(NutritionInfo).filter_by(recipe_id=recipe_id).first()
            result['nutrition'] = nutrition.to_dict() if nutrition else None

            db.close()
            return jsonify({'status': 'ok', 'data': result}), 200

        except Exception as e:
            return jsonify({'status': 'error', 'error': str(e)}), 500

    @app.route('/api/v1/meals/recipes', methods=['POST'])
    def create_recipe():
        """
        POST /api/v1/meals/recipes
        Body: {
          "household_id": 1,
          "name": "Paneer Tikka",
          "cuisine": "Indian",
          "cuisine_type": "vegetarian",
          "servings": 4,
          "prep_time_minutes": 20,
          "cook_time_minutes": 15,
          "difficulty": "medium",
          "instructions": "...",
          "ingredients": [
            {"ingredient_id": 5, "quantity": 300, "unit": "g", "optional": false}
          ]
        }
        Returns: recipe object
        """
        try:
            data = request.get_json() or {}
            household_id = data.get('household_id')
            name = data.get('name')
            ingredients_data = data.get('ingredients', [])

            if not household_id or not name:
                return jsonify({'status': 'error', 'error': 'household_id and name required'}), 400

            db = get_db()

            # Verify household exists
            household = db.query(HouseholdProfile).filter_by(id=household_id).first()
            if not household:
                db.close()
                return jsonify({'status': 'error', 'error': 'Household not found'}), 404

            recipe = Recipe(
                household_id=household_id,
                name=name,
                description=data.get('description', ''),
                cuisine=data.get('cuisine'),
                cuisine_type=data.get('cuisine_type', 'vegetarian'),
                servings=data.get('servings', 1),
                prep_time_minutes=data.get('prep_time_minutes'),
                cook_time_minutes=data.get('cook_time_minutes'),
                total_time_minutes=data.get('total_time_minutes'),
                difficulty=data.get('difficulty', 'medium'),
                source=data.get('source'),
                instructions=data.get('instructions', ''),
            )
            db.add(recipe)
            db.flush()  # Get recipe ID before adding ingredients

            # Add ingredients
            for ing_data in ingredients_data:
                mapping = RecipeIngredientMapping(
                    recipe_id=recipe.id,
                    ingredient_id=ing_data.get('ingredient_id'),
                    quantity=ing_data.get('quantity'),
                    unit=ing_data.get('unit', 'g'),
                    optional=ing_data.get('optional', False),
                )
                db.add(mapping)

            db.commit()
            db.refresh(recipe)

            result = recipe.to_dict()
            result['ingredients'] = [m.to_dict() for m in recipe.ingredients]

            db.close()
            return jsonify({'status': 'ok', 'data': result}), 201

        except Exception as e:
            return jsonify({'status': 'error', 'error': str(e)}), 500

    # -----------------------------------------------------------------------
    # Shopping List
    # -----------------------------------------------------------------------

    @app.route('/api/v1/meals/shopping-list', methods=['POST'])
    def generate_shopping_list():
        """
        POST /api/v1/meals/shopping-list
        Body: {
          "household_id": 1,
          "meal_plan_id": 1,
          "name": "Weekly Shopping List"
        }
        Returns: aggregated shopping list with all ingredients
        """
        try:
            data = request.get_json() or {}
            household_id = data.get('household_id')
            meal_plan_id = data.get('meal_plan_id')
            name = data.get('name', 'Shopping List')

            if not household_id or not meal_plan_id:
                return jsonify({'status': 'error', 'error': 'household_id and meal_plan_id required'}), 400

            db = get_db()

            # Verify meal plan exists
            meal_plan = db.query(MealPlan).filter_by(id=meal_plan_id, household_id=household_id).first()
            if not meal_plan:
                db.close()
                return jsonify({'status': 'error', 'error': 'Meal plan not found'}), 404

            # Create shopping list
            shopping_list = ShoppingList(
                household_id=household_id,
                meal_plan_id=meal_plan_id,
                name=name,
                start_date=meal_plan.start_date,
                end_date=meal_plan.end_date,
            )
            db.add(shopping_list)
            db.flush()

            # Get all meals for the plan
            meals = db.query(Meal).filter(
                and_(
                    Meal.household_id == household_id,
                    Meal.date >= meal_plan.start_date,
                    Meal.date <= meal_plan.end_date,
                )
            ).all()

            # Aggregate ingredients from all recipes
            ingredient_totals = {}  # ingredient_id -> {quantity, unit, ingredient_obj}

            for meal in meals:
                recipe = db.query(Recipe).filter_by(id=meal.recipe_id).first()
                if not recipe:
                    continue

                ingredient_mappings = db.query(RecipeIngredientMapping).filter_by(recipe_id=recipe.id).all()

                for mapping in ingredient_mappings:
                    ingredient = db.query(Ingredient).filter_by(id=mapping.ingredient_id).first()
                    if not ingredient:
                        continue

                    scaled_qty = mapping.quantity * meal.servings
                    key = (mapping.ingredient_id, mapping.unit)

                    if key not in ingredient_totals:
                        ingredient_totals[key] = {
                            'quantity': 0,
                            'unit': mapping.unit,
                            'ingredient': ingredient,
                        }

                    ingredient_totals[key]['quantity'] += scaled_qty

            # Create shopping list items
            for (ing_id, unit), data_dict in ingredient_totals.items():
                item = ShoppingListItem(
                    shopping_list_id=shopping_list.id,
                    ingredient_id=ing_id,
                    quantity=data_dict['quantity'],
                    unit=unit,
                    is_checked=False,
                )
                db.add(item)

            db.commit()
            db.refresh(shopping_list)

            result = shopping_list.to_dict()
            result['items'] = [item.to_dict() for item in shopping_list.items]

            db.close()
            return jsonify({'status': 'ok', 'data': result}), 201

        except Exception as e:
            return jsonify({'status': 'error', 'error': str(e)}), 500

    # -----------------------------------------------------------------------
    # Nutrition Analysis
    # -----------------------------------------------------------------------

    @app.route('/api/v1/meals/nutrition/<int:plan_id>', methods=['GET'])
    def get_nutrition_summary(plan_id):
        """
        GET /api/v1/meals/nutrition/{plan_id}?household_id=1
        Returns nutrition summary for the meal plan.
        """
        try:
            household_id = request.args.get('household_id', type=int)
            if not household_id:
                return jsonify({'status': 'error', 'error': 'household_id is required'}), 400

            db = get_db()

            # Verify meal plan exists
            meal_plan = db.query(MealPlan).filter_by(id=plan_id, household_id=household_id).first()
            if not meal_plan:
                db.close()
                return jsonify({'status': 'error', 'error': 'Meal plan not found'}), 404

            # Get all meals for the plan
            meals = db.query(Meal).filter(
                and_(
                    Meal.household_id == household_id,
                    Meal.date >= meal_plan.start_date,
                    Meal.date <= meal_plan.end_date,
                )
            ).all()

            # Aggregate nutrition
            daily_nutrition = {}  # date -> {calories, protein, carbs, fat}

            for meal in meals:
                nutrition = db.query(NutritionInfo).filter_by(recipe_id=meal.recipe_id).first()
                if not nutrition:
                    continue

                if meal.date not in daily_nutrition:
                    daily_nutrition[meal.date] = {
                        'date': meal.date,
                        'calories': 0,
                        'protein_g': 0,
                        'carbs_g': 0,
                        'fat_g': 0,
                        'fiber_g': 0,
                        'meals': [],
                    }

                scaled_calories = nutrition.calories * meal.servings
                daily_nutrition[meal.date]['calories'] += scaled_calories
                daily_nutrition[meal.date]['protein_g'] += nutrition.protein_g * meal.servings
                daily_nutrition[meal.date]['carbs_g'] += nutrition.carbs_g * meal.servings
                daily_nutrition[meal.date]['fat_g'] += nutrition.fat_g * meal.servings
                daily_nutrition[meal.date]['fiber_g'] += nutrition.fiber_g * meal.servings
                daily_nutrition[meal.date]['meals'].append({
                    'meal_type': meal.meal_type,
                    'recipe_name': meal.recipe.name if meal.recipe else None,
                })

            # Calculate weekly average
            daily_values = list(daily_nutrition.values())
            if daily_values:
                avg_calories = sum(d['calories'] for d in daily_values) / len(daily_values)
                avg_protein = sum(d['protein_g'] for d in daily_values) / len(daily_values)
                avg_carbs = sum(d['carbs_g'] for d in daily_values) / len(daily_values)
                avg_fat = sum(d['fat_g'] for d in daily_values) / len(daily_values)
            else:
                avg_calories = avg_protein = avg_carbs = avg_fat = 0

            result = {
                'plan_id': plan_id,
                'start_date': meal_plan.start_date,
                'end_date': meal_plan.end_date,
                'daily_breakdown': sorted(daily_values, key=lambda x: x['date']),
                'weekly_average': {
                    'calories': round(avg_calories, 1),
                    'protein_g': round(avg_protein, 1),
                    'carbs_g': round(avg_carbs, 1),
                    'fat_g': round(avg_fat, 1),
                },
            }

            db.close()
            return jsonify({'status': 'ok', 'data': result}), 200

        except Exception as e:
            return jsonify({'status': 'error', 'error': str(e)}), 500
