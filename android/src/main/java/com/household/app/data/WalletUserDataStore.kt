package com.household.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.household.app.data.entities.WalletTransactionEntity
import com.household.app.data.entities.WalletTripEntity
import com.household.app.data.entities.TransactionOverrideEntity
import com.household.app.data.entities.ExcludedTransactionEntity

/**
 * Persists user wallet changes to Room database.
 * All data is stored locally on device:
 * imported CSV transactions, user-created trips, exclusions and category/trip overrides.
 */
object WalletUserDataStore {

    suspend fun loadMergedTransactions(
        context: Context,
        base: List<WalletDataLoader.WalletTransaction>
    ): List<WalletDataLoader.WalletTransaction> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val imported = db.walletTransactionDao().getAllTransactions()
        val excludedIds = db.excludedTransactionDao().getExcludedIds()
        val overrides = db.transactionOverrideDao().getAllOverrides()

        // Build override maps
        val categoryOverrides = mutableMapOf<String, String>()
        val tripOverrides = mutableMapOf<String, String>()
        overrides.forEach { override ->
            if (override.categoryOverride != null) {
                categoryOverrides[override.transactionId.toString()] = override.categoryOverride
            }
            if (override.tripOverride != null) {
                tripOverrides[override.transactionId.toString()] = override.tripOverride
            }
        }

        // Keep first-seen transaction id only to avoid duplicates from repeated imports.
        val deduped = LinkedHashMap<Int, WalletDataLoader.WalletTransaction>()
        
        // First add base transactions
        base.forEach { tx ->
            if (!deduped.containsKey(tx.id)) {
                deduped[tx.id] = tx
            }
        }
        
        // Then add imported transactions, checking for duplicates
        imported.forEach { entity ->
            if (!deduped.containsKey(entity.id)) {
                deduped[entity.id] = WalletDataLoader.WalletTransaction(
                    id = entity.id,
                    title = entity.title,
                    category = entity.category,
                    amount = entity.amount,
                    date = entity.date,
                    paymentType = entity.paymentType,
                    trip = entity.trip,
                    note = entity.note,
                    bankName = entity.bankName,
                    excluded = entity.excluded
                )
            }
        }

        return@withContext deduped.values.map { tx ->
            tx.copy(
                category = categoryOverrides[tx.id.toString()] ?: tx.category,
                trip = tripOverrides[tx.id.toString()] ?: tx.trip,
                excluded = tx.excluded || excludedIds.contains(tx.id)
            )
        }
    }

    suspend fun loadMergedTrips(
        context: Context,
        base: List<WalletDataLoader.WalletTrip>
    ): List<WalletDataLoader.WalletTrip> = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val userTrips = db.walletTripDao().getAllTrips()
        val deduped = LinkedHashMap<String, WalletDataLoader.WalletTrip>()
        
        // First add base trips
        base.forEach { trip ->
            val key = trip.name.trim().lowercase()
            if (key.isNotBlank()) {
                deduped[key] = trip
            }
        }
        
        // Then add user-created trips
        userTrips.forEach { entity ->
            val key = entity.name.trim().lowercase()
            if (key.isNotBlank()) {
                deduped[key] = WalletDataLoader.WalletTrip(
                    name = entity.name,
                    budget = entity.budget
                )
            }
        }
        
        return@withContext deduped.values.toList()
    }

    suspend fun appendImportedTransactions(
        context: Context,
        transactions: List<WalletDataLoader.WalletTransaction>
    ) = withContext(Dispatchers.IO) {
        if (transactions.isEmpty()) return@withContext
        val db = AppDatabase.getInstance(context)
        val dao = db.walletTransactionDao()
        
        val existing = dao.getAllTransactions()
        val existingIds = existing.map { it.id }.toMutableSet()
        
        val newTransactions = transactions.filter { !existingIds.contains(it.id) }.map { tx ->
            WalletTransactionEntity(
                id = tx.id,
                title = tx.title,
                category = tx.category,
                amount = tx.amount,
                date = tx.date,
                paymentType = tx.paymentType,
                trip = tx.trip,
                note = tx.note,
                bankName = tx.bankName,
                excluded = tx.excluded
            )
        }
        
        if (newTransactions.isNotEmpty()) {
            dao.insertTransactions(newTransactions)
        }
    }

    suspend fun appendUserTrips(
        context: Context,
        trips: List<WalletDataLoader.WalletTrip>
    ) = withContext(Dispatchers.IO) {
        if (trips.isEmpty()) return@withContext
        val db = AppDatabase.getInstance(context)
        val dao = db.walletTripDao()
        
        val existing = dao.getAllTrips()
        val existingNames = existing.map { it.name.trim().lowercase() }.toMutableSet()
        
        val newTrips = trips.filter { trip ->
            val key = trip.name.trim().lowercase()
            key.isNotBlank() && !existingNames.contains(key)
        }.map { trip ->
            WalletTripEntity(name = trip.name, budget = trip.budget)
        }
        
        if (newTrips.isNotEmpty()) {
            dao.insertTrips(newTrips)
        }
    }

    suspend fun setTransactionExcluded(
        context: Context,
        transactionId: Int,
        excluded: Boolean
    ) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.excludedTransactionDao()
        
        if (excluded) {
            dao.insertExcluded(ExcludedTransactionEntity(transactionId))
        } else {
            dao.deleteExcluded(ExcludedTransactionEntity(transactionId))
        }
    }

    suspend fun setCategoryOverride(
        context: Context,
        transactionId: Int,
        category: String?
    ) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.transactionOverrideDao()
        
        val existing = dao.getOverride(transactionId)
        if (existing != null) {
            dao.updateOverride(existing.copy(categoryOverride = category))
        } else {
            dao.insertOverride(TransactionOverrideEntity(transactionId, categoryOverride = category))
        }
    }

    suspend fun setTripOverride(
        context: Context,
        transactionId: Int,
        trip: String?
    ) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val dao = db.transactionOverrideDao()
        
        val existing = dao.getOverride(transactionId)
        if (existing != null) {
            dao.updateOverride(existing.copy(tripOverride = trip))
        } else {
            dao.insertOverride(TransactionOverrideEntity(transactionId, tripOverride = trip))
        }
    }

    suspend fun nextUserTransactionId(context: Context, baselineMaxId: Int): Int = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val imported = db.walletTransactionDao().getAllTransactions()
        val importedMax = imported.maxOfOrNull { it.id } ?: 0
        maxOf(900000, baselineMaxId + 1, importedMax + 1)
    }
}
