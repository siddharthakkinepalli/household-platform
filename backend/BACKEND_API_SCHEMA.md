# Household Platform Backend API Schema

**Version:** 1.0  
**Base URL:** `http://localhost:5001`  
**Date:** April 2026

## Overview

The Household Platform Backend provides three main API modules:

1. **Expense Management** — transaction tracking, budgeting, trip splitting
2. **Meal Planning** — recipe management, meal plans, nutrition tracking
3. **Recipe & Ingredient Management** — recipe parsing, ingredient database, shopping lists

All endpoints return a JSON response with the structure:
```json
{
  "status": "ok|error",
  "data": {...},
  "error": "error message (if status=error)"
}
```

---

## 1. Household Management

### Create Household
```
POST /household/create
```

**Request Body:**
```json
{
  "name": "My Household",
  "description": "Family home in Berlin",
  "currency": "EUR"
}
```

**Response (201):**
```json
{
  "id": 1,
  "name": "My Household",
  "description": "Family home in Berlin",
  "currency": "EUR",
  "created_at": "2026-04-30T10:00:00",
  "updated_at": "2026-04-30T10:00:00"
}
```

### Get Household Profile
```
GET /household/{household_id}
```

**Query Parameters:**
- `household_id` (required, int) — household ID

**Response (200):**
```json
{
  "id": 1,
  "name": "My Household",
  "description": "Family home",
  "currency": "EUR",
  "created_at": "2026-04-30T10:00:00",
  "updated_at": "2026-04-30T10:00:00",
  "members": [
    {
      "id": 1,
      "household_id": 1,
      "name": "John Doe",
      "role": "admin",
      "email": "john@example.com",
      "created_at": "2026-04-30T10:00:00"
    }
  ],
  "summary": {
    "total_expenses": -1250.50,
    "total_income": 5000.00,
    "transaction_count": 45
  }
}
```

---

## 2. Expense Module (`/api/v1/expenses/*`)

### List Transactions
```
GET /api/v1/expenses/transactions
```

**Query Parameters:**
- `household_id` (required, int)
- `start_date` (optional, string) — YYYY-MM-DD format
- `end_date` (optional, string) — YYYY-MM-DD format
- `category` (optional, string) — filter by category
- `excluded` (optional, bool) — include excluded transactions (default: false)

**Response (200):**
```json
{
  "status": "ok",
  "data": [
    {
      "id": 1,
      "household_id": 1,
      "date": "2026-04-27",
      "description": "Grocery store",
      "amount": -45.50,
      "category": "Food & Dining",
      "budget_category": "Grocery",
      "bank": "N26",
      "member_id": 1,
      "imported_at": "2026-04-30T10:00:00",
      "excluded": false
    }
  ]
}
```

### Add Transaction
```
POST /api/v1/expenses/transactions
```

**Request Body:**
```json
{
  "household_id": 1,
  "date": "2026-04-27",
  "description": "Grocery store",
  "amount": -45.50,
  "category": "Food & Dining",
  "budget_category": "Grocery",
  "bank": "N26",
  "member_id": 1
}
```

**Response (201):** Transaction object (same as list response)

### Import Transactions
```
POST /api/v1/expenses/import
```

**Request Body:**
```json
{
  "household_id": 1,
  "transactions": [
    {
      "date": "2026-04-27",
      "description": "Grocery store",
      "amount": -45.50,
      "category": "Food & Dining",
      "bank": "N26"
    }
  ]
}
```

**Response (201):**
```json
{
  "imported": 5,
  "skipped": 2
}
```

---

## 3. Meal Planning Module (`/api/v1/meals/*`)

### List Meal Plans
```
GET /api/v1/meals/plans
```

**Query Parameters:**
- `household_id` (required, int)
- `active_only` (optional, bool) — show only active plans (default: false)

**Response (200):**
```json
{
  "status": "ok",
  "data": [
    {
      "id": 1,
      "household_id": 1,
      "name": "Weekly Plan",
      "description": "Healthy Indian meals",
      "start_date": "2026-05-01",
      "end_date": "2026-05-07",
      "plan_type": "weekly",
      "is_active": true,
      "notes": "Vegetarian focus",
      "created_at": "2026-04-30T10:00:00",
      "updated_at": "2026-04-30T10:00:00"
    }
  ]
}
```

### Create Meal Plan
```
POST /api/v1/meals/plans
```

**Request Body:**
```json
{
  "household_id": 1,
  "name": "Weekly Plan - May",
  "description": "Healthy meals for the week",
  "start_date": "2026-05-01",
  "end_date": "2026-05-07",
  "plan_type": "weekly"
}
```

**Response (201):** Meal plan object

### Get Meal Plan with Meals
```
GET /api/v1/meals/plans/{plan_id}
```

**Query Parameters:**
- `household_id` (required, int)

**Response (200):**
```json
{
  "status": "ok",
  "data": {
    "id": 1,
    "household_id": 1,
    "name": "Weekly Plan",
    "start_date": "2026-05-01",
    "end_date": "2026-05-07",
    "plan_type": "weekly",
    "is_active": true,
    "meals": [
      {
        "id": 1,
        "household_id": 1,
        "date": "2026-05-01",
        "meal_type": "breakfast",
        "recipe_id": 5,
        "recipe_name": "Paneer Tikka",
        "servings": 2,
        "notes": "Make extra",
        "created_at": "2026-04-30T10:00:00"
      }
    ]
  }
}
```

### Add Meal to Plan
```
POST /api/v1/meals/plans/{plan_id}/meals
```

**Request Body:**
```json
{
  "household_id": 1,
  "date": "2026-05-01",
  "meal_type": "breakfast",
  "recipe_id": 5,
  "servings": 2,
  "notes": "Make extra for lunch"
}
```

**Response (201):** Meal object

### List Recipes
```
GET /api/v1/meals/recipes
```

**Query Parameters:**
- `household_id` (required, int)
- `cuisine` (optional, string) — filter by cuisine (Indian, Italian, etc.)
- `cuisine_type` (optional, string) — vegetarian, vegan, meat, seafood
- `search` (optional, string) — search recipe name/description

**Response (200):**
```json
{
  "status": "ok",
  "data": [
    {
      "id": 5,
      "household_id": 1,
      "name": "Paneer Tikka",
      "description": "Indian cheese appetizer",
      "cuisine": "Indian",
      "cuisine_type": "vegetarian",
      "servings": 4,
      "prep_time_minutes": 20,
      "cook_time_minutes": 15,
      "total_time_minutes": 35,
      "difficulty": "medium",
      "source": "NYT Cooking",
      "instructions": "..."
    }
  ]
}
```

### Get Recipe Details
```
GET /api/v1/meals/recipes/{recipe_id}
```

**Query Parameters:**
- `household_id` (required, int)

**Response (200):**
```json
{
  "status": "ok",
  "data": {
    "id": 5,
    "household_id": 1,
    "name": "Paneer Tikka",
    "description": "Indian cheese appetizer",
    "cuisine": "Indian",
    "cuisine_type": "vegetarian",
    "servings": 4,
    "prep_time_minutes": 20,
    "cook_time_minutes": 15,
    "difficulty": "medium",
    "source": "NYT Cooking",
    "instructions": "Marinate paneer in yogurt and spices. Thread on skewers. Grill until golden.",
    "ingredients": [
      {
        "id": 1,
        "recipe_id": 5,
        "ingredient_id": 10,
        "ingredient_name": "Paneer",
        "quantity": 300,
        "unit": "g",
        "optional": false
      }
    ],
    "nutrition": {
      "id": 1,
      "recipe_id": 5,
      "calories": 250,
      "protein_g": 20,
      "carbs_g": 5,
      "fat_g": 15,
      "fiber_g": 2,
      "sodium_mg": 450,
      "sugar_g": 1
    }
  }
}
```

### Create Recipe
```
POST /api/v1/meals/recipes
```

**Request Body:**
```json
{
  "household_id": 1,
  "name": "Paneer Tikka",
  "description": "Indian cheese appetizer",
  "cuisine": "Indian",
  "cuisine_type": "vegetarian",
  "servings": 4,
  "prep_time_minutes": 20,
  "cook_time_minutes": 15,
  "difficulty": "medium",
  "instructions": "Marinate paneer in yogurt and spices. Thread on skewers. Grill until golden.",
  "ingredients": [
    {
      "ingredient_id": 10,
      "quantity": 300,
      "unit": "g",
      "optional": false
    }
  ]
}
```

**Response (201):** Recipe object with ingredients

### Generate Shopping List
```
POST /api/v1/meals/shopping-list
```

**Request Body:**
```json
{
  "household_id": 1,
  "meal_plan_id": 1,
  "name": "Weekly Shopping List"
}
```

**Response (201):**
```json
{
  "status": "ok",
  "data": {
    "id": 1,
    "household_id": 1,
    "meal_plan_id": 1,
    "name": "Weekly Shopping List",
    "start_date": "2026-05-01",
    "end_date": "2026-05-07",
    "is_completed": false,
    "created_at": "2026-04-30T10:00:00",
    "items": [
      {
        "id": 1,
        "shopping_list_id": 1,
        "ingredient_id": 10,
        "ingredient_name": "Paneer",
        "quantity": 600,
        "unit": "g",
        "is_checked": false,
        "notes": null
      }
    ]
  }
}
```

### Get Nutrition Summary
```
GET /api/v1/meals/nutrition/{plan_id}
```

**Query Parameters:**
- `household_id` (required, int)

**Response (200):**
```json
{
  "status": "ok",
  "data": {
    "plan_id": 1,
    "start_date": "2026-05-01",
    "end_date": "2026-05-07",
    "daily_breakdown": [
      {
        "date": "2026-05-01",
        "calories": 2150.5,
        "protein_g": 85.0,
        "carbs_g": 250.0,
        "fat_g": 65.0,
        "fiber_g": 15.0,
        "meals": [
          {
            "meal_type": "breakfast",
            "recipe_name": "Paneer Tikka"
          }
        ]
      }
    ],
    "weekly_average": {
      "calories": 2100.0,
      "protein_g": 82.5,
      "carbs_g": 245.0,
      "fat_g": 62.5
    }
  }
}
```

---

## 4. Recipe & Ingredient Module (`/api/v1/recipes/*`)

### Parse Recipe
```
POST /api/v1/recipes/parse
```

**Request Body:**
```json
{
  "household_id": 1,
  "raw_text": "Paneer Tikka (Indian)...\n\nIngredients:\n- 300g Paneer\n- 1 cup yogurt\n...\n\nInstructions:\nMarinate paneer in yogurt...",
  "source": "scan|manual|ocr"
}
```

**Response (200):**
```json
{
  "status": "ok",
  "data": {
    "title": "Paneer Tikka",
    "cuisine": "Indian",
    "cuisine_type": "vegetarian",
    "servings": 4,
    "prep_time_minutes": 20,
    "cook_time_minutes": 15,
    "instructions": "Marinate paneer in yogurt and spices. Thread on skewers. Grill until golden.",
    "ingredients": [
      {
        "name": "Paneer",
        "quantity": 300,
        "unit": "g"
      }
    ],
    "confidence": 0.85,
    "source": "scan",
    "raw_text": "..."
  }
}
```

### Save Recipe
```
POST /api/v1/recipes/save
```

**Request Body:**
```json
{
  "household_id": 1,
  "name": "Paneer Tikka",
  "description": "Indian cheese appetizer",
  "cuisine": "Indian",
  "cuisine_type": "vegetarian",
  "servings": 4,
  "prep_time_minutes": 20,
  "cook_time_minutes": 15,
  "instructions": "Marinate paneer in yogurt and spices...",
  "ingredients": [
    {
      "ingredient_id": 10,
      "quantity": 300,
      "unit": "g",
      "optional": false
    }
  ],
  "nutrition": {
    "calories": 250,
    "protein_g": 20,
    "carbs_g": 5,
    "fat_g": 15,
    "fiber_g": 2
  }
}
```

**Response (201):** Recipe object with nutrition and ingredients

### Bulk Import Ingredients
```
POST /api/v1/recipes/import-ingredients
```

**Request Body:**
```json
{
  "household_id": 1,
  "ingredients": [
    {
      "name": "Paneer",
      "category": "dairy",
      "unit": "g",
      "calories_per_unit": 3.2,
      "protein_g": 0.25,
      "carbs_g": 0.02,
      "fat_g": 0.25,
      "fiber_g": 0,
      "is_seasonal": false,
      "notes": "Fresh dairy product"
    }
  ]
}
```

**Response (201):**
```json
{
  "status": "ok",
  "data": {
    "imported": 10,
    "skipped": 2
  }
}
```

### Search Recipes
```
GET /api/v1/recipes/search
```

**Query Parameters:**
- `household_id` (required, int)
- `q` (optional, string) — search query (recipe name/description)
- `cuisine` (optional, string) — filter by cuisine
- `ingredient_id` (optional, int) — filter by ingredient

**Response (200):**
```json
{
  "status": "ok",
  "data": [
    {
      "id": 5,
      "household_id": 1,
      "name": "Paneer Tikka",
      "cuisine": "Indian",
      "cuisine_type": "vegetarian"
    }
  ]
}
```

### Get Recipe Ingredients with Nutrition
```
GET /api/v1/recipes/{recipe_id}/ingredients
```

**Query Parameters:**
- `household_id` (required, int)

**Response (200):**
```json
{
  "status": "ok",
  "data": {
    "recipe_id": 5,
    "recipe_name": "Paneer Tikka",
    "ingredients": [
      {
        "id": 1,
        "recipe_id": 5,
        "ingredient_id": 10,
        "ingredient_name": "Paneer",
        "quantity": 300,
        "unit": "g",
        "optional": false,
        "nutrition": {
          "calories": 96.0,
          "protein_g": 7.5,
          "carbs_g": 0.6,
          "fat_g": 7.5,
          "fiber_g": 0
        }
      }
    ],
    "total_nutrition": {
      "calories": 96.0,
      "protein_g": 7.5,
      "carbs_g": 0.6,
      "fat_g": 7.5,
      "fiber_g": 0
    }
  }
}
```

---

## 5. Pipeline & Timeline (`/pipeline/*`)

### Process Text (Event Extraction)
```
POST /pipeline/process_text
```

**Request Body:**
```json
{
  "household_id": 1,
  "source": "scan|voice|email|manual",
  "raw_text": "School Annual Day on 3rd June at 3 PM"
}
```

**Response (201):**
```json
{
  "event": {
    "id": 1,
    "household_id": 1,
    "title": "School Annual Day",
    "event_date": "2026-06-03",
    "event_time": "15:00",
    "event_type": "event",
    "source": "scan",
    "confidence": 0.85
  },
  "automation_actions": [
    {
      "action": "Set reminder 1 day before",
      "status": "queued"
    }
  ]
}
```

### Process Scan Image
```
POST /pipeline/process_scan_image
```

**Form Fields:**
- `household_id` (required, int)
- `source` (optional, string) — defaults to "scan"
- `file` (required, file) — image file

**Response (201):** Same as `/pipeline/process_text` + OCR metadata

### Get Timeline (Today's Events)
```
GET /timeline/today
```

**Query Parameters:**
- `household_id` (required, int)

**Response (200):**
```json
{
  "count": 3,
  "items": [
    {
      "id": 1,
      "household_id": 1,
      "title": "Doctor appointment",
      "event_date": "2026-04-30",
      "event_time": "14:00",
      "event_type": "reminder",
      "source": "scan"
    }
  ]
}
```

---

## 6. Backup & Restore

### Export Backup
```
POST /backup/export
```

**Request Body:**
```json
{
  "household_id": 1,
  "backup_name": "backup_2026-04-30"
}
```

**Response (201):**
```json
{
  "id": 1,
  "household_id": 1,
  "backup_name": "backup_2026-04-30",
  "backup_path": "/path/to/backup.json",
  "data_size": 15234,
  "created_at": "2026-04-30T10:00:00"
}
```

### Restore Backup
```
POST /backup/restore
```

**Request Body:**
```json
{
  "household_id": 1,
  "backup_id": 1
}
```

**Response (200):**
```json
{
  "restored": true,
  "members": 3,
  "expenses": 150
}
```

---

## Database Schema

### Core Tables

| Table | Columns |
|-------|---------|
| `household_profiles` | id, name, description, currency, created_at, updated_at |
| `household_members` | id, household_id (FK), name, role, email, created_at |
| `household_expenses` | id, household_id (FK), date, description, amount, category, budget_category, bank, member_id (FK), imported_at, hash, excluded |
| `household_backups` | id, household_id (FK), backup_name, backup_path, data_size, created_at, expires_at |
| `household_events` | id, household_id (FK), title, event_date, event_time, event_type, source, raw_text, confidence, created_at |
| `automation_logs` | id, household_id (FK), event_id (FK), action, payload, status, created_at |

### Meal Planning Tables

| Table | Columns |
|-------|---------|
| `recipes` | id, household_id (FK), name, description, cuisine, cuisine_type, servings, prep_time_minutes, cook_time_minutes, total_time_minutes, difficulty, source, instructions, created_at, updated_at |
| `ingredients` | id, household_id (FK), name, category, unit, calories_per_unit, protein_g, carbs_g, fat_g, fiber_g, is_seasonal, notes, created_at |
| `recipe_ingredient_mapping` | id, recipe_id (FK), ingredient_id (FK), quantity, unit, optional |
| `nutrition_info` | id, recipe_id (FK) UNIQUE, calories, protein_g, carbs_g, fat_g, fiber_g, sodium_mg, sugar_g, updated_at |
| `meals` | id, household_id (FK), date, meal_type, recipe_id (FK), servings, notes, created_at |
| `meal_plans` | id, household_id (FK), name, description, start_date, end_date, plan_type, is_active, notes, created_at, updated_at |
| `shopping_lists` | id, household_id (FK), meal_plan_id (FK), name, start_date, end_date, is_completed, created_at, updated_at |
| `shopping_list_items` | id, shopping_list_id (FK), ingredient_id (FK), quantity, unit, is_checked, notes |

---

## Error Handling

All endpoints return error responses in the format:

```json
{
  "status": "error",
  "error": "Detailed error message"
}
```

**Common HTTP Status Codes:**
- `200 OK` — Success
- `201 Created` — Resource created
- `400 Bad Request` — Invalid parameters
- `404 Not Found` — Resource not found
- `500 Internal Server Error` — Server error

---

## Data Types & Formats

### Date Format
All dates use ISO 8601 format: `YYYY-MM-DD`

### Time Format
Times use 24-hour format: `HH:MM` (e.g., `14:30`)

### Amount Format
All monetary amounts are floats (e.g., `45.50`). Negative values indicate expenses, positive indicate income.

### Meal Types
- `breakfast`
- `lunch`
- `dinner`
- `snack`

### Cuisine Types
- `vegetarian`
- `vegan`
- `meat`
- `seafood`

### Plan Types
- `weekly`
- `custom`

---

## Rate Limiting

No rate limiting is currently implemented. In production, consider adding:
- 1000 requests per hour per household
- 100 concurrent requests per household

---

## CORS

CORS is currently disabled. Enable in production with appropriate origin whitelist.

---

## Authentication

**Current Status:** No authentication implemented.

**Recommended:** Add JWT or OAuth2 authentication for production.

---

## Deployment

### Running the Backend

```bash
# Check syntax
python main.py --check

# Run on default port (5001)
python main.py

# Run on custom port
python main.py --port 8000

# Run with debug mode
python main.py --debug
```

### Dependencies

```
flask>=2.3.0
sqlalchemy>=2.0.0
```

### Database

SQLite database is automatically created at: `backend/household_platform.db`

Backup files are stored in: `backend/backups/`

---

## Future Enhancements

1. **Advanced Recipe Parsing** — Integration with LLM for better OCR-to-recipe conversion
2. **Budget Tracking** — Monthly budget limits per category with alerts
3. **Trip Expense Settlement** — Automatic debt calculation and settlement suggestions
4. **Recipe Recommendations** — Based on available ingredients, dietary preferences
5. **Nutrition Tracking** — Daily calorie/macro goals with weekly summaries
6. **Seasonal Ingredients** — Highlight available seasonal ingredients
7. **Export to CSV/PDF** — Shopping lists, meal plans, expense reports

---

## Testing

Example curl requests:

```bash
# Create household
curl -X POST http://localhost:5001/household/create \
  -H "Content-Type: application/json" \
  -d '{"name": "Test Household", "currency": "EUR"}'

# Create meal plan
curl -X POST http://localhost:5001/api/v1/meals/plans \
  -H "Content-Type: application/json" \
  -d '{"household_id": 1, "name": "Weekly Plan", "start_date": "2026-05-01", "end_date": "2026-05-07"}'

# List recipes
curl http://localhost:5001/api/v1/meals/recipes?household_id=1
```

---

## Support & Contact

For issues or questions, refer to the household platform documentation or contact the development team.
