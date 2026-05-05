package com.household.app.ui.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.household.app.BuildConfig
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

class ExpensesViewModel : ViewModel() {
    private val _recentTransactions = MutableLiveData<List<Transaction>>()
    val recentTransactions: LiveData<List<Transaction>> = _recentTransactions

    private val _categorySummary = MutableLiveData<List<CategorySummary>>()
    val categorySummary: LiveData<List<CategorySummary>> = _categorySummary

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        refreshTransactions()
    }

    fun refreshTransactions() {
        viewModelScope.launch {
            try {
                val txs = withContext(Dispatchers.IO) { fetchTransactions() }
                _recentTransactions.value = txs
                _categorySummary.value = buildCategorySummary(txs)
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load expenses: ${e.message}"
            }
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

    private fun buildCategorySummary(transactions: List<Transaction>): List<CategorySummary> {
        return transactions
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
}
