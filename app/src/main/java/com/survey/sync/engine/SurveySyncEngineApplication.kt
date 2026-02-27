package com.survey.sync.engine

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.survey.sync.engine.work.SyncWorkManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for SurveySyncEngine.
 *
 * Configures:
 * - Hilt for dependency injection
 * - WorkManager for background sync
 * - Periodic sync scheduling
 */
@HiltAndroidApp
class SurveySyncEngineApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncWorkManager: SyncWorkManager

    override fun onCreate() {
        super.onCreate()

        // Schedule periodic background sync
        // This addresses Scenario 1: When agent reaches connectivity,
        // the sync engine will automatically detect pending surveys
        syncWorkManager.schedulePeriodicSync()
    }

    /**
     * Configure WorkManager to use Hilt's WorkerFactory.
     * This enables dependency injection in Workers.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
