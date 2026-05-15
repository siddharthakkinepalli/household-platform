package com.household.app.domain.services

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Indian Meal Planner Logic
 *
 * Integrates with pantry to suggest meals based on:
 * - Available ingredients (from Receipt Scanner)
 * - Time of day (breakfast, lunch, dinner)
 * - Family preferences
 */
object IndianMealPlanner {

    // Indian cuisine categories
    enum class MealType {
        BREAKFAST, LUNCH, DINNER, SNACK
    }

    data class MealSuggestion(
        val name: String,
        val cuisine: String,
        val mealType: MealType,
        val ingredients: List<String>,
        val prepTimeMinutes: Int,
        val pantryStatus: PantryStatus
    )

    enum class PantryStatus {
        READY,      // All ingredients available
        PARTIAL,   // Some ingredients missing
        MISSING    // Most ingredients missing
    }

    // Common Indian dishes mapped to ingredients
    private val DISH_INGREDIENTS = mapOf(
        "Dal Tadka" to listOf("toor dal", "onion", "tomato", "garlic", "cumin", "turmeric"),
        "Biryani" to listOf("rice", "chicken", "onion", "yogurt", "spices", "saffron"),
        "Paneer Butter Masala" to listOf("paneer", "tomato", "cream", "butter", "spices"),
        "Aloo Gobi" to listOf("potato", "cauliflower", "onion", "tomato", "spices"),
        "Chole" to listOf("chickpeas", "onion", "tomato", "spices", "chole masala"),
        "Roti" to listOf("wheat flour", "ghee"),
        "Paratha" to listOf("wheat flour", "ghee", "aloo"),
        "Idli" to listOf("rice", "urad dal"),
        "Dosa" to listOf("rice", "urad dal", "potato"),
        "Upma" to listOf("semolina", "vegetables"),
        "Poha" to listOf("flattened rice", "potato", "onion"),
        "Kheer" to listOf("milk", "rice", "sugar", "cardamom"),
        "Rajma" to listOf("kidney beans", "onion", "tomato", "spices"),
        "Kadhi Pakora" to listOf("yogurt", "gram flour", "onion"),
        "Palak Paneer" to listOf("spinach", "paneer", "onion", "cream"),
        "Butter Chicken" to listOf("chicken", "tomato", "cream", "butter", "spices"),
        "Rogan Josh" to listOf("lamb", "onion", "tomato", "spices"),
        "Fish Curry" to listOf("fish", "coconut", "curry leaves", "spices"),
        "Jeera Rice" to listOf("rice", "cumin"),
        "Cucumber Raita" to listOf("yogurt", "cucumber", "cumin")
    )

    // Time-based meal suggestions
    private val MEALS_BY_TIME = mapOf(
        MealType.BREAKFAST to listOf("Idli", "Dosa", "Upma", "Poha", "Paratha"),
        MealType.LUNCH to listOf("Dal Tadka", "Roti", "Aloo Gobi", "Chole", "Rajma"),
        MealType.DINNER to listOf("Biryani", "Paneer Butter Masala", "Butter Chicken", "Rogan Josh", "Palak Paneer"),
        MealType.SNACK to listOf("Kheer", "Cucumber Raita")
    )

    /**
     * Get meal suggestion based on time of day
     */
    fun getSuggestionForTime(dateTime: LocalDateTime = LocalDateTime.now()): MealSuggestion {
        val mealType = getMealType(dateTime.toLocalTime())
        val dishes = MEALS_BY_TIME[mealType] ?: MEALS_BY_TIME[MealType.LUNCH]!!
        val dish = dishes.random()

        return MealSuggestion(
            name = dish,
            cuisine = "Indian",
            mealType = mealType,
            ingredients = DISH_INGREDIENTS[dish] ?: emptyList(),
            prepTimeMinutes = estimatePrepTime(dish),
            pantryStatus = PantryStatus.PARTIAL  // Would check actual pantry
        )
    }

    /**
     * Check pantry ingredients against dish requirements
     */
    fun checkPantryStatus(dish: String, availableIngredients: List<String>): PantryStatus {
        val required = DISH_INGREDIENTS[dish] ?: return PantryStatus.MISSING
        val available = availableIngredients.map { it.lowercase() }

        val matchCount = required.count { req ->
            available.any { it.contains(req.lowercase()) || req.lowercase().contains(it) }
        }

        val ratio = matchCount.toFloat() / required.size

        return when {
            ratio >= 0.8f -> PantryStatus.READY
            ratio >= 0.4f -> PantryStatus.PARTIAL
            else -> PantryStatus.MISSING
        }
    }

    /**
     * Get all suggested meals for the day
     */
    fun getDailyMealPlan(): List<MealSuggestion> {
        val now = LocalDateTime.now()
        return listOf(
            getSuggestionForTime(now.withHour(7).withMinute(0)),  // Breakfast
            getSuggestionForTime(now.withHour(13).withMinute(0)),  // Lunch
            getSuggestionForTime(now.withHour(19).withMinute(0))   // Dinner
        )
    }

    /**
     * Determine meal type based on time
     */
    private fun getMealType(time: LocalTime): MealType {
        return when {
            time.hour < 10 -> MealType.BREAKFAST
            time.hour < 15 -> MealType.LUNCH
            time.hour < 18 -> MealType.SNACK
            else -> MealType.DINNER
        }
    }

    /**
     * Estimate prep time based on dish complexity
     */
    private fun estimatePrepTime(dish: String): Int {
        return when {
            dish in listOf("Idli", "Upma", "Poha", "Cucumber Raita") -> 20
            dish in listOf("Dal Tadka", "Roti", "Jeera Rice") -> 30
            dish in listOf("Aloo Gobi", "Chole", "Rajma", "Paratha") -> 45
            dish in listOf("Paneer Butter Masala", "Butter Chicken", "Palak Paneer") -> 60
            dish in listOf("Biryani", "Rogan Josh", "Kadhi Pakora") -> 75
            else -> 45
        }
    }

    /**
     * Format meal for display
     */
    fun formatMealDisplay(meal: MealSuggestion): String {
        val emoji = when (meal.mealType) {
            MealType.BREAKFAST -> "🍳"
            MealType.LUNCH -> "🍛"
            MealType.DINNER -> "🍽️"
            MealType.SNACK -> "🫖"
        }
        val statusIcon = when (meal.pantryStatus) {
            PantryStatus.READY -> "✅"
            PantryStatus.PARTIAL -> "⚠️"
            PantryStatus.MISSING -> "❌"
        }
        return "$emoji ${meal.name} $statusIcon (${meal.prepTimeMinutes} min)"
    }
}