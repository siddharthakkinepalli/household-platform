package com.household.app

import android.app.Application
import com.household.app.vault.DriveDataStore
import com.household.app.vault.workers.PipelineManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

class HouseholdApp : Application() {
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
