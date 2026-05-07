package com.household.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.household.app.BuildConfig
import com.household.app.data.AppDatabase
import com.household.app.data.entities.MerchantRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class Transaction(
    val id: String,
    val description: String,
    val amount: Double,
    val category: String,
    val date: String
)

data class CategorySummary(
    val category: String,
    val totalAmount: Double,
    val transactionCount: Int
)

class ExpensesViewModel(application: Application) : AndroidViewModel(application) {
    private val db by lazy { AppDatabase.getInstance(getApplication()) }

    private val _recentTransactions = MutableLiveData<List<Transaction>>()
    val recentTransactions: LiveData<List<Transaction>> = _recentTransactions

    private val _categorySummary = MutableLiveData<List<CategorySummary>>()
    val categorySummary: LiveData<List<CategorySummary>> = _categorySummary

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _selectedCategory = MutableLiveData("All")
    val selectedCategory: LiveData<String> = _selectedCategory

    private val _selectedTimeFilter = MutableLiveData("This Month")
    val selectedTimeFilter: LiveData<String> = _selectedTimeFilter

    init {
        refreshTransactions()
    }

    fun refreshTransactions() {
        viewModelScope.launch {
            try {
                val txs = withContext(Dispatchers.IO) {
                    applyMerchantRules(fetchTransactions())
                }
                _recentTransactions.value = txs
                _categorySummary.value = buildCategorySummary(txs)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load expenses: ${e.message}"
            }
        }
    }

    fun filterByCategory(category: String) {
        _selectedCategory.value = if (category.isBlank()) "All" else category
    }

    fun selectTimeFilter(filter: String) {
        _selectedTimeFilter.value = filter
    }

    fun reclassifyTransaction(
        transactionId: String,
        merchantName: String,
        newCategory: String,
        applyToHistory: Boolean
    ) {
        viewModelScope.launch {
            val current = _recentTransactions.value.orEmpty()
            if (current.isEmpty()) return@launch

            val normalizedMerchant = normalizeMerchant(merchantName)
            val normalizedCategory = if (newCategory.equals("Exclude", ignoreCase = true)) {
                "Excluded"
            } else {
                newCategory
            }

            val updated = if (applyToHistory) {
                withContext(Dispatchers.IO) {
                    db.merchantRuleDao().upsertRule(
                        MerchantRuleEntity(
                            merchantPattern = normalizedMerchant,
                            targetCategoryId = normalizedCategory,
                            isExclusion = normalizedCategory == "Excluded"
                        )
                    )
                }
                current.map { tx ->
                    if (normalizeMerchant(tx.description) == normalizedMerchant) {
                        tx.copy(category = normalizedCategory)
                    } else {
                        tx
                    }
                }
            } else {
                current.map { tx ->
                    if (tx.id == transactionId) tx.copy(category = normalizedCategory) else tx
                }
            }

            _recentTransactions.value = updated
            _categorySummary.value = buildCategorySummary(updated)
        }
    }

    private fun fetchTransactions(): List<Transaction> {
        val url = URL(
            "${BuildConfig.BACKEND_BASE_URL}/expenses/transactions?household_id=${BuildConfig.HOUSEHOLD_ID}"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
        }

        connection.inputStream.bufferedReader().use { reader ->
            val jsonText = reader.readText()
            val array = JSONArray(jsonText)
            return (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Transaction(
                    id = obj.optString("id", i.toString()),
                    description = obj.optString("description", ""),
                    amount = obj.optDouble("amount", 0.0),
                    category = obj.optString("category", "Uncategorized"),
                    date = obj.optString("date", "")
                )
            }
        }
    }

    private suspend fun applyMerchantRules(transactions: List<Transaction>): List<Transaction> {
        val rules = db.merchantRuleDao().getAllRules()
        if (rules.isEmpty()) return transactions

        val byMerchant = rules.associateBy { normalizeMerchant(it.merchantPattern) }
        return transactions.map { tx ->
            val merchant = normalizeMerchant(tx.description)
            val rule = byMerchant[merchant]
            if (rule != null) {
                tx.copy(category = if (rule.isExclusion) "Excluded" else rule.targetCategoryId)
            } else {
                tx
            }
        }
    }

    private fun buildCategorySummary(transactions: List<Transaction>): List<CategorySummary> {
        return transactions
            .filterNot { it.category.equals("Excluded", ignoreCase = true) }
            .groupBy { it.category }
            .map { (category, txs) ->
                CategorySummary(
                    category = category,
                    totalAmount = txs.sumOf { it.amount },
                    transactionCount = txs.size
                )
            }
            .sortedByDescending { kotlin.math.abs(it.totalAmount) }
    }

    private fun normalizeMerchant(raw: String): String {
        val cleaned = raw
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
        return cleaned
    }
}
