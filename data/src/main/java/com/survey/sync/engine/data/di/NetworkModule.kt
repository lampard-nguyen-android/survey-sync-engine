package com.survey.sync.engine.data.di

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.survey.sync.engine.data.network.DomainResultCallAdapterFactory
import com.survey.sync.engine.data.remote.api.SurveyApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module for providing network-related dependencies.
 * Configures Retrofit, OkHttp, Moshi, and API services.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Provides HTTP logging interceptor for debugging API calls.
     */
    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    /**
     * Provides OkHttpClient with timeout configurations.
     * Configured for rural areas with poor connectivity.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS) // Longer timeout for poor connectivity
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Provides Moshi instance configured with Kotlin support.
     * Supports code generation via @JsonClass(generateAdapter = true).
     */
    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    /**
     * Provides Retrofit instance configured with DomainResult CallAdapter and Moshi converter.
     * The CallAdapter automatically wraps all API responses in DomainResult for error handling.
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(SurveyApiService.BASE_URL)
            .client(okHttpClient)
            .addCallAdapterFactory(DomainResultCallAdapterFactory(moshi))
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    /**
     * Provides SurveyApiService implementation.
     */
    @Provides
    @Singleton
    fun provideSurveyApiService(retrofit: Retrofit): SurveyApiService {
        return retrofit.create(SurveyApiService::class.java)
    }
}
