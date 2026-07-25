package com.flintr.tv.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.flintr.tv.utils.error.reportHandled

/**
 * Destination type for navigation state
 */
enum class DestinationType {
    LIVE_TV,
    VOD,
    SERIES,
    FAVORITES,
    UNKNOWN;

    /**
     * Converts enum to string for Parcelable serialization
     */
    fun toSerializedString(): String = name

    companion object {
        /**
         * Converts string to enum for Parcelable deserialization
         */
        fun fromSerializedString(value: String): DestinationType {
            return try {
                valueOf(value)
            } catch (e: IllegalArgumentException) {
                reportHandled("NavigationState.fromSerializedString", e)
                UNKNOWN
            }
        }
    }
}

/**
 * State that is saved before navigating to the Player screen.
 * Used to restore navigation position (tab, category, content, scroll position, focus)
 * when user returns from the Player.
 * 
 * Note: destinationType is stored as String for Parcelable compatibility,
 * but should be accessed via the destinationTypeEnum property for type safety.
 */
@Parcelize
data class NavigationStateBeforePlayer(
    private val destinationTypeString: String,  // Serialized DestinationType enum
    val categoryId: String?,
    val contentId: String,        // "live_123", "vod_456", "series_789_1_101"
    val scrollIndex: Int,
    val scrollOffset: Int
) : Parcelable {
    /**
     * Type-safe access to destination type
     */
    val destinationType: DestinationType
        get() = DestinationType.fromSerializedString(destinationTypeString)

    /**
     * Constructor with enum for type safety
     */
    constructor(
        destinationType: DestinationType,
        categoryId: String?,
        contentId: String,
        scrollIndex: Int,
        scrollOffset: Int
    ) : this(
        destinationTypeString = destinationType.toSerializedString(),
        categoryId = categoryId,
        contentId = contentId,
        scrollIndex = scrollIndex,
        scrollOffset = scrollOffset
    )
}
