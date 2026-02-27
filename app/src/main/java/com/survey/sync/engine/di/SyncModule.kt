package com.survey.sync.engine.di

import com.survey.sync.engine.domain.sync.SyncScheduler
import com.survey.sync.engine.work.SyncWorkManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for sync-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    /**
     * Bind SyncWorkManager as the implementation of SyncScheduler.
     * This allows the presentation layer to depend on the abstraction
     * rather than the concrete WorkManager implementation.
     */
    @Binds
    @Singleton
    abstract fun bindSyncScheduler(
        syncWorkManager: SyncWorkManager
    ): SyncScheduler
}
