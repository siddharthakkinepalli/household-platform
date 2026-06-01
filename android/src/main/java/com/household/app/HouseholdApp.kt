package com.household.app

import android.app.Application
import com.household.app.vault.DriveDataStore
import com.household.app.vault.workers.PipelineManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import javax.inject.Inject

@HiltAndroidApp
class HouseholdApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * Provides the WorkManager configuration that uses [HiltWorkerFactory].
     * Required for @HiltWorker injection in [TransitRefreshWorker] and other Hilt workers.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        OpenCVLoader.initLocal()
        PipelineManager.scheduleExpiryNotifications(this)
        appScope.launch {
            if (DriveDataStore.isDriveEnabled(this@HouseholdApp)) {
                PipelineManager.scheduleTaxChecklistSync(this@HouseholdApp)
                PipelineManager.scheduleDbBackup(this@HouseholdApp)
            }
        }
    }
}
