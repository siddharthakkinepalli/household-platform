package com.household.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.household.app.data.entities.DashboardPrefsEntity
import com.household.app.data.entities.WeightSnapshotEntity
import com.household.app.data.entities.MealsSummaryEntity

object DashboardPrefs {

    data class WalletQuickFilter(
        val category: String,
        val query: String
    )

    data class WeightSnapshot(
        val currentKg: Double,
        val previousKg: Double?,
        val date: LocalDate
    )

    data class MealsSummary(
        val mealsToday: Int,
        val nextMeal: String,
        val updatedOn: LocalDate
    )

    suspend fun setWalletQuickFilter(context: Context, category: String, query: String) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.dashboardPrefsDao()
        val existing = dao.getPrefs() ?: DashboardPrefsEntity()
        dao.insertPrefs(existing.copy(walletQuickCategory = category, walletQuickQuery = query))
    }

    suspend fun consumeWalletQuickFilter(context: Context): WalletQuickFilter? = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.dashboardPrefsDao()
        val existing = dao.getPrefs() ?: return@withContext null
        
        if (existing.walletQuickCategory.isBlank()) return@withContext null
        
        val filter = WalletQuickFilter(category = existing.walletQuickCategory, query = existing.walletQuickQuery)
        dao.insertPrefs(existing.copy(walletQuickCategory = "", walletQuickQuery = ""))
        
        filter
    }

    suspend fun getMonthlyBudget(context: Context): Int = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.dashboardPrefsDao()
        dao.getPrefs()?.monthlyBudget ?: 3000
    }

    suspend fun setMonthlyBudget(context: Context, budget: Int) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.dashboardPrefsDao()
        val existing = dao.getPrefs() ?: DashboardPrefsEntity()
        dao.insertPrefs(existing.copy(monthlyBudget = budget.coerceIn(500, 10000)))
    }

    suspend fun setWeightSnapshot(context: Context, currentKg: Double, previousKg: Double?, date: LocalDate) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.weightSnapshotDao()
        dao.insertSnapshot(WeightSnapshotEntity(
            currentKg = currentKg,
            previousKg = previousKg,
            date = date.toString()
        ))
    }

    suspend fun getWeightSnapshot(context: Context): WeightSnapshot? = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.weightSnapshotDao()
        val entity = dao.getLatestSnapshot()
        
        entity?.let {
            WeightSnapshot(
                currentKg = it.currentKg,
                previousKg = it.previousKg,
                date = LocalDate.parse(it.date)
            )
        }
    }

    suspend fun setMealsSummary(context: Context, mealsToday: Int, nextMeal: String, updatedOn: LocalDate) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.mealsSummaryDao()
        dao.insertSummary(MealsSummaryEntity(
            mealsToday = mealsToday,
            nextMeal = nextMeal,
            updatedOn = updatedOn.toString()
        ))
    }

    suspend fun getMealsSummary(context: Context): MealsSummary? = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.mealsSummaryDao()
        val entity = dao.getSummary()
        
        entity?.let {
            MealsSummary(
                mealsToday = it.mealsToday,
                nextMeal = it.nextMeal,
                updatedOn = LocalDate.parse(it.updatedOn)
            )
        }
    }
}
