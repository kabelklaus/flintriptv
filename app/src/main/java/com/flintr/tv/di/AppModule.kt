package com.flintr.tv.di

import android.content.Context
import com.flintr.tv.data.api.GitHubApiService
import com.flintr.tv.data.local.CategoryVisibilityService
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.PlaybackSettingsRepository
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.local.FirestoreSyncManager
import com.flintr.tv.data.local.WatchedHistoryService
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.EmptyArrayAsObjectAdapterFactory
import com.flintr.tv.data.model.EpisodeInfo
import com.flintr.tv.data.model.SeriesInfo
import com.flintr.tv.data.model.SeriesInfoResponseAdapterFactory
import com.flintr.tv.data.model.VodInfoResponseAdapterFactory
import com.flintr.tv.data.repository.UpdateRepository
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.utils.UpdateDownloadService
import com.flintr.tv.utils.UpdateInstaller
import com.flintr.tv.utils.error.CrashlyticsErrorReporter
import com.flintr.tv.utils.error.ErrorReporter
import com.flintr.tv.utils.error.FirebaseCrashlyticsSink
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    @Named("IO")
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO
    
    @Provides
    @Singleton
    fun provideErrorReporter(): ErrorReporter =
        CrashlyticsErrorReporter(FirebaseCrashlyticsSink())

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(SeriesInfoResponseAdapterFactory())
            .add(VodInfoResponseAdapterFactory())
            .add(EmptyArrayAsObjectAdapterFactory(setOf(EpisodeInfo::class.java, SeriesInfo::class.java)))
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideXtreamRepository(
        credentialsRepository: CredentialsRepository,
        okHttpClient: OkHttpClient,
        moshi: Moshi,
        cacheService: DataCacheService
    ): XtreamRepository {
        return XtreamRepository(credentialsRepository, okHttpClient, moshi, cacheService)
    }
    
    // Helper function to create Retrofit dynamically for login validation
    fun createRetrofitForUrl(
        serverUrl: String,
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    @Provides
    @Singleton
    @Named("GitHub")
    fun provideGitHubOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        
        // Add Authorization header interceptor for GitHub API
        val authInterceptor = okhttp3.Interceptor { chain ->
            val originalRequest = chain.request()
            val token = com.flintr.tv.BuildConfig.GITHUB_TOKEN
            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "token $token")
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            chain.proceed(authenticatedRequest)
        }
        builder.addInterceptor(authInterceptor)
        
        // Add logging only in debug builds
        if (com.flintr.tv.BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                redactHeader("Authorization")
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }
        
        return builder.build()
    }
    
    @Provides
    @Singleton
    @Named("GitHub")
    fun provideGitHubRetrofit(
        @Named("GitHub") gitHubOkHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(gitHubOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideGitHubApiService(
        @Named("GitHub") retrofit: Retrofit
    ): GitHubApiService {
        return retrofit.create(GitHubApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideUpdateRepository(
        gitHubApiService: GitHubApiService
    ): UpdateRepository {
        return UpdateRepository(gitHubApiService)
    }
    
    @Provides
    @Singleton
    fun provideUserRepository(): UserRepository {
        return UserRepository()
    }
    
    @Provides
    @Singleton
    @Named("Download")
    fun provideDownloadOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        
        // Add Authorization header for private repository downloads
        val authInterceptor = okhttp3.Interceptor { chain ->
            val originalRequest = chain.request()
            val token = com.flintr.tv.BuildConfig.GITHUB_TOKEN
            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "token $token")
                .build()
            chain.proceed(authenticatedRequest)
        }
        builder.addInterceptor(authInterceptor)
        
        // Add logging only in debug builds
        if (com.flintr.tv.BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                redactHeader("Authorization")
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }
        
        return builder.build()
    }
    
    @Provides
    @Singleton
    fun provideUpdateDownloadService(
        @Named("Download") downloadOkHttpClient: OkHttpClient,
        @ApplicationContext context: Context
    ): UpdateDownloadService {
        return UpdateDownloadService(downloadOkHttpClient, context)
    }
    
    @Provides
    @Singleton
    fun provideUpdateInstaller(
        @ApplicationContext context: Context
    ): UpdateInstaller {
        return UpdateInstaller(context)
    }
    
    /**
     * Dependencies container for WatchedHistoryService to reduce parameter count.
     */
    data class WatchedHistoryServiceDependencies(
        val userRepository: UserRepository,
        val credentialsRepository: CredentialsRepository,
        val syncManager: FirestoreSyncManager
    )

    @Provides
    @Singleton
    fun provideWatchedHistoryServiceDependencies(
        userRepository: UserRepository,
        credentialsRepository: CredentialsRepository,
        @Named("WatchedHistory") syncManager: FirestoreSyncManager
    ): WatchedHistoryServiceDependencies {
        return WatchedHistoryServiceDependencies(
            userRepository,
            credentialsRepository,
            syncManager
        )
    }

    @Provides
    @Singleton
    fun provideWatchedHistoryService(
        @ApplicationContext context: Context,
        moshi: Moshi,
        @Named("IO") ioDispatcher: CoroutineDispatcher,
        dependencies: WatchedHistoryServiceDependencies
    ): WatchedHistoryService {
        return WatchedHistoryService(
            context,
            moshi,
            dependencies.userRepository,
            dependencies.credentialsRepository,
            dependencies.syncManager,
            ioDispatcher
        )
    }

    /**
     * Dependencies container for WatchProgressService to reduce parameter count.
     */
    data class WatchProgressServiceDependencies(
        val watchlistRepository: WatchlistRepository,
        val watchedHistoryService: WatchedHistoryService,
        val userRepository: UserRepository,
        val credentialsRepository: CredentialsRepository,
        val cacheService: DataCacheService,
        val syncManager: FirestoreSyncManager
    )

    @Provides
    @Singleton
    fun provideWatchProgressServiceDependencies(
        watchlistRepository: WatchlistRepository,
        watchedHistoryService: WatchedHistoryService,
        userRepository: UserRepository,
        credentialsRepository: CredentialsRepository,
        cacheService: DataCacheService,
        @Named("WatchProgress") syncManager: FirestoreSyncManager
    ): WatchProgressServiceDependencies {
        return WatchProgressServiceDependencies(
            watchlistRepository,
            watchedHistoryService,
            userRepository,
            credentialsRepository,
            cacheService,
            syncManager
        )
    }

    @Provides
    @Singleton
    fun provideWatchProgressService(
        @ApplicationContext context: Context,
        moshi: Moshi,
        @Named("IO") ioDispatcher: CoroutineDispatcher,
        dependencies: WatchProgressServiceDependencies
    ): WatchProgressService {
        return WatchProgressService(
            context,
            moshi,
            dependencies.watchlistRepository,
            dependencies.watchedHistoryService,
            dependencies.userRepository,
            dependencies.credentialsRepository,
            dependencies.cacheService,
            dependencies.syncManager,
            ioDispatcher
        )
    }
    
    @Provides
    @Singleton
    @Named("CategoryVisibility")
    fun provideCategoryVisibilitySyncManager(): FirestoreSyncManager {
        return FirestoreSyncManager(tag = "FirestoreSyncManager-CategoryVisibility")
    }
    
    @Provides
    @Singleton
    @Named("Watchlist")
    fun provideWatchlistSyncManager(): FirestoreSyncManager {
        return FirestoreSyncManager(tag = "FirestoreSyncManager-Favorites")
    }
    
    @Provides
    @Singleton
    @Named("WatchProgress")
    fun provideWatchProgressSyncManager(): FirestoreSyncManager {
        return FirestoreSyncManager(tag = "FirestoreSyncManager-WatchProgress")
    }
    
    @Provides
    @Singleton
    @Named("WatchedHistory")
    fun provideWatchedHistorySyncManager(): FirestoreSyncManager {
        return FirestoreSyncManager(tag = "FirestoreSyncManager-WatchedHistory")
    }
    
    @Provides
    @Singleton
    fun providePlaybackSettingsRepository(
        @ApplicationContext context: Context
    ): PlaybackSettingsRepository {
        return PlaybackSettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideCategoryVisibilityService(
        @ApplicationContext context: Context,
        userRepository: UserRepository,
        credentialsRepository: CredentialsRepository,
        @Named("CategoryVisibility") syncManager: FirestoreSyncManager
    ): CategoryVisibilityService {
        return CategoryVisibilityService(context, userRepository, credentialsRepository, syncManager)
    }
    
    @Provides
    @Singleton
    fun provideResourcesProvider(
        androidResourcesProvider: AndroidResourcesProvider
    ): ResourcesProvider {
        return androidResourcesProvider
    }
}
