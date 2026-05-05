"""
recipes_routes.py
Flask routes for Recipe Scanner & Grocery features.

Endpoints:
  POST   /api/v1/recipes/scan              → upload image, extract recipe via OCR
  POST   /api/v1/recipes/parse-text        → parse raw recipe text
  GET    /api/v1/recipes/search            → search recipes by name/ingredient/cuisine/time
  POST   /api/v1/recipes/save              → save parsed recipe to database
  GET    /api/v1/recipes/{recipe_id}       → get full recipe
  GET    /api/v1/recipes/{recipe_id}/nutrition → get nutrition info
  POST   /api/v1/recipes/ingredients/bulk-import → parse shopping list text → DB
  GET    /api/v1/recipes/ingredients       → search/autocomplete ingredients
  POST   /api/v1/shopping/add              → add item to shopping list
  GET    /api/v1/shopping/list             → get shopping list
  DELETE /api/v1/shopping/{item_id}        → remove item
  PATCH  /api/v1/shopping/{item_id}        → mark item as checked/update price
"""

import io
import logging
from pathlib import Path
from datetime import datetime
from functools import wraps

from flask import Blueprint, request, jsonify
from sqlalchemy import or_
from PIL import Image
import numpy as np

try:
    import easyocr
    HAS_EASYOCR = True
except ImportError:
    HAS_EASYOCR = False

from household_models import (
    Recipe, Ingredient, RecipeIngredientMapping, NutritionInfo,
    ShoppingListItem, HouseholdProfile
)
from recipes_parsing_engine import RecipeParsingEngine, ParsedRecipe

recipes_bp = Blueprint('recipes', __name__, url_prefix='/api/v1/recipes')
shopping_bp = Blueprint('shopping', __name__, url_prefix='/api/v1/shopping')

logger = logging.getLogger(__name__)

# Initialize OCR reader (lazy load)
ocr_reader = None


def get_ocr_reader():
    """Lazy load EasyOCR to speed up startup."""
    global ocr_reader
    if ocr_reader is None and HAS_EASYOCR:
        logger.info("Initializing EasyOCR for recipe scanning...")
        try:
            ocr_reader = easyocr.Reader(['en'], gpu=False)
            logger.info("EasyOCR initialized successfully")
        except Exception as e:
            logger.error(f"Failed to initialize EasyOCR: {e}")
            return None
    return ocr_reader


def require_household(f):
    """Decorator: validate household_id from JSON or query params."""
    @wraps(f)
    def decorated_function(*args, **kwargs):
        household_id = request.json.get('household_id') if request.json else None
        household_id = household_id or request.args.get('household_id')

        if not household_id:
            return jsonify({'error': 'household_id required'}), 400

        try:
            household_id = int(household_id)
        except ValueError:
            return jsonify({'error': 'household_id must be integer'}), 400

        return f(household_id, *args, **kwargs)

    return decorated_function


# ============================================================================
# RECIPE ENDPOINTS
# ============================================================================

@recipes_bp.route('/scan', methods=['POST'])
@require_household
def scan_recipe_image(household_id):
    """
    Upload image → OCR → parse recipe.
    Expects: multipart/form-data with 'image' file
    Returns: parsed recipe with confidence score
    """
    if 'image' not in request.files:
        return jsonify({'error': 'No image file provided'}), 400

    file = request.files['image']
    if file.filename == '':
        return jsonify({'error': 'No selected file'}), 400

    try:
        # Read image
        image_bytes = file.read()
        image = Image.open(io.BytesIO(image_bytes))

        # Convert to RGB if needed
        if image.mode != 'RGB':
            image = image.convert('RGB')

        # Convert to numpy array for EasyOCR
        img_array = np.array(image)

        # Perform OCR
        reader = get_ocr_reader()
        if not reader:
            return jsonify({'error': 'OCR service not available'}), 503

        logger.info(f"Running OCR on image: {img_array.shape}")
        ocr_result = reader.readtext(img_array, detail=1)

        # Extract text from OCR results
        extracted_text = '\n'.join([line[1] for line in ocr_result])

        if not extracted_text.strip():
            return jsonify({
                'error': 'No text detected in image',
                'extracted_text': '',
            }), 400

        logger.info(f"OCR extracted {len(extracted_text)} chars")

        # Parse recipe from extracted text
        parsed_recipe = RecipeParsingEngine.parse_recipe_text(extracted_text)

        # Calculate difficulty
        parsed_recipe.difficulty = RecipeParsingEngine.calculate_difficulty(
            len(parsed_recipe.ingredients),
            len(parsed_recipe.instructions),
            {
                'prep_time_minutes': parsed_recipe.prep_time_minutes,
                'cook_time_minutes': parsed_recipe.cook_time_minutes,
            }
        )

        return jsonify({
            'success': True,
            'recipe': parsed_recipe.to_dict(),
            'extracted_text_length': len(extracted_text),
        }), 200

    except Exception as e:
        logger.error(f"Recipe scan error: {e}", exc_info=True)
        return jsonify({'error': str(e)}), 500


@recipes_bp.route('/parse-text', methods=['POST'])
@require_household
def parse_recipe_text(household_id):
    """
    Parse raw recipe text (from clipboard, manual input).
    Expects JSON: {"text": "recipe text...", "household_id": 1}
    Returns: parsed recipe with ingredients + metadata
    """
    data = request.get_json()
    if not data or 'text' not in data:
        return jsonify({'error': 'text required in JSON body'}), 400

    recipe_text = data['text'].strip()
    if len(recipe_text) < 10:
        return jsonify({'error': 'Recipe text too short'}), 400

    try:
        # Parse recipe
        parsed_recipe = RecipeParsingEngine.parse_recipe_text(recipe_text)

        # Calculate difficulty
        parsed_recipe.difficulty = RecipeParsingEngine.calculate_difficulty(
            len(parsed_recipe.ingredients),
            len(parsed_recipe.instructions),
            {
                'prep_time_minutes': parsed_recipe.prep_time_minutes,
                'cook_time_minutes': parsed_recipe.cook_time_minutes,
            }
        )

        return jsonify({
            'success': True,
            'recipe': parsed_recipe.to_dict(),
        }), 200

    except Exception as e:
        logger.error(f"Parse text error: {e}")
        return jsonify({'error': str(e)}), 500


@recipes_bp.route('/search', methods=['GET'])
@require_household
def search_recipes(household_id):
    """
    Search recipes by name, ingredient, cuisine, cooking time.
    Query params:
      - q: search query (recipe name)
      - ingredient: filter by ingredient
      - cuisine: filter by cuisine
      - max_time: max cooking time in minutes
      - limit: max results (default 20)
    """
    from household_models import SessionLocal
    db = SessionLocal()

    try:
        q = request.args.get('q', '')
        ingredient_filter = request.args.get('ingredient', '')
        cuisine_filter = request.args.get('cuisine', '')
        max_time = request.args.get('max_time', type=int)
        limit = request.args.get('limit', 20, type=int)

        # Base query
        query = db.query(Recipe).filter_by(household_id=household_id)

        # Filter by name
        if q:
            query = query.filter(Recipe.name.ilike(f'%{q}%'))

        # Filter by cuisine
        if cuisine_filter:
            query = query.filter(Recipe.cuisine.ilike(f'%{cuisine_filter}%'))

        # Filter by cooking time
        if max_time:
            query = query.filter(Recipe.total_time_minutes <= max_time)

        # Filter by ingredient (requires join)
        if ingredient_filter:
            query = query.join(RecipeIngredientMapping).join(Ingredient).filter(
                Ingredient.name.ilike(f'%{ingredient_filter}%')
            )

        results = query.limit(limit).all()
        return jsonify({
            'success': True,
            'count': len(results),
            'recipes': [r.to_dict() for r in results],
        }), 200

    except Exception as e:
        logger.error(f"Search error: {e}")
        return jsonify({'error': str(e)}), 500
    finally:
        db.close()


@recipes_bp.route('/save', methods=['POST'])
@require_household
def save_recipe(household_id):
    """
    Save parsed recipe to database.
    Expects JSON: {"recipe": {...parsed recipe...}, "household_id": 1}
    Returns: saved recipe with id
    """
    from household_models import SessionLocal
    db = SessionLocal()

    try:
        data = request.get_json()
        recipe_data = data.get('recipe')

        if not recipe_data:
            return jsonify({'error': 'recipe data required'}), 400

        # Verify household exists
        household = db.query(HouseholdProfile).filter_by(id=household_id).first()
        if not household:
            return jsonify({'error': 'household not found'}), 404

        # Create recipe
        recipe = Recipe(
            household_id=household_id,
            name=recipe_data.get('name', 'Untitled'),
            description=recipe_data.get('description', ''),
            cuisine=recipe_data.get('cuisine'),
            cuisine_type=recipe_data.get('cuisine_type', 'vegetarian'),
            servings=recipe_data.get('servings', 1),
            prep_time_minutes=recipe_data.get('prep_time_minutes'),
            cook_time_minutes=recipe_data.get('cook_time_minutes'),
            total_time_minutes=recipe_data.get('total_time_minutes'),
            difficulty=recipe_data.get('difficulty', 'medium'),
            source=recipe_data.get('source', 'manual'),
            instructions=recipe_data.get('instructions', ''),
        )
        db.add(recipe)
        db.flush()

        # Add or link ingredients
        for ing_data in recipe_data.get('ingredients', []):
            # Find or create ingredient
            ingredient = db.query(Ingredient).filter(
                Ingredient.household_id == household_id,
                Ingredient.name.ilike(ing_data.get('name'))
            ).first()

            if not ingredient:
                ingredient = Ingredient(
                    household_id=household_id,
                    name=ing_data.get('name'),
                    category=_infer_category(ing_data.get('name')),
                    unit=ing_data.get('normalized_unit', 'g'),
                    calories_per_unit=0,
                )
                db.add(ingredient)
                db.flush()

            # Create mapping
            mapping = RecipeIngredientMapping(
                recipe_id=recipe.id,
                ingredient_id=ingredient.id,
                quantity=ing_data.get('quantity'),
                unit=ing_data.get('normalized_unit', ing_data.get('unit', 'g')),
                optional=ing_data.get('optional', False),
            )
            db.add(mapping)

        # Add nutrition info if provided
        if recipe_data.get('nutrition'):
            nutrition = NutritionInfo(
                recipe_id=recipe.id,
                calories=recipe_data['nutrition'].get('calories', 0),
                protein_g=recipe_data['nutrition'].get('protein_g', 0),
                carbs_g=recipe_data['nutrition'].get('carbs_g', 0),
                fat_g=recipe_data['nutrition'].get('fat_g', 0),
                fiber_g=recipe_data['nutrition'].get('fiber_g', 0),
                sodium_mg=recipe_data['nutrition'].get('sodium_mg', 0),
                sugar_g=recipe_data['nutrition'].get('sugar_g', 0),
            )
            db.add(nutrition)

        db.commit()
        db.refresh(recipe)

        return jsonify({
            'success': True,
            'recipe_id': recipe.id,
            'recipe': recipe.to_dict(),
        }), 201

    except Exception as e:
        db.rollback()
        logger.error(f"Save recipe error: {e}")
        return jsonify({'error': str(e)}), 500
    finally:
        db.close()


@recipes_bp.route('/<int:recipe_id>', methods=['GET'])
@require_household
def get_recipe(household_id, recipe_id):
    """Get full recipe with ingredients and nutrition info."""
    from household_models import SessionLocal
    db = SessionLocal()

    try:
        recipe = db.query(Recipe).filter(
            Recipe.id == recipe_id,
            Recipe.household_id == household_id
        ).first()

        if not recipe:
            return jsonify({'error': 'recipe not found'}), 404

        recipe_dict = recipe.to_dict()
        recipe_dict['ingredients'] = [ing.to_dict() for ing in recipe.ingredients]
        if recipe.nutrition_info:
            recipe_dict['nutrition'] = recipe.nutrition_info.to_dict()

        return jsonify({
            'success': True,
            'recipe': recipe_dict,
        }), 200

    except Exception as e:
        logger.error(f"Get recipe error: {e}")
        return jsonify({'error': str(e)}), 500
    finally:
        db.close()


@recipes_bp.route('/<int:recipe_id>/nutrition', methods=['GET'])
@require_household
def get_recipe_nutrition(household_id, recipe_id):
    """Get nutritional breakdown per serving."""
    from household_models import SessionLocal
    db = SessionLocal()

    try:
        recipe = db.query(Recipe).filter(
            Recipe.id == recipe_id,
            Recipe.household_id == household_id
        ).first()

        if not recipe:
            return jsonify({'error': 'recipe not found'}), 404

        if not recipe.nutrition_info:
            return jsonify({
                'success': True,
                'nutrition': None,
                'message': 'Nutrition info not available',
            }), 200

        return jsonify({
            'success': True,
            'nutrition': recipe.nutrition_info.to_dict(),
            'servings': recipe.servings,
        }), 200

    except Exception as e:
        logger.error(f"Get nutrition error: {e}")
        return jsonify({'error': str(e)}), 500
    finally:
        db.close()


@recipes_bp.route('/ingredients', methods=['GET'])
@require_household
def search_ingredients(household_id):
    """
    Search/autocomplete ingredients.
    Query params:
      - q: search query
      - category: filter by category
      - limit: max results (default 50)
    """
    from household_models import SessionLocal
    db = SessionLocal()

    try:
        q = request.args.get('q', '')
        category = request.args.get('category')
        limit = request.args.get('limit', 50, type=int)

        query = db.query(Ingredient).filter_by(household_id=household_id)

        if q:
            query = query.filter(Ingredient.name.ilike(f'%{q}%'))

        if category:
            query = query.filter(Ingredient.category.ilike(f'%{category}%'))

        results = query.limit(limit).all()
        return jsonify({
            'success': True,
            'count': len(results),
            'ingredients': [ing.to_dict() for ing in results],
        }), 200

    except Exception as e:
        logger.error(f"Search ingredients error: {e}")
        return jsonify({'error': str(e)}), 500
    finally:
        db.close()


@recipes_bp.route('/ingredients/bulk-import', methods=['POST'])
@require_household
def bulk_import_ingredients(household_id):
    """
    Parse shopping list text → ingredient DB.
    Expects: {"text": "shopping list text...", "household_id": 1}
    """
    from household_models import SessionLocal
    db = SessionLocal()

    try:
        data = request.get_json()
        text = data.get('text', '').strip()

        if not text:
            return jsonify({'error': 'text required'}), 400

        # Parse lines as ingredients
        lines = text.split('\n')
        created_count = 0
        skipped_count = 0

        for line in lines:
            line = line.strip()
            if not line or len(line) < 2:
                continue

            # Try to parse as ingredient
            parsed_ing = RecipeParsingEngine.parse_ingredient_line(line)
            if not parsed_ing:
                skipped_count += 1
                continue

            # Check if ingredient already exists
            existing = db.query(Ingredient).filter(
                Ingredient.household_id == household_id,
                Ingredient.name.ilike(parsed_ing.name)
            ).first()

            if not existing:
                ingredient = Ingredient(
                    household_id=household_id,
                    name=parsed_ing.name,
                    category=_infer_category(parsed_ing.name),
                    unit=parsed_ing.normalized_unit or 'g',
                )
                db.add(ingredient)
                created_count += 1

        db.commit()
        return jsonify({
            'success': True,
            'created': created_count,
            'skipped': skipped_count,
        }), 200

    except Exception as e:
        db.rollback()
        logger.error(f"Bulk import error: {e}")
        return jsonify({'error': str(e)}), 500
    finally:
        db.close()


# ============================================================================
# SHOPPING LIST ENDPOINTS
# ============================================================================

@shopping_bp.route('/add', methods=['POST'])
@require_household
def add_shopping_item(household_id):
    """
    Add item to shopping list.
    Expects: {"name": "...", "quantity": 2, "unit": "kg", "category": "produce", ...}
    """
    from household_models import SessionLocal
    db = SessionLocal()

    try:
        data = request.get_json()

        # Verify household
        household = db.query(HouseholdProfile).filter_by(id=household_id).first()
        if not household:
            return jsonify({'error': 'household not found'}), 404

        item = ShoppingListItem(
            household_id=household_id,
            name=data.get('name', 'Item'),
            quantity=data.get('quantity', 1),
            unit=data.get('unit', 'g'),
            category=data.get('category'),
            notes=data.get('notes'),
        )
        db.add(item)
        db.commit()
        db.refresh(item)

        return jsonify({
            'success': True,
            'item_id': item.id,
            'item': item.to_dict(),
        }), 201

    except Exception as e:
        db.rollback()
        logger.error(f"Add shopping item error: {e}")
        return jsonify({'error': str(e)}), 500
    finally:
        db.close()


@shopping_bp.route('/list', methods=['GET'])
@require_household
def get_shopping_list(household_id):
    """Get shopping list for household."""
    from household_models import SessionLocal
    db = SessionLocal()

    try:
        items = db.query(ShoppingListItem).filter_by(household_id=household_id).all()
        return jsonify({
            'success': True,
            'count': len(items),
            'items': [item.to_dict() for item in items],
        }), 200

    except Exception as e:
        logger.error(f"Get shopping list error: {e}")
        return jsonify({'error': str(e)}), 500
    finally:
        db.close()


@shopping_bp.route('/<int:item_id>', methods=['DELETE'])
@require_household
def delete_shopping_item(household_id, item_id):
    """Remove item from shopping list."""
    from household_models import SessionLocal
    db = SessionLocal()

    try:
        item = db.query(ShoppingListItem).filter(
            ShoppingListItem.id == item_id,
            ShoppingListItem.household_id == household_id
        ).first()

        if not item:
            return jsonify({'error': 'item not found'}), 404

        db.delete(item)
        db.commit()

        return jsonify({'success': True}), 200

    except Exception as e:
        db.rollback()
        logger.error(f"Delete item error: {e}")
        return jsonify({'error': str(e)}), 500
    finally:
        db.close()


@shopping_bp.route('/<int:item_id>', methods=['PATCH'])
@require_household
def update_shopping_item(household_id, item_id):
    """Update item (mark as checked, update price, etc.)."""
    from household_models import SessionLocal
    db = SessionLocal()

    try:
        item = db.query(ShoppingListItem).filter(
            ShoppingListItem.id == item_id,
            ShoppingListItem.household_id == household_id
        ).first()

        if not item:
            return jsonify({'error': 'item not found'}), 404

        data = request.get_json()

        # Update fields
        if 'is_checked' in data:
            item.is_checked = data['is_checked']
        if 'price_paid' in data:
            item.price_paid = data['price_paid']
        if 'date_purchased' in data:
            item.date_purchased = data['date_purchased']
        if 'quantity' in data:
            item.quantity = data['quantity']
        if 'unit' in data:
            item.unit = data['unit']

        db.commit()
        db.refresh(item)

        return jsonify({
            'success': True,
            'item': item.to_dict(),
        }), 200

    except Exception as e:
        db.rollback()
        logger.error(f"Update item error: {e}")
        return jsonify({'error': str(e)}), 500
    finally:
        db.close()


# ============================================================================
# HELPER FUNCTIONS
# ============================================================================

def _infer_category(ingredient_name: str) -> str:
    """Infer ingredient category from name."""
    name_lower = ingredient_name.lower()

    categories = {
        'Vegetables': ['tomato', 'cucumber', 'onion', 'potato', 'lettuce', 'carrot', 'broccoli'],
        'Fruits': ['apple', 'banana', 'orange', 'strawberry', 'grape'],
        'Dairy': ['milk', 'cheese', 'yogurt', 'butter', 'cream'],
        'Meat': ['chicken', 'beef', 'pork', 'lamb', 'fish'],
        'Grains': ['rice', 'wheat', 'flour', 'bread', 'pasta'],
        'Spices': ['salt', 'pepper', 'cumin', 'turmeric', 'cinnamon'],
        'Oils': ['oil', 'ghee', 'butter'],
        'Pantry': ['sugar', 'honey', 'baking'],
    }

    for category, keywords in categories.items():
        if any(kw in name_lower for kw in keywords):
            return category

    return 'Other'


# ============================================================================
# BLUEPRINT REGISTRATION
# ============================================================================

def register_recipes_routes(app, get_db_func):
    """Register recipe and shopping blueprints with Flask app."""
    app.register_blueprint(recipes_bp)
    app.register_blueprint(shopping_bp)
    logger.info("Recipe and shopping blueprints registered")
