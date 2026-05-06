package com.household.app.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.household.app.ui.compose.theme.EliteNavy
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.TextMain
import com.household.app.ui.compose.theme.TextSecondary
import com.household.app.ui.v2.components.EliteGlassCard

@Composable
fun V2MealsScreen() {
    val weekPlan = listOf(
        DayPlan("Monday", "Oats with almonds", "Paneer wrap", "Lentil curry"),
        DayPlan("Tuesday", "Greek yogurt + seeds", "Rice bowl", "Chicken salad"),
        DayPlan("Wednesday", "Egg toast", "Pasta primavera", "Fish + quinoa"),
        DayPlan("Thursday", "Smoothie", "Dal + roti", "Veg stir fry")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EliteNavy)
            .padding(16.dp)
    ) {
        Text("Meals", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = TextMain)
        Text("Personalized Weekly Plan", color = TextSecondary)

        Spacer(Modifier.height(20.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(weekPlan) { day ->
                MealDayItem(day)
            }
        }
    }
}

private data class DayPlan(
    val day: String,
    val breakfast: String,
    val lunch: String,
    val dinner: String
)

@Composable
private fun MealDayItem(dayPlan: DayPlan) {
    EliteGlassCard(glowColor = LumeEmerald, modifier = Modifier.fillMaxWidth()) {
        Text(dayPlan.day, color = TextMain, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        MealLine("Breakfast", dayPlan.breakfast)
        MealLine("Lunch", dayPlan.lunch)
        MealLine("Dinner", dayPlan.dinner)
    }
}

@Composable
private fun MealLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label:",
            color = TextSecondary,
            modifier = Modifier.weight(0.34f)
        )
        Text(
            text = value,
            color = TextMain,
            modifier = Modifier.weight(0.66f)
        )
    }
}
