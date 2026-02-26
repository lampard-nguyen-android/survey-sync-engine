package com.survey.sync.engine.data.di

import android.content.Context
import androidx.room.Room
import com.survey.sync.engine.data.dao.AnswerDao
import com.survey.sync.engine.data.dao.QuestionDefinitionDao
import com.survey.sync.engine.data.dao.SurveyDao
import com.survey.sync.engine.data.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing Room database and DAO instances.
 * All dependencies are scoped as Singleton for app-wide single instance.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the Room database instance.
     * Uses fallbackToDestructiveMigration for development convenience.
     * In production, implement proper migration strategies.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // For development - remove in production
            .build()
    }

    /**
     * Provides SurveyDao from the database.
     */
    @Provides
    @Singleton
    fun provideSurveyDao(database: AppDatabase): SurveyDao {
        return database.surveyDao()
    }

    /**
     * Provides QuestionDefinitionDao from the database.
     */
    @Provides
    @Singleton
    fun provideQuestionDefinitionDao(database: AppDatabase): QuestionDefinitionDao {
        return database.questionDefinitionDao()
    }

    /**
     * Provides AnswerDao from the database.
     */
    @Provides
    @Singleton
    fun provideAnswerDao(database: AppDatabase): AnswerDao {
        return database.answerDao()
    }
}
