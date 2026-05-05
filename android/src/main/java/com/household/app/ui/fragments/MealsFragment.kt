package com.household.app.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import com.household.app.R
import com.household.app.data.DashboardPrefs
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import java.time.LocalDate
import java.util.Locale

class MealsFragment : Fragment() {

    private data class ProteinRange(
        val min: Int,
        val max: Int
    )

    private data class ProteinProfile(
        val label: String,
        val age: Int,
        val weightKg: Double,
        val baselinePerKgMin: Double,
        val baselinePerKgMax: Double,
        val practicalRange: ProteinRange,
        val detail: String,
        val split: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Reuse the richer meals planner surface.
        return inflater.inflate(R.layout.fragment_plan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindMealPlan(view)
        bindGroceryList(view)

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val nextMeal = when {
            hour < 9 -> "Breakfast at 08:00"
            hour < 14 -> "Lunch at 13:00"
            else -> "Dinner at 19:30"
        }

        val womanProfile = buildWomanProfile()
        val manProfile = buildManProfile()

        // Keep Today summary synchronized with the current Meals surface.
        // Protein logic runs in backend; targets kept for daily snapshot in Today summary.
        lifecycleScope.launch {
            DashboardPrefs.setMealsSummary(
                context = requireContext(),
                mealsToday = 3,
                nextMeal = "$nextMeal | Protein: ${womanProfile.practicalRange.min}-${womanProfile.practicalRange.max}g / ${manProfile.practicalRange.min}-${manProfile.practicalRange.max}g",
                updatedOn = LocalDate.now()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    private fun buildWomanProfile(): ProteinProfile {
        val weightKg = 73.0
        val baselineMin = 1.0
        val baselineMax = 1.2
        val baselineRange = computeRange(weightKg, baselineMin, baselineMax)
        return ProteinProfile(
            label = "Woman, age 40",
            age = 40,
            weightKg = weightKg,
            baselinePerKgMin = baselineMin,
            baselinePerKgMax = baselineMax,
            practicalRange = baselineRange,
            detail = "Age 40+ baseline: ${baselineMin}-${baselineMax} g/kg gives ${baselineRange.min}-${baselineRange.max} g/day.",
            split = "Breakfast 20-25 g | Lunch 20-25 g | Dinner 20-25 g | Snack 10-15 g if needed"
        )
    }

    private fun buildManProfile(): ProteinProfile {
        val weightKg = 86.0
        val practicalMin = 100
        val practicalMax = 130
        return ProteinProfile(
            label = "Man, age 43",
            age = 43,
            weightKg = weightKg,
            baselinePerKgMin = 1.2,
            baselinePerKgMax = 1.6,
            practicalRange = ProteinRange(practicalMin, practicalMax),
            detail = "Weight-loss practical target: ${practicalMin}-${practicalMax} g/day (high-protein range for muscle retention).",
            split = "Breakfast 25-30 g | Lunch 30-40 g | Dinner 30-40 g | Snack 10-20 g if needed"
        )
    }

    private fun computeRange(weightKg: Double, minPerKg: Double, maxPerKg: Double): ProteinRange {
        val min = kotlin.math.round(weightKg * minPerKg).toInt()
        val max = kotlin.math.round(weightKg * maxPerKg).toInt()
        return ProteinRange(min = min, max = max)
    }

    private fun formatKg(value: Double): String {
        return String.format(Locale.getDefault(), "%.0f", value)
    }

    private fun bindMealPlan(root: View) {
        val container = root.findViewById<android.widget.LinearLayout>(R.id.meal_plan_container)
        container.removeAllViews()

        val meals = generateTwoWeekMealPlan()

        meals.forEachIndexed { index, day ->
            container.addView(createMealDayRow(day.dayName, day.breakfast, day.lunch, day.dinner))
            if (index < meals.size - 1) {
                container.addView(createDivider())
            }
        }
    }

    private fun bindGroceryList(root: View) {
        val container = root.findViewById<android.widget.LinearLayout>(R.id.grocery_list_container)
        container.removeAllViews()

        val groceries = generateTwoWeekGroceries()

        groceries.forEachIndexed { index, item ->
            container.addView(createGroceryItemRow(item.name, item.quantity, item.price))
            if (index < groceries.size - 1) {
                container.addView(createDivider())
            }
        }
    }

    private fun createMealDayRow(dayName: String, breakfast: String, lunch: String, dinner: String): android.widget.LinearLayout {
        val row = android.widget.LinearLayout(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
        }

        val dayText = android.widget.TextView(requireContext()).apply {
            text = dayName
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(com.household.app.R.color.text_primary, null))
        }
        row.addView(dayText)

        val mealsText = android.widget.TextView(requireContext()).apply {
            text = "B: $breakfast | L: $lunch | D: $dinner"
            textSize = 12f
            setTextColor(resources.getColor(com.household.app.R.color.text_secondary, null))
            setPadding(0, 4, 0, 0)
        }
        row.addView(mealsText)

        return row
    }

    private fun createGroceryItemRow(name: String, quantity: String, price: String): android.widget.LinearLayout {
        val row = android.widget.LinearLayout(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(12, 8, 12, 8)
        }

        val check = android.view.View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(18, 18)
            background = androidx.core.content.ContextCompat.getDrawable(requireContext(), com.household.app.R.drawable.bg_metric_green)
        }
        row.addView(check)

        val nameText = android.widget.TextView(requireContext()).apply {
            text = "$name — $quantity"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginStart = 10 }
            textSize = 13f
            setTextColor(resources.getColor(com.household.app.R.color.text_primary, null))
        }
        row.addView(nameText)

        val priceText = android.widget.TextView(requireContext()).apply {
            text = price
            textSize = 12f
            setTextColor(resources.getColor(com.household.app.R.color.text_secondary, null))
        }
        row.addView(priceText)

        return row
    }

    private fun createDivider(): android.view.View {
        return android.view.View(requireContext()).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                topMargin = 8
                bottomMargin = 8
            }
            setBackgroundColor(resources.getColor(com.household.app.R.color.line, null))
        }
    }

    private data class MealDay(
        val dayName: String,
        val breakfast: String,
        val lunch: String,
        val dinner: String
    )

    private data class GroceryItem(
        val name: String,
        val quantity: String,
        val price: String
    )

    private fun generateTwoWeekMealPlan(): List<MealDay> {
        val baseWeek = listOf(
            MealDay("Mon", "Bread + 3 eggs", "Rice + dal + chicken", "Idli + egg curry"),
            MealDay("Tue", "Idli + chutney", "Idli + chutney", "Idli + chutney"),
            MealDay("Wed", "Bread + 3 eggs", "Rice + dal + tuna", "Idiyappam + chicken curry"),
            MealDay("Thu", "Bread + 3 eggs", "Khichdi + curd", "Idli + chicken curry"),
            MealDay("Fri", "Bread + 3 eggs", "Rice + dal + salmon", "Dosa + egg curry"),
            MealDay("Sat", "Bread + 3 eggs", "Rice + dal + prawn", "Puttu + chicken curry"),
            MealDay("Sun", "Bread + 3 eggs", "Rice + dal + chicken", "Upma + eggs or curd")
        )

        val week2 = baseWeek.map { it.copy(dayName = it.dayName + " (W2)", breakfast = it.breakfast, lunch = it.lunch.replace("chicken", "fish"), dinner = it.dinner) }

        return baseWeek + week2
    }

    private fun generateTwoWeekGroceries(): List<GroceryItem> {
        return listOf(
            GroceryItem("Eggs (x72)", "2 dozen", "€ 8.50"),
            GroceryItem("Chicken", "2 kg", "€ 16.00"),
            GroceryItem("Fish/Salmon", "1.5 kg", "€ 18.00"),
            GroceryItem("Prawns", "500 g", "€ 12.00"),
            GroceryItem("Curd", "1 liter", "€ 4.20"),
            GroceryItem("Toor dal", "1 kg", "€ 3.60"),
            GroceryItem("Rice (brown)", "2 kg", "€ 4.80"),
            GroceryItem("Idli rice + urad", "500 g each", "€ 5.40"),
            GroceryItem("Bread", "2 loaves", "€ 3.80"),
            GroceryItem("Onions", "1 kg", "€ 1.50"),
            GroceryItem("Tomatoes", "1 kg", "€ 2.20"),
            GroceryItem("Ginger-garlic paste", "200 g", "€ 1.80"),
            GroceryItem("Green chillies", "200 g", "€ 0.80"),
            GroceryItem("Coriander/cumin seeds", "100 g each", "€ 2.40"),
            GroceryItem("Turmeric powder", "100 g", "€ 1.20"),
            GroceryItem("Coconut oil", "500 ml", "€ 6.50"),
            GroceryItem("Salt/spices mix", "assorted", "€ 3.00"),
            GroceryItem("Vegetables (mixed)", "2 kg", "€ 5.50")
        )
    }
}
