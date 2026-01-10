package com.flintr.tv.di

import android.content.Context
import com.flintr.tv.data.api.GitHubApiService
import com.flintr.tv.data.api.XtreamApiService
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.repository.UpdateRepository
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.utils.UpdateDownloadService
import com.flintr.tv.utils.UpdateInstaller
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import javax.annotation.Nullable

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    @Nullable
    @Named("Xtream")
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
        credentialsRepository: CredentialsRepository
    ): Retrofit? {
        val baseUrl = credentialsRepository.getCredentials()?.serverUrl
            ?.let { if (it.endsWith("/")) it else "$it/" }
            ?: return null
        
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    @Provides
    @Singleton
    @Nullable
    fun provideXtreamApiService(@Nullable @Named("Xtream") retrofit: Retrofit?): XtreamApiService? {
        return retrofit?.create(XtreamApiService::class.java)
    }
    
    @Provides
    @Singleton
    fun provideXtreamRepository(
        @Nullable apiService: XtreamApiService?,
        credentialsRepository: CredentialsRepository,
        okHttpClient: OkHttpClient,
        moshi: Moshi,
        cacheService: DataCacheService
    ): XtreamRepository {
        return XtreamRepository(apiService, credentialsRepository, okHttpClient, moshi, cacheService)
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
}

