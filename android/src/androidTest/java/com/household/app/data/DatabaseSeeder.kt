package com.household.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.household.app.data.entities.WalletTransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseSeeder {

    private lateinit var db: AppDatabase
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        db = AppDatabase.getInstance(context)
    }

    @Test
    fun seedDataFromCsvSamples() = runBlocking {
        db.walletTransactionDao().deleteAllTransactions()

        val transactions = mutableListOf<WalletTransactionEntity>()
        var currentId = 1

        // 1. N26 Sample Data (approx 25 rows)
        val n26Data = """
            2024-05-27,"SIDDHARTH AKKINEPALLI",200.00,"Credit Transfer"
            2024-05-28,"ALDI SUED",-35.46,"Presentment"
            2024-06-01,"REWE Ulm/Mitte Sch",-18.66,"Presentment"
            2024-06-01,"REWE Jochen Widmann oH",-8.86,"Presentment"
            2024-06-01,"HAMMA GMBH & CO. KG",-4.65,"Presentment"
            2024-06-02,"REWE Ulm/Mitte Sch",-17.92,"Presentment"
            2024-06-06,"THAI HAUS TUITONG",-13.00,"Presentment"
            2024-06-06,"REWE Sven Thietz oHG",-18.05,"Presentment"
            2024-06-07,"REWE Jochen Widmann oH",-3.98,"Presentment"
            2024-06-09,"ALDI SUED",-30.61,"Presentment"
            2024-06-10,"VOI GELATO ULM",-3.80,"Presentment"
            2024-06-11,"SumUp  *Bucciol und Ca",-5.40,"Presentment"
            2024-06-11,"ALDI SUED",-38.79,"Presentment"
            2024-06-11,"SIDDHARTH AKKINEPALLI",100.00,"Credit Transfer"
            2024-06-13,"Valora Food Service Gm",-3.60,"Presentment"
            2024-06-14,"MCDONALDS 01807",-14.97,"Presentment"
            2024-06-16,"Subway 73110 Sedelhöfe",-20.27,"Presentment"
            2024-06-17,"N26 Reward",0.44,"Reward"
            2024-06-18,"KONDITOREI KONFISERIE",-11.10,"Presentment"
            2024-06-18,"Woolworth GmbH Fil  12",-46.25,"Presentment"
            2024-06-18,"REWE Jochen Widmann oH",-5.91,"Presentment"
            2024-06-25,"SIDDHARTH AKKINEPALLI",700.00,"Credit Transfer"
            2024-06-27,"*DEUTSCHE BANK AG",-80.00,"Presentment"
            2024-06-28,"Druga-violina",-28.40,"Presentment"
            2024-06-28,"LIDL-SI 306",-7.23,"Presentment"
        """.trimIndent()

        n26Data.lines().forEach { line ->
            val parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
            if (parts.size >= 4) {
                val date = LocalDate.parse(parts[0].trim())
                val title = parts[1].trim().removeSurrounding("\"")
                val amount = parts[2].trim().toDouble()
                val type = parts[3].trim().removeSurrounding("\"")
                
                transactions.add(WalletTransactionEntity(
                    id = currentId++,
                    title = title,
                    category = "Uncategorized",
                    amount = amount,
                    date = date,
                    paymentType = type,
                    bankName = "N26",
                    contentHash = UUID.randomUUID().toString()
                ))
            }
        }

        // 2. Commerzbank Sample Data (approx 25 rows)
        val commerzData = """
            27.05.2026;chithra madhusudhanan;-1312,00;Transfer;Other Expenses
            27.05.2026;SIDDHARTH AKKINEPALLI;130,00;Transfer;Earnings
            27.05.2026;SIDDHARTH AKKINEPALLI;113,38;Transfer;Earnings
            27.05.2026;SIDDHARTH AKKINEPALLI;200,00;Transfer;Earnings
            27.05.2026;TOPTANK;-63,86;Debit;Other Expenses
            27.05.2026;PayPal Europe S.a.r.l.;-11,99;Debit;Online Shopping
            26.05.2026;Siddharth ING;-400,00;Transfer;Other Expenses
            26.05.2026;siddharth akkinepalli;-1000,00;Transfer;Other Expenses
            26.05.2026;ARAL Ulm Karlstraße;-22,39;Debit;Mobility
            26.05.2026;Lindner Gewerbeimmobilien;-809,00;Transfer;Home
            26.05.2026;Wise Europe SA;-700,00;Transfer;Other Expenses
            26.05.2026;Deutsche Bank withdrawal;-100,00;Cash;Cash
            26.05.2026;Parkfläche Wartburg;-7,00;Debit;Parking
            26.05.2026;Elektrobit Automotive GmbH;4497,02;Transfer;Earnings
            25.05.2026;Willbold GmbH;-10,00;Debit;Online Shopping
            25.05.2026;NORMA SAGT DANKE;-40,72;Debit;Living Expenses
            25.05.2026;ENTERPRISE RENT A CAR;-358,03;Debit;Mobility
            21.05.2026;Siddharth Akkinepalli;0,11;Transfer;Earnings
            21.05.2026;NORMA SAGT DANKE;-7,18;Debit;Living Expenses
            20.05.2026;jobsties;-100,00;Transfer;Other Expenses
            20.05.2026;Wise Europe SA;-63,80;Transfer;Other Expenses
            20.05.2026;NORMA SAGT DANKE;-8,70;Debit;Living Expenses
            20.05.2026;PayPal - ACCS International;-193,45;Debit;Online Shopping
            19.05.2026;PayPal - PP.4139.PP;1191,07;Transfer;Earnings
            19.05.2026;Chithra Madhusudhanan;360,00;Transfer;Earnings
        """.trimIndent()

        val commerzFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        commerzData.lines().forEach { line ->
            val parts = line.split(";")
            if (parts.size >= 5) {
                val date = LocalDate.parse(parts[0].trim(), commerzFormatter)
                val title = parts[1].trim()
                val amount = parts[2].trim().replace(",", ".").toDouble()
                val type = parts[3].trim()
                val category = parts[4].trim()

                transactions.add(WalletTransactionEntity(
                    id = currentId++,
                    title = title,
                    category = category,
                    amount = amount,
                    date = date,
                    paymentType = type,
                    bankName = "Commerzbank",
                    contentHash = UUID.randomUUID().toString()
                ))
            }
        }

        db.walletTransactionDao().insertTransactions(transactions)
    }
}
