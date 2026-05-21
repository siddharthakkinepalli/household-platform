package com.household.app.domain.services

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.household.app.data.AppDatabase
import com.household.app.domain.models.HouseholdBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter
import java.time.LocalDate

class HouseholdExportService(private val db: AppDatabase) {

    private val gson = GsonBuilder()
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .setPrettyPrinting()
        .create()

    suspend fun export(context: Context): Uri = withContext(Dispatchers.IO) {
        val backup = HouseholdBackup(
            walletTransactions = db.walletTransactionDao().getAllTransactions(),
            vaultEntries = db.vaultDao().getAllEntriesList(),
            pantryItems = db.pantryDao().getAllPantry(),
            inventoryEvents = db.inventoryEventDao().getAllEvents(),
            merchantRules = db.merchantRuleDao().getAllRules(),
            categoryThresholds = db.categoryThresholdDao().getAllThresholds(),
            familyMembers = db.familyMemberDao().getAllMembersList(),
            documents = db.documentDao().getAllDocumentsList(),
            documentAlerts = db.documentAlertDao().getAllAlertsList()
        )

        val timestamp = System.currentTimeMillis()
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val outFile = File(downloadsDir, "household_backup_$timestamp.json")

        OutputStreamWriter(outFile.outputStream(), Charsets.UTF_8).buffered().use { writer ->
            gson.toJson(backup, writer)
        }

        Uri.fromFile(outFile)
    }
}

internal class LocalDateAdapter : TypeAdapter<LocalDate?>() {
    override fun write(out: JsonWriter, value: LocalDate?) {
        if (value == null) out.nullValue() else out.value(value.toString())
    }

    override fun read(reader: JsonReader): LocalDate? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return LocalDate.parse(reader.nextString())
    }
}
