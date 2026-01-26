package com.flintr.tv.data.local

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.TvContractCompat
import com.flintr.tv.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Service for managing Android TV Watch Next / Play Next integration.
 * Handles adding, updating, and removing watch next entries in the Android TV homescreen.
 */
@Singleton
class WatchNextService @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher,
    private val cacheService: DataCacheService,
    private val credentialsRepository: CredentialsRepository
) {
    companion object {
        private const val TAG = "WatchNextService"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val IMAGE_TIMEOUT_MS = 5000L
        private const val MIN_POSTER_WIDTH = 320
        private const val MIN_POSTER_HEIGHT = 180
        private const val IDEAL_POSTER_WIDTH = 1280
        private const val IDEAL_POSTER_HEIGHT = 720
    }

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Content type enum for Watch Next entries
     */
    enum class ContentType {
        VOD,
        SERIES
    }

    /**
     * Parameters for Watch Next program operations
     */
    data class WatchNextProgramParams(
        val contentType: ContentType,
        val streamId: Int? = null,
        val seriesId: Int? = null,
        val seasonNumber: Int? = null,
        val episodeId: Int? = null,
        val title: String,
        val description: String? = null,
        val posterUrl: String? = null,
        val progress: Float,
        val duration: Long,
        val lastEngagementTime: Long = System.currentTimeMillis()
    )

    /**
     * Adds or updates a Watch Next entry.
     * 
     * @param contentType Content type (VOD or SERIES)
     * @param streamId Stream ID for VOD, or episode ID for series
     * @param seriesId Series ID (only for series)
     * @param seasonNumber Season number (only for series)
     * @param episodeId Episode ID (only for series)
     * @param title Content title
     * @param description Content description (optional)
     * @param posterUrl Poster image URL (optional, must be HTTPS)
     * @param progress Playback progress (0.0 - 1.0)
     * @param duration Total duration in milliseconds
     * @param lastEngagementTime Last engagement timestamp in milliseconds
     */
    @SuppressLint("RestrictedApi")
    suspend fun addOrUpdateWatchNextProgram(
        contentType: ContentType,
        streamId: Int? = null,
        seriesId: Int? = null,
        seasonNumber: Int? = null,
        episodeId: Int? = null,
        title: String,
        description: String? = null,
        posterUrl: String? = null,
        progress: Float,
        duration: Long,
        lastEngagementTime: Long = System.currentTimeMillis()
    ) {
        withContext(ioDispatcher) {
            try {
                val internalProviderId = generateInternalProviderId(contentType, streamId, seriesId, seasonNumber, episodeId)
                val posterUrlToUse = posterUrl ?: getPosterUrlFromCache(contentType, streamId, seriesId, episodeId)
                val validatedPosterUrl = validatePosterUrl(posterUrlToUse)

                if (validatedPosterUrl == null) {
                    Log.w(TAG, "No valid poster URL for Watch Next entry: $internalProviderId - Android TV may not display this entry!")
                }

                val programType = when (contentType) {
                    ContentType.VOD -> TvContractCompat.PreviewPrograms.TYPE_MOVIE
                    ContentType.SERIES -> TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
                }

                val deepLinkUri = buildDeepLinkUri(contentType, streamId, seriesId, seasonNumber, episodeId)
                val deepLinkUriString = deepLinkUri.toString()
                validateDeepLinkUri(deepLinkUriString)

                val values = buildWatchNextContentValues(
                    internalProviderId, programType, title, lastEngagementTime,
                    duration, deepLinkUriString, validatedPosterUrl, posterUrlToUse
                )

                val existingUri = findExistingProgram(internalProviderId)
                insertOrUpdateEntry(existingUri, values, internalProviderId, title, programType, deepLinkUri, validatedPosterUrl, duration, lastEngagementTime)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add/update Watch Next entry", e)
            }
        }
    }

    /**
     * Removes a Watch Next entry by internal provider ID.
     */
    suspend fun removeWatchNextProgram(internalProviderId: String) {
        withContext(ioDispatcher) {
            try {
                val existingUri = findExistingProgram(internalProviderId)
                if (existingUri != null) {
                    retryOperation("delete") {
                        contentResolver.delete(existingUri, null, null)
                    }
                    Log.d(TAG, "Removed Watch Next entry: $internalProviderId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove Watch Next entry: $internalProviderId", e)
            }
        }
    }

    /**
     * Removes all Watch Next entries created by this app.
     */
    suspend fun removeAllWatchNextPrograms() {
        withContext(ioDispatcher) {
            try {
                processWatchNextEntries { id -> removeWatchNextProgram(id) }
                Log.d(TAG, "Removed all Watch Next entries")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove all Watch Next entries", e)
            }
        }
    }

    /**
     * Cleans up invalid Watch Next entries (entries that no longer exist in the catalog).
     *
     * @param validIds Set of valid internal provider IDs to keep
     */
    suspend fun cleanupInvalidEntries(validIds: Set<String>) {
        withContext(ioDispatcher) {
            try {
                processWatchNextEntries { id ->
                    if (id !in validIds) {
                        removeWatchNextProgram(id)
                        Log.d(TAG, "Cleaned up invalid Watch Next entry: $id")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cleanup invalid Watch Next entries", e)
            }
        }
    }

    /**
     * Gets poster URL from cache if not provided.
     * Tries to find VOD or Series data and extract poster URL.
     */
    private suspend fun getPosterUrlFromCache(
        contentType: ContentType,
        streamId: Int?,
        seriesId: Int?,
        episodeId: Int?
    ): String? {
        return try {
            credentialsRepository.getCredentials() ?: return null
            val cachedData = cacheService.loadCachedDataAsync() ?: return null
            
            when (contentType) {
                ContentType.VOD -> {
                    if (streamId != null) {
                        val vodStream = cachedData.movies.find { it.streamId == streamId }
                        // Prefer cover over streamIcon (cover is usually higher quality)
                        vodStream?.cover ?: vodStream?.streamIcon
                    } else {
                        null
                    }
                }
                ContentType.SERIES -> {
                    if (seriesId != null && episodeId != null) {
                        val series = cachedData.series.find { it.seriesId == seriesId }
                        series?.seasons?.forEach { season ->
                            season.episodes?.forEach { episode ->
                                if (episode.id == episodeId) {
                                    // Prefer episode poster, fallback to series cover
                                    return episode.info?.movieImage ?: series.cover
                                }
                            }
                        }
                        // Fallback to series cover if episode not found
                        series?.cover
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get poster URL from cache", e)
            null
        }
    }
    
    /**
     * Generates a stable internal provider ID for a content item.
     */
    private fun generateInternalProviderId(
        contentType: ContentType,
        streamId: Int?,
        seriesId: Int?,
        seasonNumber: Int?,
        episodeId: Int?
    ): String {
        return when (contentType) {
            ContentType.VOD -> {
                requireNotNull(streamId) { "streamId is required for VOD" }
                "vod_$streamId"
            }
            ContentType.SERIES -> {
                requireNotNull(seriesId) { "seriesId is required for SERIES" }
                requireNotNull(seasonNumber) { "seasonNumber is required for SERIES" }
                requireNotNull(episodeId) { "episodeId is required for SERIES" }
                "series_${seriesId}_s${seasonNumber}_e${episodeId}"
            }
        }
    }

    /**
     * Builds a deep link URI for the content.
     * Android TV expects an Intent URI format: intent://...#Intent;scheme=flintrtv;package=com.flintr.tv;end
     */
    private fun buildDeepLinkUri(
        contentType: ContentType,
        streamId: Int?,
        seriesId: Int?,
        seasonNumber: Int?,
        episodeId: Int?
    ): Uri {
        // Build the custom scheme URI first
        val customUriBuilder = Uri.Builder()
            .scheme("flintrtv")
            .authority("play")
            .appendQueryParameter("type", contentType.name.lowercase())

        when (contentType) {
            ContentType.VOD -> {
                streamId?.let { customUriBuilder.appendQueryParameter("streamId", it.toString()) }
            }
            ContentType.SERIES -> {
                seriesId?.let { customUriBuilder.appendQueryParameter("seriesId", it.toString()) }
                seasonNumber?.let { customUriBuilder.appendQueryParameter("seasonNumber", it.toString()) }
                episodeId?.let { customUriBuilder.appendQueryParameter("episodeId", it.toString()) }
            }
        }
        
        val customUri = customUriBuilder.build()
        
        // Convert to Intent URI format for Android TV
        // Format: intent://play?type=vod&streamId=123#Intent;scheme=flintrtv;package=com.flintr.tv;end
        val intentUriString = StringBuilder()
            .append("intent://play")
            .append(customUri.query?.let { "?$it" } ?: "")
            .append("#Intent;scheme=flintrtv;package=com.flintr.tv;end")
            .toString()
        
        return intentUriString.toUri()
    }

    /**
     * Validates a poster URL:
     * - Must be HTTPS (not HTTP)
     * - Should be reachable (with timeout)
     * - Returns null if invalid (Android TV will show app icon as fallback)
     */
    private suspend fun validatePosterUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null

        // Must be HTTPS
        if (!url.startsWith("https://", ignoreCase = true)) {
            Log.w(TAG, "Poster URL is not HTTPS: $url")
            return null
        }

        // Try to validate image (with timeout)
        return try {
            withContext(ioDispatcher) {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = IMAGE_TIMEOUT_MS.toInt()
                connection.readTimeout = IMAGE_TIMEOUT_MS.toInt()
                connection.requestMethod = "HEAD"
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Check content type
                    val contentType = connection.contentType
                    if (contentType != null && (contentType.startsWith("image/jpeg") || contentType.startsWith("image/png"))) {
                        url
                    } else {
                        Log.w(TAG, "Poster URL has invalid content type: $contentType")
                        null
                    }
                } else {
                    Log.w(TAG, "Poster URL returned status code: $responseCode")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to validate poster URL: $url", e)
            null
        }
    }

    /**
     * Finds an existing Watch Next program by internal provider ID.
     * Note: Android TV Provider API doesn't allow selections, so we must query all and filter in memory.
     */
    private fun findExistingProgram(internalProviderId: String): Uri? {
        // Android TV Provider API doesn't allow WHERE clauses, so we query all entries
        val projection = arrayOf(
            android.provider.BaseColumns._ID,
            TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
        )

        val cursor = contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            projection,
            null, // No selection allowed
            null, // No selection args
            null
        )

        return cursor?.use {
            val idIndex = it.getColumnIndex(android.provider.BaseColumns._ID)
            val providerIdIndex = it.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
            
            if (idIndex >= 0 && providerIdIndex >= 0) {
                while (it.moveToNext()) {
                    val providerId = it.getString(providerIdIndex)
                    if (providerId == internalProviderId) {
                        val id = it.getLong(idIndex)
                        // Build URI manually
                        return Uri.withAppendedPath(
                            TvContractCompat.WatchNextPrograms.CONTENT_URI,
                            id.toString()
                        )
                    }
                }
            }
            null
        }
    }

    /**
     * Debug function to list all Watch Next entries (for troubleshooting).
     */
    @SuppressLint("RestrictedApi")
    suspend fun debugListAllWatchNextEntries() {
        withContext(ioDispatcher) {
            try {
                val projection = getDebugProjection()
                val cursor = contentResolver.query(TvContractCompat.WatchNextPrograms.CONTENT_URI, projection, null, null, null)

                cursor?.use { logDebugEntries(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list Watch Next entries", e)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun getDebugProjection(): Array<String> = arrayOf(
        android.provider.BaseColumns._ID,
        TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
        TvContractCompat.WatchNextPrograms.COLUMN_TITLE,
        TvContractCompat.WatchNextPrograms.COLUMN_TYPE,
        TvContractCompat.WatchNextPrograms.COLUMN_INTENT_URI,
        TvContractCompat.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS
    )

    @SuppressLint("RestrictedApi")
    private fun logDebugEntries(cursor: android.database.Cursor) {
        val idIndex = cursor.getColumnIndex(android.provider.BaseColumns._ID)
        val providerIdIndex = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
        val titleIndex = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_TITLE)
        val typeIndex = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_TYPE)
        val intentIndex = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_INTENT_URI)
        val engagementIndex = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS)

        Log.d(TAG, "=== Watch Next Entries (Total: ${cursor.count}) ===")

        if (cursor.count == 0) {
            Log.d(TAG, "No Watch Next entries found")
        } else {
            while (cursor.moveToNext()) {
                val entry = extractDebugEntryInfo(cursor, idIndex, providerIdIndex, titleIndex, typeIndex, intentIndex, engagementIndex)
                Log.d(TAG, "Entry: id=${entry.id}, providerId=${entry.providerId}, title=${entry.title}, type=${entry.type}, intent=${entry.intent}, engagement=${entry.engagement}")
            }
        }
        Log.d(TAG, "=== End Watch Next Entries ===")
    }
    
    /**
     * Checks if an internal provider ID belongs to this app.
     */
    private fun isOurProviderId(id: String?): Boolean {
        return id != null && (id.startsWith("vod_") || id.startsWith("series_"))
    }

    /**
     * Processes Watch Next entries from cursor with given action.
     */
    private suspend fun processWatchNextEntries(action: suspend (String) -> Unit) {
        val projection = arrayOf(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
        val cursor = contentResolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            projection,
            null,
            null,
            null
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
            if (idIndex < 0) return@use

            while (it.moveToNext()) {
                val id = it.getString(idIndex)
                if (isOurProviderId(id)) {
                    action(id)
                }
            }
        }
    }

    /**
     * Sets poster URL in ContentValues with fallback logic.
     */
    @SuppressLint("RestrictedApi")
    private fun ContentValues.setPosterUrl(validatedPosterUrl: String?, posterUrlToUse: String?, internalProviderId: String) {
        when {
            validatedPosterUrl != null -> {
                put(TvContractCompat.WatchNextPrograms.COLUMN_POSTER_ART_URI, validatedPosterUrl)
            }
            posterUrlToUse != null -> {
                Log.w(TAG, "Setting unvalidated poster URL: $posterUrlToUse")
                put(TvContractCompat.WatchNextPrograms.COLUMN_POSTER_ART_URI, posterUrlToUse)
            }
            else -> {
                Log.e(TAG, "CRITICAL: No poster URL available for Watch Next entry - Android TV may not display this!")
            }
        }
    }

    /**
     * Validates deep link URI and logs result.
     */
    private fun validateDeepLinkUri(deepLinkUriString: String) {
        try {
            val intent = Intent.parseUri(deepLinkUriString, Intent.URI_INTENT_SCHEME)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Deep link URI validated: $deepLinkUriString, intent=$intent")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Deep link URI may be invalid: $deepLinkUriString", e)
        }
    }

    /**
     * Builds ContentValues for Watch Next program.
     */
    @SuppressLint("RestrictedApi")
    private fun buildWatchNextContentValues(
        internalProviderId: String,
        programType: Int,
        title: String,
        lastEngagementTime: Long,
        duration: Long,
        deepLinkUriString: String,
        validatedPosterUrl: String?,
        posterUrlToUse: String?
    ): ContentValues {
        return ContentValues().apply {
            put(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, internalProviderId)
            put(TvContractCompat.WatchNextPrograms.COLUMN_TYPE, programType)
            put(TvContractCompat.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            put(TvContractCompat.WatchNextPrograms.COLUMN_TITLE, title)
            put(TvContractCompat.WatchNextPrograms.COLUMN_CONTENT_ID, internalProviderId)
            put(TvContractCompat.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, lastEngagementTime)
            put(TvContractCompat.WatchNextPrograms.COLUMN_DURATION_MILLIS, duration)
            put(TvContractCompat.WatchNextPrograms.COLUMN_INTENT_URI, deepLinkUriString)
            setPosterUrl(validatedPosterUrl, posterUrlToUse, internalProviderId)
        }
    }

    /**
     * Inserts or updates Watch Next entry and logs result.
     */
    private suspend fun insertOrUpdateEntry(
        existingUri: Uri?,
        values: ContentValues,
        internalProviderId: String,
        title: String,
        programType: Int,
        deepLinkUri: Uri,
        validatedPosterUrl: String?,
        duration: Long,
        lastEngagementTime: Long
    ) {
        if (existingUri != null) {
            val updateCount = retryOperation("update") {
                contentResolver.update(existingUri, values, null, null)
            }
            Log.d(TAG, "Updated Watch Next entry: $internalProviderId, updateCount=$updateCount, uri=$existingUri")
            logDebugValues(title, programType, deepLinkUri, validatedPosterUrl, duration, lastEngagementTime)
        } else {
            val insertedUri = retryOperation("insert") {
                contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
            }
            if (insertedUri != null) {
                Log.d(TAG, "Added Watch Next entry: $internalProviderId, uri=$insertedUri")
                logDebugValues(title, programType, deepLinkUri, validatedPosterUrl, duration, lastEngagementTime)
            } else {
                Log.e(TAG, "Failed to insert Watch Next entry: $internalProviderId (insert returned null)")
            }
        }
    }

    /**
     * Logs debug values if in debug mode.
     */
    private suspend fun logDebugValues(
        title: String,
        programType: Int,
        deepLinkUri: Uri,
        validatedPosterUrl: String?,
        duration: Long,
        lastEngagementTime: Long
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Watch Next values: title=$title, type=$programType, deepLink=$deepLinkUri, poster=$validatedPosterUrl, duration=$duration, lastEngagement=$lastEngagementTime")
            debugListAllWatchNextEntries()
        }
    }

    /**
     * Data class for debug entry information.
     */
    private data class DebugEntryInfo(
        val id: Long,
        val providerId: String,
        val title: String,
        val type: Int,
        val intent: String,
        val engagement: Long
    )

    /**
     * Extracts debug entry info from cursor at current position.
     */
    private fun extractDebugEntryInfo(
        cursor: android.database.Cursor,
        idIndex: Int,
        providerIdIndex: Int,
        titleIndex: Int,
        typeIndex: Int,
        intentIndex: Int,
        engagementIndex: Int
    ): DebugEntryInfo {
        return DebugEntryInfo(
            id = if (idIndex >= 0) cursor.getLong(idIndex) else -1,
            providerId = if (providerIdIndex >= 0) cursor.getString(providerIdIndex) ?: "N/A" else "N/A",
            title = if (titleIndex >= 0) cursor.getString(titleIndex) ?: "N/A" else "N/A",
            type = if (typeIndex >= 0) cursor.getInt(typeIndex) else -1,
            intent = if (intentIndex >= 0) cursor.getString(intentIndex) ?: "N/A" else "N/A",
            engagement = if (engagementIndex >= 0) cursor.getLong(engagementIndex) else -1
        )
    }

    /**
     * Retries an operation up to MAX_RETRIES times with delay.
     */
    private suspend fun <T> retryOperation(operationName: String, operation: () -> T): T? {
        var lastException: Exception? = null
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    Log.w(TAG, "Retry $operationName attempt ${attempt + 1}/$MAX_RETRIES failed, retrying...", e)
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        
        Log.e(TAG, "Failed $operationName after $MAX_RETRIES attempts", lastException)
        return null
    }
}
