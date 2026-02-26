package com.survey.sync.engine.data.di

import com.survey.sync.engine.data.repository.SurveyRepositoryImpl
import com.survey.sync.engine.domain.repository.SurveyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for binding repository interfaces to their implementations.
 * Uses @Binds for more efficient dependency injection compared to @Provides.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Binds SurveyRepository interface to its implementation.
     * This allows the domain layer to depend on the interface while the
     * data layer provides the concrete implementation.
     */
    @Binds
    @Singleton
    abstract fun bindSurveyRepository(
        surveyRepositoryImpl: SurveyRepositoryImpl
    ): SurveyRepository
}
