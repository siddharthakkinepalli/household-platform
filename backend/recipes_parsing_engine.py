"""
recipes_parsing_engine.py
Extends ai_pipeline.py with recipe-specific parsing logic.

Capabilities:
  1. OCR image → text extraction (via EasyOCR)
  2. Recipe text parsing → structured ingredients + instructions
  3. Ingredient normalization (qty + unit + name)
  4. Unit conversion (cups → ml, tbsp → ml, oz → g, etc.)
  5. Cuisine detection (Indian, Italian, Mexican, etc.)
  6. Cooking time extraction
"""

import re
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass
from datetime import datetime


@dataclass
class ParsedIngredient:
    """Structured ingredient extracted from recipe text."""
    name: str
    quantity: Optional[float]
    unit: str
    original_text: str
    normalized_unit: Optional[str] = None
    normalized_quantity: Optional[float] = None
    optional: bool = False

    def to_dict(self):
        return {
            'name': self.name,
            'quantity': self.quantity,
            'unit': self.unit,
            'original_text': self.original_text,
            'normalized_unit': self.normalized_unit,
            'normalized_quantity': self.normalized_quantity,
            'optional': self.optional,
        }


@dataclass
class ParsedRecipe:
    """Fully parsed recipe from OCR or text input."""
    name: str
    ingredients: List[ParsedIngredient]
    instructions: str
    cuisine: Optional[str] = None
    cuisine_type: Optional[str] = None  # vegetarian, vegan, meat, seafood
    servings: int = 1
    prep_time_minutes: Optional[int] = None
    cook_time_minutes: Optional[int] = None
    total_time_minutes: Optional[int] = None
    difficulty: str = 'medium'  # easy, medium, hard
    source: str = 'manual'
    confidence: float = 0.5
    raw_text: str = ''

    def to_dict(self):
        return {
            'name': self.name,
            'ingredients': [ing.to_dict() for ing in self.ingredients],
            'instructions': self.instructions,
            'cuisine': self.cuisine,
            'cuisine_type': self.cuisine_type,
            'servings': self.servings,
            'prep_time_minutes': self.prep_time_minutes,
            'cook_time_minutes': self.cook_time_minutes,
            'total_time_minutes': self.total_time_minutes,
            'difficulty': self.difficulty,
            'source': self.source,
            'confidence': self.confidence,
        }


class RecipeParsingEngine:
    """Core recipe parsing logic for OCR + text → structured recipes."""

    # Unit conversion table (normalize to grams and ml)
    UNIT_CONVERSIONS = {
        # Volume
        'tsp': 5,          # teaspoon → ml
        'tbsp': 15,        # tablespoon → ml
        'fl oz': 29.5735,  # fluid ounce → ml
        'cup': 236.588,    # cup → ml
        'ml': 1,
        'l': 1000,
        'dl': 100,
        
        # Weight
        'g': 1,            # grams
        'kg': 1000,
        'mg': 0.001,
        'oz': 28.3495,     # ounce → grams
        'lb': 453.592,     # pound → grams
        'piece': 0,        # pieces (no conversion)
        'pieces': 0,
    }

    # Ingredient category keywords
    INGREDIENT_CATEGORIES = {
        'Vegetables': ['tomato', 'cucumber', 'bell pepper', 'onion', 'potato', 'lettuce', 'carrot',
                      'zucchini', 'broccoli', 'cauliflower', 'spinach', 'cabbage', 'garlic', 'ginger'],
        'Fruits': ['apple', 'banana', 'orange', 'strawberry', 'grape', 'pear', 'kiwi', 'pineapple',
                  'mango', 'melon', 'lemon', 'lime', 'coconut'],
        'Dairy': ['milk', 'cheese', 'yogurt', 'butter', 'cream', 'curd', 'paneer', 'ghee'],
        'Meat': ['chicken', 'beef', 'pork', 'lamb', 'mutton', 'fish', 'shrimp', 'salmon', 'turkey'],
        'Grains': ['rice', 'wheat', 'flour', 'bread', 'pasta', 'oats', 'barley', 'semolina'],
        'Spices': ['turmeric', 'cumin', 'coriander', 'chili', 'pepper', 'salt', 'cinnamon', 'clove',
                  'cardamom', 'nutmeg', 'garam masala', 'asafetida'],
        'Oils': ['olive oil', 'coconut oil', 'vegetable oil', 'mustard oil', 'sunflower oil'],
        'Pantry': ['sugar', 'honey', 'jam', 'peanut butter', 'baking powder', 'baking soda', 'yeast'],
    }

    # Cuisine patterns
    CUISINE_PATTERNS = {
        'Indian': ['curry', 'masala', 'turmeric', 'dal', 'paneer', 'tandoor', 'naan', 'roti', 'ghee', 'asafetida'],
        'Italian': ['pasta', 'risotto', 'mozzarella', 'parmesan', 'basil', 'oregano', 'bolognese'],
        'Mexican': ['tortilla', 'salsa', 'cilantro', 'cumin', 'chili', 'coriander', 'tamale'],
        'Chinese': ['soy sauce', 'ginger', 'sesame', 'wok', 'stir fry', 'oyster sauce'],
        'Thai': ['coconut milk', 'lime', 'lemongrass', 'thai basil', 'fish sauce'],
        'Mediterranean': ['olive oil', 'feta', 'oregano', 'olives', 'lemon'],
    }

    # Cooking time patterns
    TIME_PATTERNS = [
        r'(?:prep|preparation)\s*:?\s*(\d+)\s*(?:min|minutes)',
        r'(?:cook|cooking)\s*:?\s*(\d+)\s*(?:min|minutes)',
        r'(?:bake|baking)\s*:?\s*(\d+)\s*(?:min|minutes)',
        r'(?:total)\s*(?:time)?\s*:?\s*(\d+)\s*(?:min|minutes)',
        r'(\d+)\s*(?:min|minutes)\s*(?:prep|preparation)',
        r'(\d+)\s*(?:min|minutes)\s*(?:cook|cooking)',
    ]

    @staticmethod
    def normalize_unit(unit: str) -> Tuple[Optional[str], float]:
        """
        Normalize unit to metric equivalent.
        Returns: (normalized_unit, conversion_factor)
        - normalized_unit: 'g' for weight, 'ml' for volume, None for pieces
        - conversion_factor: multiplier to base unit
        """
        unit_lower = unit.lower().strip()
        
        # Direct match
        if unit_lower in RecipeParsingEngine.UNIT_CONVERSIONS:
            value = RecipeParsingEngine.UNIT_CONVERSIONS[unit_lower]
            if unit_lower in ['ml', 'dl', 'l']:
                return 'ml', value
            elif unit_lower in ['mg', 'g', 'kg', 'oz', 'lb']:
                return 'g', value
            else:
                return None, value

        # Fuzzy match (common abbreviations)
        for key, value in RecipeParsingEngine.UNIT_CONVERSIONS.items():
            if key.startswith(unit_lower) or unit_lower.startswith(key):
                if key in ['ml', 'dl', 'l']:
                    return 'ml', value
                elif key in ['mg', 'g', 'kg', 'oz', 'lb']:
                    return 'g', value
                else:
                    return None, value

        # Unknown unit
        return None, 1.0

    @staticmethod
    def parse_ingredient_line(line: str) -> Optional[ParsedIngredient]:
        """
        Parse a single ingredient line into structured format.
        Handles: "2 cups flour", "500g sugar", "1 tbsp oil", "3-4 cloves garlic"
        """
        line = line.strip()
        if not line or line.startswith('#') or len(line) < 2:
            return None

        # Check if optional
        optional = 'optional' in line.lower()
        line_clean = line.replace('(optional)', '').replace('Optional', '').strip()

        # Pattern: quantity + unit + ingredient
        # Matches: "2 cups flour", "500g sugar", "1/2 tsp salt", "1-2 onions", "a pinch of salt"
        pattern = r'^(\d+(?:\.\d+)?(?:\s*[-/]\s*\d+(?:\.\d+)?)?)\s*([a-zA-Z\s]+?)\s+(.+?)$'
        match = re.match(pattern, line_clean, re.IGNORECASE)

        if match:
            qty_str, unit_str, ingredient_name = match.groups()
            
            # Parse quantity (handle ranges: "1-2" → 1.5)
            if '-' in qty_str or '/' in qty_str:
                parts = re.split(r'[-/]', qty_str)
                try:
                    qty = (float(parts[0]) + float(parts[1])) / 2
                except:
                    qty = float(parts[0])
            else:
                try:
                    qty = float(qty_str)
                except:
                    qty = None

            unit = unit_str.strip()
            norm_unit, factor = RecipeParsingEngine.normalize_unit(unit)

            return ParsedIngredient(
                name=ingredient_name.strip(),
                quantity=qty,
                unit=unit,
                normalized_unit=norm_unit,
                normalized_quantity=qty * factor if qty else None,
                original_text=line,
                optional=optional,
            )

        # Fallback: just ingredient name (no qty/unit)
        return ParsedIngredient(
            name=line_clean,
            quantity=None,
            unit='',
            normalized_unit=None,
            normalized_quantity=None,
            original_text=line,
            optional=optional,
        )

    @staticmethod
    def extract_servings(text: str) -> int:
        """Extract servings from text. Default: 1"""
        pattern = r'(?:serves|servings?|makes?)\s*:?\s*(\d+)'
        match = re.search(pattern, text, re.IGNORECASE)
        return int(match.group(1)) if match else 1

    @staticmethod
    def extract_cooking_time(text: str) -> Dict[str, Optional[int]]:
        """Extract prep, cook, and total time in minutes."""
        times = {
            'prep_time_minutes': None,
            'cook_time_minutes': None,
            'total_time_minutes': None,
        }

        # Prep time
        prep_match = re.search(r'prep\s*:?\s*(\d+)\s*(?:min|minutes)', text, re.IGNORECASE)
        if prep_match:
            times['prep_time_minutes'] = int(prep_match.group(1))

        # Cook time
        cook_match = re.search(r'cook\s*:?\s*(\d+)\s*(?:min|minutes)', text, re.IGNORECASE)
        if cook_match:
            times['cook_time_minutes'] = int(cook_match.group(1))

        # Total time
        total_match = re.search(r'total\s*(?:time)?\s*:?\s*(\d+)\s*(?:min|minutes)', text, re.IGNORECASE)
        if total_match:
            times['total_time_minutes'] = int(total_match.group(1))

        # Calculate total if missing
        if times['total_time_minutes'] is None:
            if times['prep_time_minutes'] and times['cook_time_minutes']:
                times['total_time_minutes'] = times['prep_time_minutes'] + times['cook_time_minutes']

        return times

    @staticmethod
    def detect_cuisine(text: str) -> Optional[str]:
        """Detect cuisine type based on keywords."""
        text_lower = text.lower()
        cuisine_scores = {}

        for cuisine, keywords in RecipeParsingEngine.CUISINE_PATTERNS.items():
            score = sum(1 for kw in keywords if kw in text_lower)
            if score > 0:
                cuisine_scores[cuisine] = score

        if cuisine_scores:
            return max(cuisine_scores, key=cuisine_scores.get)
        return None

    @staticmethod
    def detect_cuisine_type(text: str, ingredients: List[ParsedIngredient]) -> str:
        """
        Detect dietary type: vegetarian, vegan, meat, seafood
        """
        text_lower = text.lower()
        ingredients_lower = ' '.join([ing.name.lower() for ing in ingredients])
        combined = text_lower + ' ' + ingredients_lower

        vegan_keywords = ['tofu', 'tempeh', 'nutritional yeast', 'plant-based', 'vegan']
        meat_keywords = ['chicken', 'beef', 'pork', 'lamb', 'mutton', 'fish', 'shrimp', 'salmon', 'turkey', 'duck']
        seafood_keywords = ['fish', 'shrimp', 'salmon', 'tuna', 'crab', 'lobster', 'oyster', 'mussel']
        dairy_keywords = ['milk', 'cheese', 'butter', 'cream', 'yogurt', 'ghee']

        has_vegan = any(kw in combined for kw in vegan_keywords)
        has_meat = any(kw in combined for kw in meat_keywords)
        has_seafood = any(kw in combined for kw in seafood_keywords)
        has_dairy = any(kw in combined for kw in dairy_keywords)

        if has_vegan and not has_meat and not has_dairy:
            return 'vegan'
        elif has_seafood and not has_meat:
            return 'seafood'
        elif has_meat:
            return 'meat'
        elif has_dairy or (not has_vegan and not has_meat):
            return 'vegetarian'
        else:
            return 'vegetarian'

    @staticmethod
    def parse_recipe_text(text: str) -> ParsedRecipe:
        """
        Parse recipe text into structured format.
        Expected format:
        Recipe Name
        Ingredients:
        - ingredient 1
        - ingredient 2
        Instructions:
        Step 1...
        Step 2...
        """
        lines = text.split('\n')
        recipe_name = 'Untitled Recipe'
        ingredients_section = False
        instructions_section = False
        ingredient_lines = []
        instruction_lines = []

        for line in lines:
            line = line.strip()

            # Detect sections
            if 'ingredient' in line.lower() and ':' in line:
                ingredients_section = True
                instructions_section = False
                continue

            if 'instruction' in line.lower() or 'direction' in line.lower() or 'method' in line.lower():
                ingredients_section = False
                instructions_section = True
                continue

            # First non-empty line is usually the recipe name
            if not recipe_name or recipe_name == 'Untitled Recipe':
                if line and not any(kw in line.lower() for kw in ['ingredient', 'instruction', 'serves', 'prep']):
                    recipe_name = line
                    continue

            # Collect ingredients
            if ingredients_section:
                if line and not any(x in line.lower() for x in ['serves', 'prep', 'cook']):
                    ingredient_lines.append(line)

            # Collect instructions
            if instructions_section:
                if line:
                    instruction_lines.append(line)

        # Parse ingredients
        parsed_ingredients = []
        for ing_line in ingredient_lines:
            parsed_ing = RecipeParsingEngine.parse_ingredient_line(ing_line)
            if parsed_ing:
                parsed_ingredients.append(parsed_ing)

        # Extract metadata
        servings = RecipeParsingEngine.extract_servings(text)
        times = RecipeParsingEngine.extract_cooking_time(text)
        cuisine = RecipeParsingEngine.detect_cuisine(text)
        cuisine_type = RecipeParsingEngine.detect_cuisine_type(text, parsed_ingredients)

        # Combine instructions
        instructions = '\n'.join(instruction_lines)

        # Calculate confidence
        confidence = 0.5
        if recipe_name != 'Untitled Recipe':
            confidence += 0.2
        if parsed_ingredients:
            confidence += 0.2
        if instructions:
            confidence += 0.1
        confidence = min(confidence, 0.95)

        return ParsedRecipe(
            name=recipe_name,
            ingredients=parsed_ingredients,
            instructions=instructions,
            cuisine=cuisine,
            cuisine_type=cuisine_type,
            servings=servings,
            prep_time_minutes=times.get('prep_time_minutes'),
            cook_time_minutes=times.get('cook_time_minutes'),
            total_time_minutes=times.get('total_time_minutes'),
            source='manual',
            confidence=confidence,
            raw_text=text,
        )

    @staticmethod
    def calculate_difficulty(ingredients_count: int, instructions_len: int, times: Dict) -> str:
        """
        Estimate difficulty: easy, medium, hard
        Based on: ingredient count, instruction length, cooking time
        """
        score = 0

        # Ingredient complexity
        if ingredients_count < 5:
            score += 1
        elif ingredients_count < 10:
            score += 2
        else:
            score += 3

        # Instruction complexity
        if instructions_len < 200:
            score += 1
        elif instructions_len < 500:
            score += 2
        else:
            score += 3

        # Time
        total_time = (times.get('prep_time_minutes') or 0) + (times.get('cook_time_minutes') or 0)
        if total_time < 30:
            score += 1
        elif total_time < 60:
            score += 2
        else:
            score += 3

        # Average score
        avg_score = score / 3

        if avg_score <= 1.7:
            return 'easy'
        elif avg_score <= 2.3:
            return 'medium'
        else:
            return 'hard'
