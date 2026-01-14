package com.flintr.tv.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * State that is saved before navigating to the Player screen.
 * Used to restore navigation position (tab, category, content, scroll position, focus)
 * when user returns from the Player.
 */
@Parcelize
data class NavigationStateBeforePlayer(
    val destinationType: String,  // "LIVE_TV", "VOD", "SERIES", "FAVORITES"
    val categoryId: String?,
    val contentId: String,        // "live_123", "vod_456", "series_789_1_101"
    val scrollIndex: Int,
    val scrollOffset: Int
) : Parcelable
