# RECIPE PARSING SPECIFICATION

**Version**: 1.0  
**Date**: April 30, 2026  
**Platform**: Household Platform Backend

---

## Overview

The Recipe Parsing Engine converts recipe images and text into structured data for storage in the household platform. It handles OCR extraction, ingredient parsing, unit normalization, and metadata inference.

---

## Input Formats

### 1. Image Input (`/api/v1/recipes/scan`)

**Format**: Multipart form with `image` file  
**Supported**: JPEG, PNG, WebP  
**Max Size**: 10 MB (configurable)  
**Quality**: Minimum 72 DPI recommended  

**Request**:
```bash
POST /api/v1/recipes/scan?household_id=1
Content-Type: multipart/form-data

[image bytes]
```

**Process**:
1. Read image file
2. Convert to RGB if needed
3. Apply EasyOCR (English language model)
4. Extract raw text from OCR results
5. Parse extracted text as recipe

---

### 2. Text Input (`/api/v1/recipes/parse-text`)

**Format**: JSON with recipe text  
**Min Length**: 10 characters

**Request**:
```json
{
  "household_id": 1,
  "text": "Tomato Rice\n\nIngredients:\n- 1 cup rice\n- 2 tomatoes\n- 1 onion\n\nInstructions:\nBoil rice..."
}
```

**Expected Format** (optional but recommended):
```
Recipe Name

Ingredients:
- qty unit ingredient_name [optional]
- qty unit ingredient_name [optional]
- ...

Cooking Time:
Prep: 15 minutes
Cook: 30 minutes

Serves: 2

Instructions:
Step 1: ...
Step 2: ...
```

---

## Output Format

### Parsed Recipe JSON

```json
{
  "name": "Tomato Rice",
  "description": "Simple tomato rice with aromatic spices",
  "cuisine": "Indian",
  "cuisine_type": "vegetarian",
  "servings": 2,
  "prep_time_minutes": 15,
  "cook_time_minutes": 30,
  "total_time_minutes": 45,
  "difficulty": "easy",
  "source": "manual",
  "confidence": 0.82,
  "instructions": "1. Cook rice...\n2. Fry onions...",
  "ingredients": [
    {
      "name": "rice",
      "quantity": 1.0,
      "unit": "cup",
      "normalized_unit": "ml",
      "normalized_quantity": 240,
      "original_text": "1 cup rice",
      "optional": false
    },
    {
      "name": "tomato",
      "quantity": 2.0,
      "unit": "pieces",
      "normalized_unit": null,
      "normalized_quantity": 2.0,
      "original_text": "2 tomatoes",
      "optional": false
    },
    {
      "name": "turmeric",
      "quantity": 0.5,
      "unit": "tsp",
      "normalized_unit": "ml",
      "normalized_quantity": 2.5,
      "original_text": "1/2 tsp turmeric",
      "optional": false
    }
  ]
}
```

---

## Ingredient Parsing Rules

### Format Recognition

The parser recognizes ingredients in these formats:

| Format | Example | Parsed |
|--------|---------|--------|
| **qty + unit + name** | `2 cups flour` | qty=2, unit=cups, name=flour |
| **qty range** | `1-2 onions` | qty=1.5 (avg), unit=pieces |
| **fraction** | `1/2 tsp salt` | qty=0.5, unit=tsp |
| **decimals** | `2.5 kg chicken` | qty=2.5, unit=kg |
| **name only** | `salt` | qty=None, unit='' |
| **optional** | `1 tbsp oil (optional)` | optional=true |

### Unit Normalization

Units are normalized to metric equivalents:

#### Volume Conversions
| Unit | To ml | Normalized |
|------|-------|-----------|
| tsp (teaspoon) | 5 ml | ml |
| tbsp (tablespoon) | 15 ml | ml |
| fl oz (fluid ounce) | 29.57 ml | ml |
| cup | 236.59 ml | ml |
| ml | 1 ml | ml |
| l (liter) | 1000 ml | ml |

#### Weight Conversions
| Unit | To g | Normalized |
|------|------|-----------|
| g (gram) | 1 g | g |
| kg | 1000 g | g |
| oz (ounce) | 28.35 g | g |
| lb (pound) | 453.59 g | g |

#### Pieces
| Unit | Normalized |
|------|-----------|
| piece, pieces | null (no conversion) |

### Ingredient Names

- **Cleaning**: Remove common prefixes (`1x`, `1-`, `a `)
- **Normalization**: Lowercase, trim whitespace
- **Deduplication**: Case-insensitive matching in database

---

## Metadata Extraction

### Cuisine Detection

The engine detects cuisine by matching keywords:

```
Indian  → curry, masala, turmeric, dal, paneer, tandoor, naan, ghee
Italian → pasta, risotto, mozzarella, basil, oregano, bolognese
Mexican → tortilla, salsa, cilantro, cumin, chili, coriander
Chinese → soy sauce, ginger, sesame, wok, stir fry
Thai    → coconut milk, lime, lemongrass, fish sauce
```

**Default**: None (cuisine_type defaults to 'vegetarian')

### Cuisine Type Detection

Inferred from ingredients:

```
Vegan        → tofu, tempeh, nutritional yeast (no meat/dairy)
Meat         → chicken, beef, pork, lamb, mutton, fish, shrimp
Seafood      → fish, shrimp, salmon, crab, lobster (no red meat)
Vegetarian   → has dairy/eggs (default)
```

### Cooking Time Extraction

Patterns matched:

```
Prep: 30 minutes
Cook: 45 minutes
Total time: 75 minutes
Bake: 20 min
```

### Servings Extraction

Patterns:
```
Serves 4
Servings: 4
Makes 2 servings
```

**Default**: 1

### Difficulty Estimation

Calculated from:
- **Ingredient count**: < 5 (easy), < 10 (medium), 10+ (hard)
- **Instructions length**: < 200 chars (easy), < 500 (medium), 500+ (hard)
- **Total time**: < 30 min (easy), < 60 min (medium), 60+ min (hard)

**Formula**: Average of three factors (1-3 scale) → easy/medium/hard

---

## Category Inference

Ingredients are automatically categorized:

| Category | Keywords |
|----------|----------|
| Vegetables | tomato, cucumber, onion, potato, lettuce, carrot, broccoli |
| Fruits | apple, banana, orange, strawberry, grape, kiwi |
| Dairy | milk, cheese, yogurt, butter, cream, curd, paneer |
| Meat | chicken, beef, pork, lamb, fish, shrimp |
| Grains | rice, wheat, flour, bread, pasta, oats |
| Spices | turmeric, cumin, salt, pepper, cinnamon, cumin |
| Oils | oil, ghee, butter |
| Pantry | sugar, honey, baking soda |
| Other | (default for unmatched) |

---

## Confidence Scoring

Confidence score (0.0-0.99) reflects parsing quality:

```
Base: 0.45

+ Recipe name found: +0.2
+ Ingredients found: +0.2
+ Instructions found: +0.1
+ Date/time extracted: +0.15

= Total confidence (capped at 0.99)
```

---

## Database Schema

### Recipe Model
```sql
CREATE TABLE recipes (
  id INTEGER PRIMARY KEY,
  household_id INTEGER,
  name VARCHAR(255),
  description TEXT,
  cuisine VARCHAR(100),
  cuisine_type VARCHAR(50),
  servings INTEGER,
  prep_time_minutes INTEGER,
  cook_time_minutes INTEGER,
  total_time_minutes INTEGER,
  difficulty VARCHAR(20),
  source VARCHAR(255),
  instructions TEXT,
  created_at DATETIME,
  updated_at DATETIME
);
```

### Ingredient Model
```sql
CREATE TABLE ingredients (
  id INTEGER PRIMARY KEY,
  household_id INTEGER,
  name VARCHAR(255),
  category VARCHAR(100),
  unit VARCHAR(50),
  calories_per_unit FLOAT,
  protein_g FLOAT,
  carbs_g FLOAT,
  fat_g FLOAT,
  fiber_g FLOAT,
  is_seasonal BOOLEAN,
  notes TEXT,
  created_at DATETIME
);
```

### RecipeIngredientMapping
```sql
CREATE TABLE recipe_ingredient_mapping (
  id INTEGER PRIMARY KEY,
  recipe_id INTEGER,
  ingredient_id INTEGER,
  quantity FLOAT,
  unit VARCHAR(50),
  optional BOOLEAN
);
```

### NutritionInfo
```sql
CREATE TABLE nutrition_info (
  id INTEGER PRIMARY KEY,
  recipe_id INTEGER,
  calories FLOAT,
  protein_g FLOAT,
  carbs_g FLOAT,
  fat_g FLOAT,
  fiber_g FLOAT,
  sodium_mg FLOAT,
  sugar_g FLOAT,
  updated_at DATETIME
);
```

### ShoppingListItem
```sql
CREATE TABLE shopping_list_items (
  id INTEGER PRIMARY KEY,
  household_id INTEGER,
  name VARCHAR(255),
  quantity FLOAT,
  unit VARCHAR(50),
  category VARCHAR(100),
  price_paid FLOAT,
  date_purchased VARCHAR(10),
  is_checked BOOLEAN,
  notes TEXT,
  created_at DATETIME
);
```

---

## API Endpoints

### Recipe Endpoints

**POST /api/v1/recipes/scan**
- Upload image → OCR → parse → return recipe

**POST /api/v1/recipes/parse-text**
- Parse recipe text → return recipe

**GET /api/v1/recipes/search**
- Search by name, ingredient, cuisine, cooking time
- Query: `?q=tomato&cuisine=Indian&max_time=30&limit=10`

**POST /api/v1/recipes/save**
- Save parsed recipe to database

**GET /api/v1/recipes/{recipe_id}**
- Get full recipe with ingredients

**GET /api/v1/recipes/{recipe_id}/nutrition**
- Get nutrition info per serving

**GET /api/v1/recipes/ingredients**
- Search/autocomplete ingredients
- Query: `?q=tom&category=Vegetables&limit=20`

**POST /api/v1/recipes/ingredients/bulk-import**
- Parse shopping list text → create ingredients

### Shopping List Endpoints

**POST /api/v1/shopping/add**
- Add item to shopping list

**GET /api/v1/shopping/list**
- Get shopping list

**DELETE /api/v1/shopping/{item_id}**
- Remove item

**PATCH /api/v1/shopping/{item_id}**
- Update item (checked, price, date, etc.)

---

## Error Handling

### Response Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Bad request (missing fields, invalid format) |
| 404 | Not found (recipe, ingredient, etc.) |
| 500 | Server error (OCR failure, DB error) |
| 503 | OCR service unavailable |

### Error Response Format
```json
{
  "error": "No text detected in image",
  "extracted_text": "",
  "extracted_text_length": 0
}
```

---

## Limitations & Future Improvements

### Current Limitations
1. **OCR Quality**: Depends on image quality and language (English only for now)
2. **Ingredient Matching**: Case-insensitive but doesn't handle synonyms ("tomato" ≠ "tamatar")
3. **Nutrition Data**: Not automatically populated (manual entry required)
4. **Servings Scaling**: Not automatically calculated for different portions

### Future Improvements
- [ ] Multi-language OCR support (German, Spanish, etc.)
- [ ] Nutrition database integration (USDA, FatSecret, etc.)
- [ ] Ingredient synonym mapping
- [ ] Recipe ingredient scaling
- [ ] Video recipe parsing
- [ ] Recipe rating/reviews
- [ ] Meal plan generation from recipes

---

## Examples

### Example 1: Image Upload

**Request**:
```bash
curl -X POST http://localhost:5000/api/v1/recipes/scan?household_id=1 \
  -F "image=@recipe_photo.jpg"
```

**Response (200 OK)**:
```json
{
  "success": true,
  "recipe": {
    "name": "Chicken Tikka Masala",
    "cuisine": "Indian",
    "cuisine_type": "meat",
    "servings": 4,
    "prep_time_minutes": 30,
    "cook_time_minutes": 45,
    "difficulty": "medium",
    "ingredients": [...],
    "instructions": "..."
  },
  "extracted_text_length": 1847
}
```

### Example 2: Text Parse

**Request**:
```json
{
  "household_id": 1,
  "text": "Simple Pasta\n\nIngredients:\n- 400g pasta\n- 2 tomatoes\n- 2 cloves garlic\n\nInstructions:\nBoil pasta..."
}
```

**Response (200 OK)**:
```json
{
  "success": true,
  "recipe": {
    "name": "Simple Pasta",
    "ingredients": [
      {
        "name": "pasta",
        "quantity": 400,
        "unit": "g",
        "normalized_quantity": 400,
        "normalized_unit": "g"
      }
    ]
  }
}
```

### Example 3: Save Recipe

**Request**:
```json
{
  "household_id": 1,
  "recipe": {
    "name": "Tomato Rice",
    "instructions": "...",
    "ingredients": [...]
  }
}
```

**Response (201 Created)**:
```json
{
  "success": true,
  "recipe_id": 5,
  "recipe": {
    "id": 5,
    "name": "Tomato Rice",
    ...
  }
}
```

---

## Integration with Meal Planner

When recipes are linked to meal plans:

1. **Meal Plan Creation**: Select recipes for each day/meal
2. **Ingredient Aggregation**: Combine all recipe ingredients for shopping list
3. **Nutrition Calculation**: Sum nutrition across meals for daily totals
4. **Expense Tracking**: Link ingredient prices to actual spending

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-04-30 | Initial release |

