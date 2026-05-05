package com.household.app.data

import android.content.Context
import org.json.JSONObject
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Loads wallet data from embedded wallet_data.json resource.
 * This JSON is exported from the Expenses app database.
 */
class WalletDataLoader(private val context: Context) {

    data class WalletTransaction(
        val id: Int,
        val title: String,
        val category: String,
        val amount: Double,
        val date: LocalDate,
        val paymentType: String,  // "Bank" from JSON → "Card", cash flagged separately
        val trip: String? = null,
        val note: String = "",
        val bankName: String = "",
        val excluded: Boolean = false
    )

    data class WalletTrip(
        val name: String,
        val budget: Double
    )

    /**
     * Load all transactions from wallet_data.json resource.
     */
    fun loadTransactions(): List<WalletTransaction> {
        return try {
            val jsonText = readJsonResource()
            val json = JSONObject(jsonText)
            val transactions = mutableListOf<WalletTransaction>()
            
            val formatter = DateTimeFormatter.ISO_DATE
            val txArray = json.getJSONArray("transactions")
            
            for (i in 0 until txArray.length()) {
                val tx = txArray.getJSONObject(i)
                
                try {
                    transactions.add(
                        WalletTransaction(
                            id = tx.getInt("id"),
                            title = tx.getString("title"),
                            category = tx.getString("category").ifBlank { "Other" },
                            amount = tx.getDouble("amount"),
                            date = LocalDate.parse(tx.getString("date"), formatter),
                            paymentType = tx.optString("bank", "Card").let { 
                                if (it.equals("N26", ignoreCase = true) || it.equals("Commerzbank", ignoreCase = true)) "Card" else "Bank"
                            },
                            trip = null,  // Not linking trips to individual txns for now
                            note = tx.optString("budgetCategory", ""),
                            bankName = tx.optString("bank", ""),
                            excluded = false
                        )
                    )
                } catch (e: Exception) {
                    // Skip malformed transactions
                    continue
                }
            }
            
            transactions
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Load all trips from wallet_data.json resource.
     */
    fun loadTrips(): List<WalletTrip> {
        return try {
            val jsonText = readJsonResource()
            val json = JSONObject(jsonText)
            val trips = mutableListOf<WalletTrip>()
            
            val tripArray = json.getJSONArray("trips")
            
            for (i in 0 until tripArray.length()) {
                val trip = tripArray.getJSONObject(i)
                
                try {
                    trips.add(
                        WalletTrip(
                            name = trip.getString("name"),
                            budget = trip.getDouble("budget")
                        )
                    )
                } catch (e: Exception) {
                    continue
                }
            }
            
            trips
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readJsonResource(): String {
        val resourceId = context.resources.getIdentifier(
            "wallet_data",
            "raw",
            context.packageName
        )
        
        return if (resourceId == 0) {
            "{\"transactions\":[], \"trips\":[]}"
        } else {
            context.resources.openRawResource(resourceId).use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    reader.readText()
                }
            }
        }
    }
}
